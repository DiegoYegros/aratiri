package com.aratiri.requests.application;

import com.aratiri.infrastructure.configuration.PaymentRequestSagaProperties;
import com.aratiri.invoices.application.port.out.LightningNodePort;
import com.aratiri.invoices.domain.InvoiceCancelOutcome;
import com.aratiri.invoices.domain.LightningInvoice;
import com.aratiri.invoices.domain.LightningInvoiceCreation;
import com.aratiri.invoices.domain.LightningNodeInvoice;
import com.aratiri.requests.application.port.out.PaymentRequestPersistencePort;
import com.aratiri.requests.domain.PaymentRequest;
import com.aratiri.requests.domain.PaymentRequestStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentRequestSagaServiceTest {

    private static final Instant NOW = Instant.parse("2025-06-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private PaymentRequestPersistencePort persistencePort;
    @Mock
    private LightningNodePort lightningNodePort;
    @Mock
    private PaymentRequestProvisioningFinalizer provisioningFinalizer;

    private PaymentRequestSagaProperties properties;
    private PaymentRequestSagaService sagaService;

    @BeforeEach
    void setUp() {
        properties = new PaymentRequestSagaProperties();
        properties.setLeaseSeconds(300);
        properties.setProvisionMaxAttempts(3);
        properties.setCancelMaxAttempts(3);
        properties.setBackoffBaseMs(1000);
        properties.setBackoffMaxMs(60000);
        sagaService = new PaymentRequestSagaService(
                persistencePort,
                lightningNodePort,
                provisioningFinalizer,
                properties,
                CLOCK
        );
    }

    @Test
    void tryProvision_looksUpBeforeAddInvoice_usesRemainingLifetime() {
        PaymentRequest request = provisioningRequest(NOW.minusSeconds(600), NOW.plusSeconds(3000));
        when(persistencePort.claimProvisioning(eq("id-1"), anyString(), any(), eq(NOW))).thenReturn(1);
        when(persistencePort.findById("id-1")).thenReturn(Optional.of(request));
        when(lightningNodePort.lookupInvoice(request.paymentHash())).thenReturn(Optional.empty());
        when(lightningNodePort.createInvoice(anyLong(), anyString(), any(), any(), anyLong()))
                .thenReturn(new LightningInvoiceCreation("lnbc1", request.paymentHash(), 3000));

        sagaService.tryProvision("id-1");

        verify(lightningNodePort).lookupInvoice(request.paymentHash());
        ArgumentCaptor<Long> expiryCaptor = ArgumentCaptor.forClass(Long.class);
        verify(lightningNodePort).createInvoice(eq(1000L), eq("memo"), any(), any(), expiryCaptor.capture());
        assertEquals(3000L, expiryCaptor.getValue(), "AddInvoice must use remaining lifetime, not original duration");
        verify(provisioningFinalizer).finalizeOpenOrSettled(
                eq(request),
                eq("lnbc1"),
                eq(LightningInvoice.InvoiceState.OPEN),
                eq(0L),
                eq(3000L),
                anyString()
        );
    }

    @Test
    void tryProvision_expiredIntent_failsWithoutAddInvoice() {
        PaymentRequest request = provisioningRequest(NOW.minusSeconds(7200), NOW.minusSeconds(1));
        when(persistencePort.claimProvisioning(eq("id-1"), anyString(), any(), eq(NOW))).thenReturn(1);
        when(persistencePort.findById("id-1")).thenReturn(Optional.of(request));

        sagaService.tryProvision("id-1");

        verify(lightningNodePort, never()).lookupInvoice(anyString());
        verify(lightningNodePort, never()).createInvoice(anyLong(), anyString(), any(), any(), anyLong());
        verify(persistencePort).markProvisioningFailed(
                eq("id-1"),
                eq(PaymentRequestSagaService.EXPIRED_BEFORE_MATERIALIZATION),
                anyString()
        );
        verifyNoInteractions(provisioningFinalizer);
    }

    @Test
    void tryProvision_recoversExistingLndInvoiceWithoutAdd() {
        PaymentRequest request = provisioningRequest(NOW, NOW.plusSeconds(3600));
        when(persistencePort.claimProvisioning(eq("id-1"), anyString(), any(), eq(NOW))).thenReturn(1);
        when(persistencePort.findById("id-1")).thenReturn(Optional.of(request));
        when(lightningNodePort.lookupInvoice(request.paymentHash())).thenReturn(Optional.of(
                new LightningNodeInvoice("lnbc-existing", LightningInvoice.InvoiceState.OPEN, 0L, 1000L)
        ));

        sagaService.tryProvision("id-1");

        verify(lightningNodePort, never()).createInvoice(anyLong(), anyString(), any(), any(), anyLong());
        verify(provisioningFinalizer).finalizeOpenOrSettled(
                eq(request),
                eq("lnbc-existing"),
                eq(LightningInvoice.InvoiceState.OPEN),
                eq(0L),
                eq(3600L),
                anyString()
        );
    }

    @Test
    void tryProvision_skipsWhenClaimFails() {
        when(persistencePort.claimProvisioning(eq("id-1"), anyString(), any(), eq(NOW))).thenReturn(0);

        sagaService.tryProvision("id-1");

        verifyNoInteractions(lightningNodePort);
        verifyNoInteractions(provisioningFinalizer);
    }

    @Test
    void tryProvision_schedulesRetryOnFailure_withClaimToken() {
        PaymentRequest request = provisioningRequest(NOW, NOW.plusSeconds(3600));
        AtomicReference<String> claimToken = new AtomicReference<>();
        when(persistencePort.claimProvisioning(eq("id-1"), anyString(), any(), eq(NOW))).thenAnswer(inv -> {
            claimToken.set(inv.getArgument(1));
            return 1;
        });
        when(persistencePort.findById("id-1")).thenReturn(Optional.of(request));
        when(lightningNodePort.lookupInvoice(request.paymentHash())).thenThrow(new RuntimeException("timeout"));

        sagaService.tryProvision("id-1");

        verify(persistencePort).scheduleProvisioningRetry(
                eq("id-1"), contains("timeout"), any(), eq(claimToken.get()));
        verify(persistencePort, never()).markProvisioningFailed(anyString(), anyString(), anyString());
        assertNotNull(claimToken.get());
        assertTrue(claimToken.get().startsWith("request-saga-"));
        assertNotEquals("request-saga-" + Thread.currentThread().threadId(), claimToken.get());
    }

    @Test
    void tryCancel_settledLookupSettlesViaPipeline() {
        PaymentRequest request = cancelPendingRequest();
        when(persistencePort.claimCancellation(eq("id-1"), anyString(), any(), eq(NOW))).thenReturn(1);
        when(persistencePort.findById("id-1")).thenReturn(Optional.of(request));
        LightningNodeInvoice settled = new LightningNodeInvoice(
                "lnbc1", LightningInvoice.InvoiceState.SETTLED, 1000L, 1000L);
        when(lightningNodePort.lookupInvoice(request.paymentHash())).thenReturn(Optional.of(settled));

        sagaService.tryCancel("id-1");

        verify(provisioningFinalizer).finalizeOpenOrSettled(
                eq(request),
                eq("lnbc1"),
                eq(LightningInvoice.InvoiceState.SETTLED),
                eq(1000L),
                anyLong(),
                anyString()
        );
        verify(lightningNodePort, never()).cancelInvoice(anyString());
        verify(persistencePort, never()).finalizeCancelled(anyString(), any(), anyString());
        verify(persistencePort, never()).markPaidByPaymentHash(anyString(), any());
    }

    @Test
    void tryCancel_absentInvoice_ensuresAddThenCancelBeforeFinalize() {
        PaymentRequest request = cancelPendingRequest();
        when(persistencePort.claimCancellation(eq("id-1"), anyString(), any(), eq(NOW))).thenReturn(1);
        when(persistencePort.findById("id-1")).thenReturn(Optional.of(request));
        when(lightningNodePort.lookupInvoice(request.paymentHash()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new LightningNodeInvoice(
                        "lnbc-fence", LightningInvoice.InvoiceState.OPEN, 0L, 1000L)))
                .thenReturn(Optional.of(new LightningNodeInvoice(
                        "lnbc-fence", LightningInvoice.InvoiceState.CANCELED, 0L, 1000L)));
        when(lightningNodePort.createInvoice(anyLong(), anyString(), any(), any(), anyLong()))
                .thenReturn(new LightningInvoiceCreation("lnbc-fence", request.paymentHash(), 1L));
        when(lightningNodePort.cancelInvoice(request.paymentHash())).thenReturn(InvoiceCancelOutcome.CANCELLED);
        when(persistencePort.finalizeCancelled(eq("id-1"), eq(NOW), anyString())).thenReturn(1);

        sagaService.tryCancel("id-1");

        verify(lightningNodePort).createInvoice(eq(1000L), eq("memo"), any(), any(), anyLong());
        verify(lightningNodePort).cancelInvoice(request.paymentHash());
        verify(persistencePort).finalizeCancelled(eq("id-1"), eq(NOW), anyString());
        verify(persistencePort, never()).markPaidByPaymentHash(anyString(), any());
    }

    @Test
    void tryCancel_alreadySettledWithoutLookup_schedulesRetryNotRequestOnlyPaid() {
        PaymentRequest request = cancelPendingRequest();
        when(persistencePort.claimCancellation(eq("id-1"), anyString(), any(), eq(NOW))).thenReturn(1);
        when(persistencePort.findById("id-1")).thenReturn(Optional.of(request));
        when(lightningNodePort.lookupInvoice(request.paymentHash()))
                .thenReturn(Optional.of(new LightningNodeInvoice(
                        "lnbc1", LightningInvoice.InvoiceState.OPEN, 0L, 1000L)))
                .thenReturn(Optional.empty());
        when(lightningNodePort.cancelInvoice(request.paymentHash()))
                .thenReturn(InvoiceCancelOutcome.ALREADY_SETTLED);

        sagaService.tryCancel("id-1");

        verify(persistencePort, never()).markPaidByPaymentHash(anyString(), any());
        verify(persistencePort, never()).finalizeCancelled(anyString(), any(), anyString());
        verify(persistencePort).scheduleCancelRetry(eq("id-1"), contains("ALREADY_SETTLED"), any(), anyString());
    }

    @Test
    void tryProvision_claimTokensDifferAcrossAttempts() {
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        when(persistencePort.claimProvisioning(eq("id-1"), tokenCaptor.capture(), any(), eq(NOW))).thenReturn(0);

        sagaService.tryProvision("id-1");
        sagaService.tryProvision("id-1");

        assertEquals(2, tokenCaptor.getAllValues().size());
        assertNotEquals(tokenCaptor.getAllValues().get(0), tokenCaptor.getAllValues().get(1));
    }

    @Test
    void tryCancel_fenceObservesSettled_settlesViaSettledDuringCancelPath() {
        PaymentRequest request = cancelPendingRequest();
        when(persistencePort.claimCancellation(eq("id-1"), anyString(), any(), eq(NOW))).thenReturn(1);
        when(persistencePort.findById("id-1")).thenReturn(Optional.of(request));
        LightningNodeInvoice settled = new LightningNodeInvoice(
                "lnbc-settled", LightningInvoice.InvoiceState.SETTLED, 1000L, 1000L);
        when(lightningNodePort.lookupInvoice(request.paymentHash()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(settled));
        when(lightningNodePort.createInvoice(anyLong(), anyString(), any(), any(), anyLong()))
                .thenReturn(new LightningInvoiceCreation("lnbc-settled", request.paymentHash(), 1L));

        sagaService.tryCancel("id-1");

        verify(provisioningFinalizer).finalizeOpenOrSettled(
                eq(request),
                eq("lnbc-settled"),
                eq(LightningInvoice.InvoiceState.SETTLED),
                eq(1000L),
                anyLong(),
                anyString()
        );
        verify(lightningNodePort, never()).cancelInvoice(anyString());
        verify(persistencePort, never()).finalizeCancelled(anyString(), any(), anyString());
        verify(persistencePort, never()).scheduleCancelRetry(anyString(), anyString(), any(), anyString());
    }

    @Test
    void processDueWork_drainsBothQueues() {
        when(persistencePort.findDueProvisioning(eq(NOW), anyInt())).thenReturn(java.util.List.of());
        when(persistencePort.findDueCancellations(eq(NOW), anyInt())).thenReturn(java.util.List.of());

        sagaService.processDueWork();

        verify(persistencePort).findDueProvisioning(eq(NOW), anyInt());
        verify(persistencePort).findDueCancellations(eq(NOW), anyInt());
    }

    @Test
    void tryCancel_alreadyCanceledOnNode_finalizesWithoutCancelRpc() {
        PaymentRequest request = cancelPendingRequest();
        when(persistencePort.claimCancellation(eq("id-1"), anyString(), any(), eq(NOW))).thenReturn(1);
        when(persistencePort.findById("id-1")).thenReturn(Optional.of(request));
        when(lightningNodePort.lookupInvoice(request.paymentHash())).thenReturn(Optional.of(
                new LightningNodeInvoice("lnbc1", LightningInvoice.InvoiceState.CANCELED, 0L, 1000L)
        ));
        when(persistencePort.finalizeCancelled(eq("id-1"), eq(NOW), anyString())).thenReturn(1);

        sagaService.tryCancel("id-1");

        verify(lightningNodePort, never()).cancelInvoice(anyString());
        verify(persistencePort).finalizeCancelled(eq("id-1"), eq(NOW), anyString());
    }

    private PaymentRequest provisioningRequest(Instant createdAt, Instant expiresAt) {
        byte[] preimage = new byte[32];
        preimage[0] = 1;
        String preimageB64 = Base64.getEncoder().encodeToString(preimage);
        String hash;
        try {
            hash = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(preimage));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return new PaymentRequest(
                "id-1", "pub-1", "user-1", 1000L, "memo",
                PaymentRequestStatus.PROVISIONING, hash, preimageB64, null, null,
                "key", "payload", createdAt, expiresAt, null, null,
                1, NOW, NOW.plusSeconds(300), "worker", null,
                0, null, null, null, null
        );
    }

    private PaymentRequest cancelPendingRequest() {
        byte[] preimage = new byte[32];
        preimage[0] = 7;
        String preimageB64 = Base64.getEncoder().encodeToString(preimage);
        String hash;
        try {
            hash = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(preimage));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return new PaymentRequest(
                "id-1", "pub-1", "user-1", 1000L, "memo",
                PaymentRequestStatus.CANCEL_PENDING, hash, preimageB64,
                "lnbc1", "inv-1",
                "key", "payload", NOW, NOW.plusSeconds(3600), null, null,
                0, null, null, null, null,
                1, NOW, NOW.plusSeconds(300), "worker", null
        );
    }
}
