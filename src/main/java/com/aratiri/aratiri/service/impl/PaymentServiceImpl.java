package com.aratiri.aratiri.service.impl;

import com.aratiri.aratiri.constant.BitcoinConstants;
import com.aratiri.aratiri.dto.invoices.DecodedInvoicetDTO;
import com.aratiri.aratiri.dto.payments.OnChainPaymentDTOs;
import com.aratiri.aratiri.dto.payments.PayInvoiceRequestDTO;
import com.aratiri.aratiri.dto.payments.PaymentResponseDTO;
import com.aratiri.aratiri.dto.transactions.*;
import com.aratiri.aratiri.entity.AccountEntity;
import com.aratiri.aratiri.entity.LightningInvoiceEntity;
import com.aratiri.aratiri.entity.OutboxEventEntity;
import com.aratiri.aratiri.enums.KafkaTopics;
import com.aratiri.aratiri.event.InternalTransferInitiatedEvent;
import com.aratiri.aratiri.event.OnChainPaymentInitiatedEvent;
import com.aratiri.aratiri.event.PaymentInitiatedEvent;
import com.aratiri.aratiri.exception.AratiriException;
import com.aratiri.aratiri.repository.AccountRepository;
import com.aratiri.aratiri.repository.LightningInvoiceRepository;
import com.aratiri.aratiri.repository.OutboxEventRepository;
import com.aratiri.aratiri.service.InvoiceService;
import com.aratiri.aratiri.service.PaymentService;
import com.aratiri.aratiri.service.TransactionsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lnrpc.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import routerrpc.RouterGrpc;
import routerrpc.SendPaymentRequest;
import routerrpc.TrackPaymentRequest;

