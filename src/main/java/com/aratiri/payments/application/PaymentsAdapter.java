package com.aratiri.payments.application;

import com.aratiri.infrastructure.messaging.outbox.OutboxWriter;
import com.aratiri.payments.application.command.LightningInvoicePaymentCommandService;
import com.aratiri.payments.application.command.OnChainPaymentCommandService;
import com.aratiri.payments.application.dto.OnChainPaymentDTOs;
import com.aratiri.payments.application.dto.PayInvoiceRequestDTO;
import com.aratiri.payments.application.dto.PaymentResponseDTO;
import com.aratiri.payments.application.event.OnChainPaymentInitiatedEvent;
import com.aratiri.payments.application.event.PaymentInitiatedEvent;
import com.aratiri.payments.application.port.in.PaymentsPort;
import com.aratiri.payments.application.port.out.*;
import com.aratiri.payments.domain.*;
import com.aratiri.shared.exception.AratiriException;
import com.aratiri.transactions.application.dto.*;
import com.aratiri.transactions.application.event.InternalTransferInitiatedEvent;
import com.aratiri.webhooks.application.PaymentWebhookFacts;
import com.aratiri.webhooks.application.WebhookEventService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentsAdapter implements PaymentsPort {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final AccountsPort accountsPort;
    private final TransactionsPort transactionsPort;
    private final InvoicesPort invoicesPort;
    private final LightningNodePort lightningNodePort;
    private final OutboxWriter outboxWriter;
    private final LightningInvoicePort lightningInvoicePort;
    private final LightningInvoicePaymentCommandService lightningInvoicePaymentCommand;
    private final OnChainPaymentCommandService onChainPaymentCommand;
    private final WebhookEventService webhookEventService;
    private final PaymentFeePolicy paymentFeePolicy;
    private final ExistingPaymentPolicy existingPaymentPolicy;

    @Override
    @Transactional
    public PaymentResponseDTO payLightningInvoice(PayInvoiceRequestDTO request, String userId, String idempotencyKey) {
        return lightningInvoicePaymentCommand.execute(
                userId, idempotencyKey, request, () -> executeLightningPayment(request, userId)
        );
    }

    @Override
    @Transactional
    public PaymentResponseDTO payLightningInvoiceInternal(PayInvoiceRequestDTO request, String userId) {
        return executeLightningPayment(request, userId);
    }

    private PaymentResponseDTO executeLightningPayment(PayInvoiceRequestDTO request, String userId) {
        DecodedInvoice decodedInvoice = invoicesPort.decodeInvoice(request.getInvoice());
        String paymentHash = decodedInvoice.paymentHash();

        Optional<ExistingPaymentRejection> activeNodePayment =
                existingPaymentPolicy.activeOrSettledNodePayment(findNodePaymentState(paymentHash));
        if (activeNodePayment.isPresent()) {
            logger.warn("User {} attempted to pay an invoice that is already paid or in-flight on the node. PaymentHash: {}", userId, paymentHash);
            throw activeNodePayment.get().toException();
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
        Optional<ExistingPaymentRejection> settledAratiriInvoice =
                existingPaymentPolicy.settledAratiriInvoice(isSettledAratiriInvoice);
        if (settledAratiriInvoice.isPresent()) {
            logger.warn("User {} attempted to pay an invoice that has already been successfully paid by another Aratiri user. PaymentHash: {}", userId, paymentHash);
            throw settledAratiriInvoice.get().toException();
        }

        Optional<ExistingPaymentRejection> settledNodePayment =
                existingPaymentPolicy.settledExternalNodePayment(findNodePaymentState(paymentHash));
        if (settledNodePayment.isPresent()) {
            logger.warn("User {} attempted to pay an invoice that has already been successfully paid by this node. PaymentHash: {}", userId, paymentHash);
            throw settledNodePayment.get().toException();
        }

        long amountSat = decodedInvoice.amountSatoshis();
        long platformFeeSat = paymentFeePolicy.lightningPlatformFee(amountSat);
        long totalDebitSat = Math.addExact(amountSat, platformFeeSat);
        CreateTransactionRequest txRequest = CreateTransactionRequest.builder()
                .userId(userId)
                .amountSat(totalDebitSat)
                .currency(TransactionCurrency.BTC)
                .type(TransactionType.LIGHTNING_DEBIT)
                .status(TransactionStatus.PENDING)
                .referenceId(paymentHash)
                .description("Lightning Payment: " + decodedInvoice.description())
                .externalReference(request.getExternalReference())
                .metadata(request.getMetadata())
                .build();
        TransactionDTOResponse txDto = transactionsPort.createTransaction(txRequest);
        PaymentInitiatedEvent eventPayload = new PaymentInitiatedEvent(userId, txDto.getId(), request);
        publishPaymentInitiated(txDto.getId(), eventPayload);

        webhookEventService.createPaymentAcceptedEvent(PaymentWebhookFacts.accepted(
                txDto.getId(),
                userId,
                TransactionType.LIGHTNING_DEBIT,
                totalDebitSat,
                paymentHash,
                request.getExternalReference(),
                request.getMetadata()
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
        if (internalInvoice.userId().equals(senderId)) {
            throw new AratiriException("Payment to self is not allowed.", HttpStatus.BAD_REQUEST.value());
        }

        CreateTransactionRequest txRequest = CreateTransactionRequest.builder()
                .userId(senderId)
                .amountSat(amountSat)
                .currency(TransactionCurrency.BTC)
                .type(TransactionType.LIGHTNING_DEBIT)
                .status(TransactionStatus.PENDING)
                .referenceId(decodedInvoice.paymentHash())
                .description("Internal transfer to: " + internalInvoice.userId())
                .externalReference(request.getExternalReference())
                .metadata(request.getMetadata())
                .build();

        TransactionDTOResponse txDto = transactionsPort.createTransaction(txRequest);
        InternalTransferInitiatedEvent eventPayload = new InternalTransferInitiatedEvent(
                txDto.getId(),
                senderId,
                internalInvoice.userId(),
                amountSat,
                decodedInvoice.paymentHash()
        );
        publishInternalTransferInitiated(txDto.getId(), eventPayload);

        webhookEventService.createPaymentAcceptedEvent(PaymentWebhookFacts.accepted(
                txDto.getId(),
                senderId,
                TransactionType.LIGHTNING_DEBIT,
                amountSat,
                decodedInvoice.paymentHash(),
                request.getExternalReference(),
                request.getMetadata()
        ));

        return PaymentResponseDTO.builder()
                .transactionId(txDto.getId())
                .status(TransactionStatus.PENDING)
                .message("Internal transfer initiated.")
                .build();
    }

    @Override
    public Optional<LightningPayment> checkPaymentStatusOnNode(String paymentHash) {
        try {
            return lightningNodePort.findPayment(paymentHash);
        } catch (Exception e) {
            throw new AratiriException("gRPC error checking payment status: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    @Override
    @Transactional
    public OnChainPaymentDTOs.SendOnChainResponseDTO sendOnChain(OnChainPaymentDTOs.SendOnChainRequestDTO request, String userId, String idempotencyKey) {
        return onChainPaymentCommand.execute(userId, idempotencyKey, request, () -> executeOnChainPayment(request, userId));
    }

    private OnChainPaymentDTOs.SendOnChainResponseDTO executeOnChainPayment(
            OnChainPaymentDTOs.SendOnChainRequestDTO request,
            String userId
    ) {
        OnChainPaymentDTOs.SendOnChainRequestDTO normalizedRequest = normalizeOnChainRequest(request);
        PaymentAccount account = accountsPort.getAccount(userId);

        OnChainPaymentDTOs.EstimateFeeRequestDTO feeRequest = new OnChainPaymentDTOs.EstimateFeeRequestDTO();
        feeRequest.setAddress(normalizedRequest.getAddress());
        feeRequest.setSatsAmount(normalizedRequest.getSatsAmount());
        feeRequest.setTargetConf(normalizedRequest.getTargetConf());

        OnChainPaymentDTOs.EstimateFeeResponseDTO feeResponse = estimateOnChainFee(feeRequest, userId);
        long amountSat = normalizedRequest.getSatsAmount();
        long networkFeeSat = feeResponse.getFeeSat();
        long platformFeeSat = feeResponse.getPlatformFeeSat();
        long totalFeeSat = Math.addExact(networkFeeSat, platformFeeSat);
        long totalAmount = Math.addExact(amountSat, totalFeeSat);
        if (account.bitcoinAddress().equals(normalizedRequest.getAddress())) {
            throw new AratiriException("Payment to self not allowed.", HttpStatus.BAD_REQUEST.value());
        }

        CreateTransactionRequest txRequest = CreateTransactionRequest.builder()
                .userId(userId)
                .amountSat(totalAmount)
                .currency(TransactionCurrency.BTC)
                .type(TransactionType.ONCHAIN_DEBIT)
                .status(TransactionStatus.PENDING)
                .description("On-chain payment to " + normalizedRequest.getAddress())
                .externalReference(request.getExternalReference())
                .metadata(request.getMetadata())
                .build();

        TransactionDTOResponse txDto = transactionsPort.createTransaction(txRequest);
        OnChainPaymentInitiatedEvent eventPayload = new OnChainPaymentInitiatedEvent(userId, txDto.getId(), normalizedRequest);
        publishOnChainPaymentInitiated(txDto.getId(), eventPayload);

        webhookEventService.createPaymentAcceptedEvent(PaymentWebhookFacts.accepted(
                txDto.getId(),
                userId,
                TransactionType.ONCHAIN_DEBIT,
                totalAmount,
                null,
                request.getExternalReference(),
                request.getMetadata()
        ));

        OnChainPaymentDTOs.SendOnChainResponseDTO response = new OnChainPaymentDTOs.SendOnChainResponseDTO();
        response.setTransactionId(txDto.getId());
        response.setTransactionStatus(txDto.getStatus());
        return response;
    }

    @Override
    public OnChainPaymentDTOs.EstimateFeeResponseDTO estimateOnChainFee(OnChainPaymentDTOs.EstimateFeeRequestDTO request, String userId) {
        logger.debug("Estimating on-chain fee for user {}", userId);
        OnChainPaymentDTOs.EstimateFeeRequestDTO normalizedRequest = normalizeFeeRequest(request);
        try {
            OnChainFeeEstimate estimate = lightningNodePort.estimateOnChainFee(normalizedRequest);
            OnChainPaymentDTOs.EstimateFeeResponseDTO responseDTO = new OnChainPaymentDTOs.EstimateFeeResponseDTO();
            responseDTO.setFeeSat(estimate.feeSat());
            responseDTO.setSatPerVbyte(estimate.satPerVbyte());
            long amountSat = normalizedRequest.getSatsAmount();
            long platformFeeSat = paymentFeePolicy.onChainPlatformFee(amountSat);
            responseDTO.setPlatformFeeSat(platformFeeSat);
            responseDTO.setTotalFeeSat(Math.addExact(estimate.feeSat(), platformFeeSat));
            return responseDTO;
        } catch (Exception e) {
            throw new AratiriException("Error estimating fee: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    private Optional<LightningPaymentStatus> findNodePaymentState(String paymentHash) {
        return lightningNodePort.findPayment(paymentHash).map(LightningPayment::status);
    }

    private void publishPaymentInitiated(String transactionId, PaymentInitiatedEvent eventPayload) {
        try {
            outboxWriter.publishPaymentInitiated(transactionId, eventPayload);
            logger.info("Saved payment initiated event to outbox for aggregateId: {}", transactionId);
        } catch (Exception _) {
            throw new AratiriException("Failed to create outbox event for payment workflow.", HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    private void publishOnChainPaymentInitiated(String transactionId, OnChainPaymentInitiatedEvent eventPayload) {
        try {
            outboxWriter.publishOnChainPaymentInitiated(transactionId, eventPayload);
            logger.info("Saved on-chain payment initiated event to outbox for aggregateId: {}", transactionId);
        } catch (Exception _) {
            throw new AratiriException("Failed to create outbox event for payment workflow.", HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    private void publishInternalTransferInitiated(String transactionId, InternalTransferInitiatedEvent eventPayload) {
        try {
            outboxWriter.publishInternalTransferInitiated(transactionId, eventPayload);
            logger.info("Saved internal transfer initiated event to outbox for aggregateId: {}", transactionId);
        } catch (Exception _) {
            throw new AratiriException("Failed to create outbox event for payment workflow.", HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    private OnChainPaymentDTOs.SendOnChainRequestDTO normalizeOnChainRequest(OnChainPaymentDTOs.SendOnChainRequestDTO request) {
        OnChainPaymentDTOs.SendOnChainRequestDTO normalized = new OnChainPaymentDTOs.SendOnChainRequestDTO();
        if (request.getAddress() != null && request.getAddress().toLowerCase(Locale.ROOT).startsWith("bitcoin:")) {
            normalized.setAddress(request.getAddress().substring(8));
        } else {
            normalized.setAddress(request.getAddress());
        }
        normalized.setSatsAmount(request.getSatsAmount());
        normalized.setSatPerVbyte(request.getSatPerVbyte());
        normalized.setTargetConf(request.getTargetConf());
        normalized.setExternalReference(request.getExternalReference());
        normalized.setMetadata(request.getMetadata());
        return normalized;
    }

    private OnChainPaymentDTOs.EstimateFeeRequestDTO normalizeFeeRequest(OnChainPaymentDTOs.EstimateFeeRequestDTO request) {
        OnChainPaymentDTOs.EstimateFeeRequestDTO normalized = new OnChainPaymentDTOs.EstimateFeeRequestDTO();
        if (request.getAddress() != null && request.getAddress().toLowerCase(Locale.ROOT).startsWith("bitcoin:")) {
            normalized.setAddress(request.getAddress().substring(8));
        } else {
            normalized.setAddress(request.getAddress());
        }
        normalized.setSatsAmount(request.getSatsAmount());
        normalized.setTargetConf(request.getTargetConf());
        return normalized;
    }

}
