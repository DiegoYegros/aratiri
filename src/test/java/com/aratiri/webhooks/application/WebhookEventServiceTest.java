package com.aratiri.webhooks.application;

import com.aratiri.infrastructure.persistence.jpa.entity.*;
import com.aratiri.infrastructure.persistence.jpa.repository.WebhookDeliveryRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.WebhookEndpointRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.WebhookEventRepository;
import com.aratiri.transactions.application.dto.TransactionStatus;
import com.aratiri.transactions.application.dto.TransactionType;
import com.aratiri.webhooks.application.dto.WebhookPayload;
import com.aratiri.webhooks.application.dto.WebhookPayloadData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookEventServiceTest {

    @Mock
    private WebhookEndpointRepository webhookEndpointRepository;

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Mock
    private WebhookDeliveryRepository webhookDeliveryRepository;

    @Mock
    private JsonMapper jsonMapper;

    private WebhookEventService webhookEventService;

    @BeforeEach
    void setUp() {
        webhookEventService = new WebhookEventService(webhookEndpointRepository, webhookEventRepository, webhookDeliveryRepository, jsonMapper);
    }

    @Test
    void createPaymentAcceptedEvent_createsEventAndDeliveryForEnabledEndpoint() throws Exception {
        WebhookEndpointEntity endpoint = enabledEndpoint("payment.accepted");
        when(webhookEndpointRepository.findAllEnabledWithSubscriptions()).thenReturn(List.of(endpoint));
        when(webhookEventRepository.findByEventKey(any())).thenReturn(Optional.empty());
        when(jsonMapper.writeValueAsString(any())).thenReturn("{}");

        PaymentWebhookFacts payment = acceptedPayment();
        webhookEventService.createPaymentAcceptedEvent(payment);

        ArgumentCaptor<WebhookEventEntity> eventCaptor = ArgumentCaptor.forClass(WebhookEventEntity.class);
        verify(webhookEventRepository).save(eventCaptor.capture());
        assertEquals("payment.accepted:tx-1", eventCaptor.getValue().getEventKey());
        assertEquals("payment.accepted", eventCaptor.getValue().getEventType());
        assertEquals("TRANSACTION", eventCaptor.getValue().getAggregateType());
        assertEquals("tx-1", eventCaptor.getValue().getAggregateId());
        assertEquals("user-1", eventCaptor.getValue().getUserId());
        assertEquals("external-1", eventCaptor.getValue().getExternalReference());

        WebhookPayloadData data = capturedPayloadData();
        assertEquals("tx-1", data.getTransactionId());
        assertEquals("user-1", data.getUserId());
        assertEquals("external-1", data.getExternalReference());
        assertEquals("{\"order\":\"123\"}", data.getMetadata());
        assertEquals(1000L, data.getAmountSat());
        assertEquals("PENDING", data.getStatus());
        assertEquals("ref-1", data.getReferenceId());

        ArgumentCaptor<WebhookDeliveryEntity> deliveryCaptor = ArgumentCaptor.forClass(WebhookDeliveryEntity.class);
        verify(webhookDeliveryRepository).save(deliveryCaptor.capture());
        assertEquals(endpoint.getId(), deliveryCaptor.getValue().getEndpointId());
        assertEquals("payment.accepted", deliveryCaptor.getValue().getEventType());
        assertEquals(WebhookDeliveryStatus.PENDING, deliveryCaptor.getValue().getStatus());
    }

    @Test
    void createPaymentAcceptedEvent_noDeliveryWhenNoEndpointIsSubscribed() {
        when(webhookEndpointRepository.findAllEnabledWithSubscriptions()).thenReturn(List.of());
        when(webhookEventRepository.findByEventKey(any())).thenReturn(Optional.empty());
        when(jsonMapper.writeValueAsString(any())).thenReturn("{}");

        webhookEventService.createPaymentAcceptedEvent(acceptedPayment());

        verify(webhookEventRepository).save(any());
        verify(webhookDeliveryRepository, never()).save(any());
    }

    @Test
    void createPaymentAcceptedEvent_idempotentByEventKey() {
        WebhookEventEntity existing = WebhookEventEntity.builder().eventKey("payment.accepted:tx-1").build();
        when(webhookEventRepository.findByEventKey("payment.accepted:tx-1")).thenReturn(Optional.of(existing));

        webhookEventService.createPaymentAcceptedEvent(acceptedPayment());

        verify(webhookEventRepository, never()).save(any());
        verify(webhookDeliveryRepository, never()).save(any());
    }

    @Test
    void createPaymentSucceededEvent_ignoresCreditTransaction() {
        PaymentWebhookFacts creditPayment = new PaymentWebhookFacts(
                "tx-1",
                "user-1",
                TransactionType.LIGHTNING_CREDIT,
                1000L,
                TransactionStatus.COMPLETED,
                "ref-1",
                null,
                null,
                5000L,
                null
        );

        webhookEventService.createPaymentSucceededEvent(creditPayment);

        verifyNoInteractions(webhookEventRepository);
        verifyNoInteractions(webhookDeliveryRepository);
    }

    @Test
    void createPaymentSucceededEvent_keepsPayloadShapeAndCreatesDelivery() throws Exception {
        WebhookEndpointEntity endpoint = enabledEndpoint("payment.succeeded");
        when(webhookEndpointRepository.findAllEnabledWithSubscriptions()).thenReturn(List.of(endpoint));
        when(webhookEventRepository.findByEventKey(any())).thenReturn(Optional.empty());
        when(jsonMapper.writeValueAsString(any())).thenReturn("{}");

        PaymentWebhookFacts payment = new PaymentWebhookFacts(
                "tx-1",
                "user-1",
                TransactionType.LIGHTNING_DEBIT,
                1200L,
                TransactionStatus.COMPLETED,
                "ref-1",
                "external-1",
                "{\"order\":\"123\"}",
                5000L,
                null
        );
        webhookEventService.createPaymentSucceededEvent(payment);

        ArgumentCaptor<WebhookEventEntity> eventCaptor = ArgumentCaptor.forClass(WebhookEventEntity.class);
        verify(webhookEventRepository).save(eventCaptor.capture());
        assertEquals("payment.succeeded:tx-1", eventCaptor.getValue().getEventKey());

        WebhookPayloadData data = capturedPayloadData();
        assertEquals("tx-1", data.getTransactionId());
        assertEquals("user-1", data.getUserId());
        assertEquals("external-1", data.getExternalReference());
        assertEquals("{\"order\":\"123\"}", data.getMetadata());
        assertEquals(1200L, data.getAmountSat());
        assertEquals("COMPLETED", data.getStatus());
        assertEquals("ref-1", data.getReferenceId());
        assertEquals(5000L, data.getBalanceAfterSat());

        verify(webhookDeliveryRepository).save(any(WebhookDeliveryEntity.class));
    }

    @Test
    void createPaymentFailedEvent_keepsPayloadShapeAndCreatesDelivery() throws Exception {
        WebhookEndpointEntity endpoint = enabledEndpoint("payment.failed");
        when(webhookEndpointRepository.findAllEnabledWithSubscriptions()).thenReturn(List.of(endpoint));
        when(webhookEventRepository.findByEventKey(any())).thenReturn(Optional.empty());
        when(jsonMapper.writeValueAsString(any())).thenReturn("{}");

        PaymentWebhookFacts payment = new PaymentWebhookFacts(
                "tx-1",
                "user-1",
                TransactionType.ONCHAIN_DEBIT,
                1000L,
                TransactionStatus.FAILED,
                "ref-1",
                "external-1",
                "{\"order\":\"123\"}",
                null,
                "node failure"
        );
        webhookEventService.createPaymentFailedEvent(payment);

        ArgumentCaptor<WebhookEventEntity> eventCaptor = ArgumentCaptor.forClass(WebhookEventEntity.class);
        verify(webhookEventRepository).save(eventCaptor.capture());
        assertEquals("payment.failed:tx-1", eventCaptor.getValue().getEventKey());

        WebhookPayloadData data = capturedPayloadData();
        assertEquals("tx-1", data.getTransactionId());
        assertEquals("user-1", data.getUserId());
        assertEquals("external-1", data.getExternalReference());
        assertEquals("{\"order\":\"123\"}", data.getMetadata());
        assertEquals(1000L, data.getAmountSat());
        assertEquals("FAILED", data.getStatus());
        assertEquals("ref-1", data.getReferenceId());
        assertEquals("node failure", data.getFailureReason());

        verify(webhookDeliveryRepository).save(any(WebhookDeliveryEntity.class));
    }

    @Test
    void createAccountBalanceChangedEvent_createsEvent() throws Exception {
        WebhookEndpointEntity endpoint = enabledEndpoint("account.balance_changed");
        when(webhookEndpointRepository.findAllEnabledWithSubscriptions()).thenReturn(List.of(endpoint));
        when(webhookEventRepository.findByEventKey(any())).thenReturn(Optional.empty());
        when(jsonMapper.writeValueAsString(any())).thenReturn("{}");

        TransactionEntity tx = debitTransaction();
        AccountEntryEntity entry = new AccountEntryEntity();
        entry.setId("entry-1");
        entry.setDeltaSats(1000);
        entry.setBalanceAfter(5000);

        webhookEventService.createAccountBalanceChangedEvent(tx, entry);

        ArgumentCaptor<WebhookEventEntity> captor = ArgumentCaptor.forClass(WebhookEventEntity.class);
        verify(webhookEventRepository).save(captor.capture());
        assertEquals("account.balance_changed:entry-1", captor.getValue().getEventKey());
    }

    @Test
    void createInvoiceSettledEvent_usesDomainFactsAndKeepsPayloadShape() throws Exception {
        WebhookEndpointEntity endpoint = enabledEndpoint("invoice.settled");
        when(webhookEndpointRepository.findAllEnabledWithSubscriptions()).thenReturn(List.of(endpoint));
        when(webhookEventRepository.findByEventKey(any())).thenReturn(Optional.empty());
        when(jsonMapper.writeValueAsString(any())).thenReturn("{}");

        InvoiceSettledWebhookFacts invoice = new InvoiceSettledWebhookFacts(
                "tx-1",
                "user-1",
                "payment-hash-1",
                1500L,
                TransactionStatus.COMPLETED,
                "ref-1",
                "external-1",
                "{\"order\":\"123\"}",
                6500L
        );
        webhookEventService.createInvoiceSettledEvent(invoice);

        ArgumentCaptor<WebhookEventEntity> eventCaptor = ArgumentCaptor.forClass(WebhookEventEntity.class);
        verify(webhookEventRepository).save(eventCaptor.capture());
        assertEquals("invoice.settled:payment-hash-1", eventCaptor.getValue().getEventKey());
        assertEquals("invoice.settled", eventCaptor.getValue().getEventType());
        assertEquals("INVOICE", eventCaptor.getValue().getAggregateType());
        assertEquals("payment-hash-1", eventCaptor.getValue().getAggregateId());
        assertEquals("user-1", eventCaptor.getValue().getUserId());
        assertEquals("external-1", eventCaptor.getValue().getExternalReference());

        WebhookPayloadData data = capturedPayloadData();
        assertEquals("tx-1", data.getTransactionId());
        assertEquals("user-1", data.getUserId());
        assertEquals("external-1", data.getExternalReference());
        assertEquals("{\"order\":\"123\"}", data.getMetadata());
        assertEquals(1500L, data.getAmountSat());
        assertEquals("COMPLETED", data.getStatus());
        assertEquals("ref-1", data.getReferenceId());
        assertEquals(6500L, data.getBalanceAfterSat());
        assertEquals("payment-hash-1", data.getPaymentHash());

        verify(webhookDeliveryRepository).save(any(WebhookDeliveryEntity.class));
    }

    @Test
    void createOnchainDepositConfirmedEvent_usesDomainFactsAndKeepsPayloadShape() throws Exception {
        WebhookEndpointEntity endpoint = enabledEndpoint("onchain.deposit.confirmed");
        when(webhookEndpointRepository.findAllEnabledWithSubscriptions()).thenReturn(List.of(endpoint));
        when(webhookEventRepository.findByEventKey(any())).thenReturn(Optional.empty());
        when(jsonMapper.writeValueAsString(any())).thenReturn("{}");

        OnChainDepositWebhookFacts deposit = new OnChainDepositWebhookFacts(
                "tx-1",
                "user-1",
                2500L,
                TransactionStatus.COMPLETED,
                "tx-hash:1",
                "external-1",
                "{\"order\":\"123\"}",
                8500L
        );
        webhookEventService.createOnchainDepositConfirmedEvent(deposit);

        ArgumentCaptor<WebhookEventEntity> eventCaptor = ArgumentCaptor.forClass(WebhookEventEntity.class);
        verify(webhookEventRepository).save(eventCaptor.capture());
        assertEquals("onchain.deposit.confirmed:tx-hash:1", eventCaptor.getValue().getEventKey());
        assertEquals("onchain.deposit.confirmed", eventCaptor.getValue().getEventType());
        assertEquals("TRANSACTION", eventCaptor.getValue().getAggregateType());
        assertEquals("tx-1", eventCaptor.getValue().getAggregateId());
        assertEquals("user-1", eventCaptor.getValue().getUserId());
        assertEquals("external-1", eventCaptor.getValue().getExternalReference());

        WebhookPayloadData data = capturedPayloadData();
        assertEquals("tx-1", data.getTransactionId());
        assertEquals("user-1", data.getUserId());
        assertEquals("external-1", data.getExternalReference());
        assertEquals("{\"order\":\"123\"}", data.getMetadata());
        assertEquals(2500L, data.getAmountSat());
        assertEquals("COMPLETED", data.getStatus());
        assertEquals("tx-hash:1", data.getReferenceId());
        assertEquals(8500L, data.getBalanceAfterSat());

        verify(webhookDeliveryRepository).save(any(WebhookDeliveryEntity.class));
    }

    @Test
    void createNodeOperationUnknownOutcomeEvent_includesOperationAndTransactionFacts() throws Exception {
        WebhookEndpointEntity endpoint = enabledEndpoint("node_operation.unknown_outcome");
        when(webhookEndpointRepository.findAllEnabledWithSubscriptions()).thenReturn(List.of(endpoint));
        when(webhookEventRepository.findByEventKey(any())).thenReturn(Optional.empty());
        when(jsonMapper.writeValueAsString(any())).thenReturn("{}");

        NodeOperationUnknownOutcomeFacts facts = new NodeOperationUnknownOutcomeFacts(
                "operation-1",
                "tx-1",
                "user-1",
                "ONCHAIN_SEND",
                "UNKNOWN_OUTCOME",
                "tx-ref-1",
                "broadcast-txid-1",
                5,
                "node unreachable",
                2500L,
                TransactionStatus.PENDING.name(),
                "external-1",
                "{\"order\":\"123\"}"
        );

        webhookEventService.createNodeOperationUnknownOutcomeEvent(facts);

        ArgumentCaptor<WebhookEventEntity> eventCaptor = ArgumentCaptor.forClass(WebhookEventEntity.class);
        verify(webhookEventRepository).save(eventCaptor.capture());
        assertEquals("node_operation.unknown_outcome:operation-1", eventCaptor.getValue().getEventKey());
        assertEquals("node_operation.unknown_outcome", eventCaptor.getValue().getEventType());
        assertEquals("NODE_OPERATION", eventCaptor.getValue().getAggregateType());
        assertEquals("operation-1", eventCaptor.getValue().getAggregateId());
        assertEquals("user-1", eventCaptor.getValue().getUserId());
        assertEquals("external-1", eventCaptor.getValue().getExternalReference());

        WebhookPayloadData data = capturedPayloadData();
        assertEquals("operation-1", data.getOperationId());
        assertEquals("tx-1", data.getTransactionId());
        assertEquals("user-1", data.getUserId());
        assertEquals("ONCHAIN_SEND", data.getOperationType());
        assertEquals("UNKNOWN_OUTCOME", data.getOperationStatus());
        assertEquals("tx-ref-1", data.getReferenceId());
        assertEquals("broadcast-txid-1", data.getExternalId());
        assertEquals(5, data.getAttemptCount());
        assertEquals("node unreachable", data.getOperationError());
        assertEquals(2500L, data.getAmountSat());
        assertEquals("PENDING", data.getStatus());
        assertEquals("external-1", data.getExternalReference());
        assertEquals("{\"order\":\"123\"}", data.getMetadata());

        verify(webhookDeliveryRepository).save(any(WebhookDeliveryEntity.class));
    }

    private PaymentWebhookFacts acceptedPayment() {
        return PaymentWebhookFacts.accepted(
                "tx-1",
                "user-1",
                TransactionType.LIGHTNING_DEBIT,
                1000L,
                "ref-1",
                "external-1",
                "{\"order\":\"123\"}"
        );
    }

    private WebhookPayloadData capturedPayloadData() throws Exception {
        ArgumentCaptor<WebhookPayload> payloadCaptor = ArgumentCaptor.forClass(WebhookPayload.class);
        verify(jsonMapper).writeValueAsString(payloadCaptor.capture());
        return payloadCaptor.getValue().getData();
    }

    private TransactionEntity debitTransaction() {
        TransactionEntity tx = new TransactionEntity();
        tx.setId("tx-1");
        tx.setUserId("user-1");
        tx.setType(TransactionType.LIGHTNING_DEBIT);
        tx.setCurrentStatus("PENDING");
        tx.setCurrentAmount(1000);
        tx.setReferenceId("ref-1");
        return tx;
    }

    private WebhookEndpointEntity enabledEndpoint(String eventType) {
        WebhookEndpointEntity endpoint = WebhookEndpointEntity.builder()
                .id(java.util.UUID.randomUUID())
                .name("Test Endpoint")
                .url("https://example.com/webhook")
                .signingSecret("secret")
                .enabled(true)
                .build();
        WebhookEndpointSubscriptionEntity sub = WebhookEndpointSubscriptionEntity.builder()
                .endpoint(endpoint)
                .eventType(eventType)
                .build();
        endpoint.setSubscriptions(Set.of(sub));
        return endpoint;
    }

}
