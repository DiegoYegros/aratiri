package com.aratiri.infrastructure.messaging.consumer;

import com.aratiri.infrastructure.nodeoperations.LightningPaymentOperation;
import com.aratiri.infrastructure.nodeoperations.NodeOperationService;
import com.aratiri.infrastructure.nodeoperations.OnChainSendOperationFact;
import com.aratiri.payments.application.dto.OnChainPaymentDTOs;
import com.aratiri.payments.application.dto.PayInvoiceRequestDTO;
import com.aratiri.payments.application.event.OnChainPaymentInitiatedEvent;
import com.aratiri.payments.application.event.PaymentInitiatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentConsumerTest {

    @Mock
    private NodeOperationService nodeOperationService;

    @Mock
    private JsonMapper jsonMapper;

    @Mock
    private Acknowledgment acknowledgment;

    private PaymentConsumer consumer;

    @BeforeEach
    void setUp() {
        KafkaConsumerPolicy consumerPolicy = new KafkaConsumerPolicy(jsonMapper);
        consumer = new PaymentConsumer(nodeOperationService, consumerPolicy);
    }

    @Test
    void handlePaymentInitiated_deserializesEnqueuesAndAcknowledges() throws Exception {
        String message = "{\"event\":\"payment.initiated\"}";
        PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
        request.setInvoice("lnbc1test");
        request.setExternalReference("external-ref");
        request.setMetadata("{\"orderId\":\"order-123\"}");
        PaymentInitiatedEvent event = new PaymentInitiatedEvent("user-123", "tx-123", request);

        when(jsonMapper.readValue(message, PaymentInitiatedEvent.class)).thenReturn(event);

        consumer.handlePaymentInitiated(message, acknowledgment);

        ArgumentCaptor<LightningPaymentOperation> payment = ArgumentCaptor.forClass(LightningPaymentOperation.class);
        verify(nodeOperationService).enqueueLightningPayment(payment.capture());
        assertEquals("tx-123", payment.getValue().transactionId());
        assertEquals("user-123", payment.getValue().userId());
        assertNull(payment.getValue().paymentHash());
        assertEquals("lnbc1test", payment.getValue().invoice());
        assertEquals("external-ref", payment.getValue().externalReference());
        assertEquals("{\"orderId\":\"order-123\"}", payment.getValue().metadata());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handlePaymentInitiated_wrapsFailureAndDoesNotAcknowledge() throws Exception {
        String message = "{\"event\":\"payment.initiated\"}";
        when(jsonMapper.readValue(message, PaymentInitiatedEvent.class))
                .thenThrow(new IllegalArgumentException("bad payload"));

        assertThrows(IllegalStateException.class, () -> consumer.handlePaymentInitiated(message, acknowledgment));

        verify(acknowledgment, never()).acknowledge();
        verify(nodeOperationService, never()).enqueueLightningPayment(any());
    }

    @Test
    void handleOnChainPaymentInitiated_enqueuesOperationFactAndAcknowledges() throws Exception {
        String message = "{\"event\":\"onchain.payment.initiated\"}";
        OnChainPaymentDTOs.SendOnChainRequestDTO request = new OnChainPaymentDTOs.SendOnChainRequestDTO();
        request.setAddress("tb1qdestination");
        request.setSatsAmount(25_000L);
        request.setSatPerVbyte(18L);
        request.setTargetConf(4);
        request.setExternalReference("external-ref");
        request.setMetadata("{\"orderId\":\"order-123\"}");
        OnChainPaymentInitiatedEvent event = new OnChainPaymentInitiatedEvent("user-123", "tx-123", request);

        when(jsonMapper.readValue(message, OnChainPaymentInitiatedEvent.class)).thenReturn(event);

        consumer.handleOnChainPaymentInitiated(message, acknowledgment);

        ArgumentCaptor<OnChainSendOperationFact> fact = ArgumentCaptor.forClass(OnChainSendOperationFact.class);
        verify(nodeOperationService).enqueueOnChainSend(fact.capture());
        assertEquals("tx-123", fact.getValue().transactionId());
        assertEquals("user-123", fact.getValue().userId());
        assertEquals("tb1qdestination", fact.getValue().address());
        assertEquals(25_000L, fact.getValue().satsAmount());
        assertEquals(18L, fact.getValue().satPerVbyte());
        assertEquals(4, fact.getValue().targetConf());
        assertEquals("external-ref", fact.getValue().externalReference());
        assertEquals("{\"orderId\":\"order-123\"}", fact.getValue().metadata());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleOnChainPaymentInitiated_wrapsFailureAndDoesNotAcknowledge() throws Exception {
        String message = "{\"event\":\"onchain.payment.initiated\"}";
        when(jsonMapper.readValue(message, OnChainPaymentInitiatedEvent.class))
                .thenThrow(new IllegalArgumentException("bad payload"));

        assertThrows(IllegalStateException.class, () -> consumer.handleOnChainPaymentInitiated(message, acknowledgment));

        verify(acknowledgment, never()).acknowledge();
        verify(nodeOperationService, never()).enqueueOnChainSend(any());
    }
}
