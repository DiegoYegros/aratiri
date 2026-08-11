package com.aratiri.infrastructure.messaging.consumer;

import com.aratiri.infrastructure.messaging.KafkaTopicNames;
import com.aratiri.requests.application.PaymentRequestSagaService;
import com.aratiri.requests.application.event.PaymentRequestCancelRequestedEvent;
import com.aratiri.requests.application.port.out.PaymentRequestPersistencePort;
import com.aratiri.requests.domain.PaymentRequest;
import com.aratiri.requests.domain.PaymentRequestStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRequestCancelConsumerTest {

    @Mock
    private PaymentRequestSagaService sagaService;

    @Mock
    private PaymentRequestPersistencePort persistencePort;

    @Mock
    private JsonMapper jsonMapper;

    @Mock
    private Acknowledgment acknowledgment;

    private PaymentRequestCancelConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new PaymentRequestCancelConsumer(sagaService, persistencePort, jsonMapper);
    }

    @Test
    void handleCancelRequested_callsThroughToSagaAndAcknowledges() throws Exception {
        PaymentRequest request = request(PaymentRequestStatus.CANCEL_PENDING);
        when(jsonMapper.readValue("{msg}", PaymentRequestCancelRequestedEvent.class))
                .thenReturn(new PaymentRequestCancelRequestedEvent("id-1"));
        when(persistencePort.findById("id-1")).thenReturn(Optional.of(request));

        consumer.handleCancelRequested("{msg}", KafkaTopicNames.PAYMENT_REQUEST_CANCEL_REQUESTED, 0, 0L, acknowledgment);

        verify(sagaService).tryCancel("id-1");
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleCancelRequested_missingRequest_acknowledgesAndSkips() throws Exception {
        when(jsonMapper.readValue("{msg}", PaymentRequestCancelRequestedEvent.class))
                .thenReturn(new PaymentRequestCancelRequestedEvent("missing"));
        when(persistencePort.findById("missing")).thenReturn(Optional.empty());

        consumer.handleCancelRequested("{msg}", KafkaTopicNames.PAYMENT_REQUEST_CANCEL_REQUESTED, 0, 0L, acknowledgment);

        verify(sagaService, never()).tryCancel("missing");
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleCancelRequested_terminalRequest_acknowledgesAndSkips() throws Exception {
        PaymentRequest request = request(PaymentRequestStatus.CANCELLED);
        when(jsonMapper.readValue("{msg}", PaymentRequestCancelRequestedEvent.class))
                .thenReturn(new PaymentRequestCancelRequestedEvent("id-1"));
        when(persistencePort.findById("id-1")).thenReturn(Optional.of(request));

        consumer.handleCancelRequested("{msg}", KafkaTopicNames.PAYMENT_REQUEST_CANCEL_REQUESTED, 0, 0L, acknowledgment);

        verify(sagaService, never()).tryCancel("id-1");
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleCancelRequested_redeliveryWithHeldLease_claimFailsButIsTreatedAsSuccess() throws Exception {
        PaymentRequest request = request(PaymentRequestStatus.CANCEL_PENDING);
        when(jsonMapper.readValue("{msg}", PaymentRequestCancelRequestedEvent.class))
                .thenReturn(new PaymentRequestCancelRequestedEvent("id-1"));
        when(persistencePort.findById("id-1")).thenReturn(Optional.of(request));

        consumer.handleCancelRequested("{msg}", KafkaTopicNames.PAYMENT_REQUEST_CANCEL_REQUESTED, 0, 0L, acknowledgment);
        consumer.handleCancelRequested("{msg}", KafkaTopicNames.PAYMENT_REQUEST_CANCEL_REQUESTED, 0, 0L, acknowledgment);

        verify(sagaService, org.mockito.Mockito.times(2)).tryCancel("id-1");
        verify(acknowledgment, org.mockito.Mockito.times(2)).acknowledge();
    }

    @Test
    void handleCancelRequested_malformedJson_throwsWithoutAcknowledging() throws Exception {
        when(jsonMapper.readValue("{poison", PaymentRequestCancelRequestedEvent.class))
                .thenThrow(new IllegalArgumentException("bad payload"));

        assertThrows(
                IllegalArgumentException.class,
                () -> consumer.handleCancelRequested("{poison", KafkaTopicNames.PAYMENT_REQUEST_CANCEL_REQUESTED, 0, 0L, acknowledgment)
        );

        verify(sagaService, never()).tryCancel(org.mockito.ArgumentMatchers.anyString());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void handleFailedCancellation_logsWithoutRethrowingOrMarkingTerminal() {
        assertDoesNotThrow(() -> consumer.handleFailedCancellation(
                "{msg}", KafkaTopicNames.PAYMENT_REQUEST_CANCEL_REQUESTED, "boom"));
    }

    private static PaymentRequest request(PaymentRequestStatus status) {
        Instant now = Instant.parse("2025-01-01T00:00:00Z");
        return new PaymentRequest(
                "id-1",
                "pub-1",
                "user-1",
                1000L,
                "memo",
                status,
                "payment-hash",
                "cHJlaW1hZ2U=",
                null,
                null,
                "key-1",
                "payload-hash",
                now,
                now.plusSeconds(3600),
                null,
                null,
                0,
                null,
                null,
                null,
                null,
                0,
                null,
                null,
                null,
                null
        );
    }
}
