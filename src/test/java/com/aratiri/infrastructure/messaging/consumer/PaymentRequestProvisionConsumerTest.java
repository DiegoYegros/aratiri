package com.aratiri.infrastructure.messaging.consumer;

import com.aratiri.infrastructure.messaging.KafkaTopicNames;
import com.aratiri.requests.application.PaymentRequestSagaService;
import com.aratiri.requests.application.event.PaymentRequestProvisionRequestedEvent;
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
class PaymentRequestProvisionConsumerTest {

    @Mock
    private PaymentRequestSagaService sagaService;

    @Mock
    private PaymentRequestPersistencePort persistencePort;

    @Mock
    private JsonMapper jsonMapper;

    @Mock
    private Acknowledgment acknowledgment;

    private PaymentRequestProvisionConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new PaymentRequestProvisionConsumer(sagaService, persistencePort, jsonMapper);
    }

    @Test
    void handleProvisionRequested_callsThroughToSagaAndAcknowledges() throws Exception {
        PaymentRequest request = request(PaymentRequestStatus.PROVISIONING);
        when(jsonMapper.readValue("{msg}", PaymentRequestProvisionRequestedEvent.class))
                .thenReturn(new PaymentRequestProvisionRequestedEvent("id-1"));
        when(persistencePort.findById("id-1")).thenReturn(Optional.of(request));

        consumer.handleProvisionRequested("{msg}", KafkaTopicNames.PAYMENT_REQUEST_PROVISION_REQUESTED, 0, 0L, acknowledgment);

        verify(sagaService).tryProvision("id-1");
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleProvisionRequested_missingRequest_acknowledgesAndSkips() throws Exception {
        when(jsonMapper.readValue("{msg}", PaymentRequestProvisionRequestedEvent.class))
                .thenReturn(new PaymentRequestProvisionRequestedEvent("missing"));
        when(persistencePort.findById("missing")).thenReturn(Optional.empty());

        consumer.handleProvisionRequested("{msg}", KafkaTopicNames.PAYMENT_REQUEST_PROVISION_REQUESTED, 0, 0L, acknowledgment);

        verify(sagaService, never()).tryProvision("missing");
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleProvisionRequested_terminalRequest_acknowledgesAndSkips() throws Exception {
        PaymentRequest request = request(PaymentRequestStatus.OPEN);
        when(jsonMapper.readValue("{msg}", PaymentRequestProvisionRequestedEvent.class))
                .thenReturn(new PaymentRequestProvisionRequestedEvent("id-1"));
        when(persistencePort.findById("id-1")).thenReturn(Optional.of(request));

        consumer.handleProvisionRequested("{msg}", KafkaTopicNames.PAYMENT_REQUEST_PROVISION_REQUESTED, 0, 0L, acknowledgment);

        verify(sagaService, never()).tryProvision("id-1");
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleProvisionRequested_redeliveryWithHeldLease_claimFailsButIsTreatedAsSuccess() throws Exception {
        PaymentRequest request = request(PaymentRequestStatus.PROVISIONING);
        when(jsonMapper.readValue("{msg}", PaymentRequestProvisionRequestedEvent.class))
                .thenReturn(new PaymentRequestProvisionRequestedEvent("id-1"));
        when(persistencePort.findById("id-1")).thenReturn(Optional.of(request));

        consumer.handleProvisionRequested("{msg}", KafkaTopicNames.PAYMENT_REQUEST_PROVISION_REQUESTED, 0, 0L, acknowledgment);
        consumer.handleProvisionRequested("{msg}", KafkaTopicNames.PAYMENT_REQUEST_PROVISION_REQUESTED, 0, 0L, acknowledgment);

        verify(sagaService, org.mockito.Mockito.times(2)).tryProvision("id-1");
        verify(acknowledgment, org.mockito.Mockito.times(2)).acknowledge();
    }

    @Test
    void handleProvisionRequested_malformedJson_throwsWithoutAcknowledging() throws Exception {
        when(jsonMapper.readValue("{poison", PaymentRequestProvisionRequestedEvent.class))
                .thenThrow(new IllegalArgumentException("bad payload"));

        assertThrows(
                IllegalArgumentException.class,
                () -> consumer.handleProvisionRequested("{poison", KafkaTopicNames.PAYMENT_REQUEST_PROVISION_REQUESTED, 0, 0L, acknowledgment)
        );

        verify(sagaService, never()).tryProvision(org.mockito.ArgumentMatchers.anyString());
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void handleFailedProvisioning_logsWithoutRethrowingOrMarkingTerminal() {
        assertDoesNotThrow(() -> consumer.handleFailedProvisioning(
                "{msg}", KafkaTopicNames.PAYMENT_REQUEST_PROVISION_REQUESTED, "boom"));
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
                status == PaymentRequestStatus.PROVISIONING ? now : null,
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
