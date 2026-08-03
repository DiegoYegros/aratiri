package com.aratiri.requests.infrastructure;

import com.aratiri.infrastructure.persistence.jpa.entity.PaymentRequestEntity;
import com.aratiri.infrastructure.persistence.jpa.repository.PaymentRequestRepository;
import com.aratiri.invoices.application.port.out.OwnedPaymentRequestInvoiceSeed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LinkedPaymentRequestAdapterTest {

    private static final Instant CREATED = Instant.parse("2025-06-01T00:00:00Z");
    private static final Instant EXPIRES = Instant.parse("2025-06-01T01:00:00Z");

    @Mock
    private PaymentRequestRepository paymentRequestRepository;

    private LinkedPaymentRequestAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new LinkedPaymentRequestAdapter(paymentRequestRepository);
    }

    @Test
    void markPaidByPaymentHash_delegatesToRepository() {
        Instant paidAt = Instant.parse("2025-06-01T00:30:00Z");

        adapter.markPaidByPaymentHash("hash-1", paidAt);

        verify(paymentRequestRepository).markPaidByPaymentHash("hash-1", paidAt);
    }

    @Test
    void findOwnedInvoiceSeed_mapsRecoverableStatus() {
        when(paymentRequestRepository.findByPaymentHash("hash-1")).thenReturn(Optional.of(entity("OPEN", "preimage")));

        Optional<OwnedPaymentRequestInvoiceSeed> seed = adapter.findOwnedInvoiceSeedByPaymentHash("hash-1");

        assertTrue(seed.isPresent());
        assertEquals("user-1", seed.get().userId());
        assertEquals("hash-1", seed.get().paymentHash());
        assertEquals("preimage", seed.get().preimage());
        assertEquals("lnbc1", seed.get().paymentRequest());
        assertEquals(1000L, seed.get().amountSats());
        assertEquals("memo", seed.get().memo());
        assertEquals(3600L, seed.get().expirySeconds());
    }

    @Test
    void findOwnedInvoiceSeed_rejectsUnknownStatus() {
        when(paymentRequestRepository.findByPaymentHash("hash-1")).thenReturn(Optional.of(entity("EXPIRED", "preimage")));

        assertTrue(adapter.findOwnedInvoiceSeedByPaymentHash("hash-1").isEmpty());
    }

    @Test
    void findOwnedInvoiceSeed_rejectsBlankPreimage() {
        when(paymentRequestRepository.findByPaymentHash("hash-1")).thenReturn(Optional.of(entity("OPEN", "  ")));

        assertTrue(adapter.findOwnedInvoiceSeedByPaymentHash("hash-1").isEmpty());
    }

    @Test
    void findOwnedInvoiceSeed_rejectsMissingRow() {
        when(paymentRequestRepository.findByPaymentHash("missing")).thenReturn(Optional.empty());

        assertTrue(adapter.findOwnedInvoiceSeedByPaymentHash("missing").isEmpty());
    }

    @Test
    void findOwnedInvoiceSeed_clampsExpiryToAtLeastOne() {
        PaymentRequestEntity entity = entity("PROVISIONING", "preimage");
        entity.setExpiresAt(CREATED.minusSeconds(10));
        when(paymentRequestRepository.findByPaymentHash("hash-1")).thenReturn(Optional.of(entity));

        Optional<OwnedPaymentRequestInvoiceSeed> seed = adapter.findOwnedInvoiceSeedByPaymentHash("hash-1");

        assertTrue(seed.isPresent());
        assertEquals(1L, seed.get().expirySeconds());
    }

    private static PaymentRequestEntity entity(String status, String preimage) {
        return PaymentRequestEntity.builder()
                .id("id-1")
                .publicId("pub-1")
                .userId("user-1")
                .amountSats(1000L)
                .memo("memo")
                .status(status)
                .paymentHash("hash-1")
                .preimage(preimage)
                .paymentRequest("lnbc1")
                .idempotencyKey("key")
                .idempotencyPayloadHash("payload")
                .createdAt(CREATED)
                .expiresAt(EXPIRES)
                .provisionAttemptCount(0)
                .cancelAttemptCount(0)
                .build();
    }
}