import java.util.Iterator;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final AccountRepository accountRepository;
    private final TransactionsService transactionsService;
    private final InvoiceService invoiceService;
    private final RouterGrpc.RouterBlockingStub routerStub;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final LightningGrpc.LightningBlockingStub lightningStub;
    private final LightningInvoiceRepository lightningInvoiceRepository;
    @Value("${aratiri.payment.default.fee.limit.sat:200}")
    private int defaultFeeLimitSat;
    @Value("${aratiri.payment.default.timeout.seconds:200}")
    private int defaultTimeoutSeconds;

    @Override
    @Transactional
    public PaymentResponseDTO payLightningInvoice(PayInvoiceRequestDTO request, String userId) {
        DecodedInvoicetDTO decodedInvoice = invoiceService.decodePaymentRequest(request.getInvoice());
        String paymentHash = decodedInvoice.getPaymentHash();
        Optional<LightningInvoiceEntity> internalInvoiceOpt = lightningInvoiceRepository.findByPaymentHash(paymentHash);
        if (internalInvoiceOpt.isPresent()) {
            return processInternalTransfer(request, userId, decodedInvoice, internalInvoiceOpt.get());
        }
        return processExternalPayment(request, userId, paymentHash, decodedInvoice);
    }

    private PaymentResponseDTO processExternalPayment(PayInvoiceRequestDTO request, String userId, String paymentHash, DecodedInvoicetDTO decodedInvoice) {
        boolean isSettledAratiriInvoice = invoiceService.existsSettledInvoiceByPaymentHash(paymentHash);
        if (isSettledAratiriInvoice) {
            logger.warn("User {} attempted to pay an invoice that has already been successfully paid by another Aratiri user. PaymentHash: {}", userId, paymentHash);
            throw new AratiriException(
                    "Invoice has already been paid",
                    HttpStatus.BAD_REQUEST
            );
        }
        Optional<Payment> existingPayment = checkPaymentStatusOnNode(paymentHash);
        if (existingPayment.isPresent() && existingPayment.get().getStatus() == Payment.PaymentStatus.SUCCEEDED) {
            logger.warn("User {} attempted to pay an invoice that has already been successfully paid by this node. PaymentHash: {}", userId, paymentHash);
            throw new AratiriException(
                    "Invoice has already been paid",
                    HttpStatus.BAD_REQUEST
            );
        }
        long amountSat = decodedInvoice.getNumSatoshis();
        AccountEntity account = accountRepository.findByUserId(userId);
        if (account.getBalance() < amountSat) {
            logger.info("Insufficient balance. Tried to pay {} but balance was {}", amountSat, account.getBalance());
            throw new AratiriException("Insufficient balance", HttpStatus.BAD_REQUEST);
        }
        CreateTransactionRequest txRequest = CreateTransactionRequest.builder()
                .userId(userId)
                .amount(BitcoinConstants.satoshisToBtc(amountSat))
                .currency(TransactionCurrency.BTC)
                .type(TransactionType.LIGHTNING_DEBIT)
                .status(TransactionStatus.PENDING)
                .referenceId(paymentHash)
                .description("Lightning Payment: " + decodedInvoice.getDescription())
                .build();

        TransactionDTOResponse txDto = transactionsService.createTransaction(txRequest);
        PaymentInitiatedEvent eventPayload = new PaymentInitiatedEvent(
                userId,
                txDto.getId(),
                request
        );
        try {
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                    .aggregateType("LIGHTNING_INVOICE_PAYMENT")
                    .aggregateId(txDto.getId())
                    .eventType(KafkaTopics.PAYMENT_INITIATED.getCode())
                    .payload(objectMapper.writeValueAsString(eventPayload))
                    .build();
            outboxEventRepository.save(outboxEvent);
            logger.info("Saved PAYMENT_INITIATED event to outbox for transactionId: {}", txDto.getId());
        } catch (Exception e) {
            throw new AratiriException("Failed to create outbox event for payment initiation.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return PaymentResponseDTO.builder()
                .transactionId(txDto.getId())
                .status(txDto.getStatus())
                .message("Payment initiated. Status is pending.")
                .build();
    }

    private PaymentResponseDTO processInternalTransfer(PayInvoiceRequestDTO request, String senderId, DecodedInvoicetDTO decodedInvoice, LightningInvoiceEntity internalInvoice) {
        if (internalInvoice.getInvoiceState() == LightningInvoiceEntity.InvoiceState.SETTLED) {
            throw new AratiriException("The invoice is already paid", HttpStatus.BAD_REQUEST);
        }
        long amountSat = decodedInvoice.getNumSatoshis();
        AccountEntity senderAccount = accountRepository.findByUserId(senderId);
        if (senderAccount.getBalance() < amountSat) {
            throw new AratiriException("Insufficient balance for internal transfer", HttpStatus.BAD_REQUEST);
        }
        if (internalInvoice.getUserId().equals(senderId)) {
            throw new AratiriException("Payment to self is not allowed.", HttpStatus.BAD_REQUEST);
        }
        CreateTransactionRequest txRequest = CreateTransactionRequest.builder()
                .userId(senderId)
                .amount(BitcoinConstants.satoshisToBtc(amountSat))
                .currency(TransactionCurrency.BTC)
                .type(TransactionType.LIGHTNING_DEBIT)
                .status(TransactionStatus.PENDING)
                .referenceId(decodedInvoice.getPaymentHash())
                .description("Internal transfer to: " + internalInvoice.getUserId())
                .build();

        TransactionDTOResponse txDto = transactionsService.createTransaction(txRequest);
        InternalTransferInitiatedEvent eventPayload = new InternalTransferInitiatedEvent(
                txDto.getId(),
                senderId,
                internalInvoice.getUserId(),
                amountSat,
                decodedInvoice.getPaymentHash()
        );
        try {
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                    .aggregateType("INTERNAL_TRANSFER")
                    .aggregateId(txDto.getId())
                    .eventType(KafkaTopics.INTERNAL_TRANSFER_INITIATED.getCode())
                    .payload(objectMapper.writeValueAsString(eventPayload))
                    .build();
            outboxEventRepository.save(outboxEvent);
            logger.info("Saved INTERNAL_TRANSFER_INITIATED event to outbox for transactionId: {}", txDto.getId());
        } catch (Exception e) {
            throw new AratiriException("Failed to create outbox event for internal transfer.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return PaymentResponseDTO.builder()
                .transactionId(txDto.getId())
                .status(TransactionStatus.PENDING)
                .message("Internal transfer initiated.")
                .build();
    }

    @Async
    public void initiateGrpcLightningPayment(String transactionId, String userId, PayInvoiceRequestDTO payRequest) {
        try {
            if (payRequest.getInvoice().toLowerCase().startsWith("lightning:")) {
                payRequest.setInvoice(payRequest.getInvoice().substring(10));
            }
            SendPaymentRequest grpcRequest = SendPaymentRequest.newBuilder()
                    .setPaymentRequest(payRequest.getInvoice())
                    .setFeeLimitSat(payRequest.getFeeLimitSat() != null ? payRequest.getFeeLimitSat() : defaultFeeLimitSat)
                    .setTimeoutSeconds(payRequest.getTimeoutSeconds() != null ? payRequest.getTimeoutSeconds() : defaultTimeoutSeconds)
                    .setAllowSelfPayment(false)
                    .build();
            Iterator<Payment> paymentStream = routerStub.sendPaymentV2(grpcRequest);
            Payment finalPayment = null;
            while (paymentStream.hasNext()) {
                Payment payment = paymentStream.next();
                logger.info(
                        "Payment Update for txId: [{}], userId: [{}], Status: [{}], HTLCs: [{}]",
                        transactionId,
                        userId,
                        payment.getStatus(),
                        payment.getHtlcsCount()
                );
                if (payment.getStatus() == Payment.PaymentStatus.SUCCEEDED ||
                        payment.getStatus() == Payment.PaymentStatus.FAILED) {
                    finalPayment = payment;
                    break;
                }
            }
            if (finalPayment != null && finalPayment.getStatus() == Payment.PaymentStatus.SUCCEEDED) {
                transactionsService.confirmTransaction(transactionId, userId);
            } else {
                String reason = finalPayment != null ? finalPayment.getFailureReason().toString() : "Unknown failure";
                transactionsService.failTransaction(transactionId, reason);
            }
        } catch (Exception e) {
            transactionsService.failTransaction(transactionId, e.getMessage());
        }
    }

    @Override
    public Optional<Payment> checkPaymentStatusOnNode(String paymentHash) {
        try {
            TrackPaymentRequest request = TrackPaymentRequest.newBuilder()
                    .setPaymentHash(ByteString.fromHex(paymentHash))
                    .setNoInflightUpdates(true)
                    .build();
            Iterator<Payment> response = routerStub.trackPaymentV2(request);
            if (response.hasNext()) {
                return Optional.of(response.next());
            }
            return Optional.empty();
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                logger.debug("Payment with hash {} not found on LND node. Safe to proceed.", paymentHash);
                return Optional.empty();
            }
            throw new AratiriException("gRPC error checking payment status: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @Transactional
    public OnChainPaymentDTOs.SendOnChainResponseDTO sendOnChain(OnChainPaymentDTOs.SendOnChainRequestDTO request, String userId) {
        if (request.getAddress().toLowerCase().startsWith("bitcoin:")) {
            request.setAddress(request.getAddress().substring(8));
        }
        AccountEntity account = accountRepository.findByUserId(userId);
        if (account.getBalance() < request.getSatsAmount()) {
            throw new AratiriException("Insufficient balance", HttpStatus.BAD_REQUEST);
        }
        if (account.getBitcoinAddress().equals(request.getAddress())){
            throw new AratiriException("Payment to self not allowed.", HttpStatus.BAD_REQUEST);
        }
        CreateTransactionRequest txRequest = CreateTransactionRequest.builder()
                .userId(userId)
                .amount(BitcoinConstants.satoshisToBtc(request.getSatsAmount()))
                .currency(TransactionCurrency.BTC)
                .type(TransactionType.ONCHAIN_DEBIT)
                .status(TransactionStatus.PENDING)
                .description("On-chain payment to " + request.getAddress())
                .build();

        TransactionDTOResponse txDto = transactionsService.createTransaction(txRequest);

        OnChainPaymentInitiatedEvent eventPayload = new OnChainPaymentInitiatedEvent(
                userId, txDto.getId(), request
        );
        try {
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                    .aggregateType("ONCHAIN_PAYMENT")
                    .aggregateId(txDto.getId())
                    .eventType(KafkaTopics.ONCHAIN_PAYMENT_INITIATED.getCode())
                    .payload(objectMapper.writeValueAsString(eventPayload))
                    .build();
            outboxEventRepository.save(outboxEvent);
            logger.info("Saved ONCHAIN_PAYMENT_INITIATED event to outbox for transactionId: {}", txDto.getId());
        } catch (Exception e) {
            throw new AratiriException("Failed to create outbox event for on-chain payment.", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        OnChainPaymentDTOs.SendOnChainResponseDTO response = new OnChainPaymentDTOs.SendOnChainResponseDTO();
        response.setTransactionId(txDto.getId());
        return response;
    }

    @Override
    public OnChainPaymentDTOs.EstimateFeeResponseDTO estimateOnChainFee(OnChainPaymentDTOs.EstimateFeeRequestDTO request, String userId) {
        try {
            EstimateFeeRequest.Builder grpcRequestBuilder = EstimateFeeRequest.newBuilder()
                    .putAddrToAmount(request.getAddress(), request.getSatsAmount());
            if (request.getTargetConf() != null) {
                grpcRequestBuilder.setTargetConf(request.getTargetConf());
            } else {
                grpcRequestBuilder.setTargetConf(1);
            }
            EstimateFeeResponse response = lightningStub.estimateFee(grpcRequestBuilder.build());
            OnChainPaymentDTOs.EstimateFeeResponseDTO responseDTO = new OnChainPaymentDTOs.EstimateFeeResponseDTO();
            responseDTO.setFeeSat(response.getFeeSat());
            responseDTO.setSatPerVbyte(response.getSatPerVbyte());
            return responseDTO;
        } catch (StatusRuntimeException e) {
            throw new AratiriException("Error estimating fee: " + e.getStatus().getDescription(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Async
    @Override
    public void initiateGrpcOnChainPayment(String transactionId, String userId, OnChainPaymentDTOs.SendOnChainRequestDTO payRequest) {
        try {
            SendCoinsRequest.Builder grpcRequestBuilder = SendCoinsRequest.newBuilder()
                    .setAddr(payRequest.getAddress())
                    .setAmount(payRequest.getSatsAmount());
            if (payRequest.getSatPerVbyte() != null) {
                grpcRequestBuilder.setSatPerVbyte(payRequest.getSatPerVbyte());
            } else if (payRequest.getTargetConf() != null) {
                grpcRequestBuilder.setTargetConf(payRequest.getTargetConf());
            }
            SendCoinsResponse sendCoinsResponse = lightningStub.sendCoins(grpcRequestBuilder.build());
            logger.info("Successfully broadcast on-chain transaction with txid: {}", sendCoinsResponse.getTxid());
            transactionsService.confirmTransaction(transactionId, userId);

        } catch (Exception e) {
            logger.error("gRPC call to SendCoins failed for transactionId: {}. Reason: {}", transactionId, e.getMessage());
            transactionsService.failTransaction(transactionId, e.getMessage());
        }
    }
}
