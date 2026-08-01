package com.aratiri.requests.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class PaymentRequestTest {

    private static final Instant CREATED = Instant.parse("2025-01-01T00:00:00Z");
    private static final Instant EXPIRES = Instant.parse("2025-01-01T01:00:00Z");

    @Test
    void effectiveStatus_openBeforeExpiry() {
        PaymentRequest request = openRequest();
        assertEquals(PaymentRequestStatus.OPEN, request.effectiveStatus(CREATED.plusSeconds(10)));
        assertTrue(request.isPayable(CREATED.plusSeconds(10)));
    }

    @Test
    void effectiveStatus_expiredWhenOpenPastExpiry() {
        PaymentRequest request = openRequest();
        assertEquals(PaymentRequestStatus.EXPIRED, request.effectiveStatus(EXPIRES));
        assertFalse(request.isPayable(EXPIRES));
    }

    @Test
    void effectiveStatus_paidWinsOverExpiryAndCancel() {
        PaymentRequest paid = openRequest().withPaid(CREATED.plusSeconds(30));
        assertEquals(PaymentRequestStatus.PAID, paid.effectiveStatus(EXPIRES.plusSeconds(3600)));

        PaymentRequest cancelledThenConceptuallyPaid = openRequest().withCancelled(CREATED.plusSeconds(5)).withPaid(CREATED.plusSeconds(10));
        assertEquals(PaymentRequestStatus.PAID, cancelledThenConceptuallyPaid.effectiveStatus(CREATED.plusSeconds(20)));
    }

    @Test
    void effectiveStatus_cancelledRemainsCancelledWhenNotPaid() {
        PaymentRequest cancelled = openRequest().withCancelled(CREATED.plusSeconds(5));
        assertEquals(PaymentRequestStatus.CANCELLED, cancelled.effectiveStatus(CREATED.plusSeconds(10)));
        assertEquals(PaymentRequestStatus.CANCELLED, cancelled.effectiveStatus(EXPIRES.plusSeconds(1)));
    }

    private static PaymentRequest openRequest() {
        return new PaymentRequest(
                "id-1",
                "publicidpublicidpublicidpub12",
                "user-1",
                1000L,
                "memo",
                PaymentRequestStatus.OPEN,
                "payment-hash",
                "lnbc1",
                "invoice-1",
                "idem-1",
                "payload-hash",
                CREATED,
                EXPIRES,
                null,
                null
        );
    }
}
