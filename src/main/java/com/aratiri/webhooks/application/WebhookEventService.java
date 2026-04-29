package com.aratiri.webhooks.application;

import com.aratiri.infrastructure.persistence.jpa.entity.WebhookDeliveryEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.WebhookDeliveryStatus;
import com.aratiri.infrastructure.persistence.jpa.entity.WebhookEndpointEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.WebhookEventEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.WebhookDeliveryRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.WebhookEndpointRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.WebhookEventRepository;
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

    public void createInvoiceCreatedEvent(InvoiceCreatedWebhookFacts invoice) {
        String eventType = "invoice.created";
        String eventKey = eventType + ":" + invoice.paymentHash();
        WebhookPayloadData data = WebhookPayloadData.builder()
                .invoiceId(invoice.invoiceId())
                .userId(invoice.userId())
                .externalReference(invoice.externalReference())
                .metadata(invoice.metadata())
                .amountSat(invoice.amountSat())
                .paymentHash(invoice.paymentHash())
                .paymentRequest(invoice.paymentRequest())
                .memo(invoice.memo())
                .build();
        createEventAndDeliveries(eventKey, eventType, "INVOICE", invoice.invoiceId(), invoice.userId(), invoice.externalReference(), data);
    }

    public void createInvoiceSettledEvent(InvoiceSettledWebhookFacts invoice) {
        String eventType = "invoice.settled";
        String eventKey = eventType + ":" + invoice.paymentHash();
        WebhookPayloadData data = WebhookPayloadData.builder()
                .transactionId(invoice.transactionId())
                .userId(invoice.userId())
                .externalReference(invoice.externalReference())
                .metadata(invoice.metadata())
                .amountSat(invoice.amountSat())
                .status(invoice.status().name())
                .referenceId(invoice.referenceId())
                .balanceAfterSat(invoice.balanceAfterSat())
                .paymentHash(invoice.paymentHash())
                .build();
        createEventAndDeliveries(eventKey, eventType, "INVOICE", invoice.paymentHash(), invoice.userId(), invoice.externalReference(), data);
    }

    public void createOnchainDepositConfirmedEvent(OnChainDepositWebhookFacts deposit) {
        String eventType = "onchain.deposit.confirmed";
        String eventKey = eventType + ":" + deposit.referenceId();
        WebhookPayloadData data = WebhookPayloadData.builder()
                .transactionId(deposit.transactionId())
                .userId(deposit.userId())
                .externalReference(deposit.externalReference())
                .metadata(deposit.metadata())
                .amountSat(deposit.amountSat())
                .status(deposit.status().name())
                .referenceId(deposit.referenceId())
                .balanceAfterSat(deposit.balanceAfterSat())
                .build();
        createEventAndDeliveries(eventKey, eventType, AGGREGATE_TYPE_TRANSACTION, deposit.transactionId(), deposit.userId(), deposit.externalReference(), data);
    }

    public void createAccountBalanceChangedEvent(AccountBalanceChangedWebhookFacts facts) {
        String eventType = "account.balance_changed";
        String eventKey = eventType + ":" + facts.ledgerEntryId();
        WebhookPayloadData data = WebhookPayloadData.builder()
                .transactionId(facts.transactionId())
                .userId(facts.userId())
                .externalReference(facts.externalReference())
                .metadata(facts.metadata())
                .amountSat(facts.amountSat())
                .status(STATUS_COMPLETED)
                .referenceId(facts.referenceId())
                .balanceAfterSat(facts.balanceAfterSat())
                .build();
        createEventAndDeliveries(eventKey, eventType, "ACCOUNT_ENTRY", facts.ledgerEntryId(), facts.userId(), facts.externalReference(), data);
    }

    public void createNodeOperationUnknownOutcomeEvent(NodeOperationUnknownOutcomeFacts facts) {
        String eventType = "node_operation.unknown_outcome";
        String eventKey = eventType + ":" + facts.operationId();
        WebhookPayloadData data = WebhookPayloadData.builder()
                .operationId(facts.operationId())
                .transactionId(facts.transactionId())
                .userId(facts.userId())
                .operationType(facts.operationType())
                .operationStatus(facts.operationStatus())
                .referenceId(facts.referenceId())
                .externalId(facts.externalId())
                .attemptCount(facts.attemptCount())
                .operationError(facts.operationError())
                .amountSat(facts.amountSat())
                .status(facts.transactionStatus())
                .externalReference(facts.externalReference())
                .metadata(facts.metadata())
                .build();
        createEventAndDeliveries(eventKey, eventType, "NODE_OPERATION", facts.operationId(), facts.userId(), facts.externalReference(), data);
    }

    public void createWebhookTestEvent(WebhookTestEventFacts facts) {
        String eventType = "webhook.test";
        String eventKey = eventType + ":" + facts.endpointId() + ":" + UUID.randomUUID();
        WebhookPayloadData data = WebhookPayloadData.builder()
                .userId("system")
                .build();
        WebhookEventEntity event = createEventEntity(eventKey, eventType, "WEBHOOK", facts.endpointId().toString(), null, null, data);
        if (event == null) {
            return;
        }
        WebhookDeliveryEntity delivery = WebhookDeliveryEntity.builder()
                .eventId(event.getId())
                .endpointId(facts.endpointId())
                .eventType(eventType)
                .payload(event.getPayload())
                .status(WebhookDeliveryStatus.PENDING)
                .attemptCount(0)
                .nextAttemptAt(Instant.now())
                .build();
        webhookDeliveryRepository.save(delivery);
        log.debug("Created test webhook delivery for event={} endpoint={}", event.getId(), facts.endpointId());
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
