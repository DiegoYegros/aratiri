package com.aratiri.infrastructure.messaging.consumer;

import com.aratiri.payments.application.dto.OnChainPaymentDTOs;
import com.aratiri.payments.application.event.OnChainPaymentInitiatedEvent;
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
class OnChainSendExecutionConsumerTest {

    @Mock
    private PaymentExecutionService paymentExecutionService;

    @Mock
    private Acknowledgment acknowledgment;

    private OnChainSendExecutionConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new OnChainSendExecutionConsumer(paymentExecutionService, new JsonMapper());
    }

    @Test
    void handleOnChainPaymentInitiated_executesAndAcknowledges() throws Exception {
        OnChainPaymentDTOs.SendOnChainRequestDTO request = new OnChainPaymentDTOs.SendOnChainRequestDTO();
        request.setAddress("tb1qdestination");
        request.setSatsAmount(25_000L);
        OnChainPaymentInitiatedEvent event = new OnChainPaymentInitiatedEvent("user-123", "tx-123", request);
        String message = new JsonMapper().writeValueAsString(event);

        consumer.handleOnChainPaymentInitiated(message, "onchain.payment.initiated", 0, 1L, acknowledgment);

        verify(paymentExecutionService).executeOnChainSend(event);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleOnChainPaymentInitiated_throwsOnInvalidJsonAndDoesNotAcknowledge() {
        assertThrows(Exception.class,
                () -> consumer.handleOnChainPaymentInitiated("{poison", "onchain.payment.initiated", 0, 1L, acknowledgment));
        verify(acknowledgment, never()).acknowledge();
        verify(paymentExecutionService, never()).executeOnChainSend(any());
    }

    @Test
    void handleOnChainPaymentInitiated_retryableFailureDoesNotAcknowledge() throws Exception {
        OnChainPaymentDTOs.SendOnChainRequestDTO request = new OnChainPaymentDTOs.SendOnChainRequestDTO();
        request.setAddress("tb1qdestination");
        request.setSatsAmount(25_000L);
        OnChainPaymentInitiatedEvent event = new OnChainPaymentInitiatedEvent("user-123", "tx-123", request);
        String message = new JsonMapper().writeValueAsString(event);
        doThrow(new RetryablePaymentExecutionException("node down"))
                .when(paymentExecutionService).executeOnChainSend(any());

        assertThrows(RetryablePaymentExecutionException.class,
                () -> consumer.handleOnChainPaymentInitiated(message, "onchain.payment.initiated", 0, 1L, acknowledgment));
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void handleFailedOnChainSendExecution_dltBuildsUnknownOutcomeWebhook() throws Exception {
        OnChainPaymentDTOs.SendOnChainRequestDTO request = new OnChainPaymentDTOs.SendOnChainRequestDTO();
        request.setAddress("tb1qdestination");
        request.setSatsAmount(25_000L);
        OnChainPaymentInitiatedEvent event = new OnChainPaymentInitiatedEvent("user-123", "tx-123", request);
        String message = new JsonMapper().writeValueAsString(event);

        consumer.handleFailedOnChainSendExecution(message, "onchain.payment.initiated.DLT", "node down", 5);

        verify(paymentExecutionService).handleOnChainUnknownOutcome(event, "node down", 5);
    }

    @Test
    void handleFailedOnChainSendExecution_poisonMessage_doesNotThrow() {
        consumer.handleFailedOnChainSendExecution("{poison", "onchain.payment.initiated.DLT", "bad payload", null);
        verify(paymentExecutionService, never()).handleOnChainUnknownOutcome(any(), anyString(), anyInt());
    }
}
