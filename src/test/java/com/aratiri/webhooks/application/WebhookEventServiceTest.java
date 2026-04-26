package com.aratiri.webhooks.application;

import com.aratiri.infrastructure.persistence.jpa.entity.*;
import com.aratiri.infrastructure.persistence.jpa.repository.WebhookDeliveryRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.WebhookEndpointRepository;
import com.aratiri.infrastructure.persistence.jpa.repository.WebhookEventRepository;
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

        TransactionEntity tx = debitTransaction();
        webhookEventService.createPaymentAcceptedEvent(tx);

        ArgumentCaptor<WebhookEventEntity> eventCaptor = ArgumentCaptor.forClass(WebhookEventEntity.class);
        verify(webhookEventRepository).save(eventCaptor.capture());
        assertEquals("payment.accepted:tx-1", eventCaptor.getValue().getEventKey());

        ArgumentCaptor<WebhookDeliveryEntity> deliveryCaptor = ArgumentCaptor.forClass(WebhookDeliveryEntity.class);
        verify(webhookDeliveryRepository).save(deliveryCaptor.capture());
        assertEquals(endpoint.getId(), deliveryCaptor.getValue().getEndpointId());
        assertEquals(WebhookDeliveryStatus.PENDING, deliveryCaptor.getValue().getStatus());
    }

    @Test
    void createPaymentAcceptedEvent_noDeliveryForDisabledEndpoint() {
        disabledEndpoint("payment.accepted");
        when(webhookEndpointRepository.findAllEnabledWithSubscriptions()).thenReturn(List.of());
        when(webhookEventRepository.findByEventKey(any())).thenReturn(Optional.empty());
        when(jsonMapper.writeValueAsString(any())).thenReturn("{}");

        TransactionEntity tx = debitTransaction();
        webhookEventService.createPaymentAcceptedEvent(tx);

        verify(webhookEventRepository).save(any());
        verify(webhookDeliveryRepository, never()).save(any());
    }

    @Test
    void createPaymentAcceptedEvent_idempotentByEventKey() {
        WebhookEventEntity existing = WebhookEventEntity.builder().eventKey("payment.accepted:tx-1").build();
        when(webhookEventRepository.findByEventKey("payment.accepted:tx-1")).thenReturn(Optional.of(existing));

        TransactionEntity tx = debitTransaction();
        webhookEventService.createPaymentAcceptedEvent(tx);

        verify(webhookEventRepository, never()).save(any());
        verify(webhookDeliveryRepository, never()).save(any());
    }

    @Test
    void createPaymentSucceededEvent_ignoresCreditTransaction() {
        TransactionEntity tx = new TransactionEntity();
        tx.setId("tx-1");
        tx.setUserId("user-1");
        tx.setType(com.aratiri.transactions.application.dto.TransactionType.LIGHTNING_CREDIT);
        tx.setCurrentStatus("COMPLETED");

        webhookEventService.createPaymentSucceededEvent(tx);

        verify(webhookEventRepository, never()).save(any());
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

    private TransactionEntity debitTransaction() {
        TransactionEntity tx = new TransactionEntity();
        tx.setId("tx-1");
        tx.setUserId("user-1");
        tx.setType(com.aratiri.transactions.application.dto.TransactionType.LIGHTNING_DEBIT);
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

    private WebhookEndpointEntity disabledEndpoint(String eventType) {
        WebhookEndpointEntity endpoint = enabledEndpoint(eventType);
        endpoint.setEnabled(false);
        return endpoint;
    }
}
