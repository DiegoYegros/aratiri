package com.aratiri.requests.application;

import com.aratiri.errors.ApplicationException;
import com.aratiri.infrastructure.configuration.AratiriProperties;
import com.aratiri.invoices.application.port.in.InvoicesPort;
import com.aratiri.invoices.domain.CreatedLightningInvoice;
import com.aratiri.invoices.domain.InvoiceCancelOutcome;
import com.aratiri.requests.application.dto.CreatePaymentRequestDTO;
import com.aratiri.requests.application.dto.OwnerPaymentRequestDTO;
import com.aratiri.requests.application.dto.PublicPaymentRequestDTO;
import com.aratiri.requests.application.port.out.PaymentRequestPersistencePort;
import com.aratiri.requests.domain.PaymentRequest;
import com.aratiri.requests.domain.PaymentRequestStatus;
import com.aratiri.requests.domain.exception.PaymentRequestConflictException;
import com.aratiri.requests.domain.exception.PaymentRequestNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentRequestsAdapterTest {

    private static final Instant NOW = Instant.parse("2025-06-01T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private PaymentRequestPersistencePort persistencePort;

    @Mock
    private InvoicesPort invoicesPort;

    private AratiriProperties properties;
    private PaymentRequestsAdapter adapter;

    @BeforeEach
    void setUp() {
        properties = new AratiriProperties();
        properties.setAratiriBaseUrl("https://api.example");
        properties.setFrontendBaseUrl("https://app.example");
        adapter = new PaymentRequestsAdapter(persistencePort, invoicesPort, properties, CLOCK);
    }

    @Test
    void create_persistsLinkedInvoiceAndShareUrl() {
        CreatePaymentRequestDTO dto = createDto(2500L, "Coffee", 600L);
        when(persistencePort.findByUserIdAndIdempotencyKey("user-1", "key-1")).thenReturn(Optional.empty());
        when(invoicesPort.createInvoice(eq(2500L), eq("Coffee"), eq("user-1"), isNull(), isNull(), eq(600L)))
                .thenReturn(new CreatedLightningInvoice("inv-1", "hash-1", "lnbc2500", 600L));
        when(persistencePort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OwnerPaymentRequestDTO response = adapter.create("user-1", "key-1", dto);

        assertEquals(2500L, response.getAmountSats());
        assertEquals("OPEN", response.getStatus());
        assertEquals("lnbc2500", response.getPaymentRequest());
        assertTrue(response.getShareUrl().startsWith("https://app.example/pay/"));
        assertFalse(response.getShareUrl().contains("/r/"));
        assertEquals(32, response.getPublicId().length());

        verify(persistencePort).lockCreateSlot("user-1", "key-1");
        ArgumentCaptor<PaymentRequest> captor = ArgumentCaptor.forClass(PaymentRequest.class);
        verify(persistencePort).save(captor.capture());
        assertEquals("hash-1", captor.getValue().paymentHash());
        assertEquals("inv-1", captor.getValue().invoiceId());
        assertEquals(NOW.plusSeconds(600), captor.getValue().expiresAt());
    }

    @Test
    void shareUrl_normalizesTrailingSlashesAgainstFrontendBase() {
        properties.setFrontendBaseUrl("https://app.example///");
        CreatePaymentRequestDTO dto = createDto(1000L, "memo", 3600L);
        when(persistencePort.findByUserIdAndIdempotencyKey("user-1", "key-1")).thenReturn(Optional.empty());
        when(invoicesPort.createInvoice(anyLong(), anyString(), anyString(), any(), any(), anyLong()))
                .thenReturn(new CreatedLightningInvoice("inv-1", "hash-1", "lnbc1", 3600L));
        when(persistencePort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OwnerPaymentRequestDTO response = adapter.create("user-1", "key-1", dto);

        assertEquals("https://app.example/pay/" + response.getPublicId(), response.getShareUrl());
        assertEquals("https://app.example", PaymentRequestsAdapter.normalizeBaseUrl("https://app.example/"));
        assertEquals("https://app.example", PaymentRequestsAdapter.normalizeBaseUrl("https://app.example///"));
    }

    @Test
    void create_replaysSameIdempotencyKey() {
        CreatePaymentRequestDTO dto = createDto(1000L, "memo", 3600L);
        PaymentRequest existing = existingOpen("pub-1", "user-1", 1000L, "memo", 3600L);
        when(persistencePort.findByUserIdAndIdempotencyKey("user-1", "key-1")).thenReturn(Optional.of(existing));

        OwnerPaymentRequestDTO first = adapter.create("user-1", "key-1", dto);
        OwnerPaymentRequestDTO second = adapter.create("user-1", "key-1", dto);

        assertEquals(first.getPublicId(), second.getPublicId());
        verify(persistencePort, times(2)).lockCreateSlot("user-1", "key-1");
        verify(invoicesPort, never()).createInvoice(anyLong(), anyString(), anyString(), any(), any(), anyLong());
        verify(persistencePort, never()).save(any());
    }

    @Test
    void create_rejectsConflictingPayloadForSameKey() {
        CreatePaymentRequestDTO dto = createDto(1000L, "memo", 3600L);
        PaymentRequest existing = existingOpen("pub-1", "user-1", 2000L, "other", 3600L);
        when(persistencePort.findByUserIdAndIdempotencyKey("user-1", "key-1")).thenReturn(Optional.of(existing));

        assertThrows(PaymentRequestConflictException.class, () -> adapter.create("user-1", "key-1", dto));
        verify(persistencePort).lockCreateSlot("user-1", "key-1");
        verify(invoicesPort, never()).createInvoice(anyLong(), anyString(), anyString(), any(), any(), anyLong());
    }

    @Test
    void create_replaysTrimmedMemoAsSameIdempotentPayload() {
        PaymentRequest existing = existingOpen("pub-1", "user-1", 1000L, "memo", 3600L);
        when(persistencePort.findByUserIdAndIdempotencyKey("user-1", "key-1")).thenReturn(Optional.of(existing));

        OwnerPaymentRequestDTO replay = adapter.create("user-1", "key-1", createDto(1000L, "  memo  ", 3600L));

        assertEquals("pub-1", replay.getPublicId());
        verify(invoicesPort, never()).createInvoice(anyLong(), anyString(), anyString(), any(), any(), anyLong());
        verify(persistencePort, never()).save(any());
    }

    @Test
    void create_replaysBlankAndEmptyMemoAsSameIdempotentPayload() {
        PaymentRequest existing = existingOpen("pub-1", "user-1", 1000L, null, 3600L);
        when(persistencePort.findByUserIdAndIdempotencyKey("user-1", "key-blank")).thenReturn(Optional.of(existing));

        OwnerPaymentRequestDTO blankReplay = adapter.create("user-1", "key-blank", createDto(1000L, "   ", 3600L));
        OwnerPaymentRequestDTO emptyReplay = adapter.create("user-1", "key-blank", createDto(1000L, "", 3600L));
        OwnerPaymentRequestDTO nullReplay = adapter.create("user-1", "key-blank", createDto(1000L, null, 3600L));

        assertEquals("pub-1", blankReplay.getPublicId());
        assertEquals("pub-1", emptyReplay.getPublicId());
        assertEquals("pub-1", nullReplay.getPublicId());
        verify(invoicesPort, never()).createInvoice(anyLong(), anyString(), anyString(), any(), any(), anyLong());
        verify(persistencePort, never()).save(any());
    }

    @Test
    void create_rejectsMateriallyDifferentMemoForSameKey() {
        when(persistencePort.findByUserIdAndIdempotencyKey("user-1", "key-1"))
                .thenReturn(Optional.of(existingOpen("pub-1", "user-1", 1000L, "memo", 3600L)));
        CreatePaymentRequestDTO conflicting = createDto(1000L, "other", 3600L);

        assertThrows(
                PaymentRequestConflictException.class,
                () -> adapter.create("user-1", "key-1", conflicting)
        );
        verify(invoicesPort, never()).createInvoice(anyLong(), anyString(), anyString(), any(), any(), anyLong());
    }

    @Test
    void cancel_cancelsLinkedInvoiceOnLndThenMarksCancelled() {
        PaymentRequest open = existingOpen("pub-1", "user-1", 1000L, "memo", 3600L);
        PaymentRequest cancelled = open.withCancelled(NOW);
        when(persistencePort.findByPublicIdAndUserId("pub-1", "user-1"))
                .thenReturn(Optional.of(open), Optional.of(cancelled));
        when(invoicesPort.cancelInvoice("hash-1")).thenReturn(InvoiceCancelOutcome.CANCELLED);
        when(persistencePort.cancelIfOpen("pub-1", "user-1", NOW, NOW)).thenReturn(1);

        OwnerPaymentRequestDTO response = adapter.cancel("user-1", "pub-1");

        assertEquals("CANCELLED", response.getStatus());
        assertNull(response.getPaymentRequest());
        verify(invoicesPort).cancelInvoice("hash-1");
        verify(persistencePort).cancelIfOpen("pub-1", "user-1", NOW, NOW);
    }

    @Test
    void cancel_rpcFailureDoesNotMarkCancelled() {
        PaymentRequest open = existingOpen("pub-1", "user-1", 1000L, "memo", 3600L);
        when(persistencePort.findByPublicIdAndUserId("pub-1", "user-1")).thenReturn(Optional.of(open));
        when(invoicesPort.cancelInvoice("hash-1"))
                .thenThrow(new ApplicationException("Error cancelling invoice on LND node", 502));

        ApplicationException ex = assertThrows(ApplicationException.class, () -> adapter.cancel("user-1", "pub-1"));
        assertEquals(502, ex.getStatus());
        verify(persistencePort, never()).cancelIfOpen(anyString(), anyString(), any(), any());
        verify(persistencePort, never()).markPaidByPaymentHash(anyString(), any());
    }

    @Test
    void cancel_alreadySettledOnLndHealsToPaidThenConflicts() {
        PaymentRequest open = existingOpen("pub-1", "user-1", 1000L, "memo", 3600L);
        when(persistencePort.findByPublicIdAndUserId("pub-1", "user-1")).thenReturn(Optional.of(open));
        when(invoicesPort.cancelInvoice("hash-1")).thenReturn(InvoiceCancelOutcome.ALREADY_SETTLED);
        when(persistencePort.markPaidByPaymentHash("hash-1", NOW)).thenReturn(1);

        PaymentRequestConflictException ex = assertThrows(
                PaymentRequestConflictException.class,
                () -> adapter.cancel("user-1", "pub-1")
        );
        assertTrue(ex.getMessage().contains("already paid"));
        verify(persistencePort).markPaidByPaymentHash("hash-1", NOW);
        verify(persistencePort, never()).cancelIfOpen(anyString(), anyString(), any(), any());
    }

    @Test
    void cancel_notFoundOnLndStillMarksCancelled() {
        PaymentRequest open = existingOpen("pub-1", "user-1", 1000L, "memo", 3600L);
        PaymentRequest cancelled = open.withCancelled(NOW);
        when(persistencePort.findByPublicIdAndUserId("pub-1", "user-1"))
                .thenReturn(Optional.of(open), Optional.of(cancelled));
        when(invoicesPort.cancelInvoice("hash-1")).thenReturn(InvoiceCancelOutcome.NOT_FOUND);
        when(persistencePort.cancelIfOpen("pub-1", "user-1", NOW, NOW)).thenReturn(1);

        OwnerPaymentRequestDTO response = adapter.cancel("user-1", "pub-1");

        assertEquals("CANCELLED", response.getStatus());
        verify(persistencePort).cancelIfOpen("pub-1", "user-1", NOW, NOW);
    }

    @Test
    void cancel_idempotentWhenAlreadyCancelled() {
        PaymentRequest cancelled = existingOpen("pub-1", "user-1", 1000L, "memo", 3600L)
                .withCancelled(NOW.minusSeconds(10));
        when(persistencePort.findByPublicIdAndUserId("pub-1", "user-1")).thenReturn(Optional.of(cancelled));

        OwnerPaymentRequestDTO response = adapter.cancel("user-1", "pub-1");

        assertEquals("CANCELLED", response.getStatus());
        verify(invoicesPort, never()).cancelInvoice(anyString());
        verify(persistencePort, never()).cancelIfOpen(anyString(), anyString(), any(), any());
    }

    @Test
    void cancel_conflictsWhenPaid() {
        PaymentRequest paid = existingOpen("pub-1", "user-1", 1000L, "memo", 3600L)
                .withPaid(NOW.minusSeconds(5));
        when(persistencePort.findByPublicIdAndUserId("pub-1", "user-1")).thenReturn(Optional.of(paid));

        PaymentRequestConflictException ex = assertThrows(
                PaymentRequestConflictException.class,
                () -> adapter.cancel("user-1", "pub-1")
        );
        assertTrue(ex.getMessage().contains("already paid"));
        verify(invoicesPort, never()).cancelInvoice(anyString());
        verify(persistencePort, never()).cancelIfOpen(anyString(), anyString(), any(), any());
        verify(persistencePort, never()).markPaidByPaymentHash(anyString(), any());
    }

    @Test
    void cancel_settlementWinsRaceAfterLndCancel() {
        PaymentRequest open = existingOpen("pub-1", "user-1", 1000L, "memo", 3600L);
        PaymentRequest paid = open.withPaid(NOW);
        when(persistencePort.findByPublicIdAndUserId("pub-1", "user-1"))
                .thenReturn(Optional.of(open), Optional.of(paid));
        when(invoicesPort.cancelInvoice("hash-1")).thenReturn(InvoiceCancelOutcome.CANCELLED);
        when(persistencePort.cancelIfOpen("pub-1", "user-1", NOW, NOW)).thenReturn(0);

        PaymentRequestConflictException ex = assertThrows(
                PaymentRequestConflictException.class,
                () -> adapter.cancel("user-1", "pub-1")
        );
        assertTrue(ex.getMessage().contains("already paid"));
        verify(persistencePort, never()).markPaidByPaymentHash(anyString(), any());
    }

    @Test
    void getOwned_isolatesOwners() {
        when(persistencePort.findByPublicIdAndUserId("pub-1", "other-user")).thenReturn(Optional.empty());
        assertThrows(PaymentRequestNotFoundException.class, () -> adapter.getOwned("other-user", "pub-1"));
    }

    @Test
    void getPublic_hidesBolt11WhenNotPayable() {
        PaymentRequest paid = existingOpen("pub-1", "user-1", 1000L, "memo", 3600L)
                .withPaid(NOW.minusSeconds(1));
        when(persistencePort.findByPublicId("pub-1")).thenReturn(Optional.of(paid));

        PublicPaymentRequestDTO dto = adapter.getPublic("pub-1");

        assertEquals("PAID", dto.getStatus());
        assertNull(dto.getPaymentRequest());
        assertEquals(1000L, dto.getAmountSats());
    }

    @Test
    void listOwned_cursorPagination() {
        PaymentRequest first = existingOpen("pub-1", "user-1", 1000L, "a", 3600L);
        PaymentRequest second = new PaymentRequest(
                "id-2",
                "pub-2",
                "user-1",
                2000L,
                "b",
                PaymentRequestStatus.OPEN,
                "hash-2",
                "lnbc2",
                "inv-2",
                "key-2",
                "payload-2",
                NOW.minusSeconds(30),
                NOW.plusSeconds(3600),
                null,
                null
        );
        when(persistencePort.findByUserIdFirstPage("user-1", 2)).thenReturn(List.of(first, second));

        var page = adapter.listOwned("user-1", null, 1);

        assertEquals(1, page.getPaymentRequests().size());
        assertTrue(page.isHasMore());
        assertNotNull(page.getNextCursor());
        assertTrue(page.getNextCursor().contains(first.id()));
    }

    private static CreatePaymentRequestDTO createDto(long amount, String memo, long expiresInSeconds) {
        CreatePaymentRequestDTO dto = new CreatePaymentRequestDTO();
        dto.setAmountSats(amount);
        dto.setMemo(memo);
        dto.setExpiresInSeconds(expiresInSeconds);
        return dto;
    }

    private static PaymentRequest existingOpen(
            String publicId,
            String userId,
            long amount,
            String memo,
            long expiresInSeconds
    ) {
        String payload = amount + ":" + (memo == null ? "" : memo) + ":" + expiresInSeconds;
        String payloadHash;
        try {
            payloadHash = java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            );
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return new PaymentRequest(
                "id-1",
                publicId,
                userId,
                amount,
                memo,
                PaymentRequestStatus.OPEN,
                "hash-1",
                "lnbc1",
                "inv-1",
                "key-1",
                payloadHash,
                NOW.minusSeconds(10),
                NOW.plusSeconds(expiresInSeconds),
                null,
                null
        );
    }
}
