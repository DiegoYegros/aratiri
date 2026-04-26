package com.aratiri.webhooks.application;

import com.aratiri.infrastructure.persistence.jpa.entity.*;
import com.aratiri.infrastructure.persistence.jpa.repository.WebhookDeliveryRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.WebhookEndpointRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.WebhookEventRepository;
import com.aratiri.invoices.domain.LightningInvoice;
import com.aratiri.transactions.application.dto.TransactionStatus;
import com.aratiri.webhooks.application.dto.WebhookPayload;
import com.aratiri.webhooks.application.dto.WebhookPayloadData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookEventService {

    private static final String API_VERSION = "2026-04-25";
    private static final String AGGREGATE_TYPE_TRANSACTION = "TRANSACTION";
    private static final String STATUS_COMPLETED = "COMPLETED";

    private final WebhookEndpointRepository webhookEndpointRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final JsonMapper jsonMapper;

    public void createPaymentAcceptedEvent(PaymentWebhookFacts payment) {
        if (!payment.isDebitPayment()) {
            return;
        }
        String eventType = "payment.accepted";
        String eventKey = eventType + ":" + payment.transactionId();
        WebhookPayloadData data = WebhookPayloadData.builder()
                .transactionId(payment.transactionId())
                .userId(payment.userId())
                .externalReference(payment.externalReference())
                .metadata(payment.metadata())
                .amountSat(payment.amountSat())
                .status(payment.status().name())
                .referenceId(payment.referenceId())
                .build();
        createEventAndDeliveries(eventKey, eventType, AGGREGATE_TYPE_TRANSACTION, payment.transactionId(), payment.userId(), payment.externalReference(), data);
    }

    public void createPaymentSucceededEvent(PaymentWebhookFacts payment) {
        if (!payment.isDebitPayment()) {
            return;
        }
        String eventType = "payment.succeeded";
        String eventKey = eventType + ":" + payment.transactionId();
        WebhookPayloadData data = WebhookPayloadData.builder()
                .transactionId(payment.transactionId())
                .userId(payment.userId())
                .externalReference(payment.externalReference())
                .metadata(payment.metadata())
                .amountSat(payment.amountSat())
                .status(TransactionStatus.COMPLETED.name())
                .referenceId(payment.referenceId())
                .balanceAfterSat(payment.balanceAfterSat())
                .build();
        createEventAndDeliveries(eventKey, eventType, AGGREGATE_TYPE_TRANSACTION, payment.transactionId(), payment.userId(), payment.externalReference(), data);
    }

    public void createPaymentFailedEvent(PaymentWebhookFacts payment) {
        if (!payment.isDebitPayment()) {
            return;
        }
        String eventType = "payment.failed";
        String eventKey = eventType + ":" + payment.transactionId();
        WebhookPayloadData data = WebhookPayloadData.builder()
                .transactionId(payment.transactionId())
                .userId(payment.userId())
                .externalReference(payment.externalReference())
                .metadata(payment.metadata())
                .amountSat(payment.amountSat())
                .status(TransactionStatus.FAILED.name())
                .referenceId(payment.referenceId())
                .failureReason(payment.failureReason())
                .build();
        createEventAndDeliveries(eventKey, eventType, AGGREGATE_TYPE_TRANSACTION, payment.transactionId(), payment.userId(), payment.externalReference(), data);
    }

    public void createInvoiceCreatedEvent(LightningInvoice invoice) {
        String eventType = "invoice.created";
        String eventKey = eventType + ":" + invoice.paymentHash();
        WebhookPayloadData data = WebhookPayloadData.builder()
                .invoiceId(invoice.id())
                .userId(invoice.userId())
                .externalReference(invoice.externalReference())
                .metadata(invoice.metadata())
                .amountSat(invoice.amountSats())
                .paymentHash(invoice.paymentHash())
                .paymentRequest(invoice.paymentRequest())
                .memo(invoice.memo())
                .build();
        createEventAndDeliveries(eventKey, eventType, "INVOICE", invoice.id(), invoice.userId(), invoice.externalReference(), data);
    }

    public void createInvoiceSettledEvent(TransactionEntity transaction, String paymentHash) {
        String eventType = "invoice.settled";
        String eventKey = eventType + ":" + paymentHash;
        WebhookPayloadData data = WebhookPayloadData.builder()
                .transactionId(transaction.getId())
                .userId(transaction.getUserId())
                .externalReference(transaction.getExternalReference())
                .metadata(transaction.getMetadata())
                .amountSat(transaction.getCurrentAmount())
                .status(STATUS_COMPLETED)
                .referenceId(transaction.getReferenceId())
                .balanceAfterSat(transaction.getBalanceAfter())
                .paymentHash(paymentHash)
                .build();
        createEventAndDeliveries(eventKey, eventType, "INVOICE", paymentHash, transaction.getUserId(), transaction.getExternalReference(), data);
    }

    public void createOnchainDepositConfirmedEvent(TransactionEntity transaction) {
        String eventType = "onchain.deposit.confirmed";
        String eventKey = eventType + ":" + transaction.getReferenceId();
        WebhookPayloadData data = WebhookPayloadData.builder()
                .transactionId(transaction.getId())
                .userId(transaction.getUserId())
                .externalReference(transaction.getExternalReference())
                .metadata(transaction.getMetadata())
                .amountSat(transaction.getCurrentAmount())
                .status(STATUS_COMPLETED)
                .referenceId(transaction.getReferenceId())
                .balanceAfterSat(transaction.getBalanceAfter())
                .build();
        createEventAndDeliveries(eventKey, eventType, AGGREGATE_TYPE_TRANSACTION, transaction.getId(), transaction.getUserId(), transaction.getExternalReference(), data);
    }

    public void createAccountBalanceChangedEvent(TransactionEntity transaction, AccountEntryEntity entry) {
        String eventType = "account.balance_changed";
        String eventKey = eventType + ":" + entry.getId();
        WebhookPayloadData data = WebhookPayloadData.builder()
                .transactionId(transaction.getId())
                .userId(transaction.getUserId())
                .externalReference(transaction.getExternalReference())
                .metadata(transaction.getMetadata())
                .amountSat(Math.abs(entry.getDeltaSats()))
                .status(STATUS_COMPLETED)
                .referenceId(transaction.getReferenceId())
                .balanceAfterSat(entry.getBalanceAfter())
                .build();
        createEventAndDeliveries(eventKey, eventType, "ACCOUNT_ENTRY", entry.getId(), transaction.getUserId(), transaction.getExternalReference(), data);
    }

    public void createNodeOperationUnknownOutcomeEvent(NodeOperationEntity operation) {
        String eventType = "node_operation.unknown_outcome";
        String eventKey = eventType + ":" + operation.getId();
        WebhookPayloadData data = WebhookPayloadData.builder()
                .operationId(operation.getId().toString())
                .transactionId(operation.getTransactionId())
                .userId(operation.getUserId())
                .operationType(operation.getOperationType().name())
                .referenceId(operation.getReferenceId())
                .build();
        createEventAndDeliveries(eventKey, eventType, "NODE_OPERATION", operation.getId().toString(), operation.getUserId(), null, data);
    }

    public void createWebhookTestEvent(WebhookEndpointEntity endpoint) {
        String eventType = "webhook.test";
        String eventKey = eventType + ":" + endpoint.getId() + ":" + UUID.randomUUID();
        WebhookPayloadData data = WebhookPayloadData.builder()
                .userId("system")
                .build();
        WebhookEventEntity event = createEventEntity(eventKey, eventType, "WEBHOOK", endpoint.getId().toString(), null, null, data);
        if (event == null) {
            return;
        }
        WebhookDeliveryEntity delivery = WebhookDeliveryEntity.builder()
                .eventId(event.getId())
                .endpointId(endpoint.getId())
                .eventType(eventType)
                .payload(event.getPayload())
                .status(WebhookDeliveryStatus.PENDING)
                .attemptCount(0)
                .nextAttemptAt(Instant.now())
                .build();
        webhookDeliveryRepository.save(delivery);
        log.debug("Created test webhook delivery for event={} endpoint={}", event.getId(), endpoint.getId());
    }

    private void createEventAndDeliveries(String eventKey, String eventType, String aggregateType, String aggregateId, String userId, String externalReference, WebhookPayloadData data) {
        WebhookEventEntity event = createEventEntity(eventKey, eventType, aggregateType, aggregateId, userId, externalReference, data);
        if (event == null) {
            return;
        }

        List<WebhookEndpointEntity> endpoints = webhookEndpointRepository.findAllEnabledWithSubscriptions();
        for (WebhookEndpointEntity endpoint : endpoints) {
            boolean subscribed = endpoint.getSubscriptions().stream()
                    .anyMatch(sub -> sub.getEventType().equals(eventType));
            if (subscribed) {
                WebhookDeliveryEntity delivery = WebhookDeliveryEntity.builder()
                        .eventId(event.getId())
                        .endpointId(endpoint.getId())
                        .eventType(eventType)
                        .payload(event.getPayload())
                        .status(WebhookDeliveryStatus.PENDING)
                        .attemptCount(0)
                        .nextAttemptAt(Instant.now())
                        .build();
                webhookDeliveryRepository.save(delivery);
                log.debug("Created webhook delivery for event={} endpoint={}", event.getId(), endpoint.getId());
            }
        }
    }

    private WebhookEventEntity createEventEntity(String eventKey, String eventType, String aggregateType, String aggregateId, String userId, String externalReference, WebhookPayloadData data) {
        Optional<WebhookEventEntity> existing = webhookEventRepository.findByEventKey(eventKey);
        if (existing.isPresent()) {
            log.debug("Webhook event already exists for key: {}", eventKey);
            return null;
        }

        UUID eventId = UUID.randomUUID();
        WebhookPayload payload = WebhookPayload.builder()
                .id(eventId.toString())
                .type(eventType)
                .createdAt(Instant.now())
                .apiVersion(API_VERSION)
                .data(data)
                .build();

        String payloadJson;
        try {
            payloadJson = jsonMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Failed to serialize webhook payload for eventType={}", eventType, e);
            return null;
        }

        WebhookEventEntity event = WebhookEventEntity.builder()
                .id(eventId)
                .eventKey(eventKey)
                .eventType(eventType)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .userId(userId)
                .externalReference(externalReference)
                .payload(payloadJson)
                .build();
        try {
            webhookEventRepository.save(event);
        } catch (DataIntegrityViolationException _) {
            log.debug("Webhook event duplicate key prevented by constraint: {}", eventKey);
            return null;
        }
        return event;
    }
}
