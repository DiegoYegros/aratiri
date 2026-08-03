package com.aratiri.requests.application;

import com.aratiri.infrastructure.configuration.PaymentRequestSagaProperties;
import com.aratiri.requests.application.port.out.PaymentRequestPersistencePort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRequestSagaMetricsTest {

    private static final Instant NOW = Instant.parse("2025-06-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private PaymentRequestPersistencePort persistencePort;

    @Test
    void registerGauges_exposesQueueCounts() {
        PaymentRequestSagaProperties properties = new PaymentRequestSagaProperties();
        properties.setCancelMaxAttempts(7);
        when(persistencePort.countDueProvisioning(NOW)).thenReturn(1L);
        when(persistencePort.countInProgressProvisioning(NOW)).thenReturn(2L);
        when(persistencePort.countFailedProvisioning()).thenReturn(3L);
        when(persistencePort.countDueCancellations(NOW)).thenReturn(4L);
        when(persistencePort.countInProgressCancellations(NOW)).thenReturn(5L);
        when(persistencePort.countExhaustedCancellations(7)).thenReturn(6L);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PaymentRequestSagaMetrics metrics = new PaymentRequestSagaMetrics(
                persistencePort, properties, registry, CLOCK);
        metrics.registerGauges();

        assertEquals(1.0, registry.get("aratiri.payment_requests.provisioning.due").gauge().value());
        assertEquals(2.0, registry.get("aratiri.payment_requests.provisioning.in_progress").gauge().value());
        assertEquals(3.0, registry.get("aratiri.payment_requests.provisioning.failed").gauge().value());
        assertEquals(4.0, registry.get("aratiri.payment_requests.cancellation.due").gauge().value());
        assertEquals(5.0, registry.get("aratiri.payment_requests.cancellation.in_progress").gauge().value());
        assertEquals(6.0, registry.get("aratiri.payment_requests.cancellation.exhausted").gauge().value());
        assertNotNull(registry.find("aratiri.payment_requests.provisioning.due").gauge());
    }
}
