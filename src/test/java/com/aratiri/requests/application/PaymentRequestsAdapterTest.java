package com.aratiri.requests.application;

import com.aratiri.infrastructure.configuration.AratiriProperties;
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

    private static final Instant NOW = Instant.parse("2025-01-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private PaymentRequestPersistencePort persistencePort;
    @Mock
    private PaymentRequestIntentService intentService;
    @Mock
    private PaymentRequestSagaService sagaService;
    @Mock
    private AratiriProperties properties;

    private PaymentRequestsAdapter adapter;

    @BeforeEach
    void setUp() {
        lenient().when(properties.getFrontendBaseUrl()).thenReturn("https://app.example/");
        adapter = new PaymentRequestsAdapter(persistencePort, intentService, sagaService, properties, CLOCK);
    }

    @Test
    void create_commitsIntentThenBestEffortProvision() {
        CreatePaymentRequestDTO dto = createDto(2500L, "Coffee", 600L);
        PaymentRequest provisioning = request("id-1", "pub-1", PaymentRequestStatus.PROVISIONING, null, null);
        PaymentRequest open = request("id-1", "pub-1", PaymentRequestStatus.OPEN, "lnbc2500", "inv-1");

        when(intentService.commitIntent(eq("user-1"), eq("key-1"), eq(dto), anyString(), eq("Coffee")))
                .thenReturn(new PaymentRequestIntentService.IntentCommitResult(provisioning, true));
        doAnswer(invocation -> null).when(sagaService).tryProvision("id-1");
        when(persistencePort.findById("id-1")).thenReturn(Optional.of(open));

        CreatePaymentRequestResult result = adapter.create("user-1", "key-1", dto);

        assertTrue(result.newlyCreated());
        assertEquals("OPEN", result.body().getStatus());
        assertEquals("lnbc2500", result.body().getPaymentRequest());
        verify(sagaService).tryProvision("id-1");
    }

    @Test
    void create_replayReturnsExistingWithoutNewIntentSideEffects() {
        CreatePaymentRequestDTO dto = createDto(1000L, "memo", 3600L);
        PaymentRequest existing = request("id-1", "pub-1", PaymentRequestStatus.OPEN, "lnbc1", "inv-1");
        when(intentService.commitIntent(eq("user-1"), eq("key-1"), eq(dto), anyString(), eq("memo")))
                .thenReturn(new PaymentRequestIntentService.IntentCommitResult(existing, false));

        CreatePaymentRequestResult result = adapter.create("user-1", "key-1", dto);

        assertFalse(result.newlyCreated());
        assertEquals("pub-1", result.body().getPublicId());
        verify(sagaService, never()).tryProvision(anyString());
    }

    @Test
    void create_conflictPropagates() {
        CreatePaymentRequestDTO dto = createDto(1000L, "memo", 3600L);
        when(intentService.commitIntent(eq("user-1"), eq("key-1"), eq(dto), anyString(), eq("memo")))
                .thenThrow(new PaymentRequestConflictException("Idempotency key conflict: different request payload for the same key"));

        assertThrows(PaymentRequestConflictException.class, () -> adapter.create("user-1", "key-1", dto));
    }

    @Test
    void normalizeBaseUrl_stripsTrailingSlashes() {
        assertEquals("https://app.example", PaymentRequestsAdapter.normalizeBaseUrl("https://app.example/"));
        assertEquals("https://app.example", PaymentRequestsAdapter.normalizeBaseUrl("https://app.example///"));
    }

    @Test
    void cancel_marksCancelPendingAndInvokesSaga() {
        PaymentRequest open = request("id-1", "pub-1", PaymentRequestStatus.OPEN, "lnbc1", "inv-1");
        PaymentRequest pending = request("id-1", "pub-1", PaymentRequestStatus.CANCEL_PENDING, "lnbc1", "inv-1");
        PaymentRequest cancelled = open.withCancelled(NOW);

        when(persistencePort.findByPublicIdAndUserId("pub-1", "user-1"))
                .thenReturn(Optional.of(open), Optional.of(pending), Optional.of(cancelled));
        when(persistencePort.markCancelPendingIfPayable("pub-1", "user-1", NOW)).thenReturn(1);

        OwnerPaymentRequestDTO response = adapter.cancel("user-1", "pub-1");

        assertEquals("CANCELLED", response.getStatus());
        assertNull(response.getPaymentRequest());
        verify(persistencePort).markCancelPendingIfPayable("pub-1", "user-1", NOW);
        verify(sagaService).tryCancel("id-1");
    }

    @Test
    void cancel_replayCancelledIsIdempotent() {
        PaymentRequest cancelled = request("id-1", "pub-1", PaymentRequestStatus.OPEN, "lnbc1", "inv-1")
                .withCancelled(NOW);
        when(persistencePort.findByPublicIdAndUserId("pub-1", "user-1")).thenReturn(Optional.of(cancelled));

        OwnerPaymentRequestDTO response = adapter.cancel("user-1", "pub-1");

        assertEquals("CANCELLED", response.getStatus());
        verify(persistencePort, never()).markCancelPendingIfPayable(anyString(), anyString(), any());
        verify(sagaService, never()).tryCancel(anyString());
    }

    @Test
    void cancel_paidConflicts() {
        PaymentRequest paid = request("id-1", "pub-1", PaymentRequestStatus.OPEN, "lnbc1", "inv-1").withPaid(NOW);
        when(persistencePort.findByPublicIdAndUserId("pub-1", "user-1")).thenReturn(Optional.of(paid));

        assertThrows(PaymentRequestConflictException.class, () -> adapter.cancel("user-1", "pub-1"));
        verify(sagaService, never()).tryCancel(anyString());
    }

    @Test
    void getOwned_notFoundForOtherUser() {
        when(persistencePort.findByPublicIdAndUserId("pub-1", "other-user")).thenReturn(Optional.empty());
        assertThrows(PaymentRequestNotFoundException.class, () -> adapter.getOwned("other-user", "pub-1"));
    }

    @Test
    void getPublic_hidesBolt11WhenPaid() {
        PaymentRequest paid = request("id-1", "pub-1", PaymentRequestStatus.OPEN, "lnbc1", "inv-1").withPaid(NOW);
        when(persistencePort.findByPublicId("pub-1")).thenReturn(Optional.of(paid));

        PublicPaymentRequestDTO dto = adapter.getPublic("pub-1");
        assertEquals("PAID", dto.getStatus());
        assertNull(dto.getPaymentRequest());
    }

    @Test
    void listOwned_paginates() {
        PaymentRequest first = request("id-1", "pub-1", PaymentRequestStatus.OPEN, "lnbc1", "inv-1");
        PaymentRequest second = request("id-2", "pub-2", PaymentRequestStatus.OPEN, "lnbc2", "inv-2");
        when(persistencePort.findByUserIdFirstPage("user-1", 2)).thenReturn(List.of(first, second));

        var page = adapter.listOwned("user-1", null, 1);
        assertEquals(1, page.getPaymentRequests().size());
        assertTrue(page.isHasMore());
    }

    private static CreatePaymentRequestDTO createDto(long amount, String memo, long expiresInSeconds) {
        CreatePaymentRequestDTO dto = new CreatePaymentRequestDTO();
        dto.setAmountSats(amount);
        dto.setMemo(memo);
        dto.setExpiresInSeconds(expiresInSeconds);
        return dto;
    }

    private static PaymentRequest request(
            String id,
            String publicId,
            PaymentRequestStatus status,
            String bolt11,
            String invoiceId
    ) {
        return new PaymentRequest(
                id,
                publicId,
                "user-1",
                1000L,
                "memo",
                status,
                "payment-hash",
                "cHJlaW1hZ2U=",
                bolt11,
                invoiceId,
                "key-1",
                "payload-hash",
                NOW,
                NOW.plusSeconds(3600),
                null,
                null,
                0,
                status == PaymentRequestStatus.PROVISIONING ? NOW : null,
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
