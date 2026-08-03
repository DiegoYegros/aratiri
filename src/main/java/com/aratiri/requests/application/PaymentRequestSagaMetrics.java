package com.aratiri.requests.application;

import com.aratiri.infrastructure.configuration.PaymentRequestSagaProperties;
import com.aratiri.requests.application.port.out.PaymentRequestPersistencePort;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Actuator/Prometheus gauges for payment-request saga work queues.
 */
@Component
public class PaymentRequestSagaMetrics {

    private final PaymentRequestPersistencePort persistencePort;
    private final PaymentRequestSagaProperties properties;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public PaymentRequestSagaMetrics(
            PaymentRequestPersistencePort persistencePort,
            PaymentRequestSagaProperties properties,
            MeterRegistry meterRegistry,
            Clock clock
    ) {
        this.persistencePort = persistencePort;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    @PostConstruct
    void registerGauges() {
        Gauge.builder("aratiri.payment_requests.provisioning.due", this, m -> m.persistencePort.countDueProvisioning(m.clock.instant()))
                .description("Due PROVISIONING payment requests")
                .register(meterRegistry);
        Gauge.builder("aratiri.payment_requests.provisioning.in_progress", this, m -> m.persistencePort.countInProgressProvisioning(m.clock.instant()))
                .description("In-progress leased PROVISIONING payment requests")
                .register(meterRegistry);
        Gauge.builder("aratiri.payment_requests.provisioning.failed", this, m -> m.persistencePort.countFailedProvisioning())
                .description("FAILED provisioning payment requests (inspectable/retryable by operators)")
                .register(meterRegistry);
        Gauge.builder("aratiri.payment_requests.cancellation.due", this, m -> m.persistencePort.countDueCancellations(m.clock.instant()))
                .description("Due CANCEL_PENDING payment requests")
                .register(meterRegistry);
        Gauge.builder("aratiri.payment_requests.cancellation.in_progress", this, m -> m.persistencePort.countInProgressCancellations(m.clock.instant()))
                .description("In-progress leased CANCEL_PENDING payment requests")
                .register(meterRegistry);
        Gauge.builder(
                        "aratiri.payment_requests.cancellation.exhausted",
                        this,
                        m -> m.persistencePort.countExhaustedCancellations(m.properties.getCancelMaxAttempts())
                )
                .description("CANCEL_PENDING rows at or above max attempts")
                .register(meterRegistry);
    }
}
