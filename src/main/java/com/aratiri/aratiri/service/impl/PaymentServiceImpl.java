package com.aratiri.aratiri.service.impl;

import com.aratiri.aratiri.constants.BitcoinConstants;
import com.aratiri.aratiri.dto.invoices.DecodedInvoicetDTO;
import com.aratiri.aratiri.dto.payments.PayInvoiceRequestDTO;
import com.aratiri.aratiri.dto.payments.PaymentResponseDTO;
import com.aratiri.aratiri.dto.transactions.CreateTransactionRequest;
import com.aratiri.aratiri.dto.transactions.TransactionDTOResponse;
import com.aratiri.aratiri.entity.AccountEntity;
import com.aratiri.aratiri.entity.OutboxEventEntity;
import com.aratiri.aratiri.enums.TransactionCurrency;
import com.aratiri.aratiri.enums.TransactionStatus;
import com.aratiri.aratiri.enums.TransactionType;
import com.aratiri.aratiri.event.PaymentInitiatedEvent;
import com.aratiri.aratiri.exception.AratiriException;
import com.aratiri.aratiri.repository.AccountRepository;
import com.aratiri.aratiri.repository.OutboxEventRepository;
import com.aratiri.aratiri.service.InvoiceService;
import com.aratiri.aratiri.service.PaymentService;
import com.aratiri.aratiri.service.TransactionsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import lnrpc.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class PaymentServiceImpl implements PaymentService {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final AccountRepository accountRepository;
    private final TransactionsService transactionsService;
    private final InvoiceService invoiceService;
    private final RouterGrpc.RouterBlockingStub routerStub;
    private final OutboxEventRepository outboxEventRepository;

    public PaymentServiceImpl(AccountRepository accountRepository, TransactionsService transactionsService, InvoiceService invoiceService, RouterGrpc.RouterBlockingStub routerStub, OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
        this.accountRepository = accountRepository;
        this.transactionsService = transactionsService;
        this.invoiceService = invoiceService;
        this.routerStub = routerStub;
    }

    @Override
    @Transactional
    public PaymentResponseDTO payLightningInvoice(PayInvoiceRequestDTO request, String userId) {
        DecodedInvoicetDTO decodedInvoice = invoiceService.decodePaymentRequest(request.getInvoice());
        String paymentHash = decodedInvoice.getPaymentHash();
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
                .type(TransactionType.INVOICE_DEBIT)
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
                    .eventType("PAYMENT_INITIATED")
                    .payload(new ObjectMapper().writeValueAsString(eventPayload))
                    .build();
            outboxEventRepository.save(outboxEvent);
            logger.info("Saved PAYMENT_INITIATED event to outbox for transactionId: {}", txDto.getId());
        } catch (Exception e) {
            throw new AratiriException("Failed to create outbox event for payment initiation.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return PaymentResponseDTO.builder()
                .transactionId(txDto.getId())
                .status(TransactionStatus.PENDING)
                .message("Payment initiated. Status is pending.")
                .build();
    }

    @Async
    public void initiateGrpcPayment(String transactionId, String userId, PayInvoiceRequestDTO payRequest) {
        try {
            SendPaymentRequest grpcRequest = SendPaymentRequest.newBuilder()
                    .setPaymentRequest(payRequest.getInvoice())
                    .setFeeLimitSat(payRequest.getFeeLimitSat() != null ? payRequest.getFeeLimitSat() : 50)
                    .setTimeoutSeconds(payRequest.getTimeoutSeconds() != null ? payRequest.getTimeoutSeconds() : 200)
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
            if (e.getStatus().getCode() == io.grpc.Status.Code.NOT_FOUND) {
                logger.debug("Payment with hash {} not found on LND node. Safe to proceed.", paymentHash);
                return Optional.empty();
            }
            throw new AratiriException("gRPC error checking payment status: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}