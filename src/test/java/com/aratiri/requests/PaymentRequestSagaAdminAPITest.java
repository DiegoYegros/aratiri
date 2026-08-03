package com.aratiri.requests;

import com.aratiri.infrastructure.configuration.PaymentRequestSagaProperties;
import com.aratiri.requests.application.dto.PaymentRequestSagaStatusDTO;
import com.aratiri.requests.application.dto.PaymentRequestSagaWorkItemDTO;
import com.aratiri.requests.application.port.out.PaymentRequestPersistencePort;
import com.aratiri.requests.domain.PaymentRequest;
import com.aratiri.requests.domain.PaymentRequestStatus;
import com.aratiri.requests.domain.exception.PaymentRequestConflictException;
import com.aratiri.requests.domain.exception.PaymentRequestNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentRequestSagaAdminAPITest {

    private static final Instant NOW = Instant.parse("2025-06-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private PaymentRequestPersistencePort persistencePort;

    private PaymentRequestSagaProperties properties;
    private PaymentRequestSagaAdminAPI api;

    @BeforeEach
    void setUp() {
        properties = new PaymentRequestSagaProperties();
        properties.setCancelMaxAttempts(5);
        api = new PaymentRequestSagaAdminAPI(persistencePort, properties, CLOCK);
    }

    @Test
    void status_returnsQueueCounts() {
        when(persistencePort.countDueProvisioning(NOW)).thenReturn(1L);
        when(persistencePort.countInProgressProvisioning(NOW)).thenReturn(2L);
        when(persistencePort.countFailedProvisioning()).thenReturn(3L);
        when(persistencePort.countDueCancellations(NOW)).thenReturn(4L);
        when(persistencePort.countInProgressCancellations(NOW)).thenReturn(5L);
        when(persistencePort.countExhaustedCancellations(5)).thenReturn(6L);

        ResponseEntity<PaymentRequestSagaStatusDTO> response = api.status();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        PaymentRequestSagaStatusDTO body = response.getBody();
        assertNotNull(body);
        assertEquals(1L, body.getProvisioningDue());
        assertEquals(2L, body.getProvisioningInProgress());
        assertEquals(3L, body.getProvisioningFailed());
        assertEquals(4L, body.getCancellationDue());
        assertEquals(5L, body.getCancellationInProgress());
        assertEquals(6L, body.getCancellationExhausted());
    }

    @Test
    void failed_clampsLimitAndMapsItems() {
        PaymentRequest failed = request(PaymentRequestStatus.FAILED, 3, null);
        when(persistencePort.findFailed(500)).thenReturn(List.of(failed));

        ResponseEntity<List<PaymentRequestSagaWorkItemDTO>> response = api.failed(9999);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("pub-1", response.getBody().getFirst().getPublicId());
        assertEquals("FAILED", response.getBody().getFirst().getStatus());
        verify(persistencePort).findFailed(500);
    }

    @Test
    void failed_clampsLowerBoundToOne() {
        when(persistencePort.findFailed(1)).thenReturn(List.of());

        api.failed(0);

        verify(persistencePort).findFailed(1);
    }

    @Test
    void exhaustedCancellations_clampsLimitAndMapsItems() {
        PaymentRequest exhausted = request(PaymentRequestStatus.CANCEL_PENDING, 0, null);
        when(persistencePort.findExhaustedCancellations(5, 100)).thenReturn(List.of(exhausted));

        ResponseEntity<List<PaymentRequestSagaWorkItemDTO>> response = api.exhaustedCancellations(100);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("CANCEL_PENDING", response.getBody().getFirst().getStatus());
        verify(persistencePort).findExhaustedCancellations(5, 100);
    }

    @Test
    void retryFailed_usesConditionalRequeue_notBlindSave() {
        PaymentRequest requeued = request(PaymentRequestStatus.PROVISIONING, 0, NOW);
        when(persistencePort.requeueFailedProvisioning("pub-1", NOW)).thenReturn(1);
        when(persistencePort.findByPublicId("pub-1")).thenReturn(Optional.of(requeued));

        ResponseEntity<PaymentRequestSagaWorkItemDTO> response = api.retryFailed("pub-1");

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("PROVISIONING", response.getBody().getStatus());
        verify(persistencePort).requeueFailedProvisioning("pub-1", NOW);
        verify(persistencePort, never()).save(any());
    }

    @Test
    void retryFailed_concurrentPaid_notOverwritten_throwsConflict() {
        PaymentRequest paid = request(PaymentRequestStatus.PAID, 3, null);
        when(persistencePort.requeueFailedProvisioning("pub-1", NOW)).thenReturn(0);
        when(persistencePort.findByPublicId("pub-1")).thenReturn(Optional.of(paid));

        PaymentRequestConflictException ex = assertThrows(
                PaymentRequestConflictException.class,
                () -> api.retryFailed("pub-1")
        );
        assertEquals(HttpStatus.CONFLICT.value(), ex.getStatus());
        assertTrue(ex.getMessage().contains("PAID"));
        verify(persistencePort, never()).save(any());
    }

    @Test
    void retryFailed_missing_throwsNotFound() {
        when(persistencePort.requeueFailedProvisioning("missing", NOW)).thenReturn(0);
        when(persistencePort.findByPublicId("missing")).thenReturn(Optional.empty());

        assertThrows(PaymentRequestNotFoundException.class, () -> api.retryFailed("missing"));
        verify(persistencePort, never()).save(any());
    }

    private static PaymentRequest request(PaymentRequestStatus status, int attempts, Instant nextAttemptAt) {
        return new PaymentRequest(
                "id-1", "pub-1", "user-1", 1000L, "memo",
                status, "hash", "preimage", null, null,
                "key", "payload", NOW.minusSeconds(60), NOW.plusSeconds(3600),
                status == PaymentRequestStatus.PAID ? NOW : null, null,
                attempts, nextAttemptAt, null, null,
                status == PaymentRequestStatus.FAILED ? "boom" : null,
                status == PaymentRequestStatus.CANCEL_PENDING ? 5 : 0, null, null, null,
                status == PaymentRequestStatus.CANCEL_PENDING ? "cancel boom" : null
        );
    }
}
