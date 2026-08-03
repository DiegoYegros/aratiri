package com.aratiri.requests.application;

import com.aratiri.infrastructure.messaging.outbox.OutboxWriter;
import com.aratiri.invoices.application.InvoiceStateUpdateResult;
import com.aratiri.invoices.application.port.in.InvoiceSettlementPort;
import com.aratiri.invoices.application.port.out.LightningInvoicePersistencePort;
import com.aratiri.invoices.domain.LightningInvoice;
import com.aratiri.requests.application.port.out.PaymentRequestPersistencePort;
import com.aratiri.requests.domain.PaymentRequest;
import com.aratiri.requests.domain.PaymentRequestStatus;
import com.aratiri.webhooks.application.WebhookEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentRequestProvisioningFinalizerTest {

    private static final Instant NOW = Instant.parse("2025-06-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String CLAIM = "request-saga-claim-1";

    @Mock
    private PaymentRequestPersistencePort persistencePort;
    @Mock
    private LightningInvoicePersistencePort lightningInvoicePersistencePort;
    @Mock
    private WebhookEventService webhookEventService;
    @Mock
    private InvoiceSettlementPort invoiceSettlementPort;
    @Mock
    private OutboxWriter outboxWriter;

    private PaymentRequestProvisioningFinalizer finalizer;

    @BeforeEach
    void setUp() {
        finalizer = new PaymentRequestProvisioningFinalizer(
                persistencePort,
                lightningInvoicePersistencePort,
                webhookEventService,
                invoiceSettlementPort,
                outboxWriter,
                CLOCK
        );
    }

    @Test
    void finalizeOpen_afterCancelClearsClaim_skipsLocalInvoiceUpsert() {
        PaymentRequest request = provisioningRequest(CLAIM);
        PaymentRequest cancelPending = new PaymentRequest(
                request.id(), request.publicId(), request.userId(), request.amountSats(), request.memo(),
                PaymentRequestStatus.CANCEL_PENDING, request.paymentHash(), request.preimage(),
                null, null, request.idempotencyKey(), request.idempotencyPayloadHash(),
                request.createdAt(), request.expiresAt(), null, null,
                request.provisionAttemptCount(), null, null, null, null,
                1, NOW, null, null, null
        );
        when(persistencePort.findByIdForUpdate(request.id())).thenReturn(Optional.of(cancelPending));

        finalizer.finalizeOpenOrSettled(
                request, "lnbc-stale", LightningInvoice.InvoiceState.OPEN, 0L, 900L, CLAIM);

        verify(lightningInvoicePersistencePort, never()).findByPaymentHash(anyString());
        verify(lightningInvoicePersistencePort, never()).save(any());
        verify(webhookEventService, never()).createInvoiceCreatedEvent(any());
        verify(persistencePort, never()).finalizeProvisioningOpen(anyString(), anyString(), anyString(), anyString());
        verify(invoiceSettlementPort, never()).recordInvoiceStateUpdate(any());
    }

    @Test
    void finalizeOpen_activeClaim_upsertsThenFinalizes() {
        PaymentRequest request = provisioningRequest(CLAIM);
        when(persistencePort.findByIdForUpdate(request.id())).thenReturn(Optional.of(request));
        when(lightningInvoicePersistencePort.findByPaymentHash(request.paymentHash()))
                .thenReturn(Optional.empty());
        LightningInvoice saved = new LightningInvoice(
                "inv-1", request.userId(), request.paymentHash(), request.preimage(), "lnbc1",
                LightningInvoice.InvoiceState.OPEN, request.amountSats(),
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), 900L, 0, null, request.memo(), null, null
        );
        when(lightningInvoicePersistencePort.save(any())).thenReturn(saved);
        when(persistencePort.finalizeProvisioningOpen(request.id(), "lnbc1", "inv-1", CLAIM)).thenReturn(1);

        finalizer.finalizeOpenOrSettled(
                request, "lnbc1", LightningInvoice.InvoiceState.OPEN, 0L, 900L, CLAIM);

        verify(lightningInvoicePersistencePort).save(any());
        verify(webhookEventService).createInvoiceCreatedEvent(any());
        verify(persistencePort).finalizeProvisioningOpen(request.id(), "lnbc1", "inv-1", CLAIM);
    }

    @Test
    void finalizeSettled_upsertsFirst_withoutClaimFence() {
        PaymentRequest request = provisioningRequest(CLAIM);
        LightningInvoice openSaved = new LightningInvoice(
                "inv-1", request.userId(), request.paymentHash(), request.preimage(), "lnbc1",
                LightningInvoice.InvoiceState.OPEN, request.amountSats(),
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), 900L, 0, null, request.memo(), null, null
        );
        LightningInvoice settled = new LightningInvoice(
                "inv-1", request.userId(), request.paymentHash(), request.preimage(), "lnbc1",
                LightningInvoice.InvoiceState.SETTLED, request.amountSats(),
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), 900L, 1000L,
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), request.memo(), null, null
        );
        when(lightningInvoicePersistencePort.findByPaymentHash(request.paymentHash()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(settled));
        when(lightningInvoicePersistencePort.save(any())).thenReturn(openSaved);
        when(invoiceSettlementPort.recordInvoiceStateUpdate(any()))
                .thenReturn(InvoiceStateUpdateResult.changed());

        finalizer.finalizeOpenOrSettled(
                request, "lnbc1", LightningInvoice.InvoiceState.SETTLED, 1000L, 900L, CLAIM);

        verify(persistencePort, never()).findByIdForUpdate(anyString());
        verify(lightningInvoicePersistencePort).save(any());
        verify(invoiceSettlementPort).recordInvoiceStateUpdate(any());
        verify(persistencePort, never()).finalizeProvisioningOpen(anyString(), anyString(), anyString(), anyString());
    }

    private static PaymentRequest provisioningRequest(String lockedBy) {
        return new PaymentRequest(
                "id-1", "pub-1", "user-1", 1000L, "memo",
                PaymentRequestStatus.PROVISIONING, "abc123hash", "preimage",
                null, null, "key", "payload", NOW, NOW.plusSeconds(3600), null, null,
                1, NOW, NOW.plusSeconds(300), lockedBy, null,
                0, null, null, null, null
        );
    }
}
