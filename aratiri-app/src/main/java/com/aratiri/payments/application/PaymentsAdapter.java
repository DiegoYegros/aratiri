package com.aratiri.payments.application;

import com.aratiri.infrastructure.messaging.KafkaTopics;
import com.aratiri.payments.application.dto.OnChainPaymentDTOs;
import com.aratiri.payments.application.dto.PayInvoiceRequestDTO;
import com.aratiri.payments.application.dto.PaymentResponseDTO;
import com.aratiri.payments.application.event.OnChainPaymentInitiatedEvent;
import com.aratiri.payments.application.event.PaymentInitiatedEvent;
import com.aratiri.payments.application.port.in.PaymentsPort;
import com.aratiri.payments.application.port.out.*;
import com.aratiri.payments.domain.*;
import com.aratiri.payments.infrastructure.json.JsonUtils;
import com.aratiri.shared.constants.BitcoinConstants;
import com.aratiri.shared.exception.AratiriException;
import com.aratiri.transactions.application.dto.*;
import com.aratiri.transactions.application.event.InternalTransferInitiatedEvent;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lnrpc.Payment;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentsAdapter implements PaymentsPort {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final AccountsPort accountsPort;
    private final TransactionsPort transactionsPort;
    private final InvoicesPort invoicesPort;
    private final LightningNodePort lightningNodePort;
    private final OutboxEventPort outboxEventPort;
    private final LightningInvoicePort lightningInvoicePort;

    @Value("${aratiri.payment.default.fee.limit.sat:200}")
    private int defaultFeeLimitSat;

    @Value("${aratiri.payment.default.timeout.seconds:200}")
    private int defaultTimeoutSeconds;

    @Override
    @Transactional
    public PaymentResponseDTO payLightningInvoice(PayInvoiceRequestDTO request, String userId) {
        DecodedInvoice decodedInvoice = invoicesPort.decodeInvoice(request.getInvoice());
        String paymentHash = decodedInvoice.paymentHash();

        Optional<Payment> existingPayment = lightningNodePort.findPayment(paymentHash);
        if (existingPayment.isPresent()) {
            Payment.PaymentStatus status = existingPayment.get().getStatus();
            if (status == Payment.PaymentStatus.SUCCEEDED || status == Payment.PaymentStatus.IN_FLIGHT) {
                logger.warn("User {} attempted to pay an invoice that is already paid or in-flight on the node. PaymentHash: {}", userId, paymentHash);
                throw new AratiriException(
                        "Invoice payment is already in progress or has been settled.",
                        HttpStatus.CONFLICT.value()
                );
            }
        }

        Optional<InternalLightningInvoice> internalInvoiceOpt = lightningInvoicePort.findByPaymentHash(paymentHash);
        if (internalInvoiceOpt.isPresent()) {
            return processInternalTransfer(request, userId, decodedInvoice, internalInvoiceOpt.get());
        }

        return processExternalPayment(request, userId, paymentHash, decodedInvoice);
    }

    private PaymentResponseDTO processExternalPayment(
            PayInvoiceRequestDTO request,
            String userId,
            String paymentHash,
            DecodedInvoice decodedInvoice
    ) {
        boolean isSettledAratiriInvoice = invoicesPort.existsSettledInvoice(paymentHash);
        if (isSettledAratiriInvoice) {
            logger.warn("User {} attempted to pay an invoice that has already been successfully paid by another Aratiri user. PaymentHash: {}", userId, paymentHash);
            throw new AratiriException("Invoice has already been paid", HttpStatus.BAD_REQUEST.value());
        }

        Optional<Payment> existingPayment = lightningNodePort.findPayment(paymentHash);
        if (existingPayment.isPresent() && existingPayment.get().getStatus() == Payment.PaymentStatus.SUCCEEDED) {
            logger.warn("User {} attempted to pay an invoice that has already been successfully paid by this node. PaymentHash: {}", userId, paymentHash);
            throw new AratiriException("Invoice has already been paid", HttpStatus.BAD_REQUEST.value());
        }

        long amountSat = decodedInvoice.amountSatoshis();
        PaymentAccount account = accountsPort.getAccount(userId);
        if (account.balance() < amountSat) {
            logger.info("Insufficient balance. Tried to pay {} but balance was {}", amountSat, account.balance());
            throw new AratiriException("Insufficient balance", HttpStatus.BAD_REQUEST.value());
        }

        CreateTransactionRequest txRequest = CreateTransactionRequest.builder()
                .userId(userId)
                .amount(BitcoinConstants.satoshisToBtc(amountSat))
                .currency(TransactionCurrency.BTC)
                .type(TransactionType.LIGHTNING_DEBIT)
                .status(TransactionStatus.PENDING)
                .referenceId(paymentHash)
                .description("Lightning Payment: " + decodedInvoice.description())
                .build();

        TransactionDTOResponse txDto = transactionsPort.createTransaction(txRequest);
        PaymentInitiatedEvent eventPayload = new PaymentInitiatedEvent(userId, txDto.getId(), request);
        persistOutboxMessage(new OutboxMessage(
                "LIGHTNING_INVOICE_PAYMENT",
                txDto.getId(),
                KafkaTopics.PAYMENT_INITIATED.getCode(),
                JsonUtils.toJson(eventPayload)
        ));

        return PaymentResponseDTO.builder()
                .transactionId(txDto.getId())
                .status(txDto.getStatus())
                .message("Payment initiated. Status is pending.")
                .build();
    }

    private PaymentResponseDTO processInternalTransfer(
            PayInvoiceRequestDTO request,
            String senderId,
            DecodedInvoice decodedInvoice,
            InternalLightningInvoice internalInvoice
    ) {
        if (internalInvoice.state() == InternalLightningInvoice.InvoiceState.SETTLED) {
            throw new AratiriException("The invoice is already paid", HttpStatus.BAD_REQUEST.value());
        }

        long amountSat = decodedInvoice.amountSatoshis();
        PaymentAccount senderAccount = accountsPort.getAccount(senderId);
        if (senderAccount.balance() < amountSat) {
            throw new AratiriException("Insufficient balance for internal transfer", HttpStatus.BAD_REQUEST.value());
        }
        if (internalInvoice.userId().equals(senderId)) {
            throw new AratiriException("Payment to self is not allowed.", HttpStatus.BAD_REQUEST.value());
        }

        CreateTransactionRequest txRequest = CreateTransactionRequest.builder()
                .userId(senderId)
                .amount(BitcoinConstants.satoshisToBtc(amountSat))
                .currency(TransactionCurrency.BTC)
                .type(TransactionType.LIGHTNING_DEBIT)
                .status(TransactionStatus.PENDING)
                .referenceId(decodedInvoice.paymentHash())
                .description("Internal transfer to: " + internalInvoice.userId())
                .build();

        TransactionDTOResponse txDto = transactionsPort.createTransaction(txRequest);
        InternalTransferInitiatedEvent eventPayload = new InternalTransferInitiatedEvent(
                txDto.getId(),
                senderId,
                internalInvoice.userId(),
                amountSat,
                decodedInvoice.paymentHash()
        );
        persistOutboxMessage(new OutboxMessage(
                "INTERNAL_TRANSFER",
                txDto.getId(),
                KafkaTopics.INTERNAL_TRANSFER_INITIATED.getCode(),
                JsonUtils.toJson(eventPayload)
        ));

        return PaymentResponseDTO.builder()
                .transactionId(txDto.getId())
                .status(TransactionStatus.PENDING)
                .message("Internal transfer initiated.")
                .build();
    }

    @Async
    @Override
    public void initiateGrpcLightningPayment(String transactionId, String userId, PayInvoiceRequestDTO payRequest) {
        PayInvoiceRequestDTO normalizedRequest = normalizeInvoice(payRequest);
        try {
            Payment finalPayment = lightningNodePort.executeLightningPayment(normalizedRequest, defaultFeeLimitSat, defaultTimeoutSeconds);
            if (finalPayment != null && finalPayment.getStatus() == Payment.PaymentStatus.SUCCEEDED) {
                long feeSat = finalPayment.getFeeSat();
                long feeMsat = finalPayment.getFeeMsat();
                if (feeSat <= 0 && feeMsat > 0) {
                    feeSat = (feeMsat + 999) / 1000;
                }
                if (feeSat > 0) {
                    transactionsPort.addFeeToTransaction(transactionId, feeSat);
                }
                transactionsPort.confirmTransaction(transactionId, userId);
            } else {
                String reason = finalPayment != null ? finalPayment.getFailureReason().toString() : "Unknown failure";
                transactionsPort.failTransaction(transactionId, reason);
            }
        } catch (Exception e) {
            transactionsPort.failTransaction(transactionId, e.getMessage());
        }
    }

    @Override
    public Optional<Payment> checkPaymentStatusOnNode(String paymentHash) {
        try {
            return lightningNodePort.findPayment(paymentHash);
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                return Optional.empty();
            }
            throw new AratiriException("gRPC error checking payment status: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
        } catch (Exception e) {
            throw new AratiriException("gRPC error checking payment status: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    @Async
    @Override
    public void initiateGrpcOnChainPayment(String transactionId, String userId, OnChainPaymentDTOs.SendOnChainRequestDTO payRequest) {
        OnChainPaymentDTOs.SendOnChainRequestDTO normalizedRequest = normalizeOnChainRequest(payRequest);
        try {
            String txid = lightningNodePort.sendOnChain(normalizedRequest);
            logger.info("Successfully broadcast on-chain transaction with txid: {}", txid);
            transactionsPort.confirmTransaction(transactionId, userId);
        } catch (Exception e) {
            logger.error("gRPC call to SendCoins failed for transactionId: {}. Reason: {}", transactionId, e.getMessage());
            transactionsPort.failTransaction(transactionId, e.getMessage());
        }
    }

    @Override
    public OnChainPaymentDTOs.SendOnChainResponseDTO sendOnChain(OnChainPaymentDTOs.SendOnChainRequestDTO request, String userId) {
        OnChainPaymentDTOs.SendOnChainRequestDTO normalizedRequest = normalizeOnChainRequest(request);
        PaymentAccount account = accountsPort.getAccount(userId);

        OnChainPaymentDTOs.EstimateFeeRequestDTO feeRequest = new OnChainPaymentDTOs.EstimateFeeRequestDTO();
        feeRequest.setAddress(normalizedRequest.getAddress());
        feeRequest.setSatsAmount(normalizedRequest.getSatsAmount());
        feeRequest.setTargetConf(normalizedRequest.getTargetConf());

        OnChainPaymentDTOs.EstimateFeeResponseDTO feeResponse = estimateOnChainFee(feeRequest, userId);
        long totalAmount = normalizedRequest.getSatsAmount() + feeResponse.getFeeSat();

        if (account.balance() < totalAmount) {
            throw new AratiriException("Insufficient balance to cover amount and fee", HttpStatus.BAD_REQUEST.value());
        }
        if (account.bitcoinAddress().equals(normalizedRequest.getAddress())) {
            throw new AratiriException("Payment to self not allowed.", HttpStatus.BAD_REQUEST.value());
        }

        CreateTransactionRequest txRequest = CreateTransactionRequest.builder()
                .userId(userId)
                .amount(BitcoinConstants.satoshisToBtc(totalAmount))
                .currency(TransactionCurrency.BTC)
                .type(TransactionType.ONCHAIN_DEBIT)
                .status(TransactionStatus.PENDING)
                .description("On-chain payment to " + normalizedRequest.getAddress())
                .build();

        TransactionDTOResponse txDto = transactionsPort.createTransaction(txRequest);
        OnChainPaymentInitiatedEvent eventPayload = new OnChainPaymentInitiatedEvent(userId, txDto.getId(), normalizedRequest);
        persistOutboxMessage(new OutboxMessage(
                "ONCHAIN_PAYMENT",
                txDto.getId(),
                KafkaTopics.ONCHAIN_PAYMENT_INITIATED.getCode(),
                JsonUtils.toJson(eventPayload)
        ));

        OnChainPaymentDTOs.SendOnChainResponseDTO response = new OnChainPaymentDTOs.SendOnChainResponseDTO();
        response.setTransactionId(txDto.getId());
        response.setTransactionStatus(txDto.getStatus());
        return response;
    }

    @Override
    public OnChainPaymentDTOs.EstimateFeeResponseDTO estimateOnChainFee(OnChainPaymentDTOs.EstimateFeeRequestDTO request, String userId) {
        OnChainPaymentDTOs.EstimateFeeRequestDTO normalizedRequest = normalizeFeeRequest(request);
        try {
            OnChainFeeEstimate estimate = lightningNodePort.estimateOnChainFee(normalizedRequest);
            OnChainPaymentDTOs.EstimateFeeResponseDTO responseDTO = new OnChainPaymentDTOs.EstimateFeeResponseDTO();
            responseDTO.setFeeSat(estimate.feeSat());
            responseDTO.setSatPerVbyte(estimate.satPerVbyte());
            return responseDTO;
        } catch (Exception e) {
            throw new AratiriException("Error estimating fee: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    private void persistOutboxMessage(OutboxMessage message) {
        try {
            outboxEventPort.save(message);
            logger.info("Saved {} event to outbox for aggregateId: {}", message.eventType(), message.aggregateId());
        } catch (Exception e) {
            throw new AratiriException("Failed to create outbox event for payment workflow.", HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }



    private PayInvoiceRequestDTO normalizeInvoice(PayInvoiceRequestDTO request) {
        PayInvoiceRequestDTO normalized = new PayInvoiceRequestDTO();
        if (request.getInvoice() != null && request.getInvoice().toLowerCase().startsWith("lightning:")) {
            normalized.setInvoice(request.getInvoice().substring(10));
        } else {
            normalized.setInvoice(request.getInvoice());
        }
        normalized.setFeeLimitSat(request.getFeeLimitSat());
        normalized.setTimeoutSeconds(request.getTimeoutSeconds());
        return normalized;
    }

    private OnChainPaymentDTOs.SendOnChainRequestDTO normalizeOnChainRequest(OnChainPaymentDTOs.SendOnChainRequestDTO request) {
        OnChainPaymentDTOs.SendOnChainRequestDTO normalized = new OnChainPaymentDTOs.SendOnChainRequestDTO();
        if (request.getAddress() != null && request.getAddress().toLowerCase().startsWith("bitcoin:")) {
            normalized.setAddress(request.getAddress().substring(8));
        } else {
            normalized.setAddress(request.getAddress());
        }
        normalized.setSatsAmount(request.getSatsAmount());
        normalized.setSatPerVbyte(request.getSatPerVbyte());
        normalized.setTargetConf(request.getTargetConf());
        return normalized;
    }

    private OnChainPaymentDTOs.EstimateFeeRequestDTO normalizeFeeRequest(OnChainPaymentDTOs.EstimateFeeRequestDTO request) {
        OnChainPaymentDTOs.EstimateFeeRequestDTO normalized = new OnChainPaymentDTOs.EstimateFeeRequestDTO();
        if (request.getAddress() != null && request.getAddress().toLowerCase().startsWith("bitcoin:")) {
            normalized.setAddress(request.getAddress().substring(8));
        } else {
            normalized.setAddress(request.getAddress());
        }
        normalized.setSatsAmount(request.getSatsAmount());
        normalized.setTargetConf(request.getTargetConf());
        return normalized;
    }

}
