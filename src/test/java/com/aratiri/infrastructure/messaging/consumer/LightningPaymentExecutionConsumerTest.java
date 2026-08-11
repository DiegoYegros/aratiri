package com.aratiri.infrastructure.messaging.consumer;

import com.aratiri.payments.application.dto.PayInvoiceRequestDTO;
import com.aratiri.payments.application.event.PaymentInitiatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LightningPaymentExecutionConsumerTest {

    @Mock
    private PaymentExecutionService paymentExecutionService;

    @Mock
    private Acknowledgment acknowledgment;

    private LightningPaymentExecutionConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new LightningPaymentExecutionConsumer(paymentExecutionService, new JsonMapper());
    }

    @Test
    void handlePaymentInitiated_executesAndAcknowledges() throws Exception {
        PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
        request.setInvoice("lnbc1test");
        PaymentInitiatedEvent event = new PaymentInitiatedEvent("user-123", "tx-123", request);
        String message = new JsonMapper().writeValueAsString(event);

        consumer.handlePaymentInitiated(message, "payment.initiated", 0, 1L, acknowledgment);

        verify(paymentExecutionService).executeLightningPayment(event);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handlePaymentInitiated_throwsOnInvalidJsonAndDoesNotAcknowledge() {
        assertThrows(Exception.class,
                () -> consumer.handlePaymentInitiated("{poison", "payment.initiated", 0, 1L, acknowledgment));
        verify(acknowledgment, never()).acknowledge();
        verify(paymentExecutionService, never()).executeLightningPayment(any());
    }

    @Test
    void handlePaymentInitiated_retryableFailureDoesNotAcknowledge() throws Exception {
        PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
        request.setInvoice("lnbc1test");
        PaymentInitiatedEvent event = new PaymentInitiatedEvent("user-123", "tx-123", request);
        String message = new JsonMapper().writeValueAsString(event);
        doThrow(new RetryablePaymentExecutionException("IN_FLIGHT"))
                .when(paymentExecutionService).executeLightningPayment(any());

        assertThrows(RetryablePaymentExecutionException.class,
                () -> consumer.handlePaymentInitiated(message, "payment.initiated", 0, 1L, acknowledgment));
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void handleFailedPaymentExecution_dltBuildsUnknownOutcomeWebhook() throws Exception {
        PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
        request.setInvoice("lnbc1test");
        PaymentInitiatedEvent event = new PaymentInitiatedEvent("user-123", "tx-123", request);
        String message = new JsonMapper().writeValueAsString(event);

        consumer.handleFailedPaymentExecution(message, "payment.initiated.DLT", "node down", 5);

        verify(paymentExecutionService).handleLightningUnknownOutcome(event, "node down", 5);
    }

    @Test
    void handleFailedPaymentExecution_passesDeliveryAttemptToUnknownOutcomeHandler() throws Exception {
        PayInvoiceRequestDTO request = new PayInvoiceRequestDTO();
        request.setInvoice("lnbc1test");
        PaymentInitiatedEvent event = new PaymentInitiatedEvent("user-123", "tx-123", request);
        String message = new JsonMapper().writeValueAsString(event);

        consumer.handleFailedPaymentExecution(message, "payment.initiated-dlt", "node down", 4);

        verify(paymentExecutionService).handleLightningUnknownOutcome(event, "node down", 4);
    }

    @Test
    void handleFailedPaymentExecution_poisonMessage_doesNotThrow() {
        consumer.handleFailedPaymentExecution("{poison", "payment.initiated.DLT", "bad payload", null);
        verify(paymentExecutionService, never()).handleLightningUnknownOutcome(any(), anyString(), anyInt());
    }
}
