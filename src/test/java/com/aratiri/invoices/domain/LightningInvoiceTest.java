package com.aratiri.invoices.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class LightningInvoiceTest {

    @Test
    void constructor_storesAllFields() {
        LocalDateTime now = LocalDateTime.now();
        LightningInvoice invoice = new LightningInvoice(
                "inv-1", "user-1", "hash", "preimage", "payment-request",
                LightningInvoice.InvoiceState.OPEN, 5000L, now,
                3600L, 0L, null, "test memo", "ext-ref", "metadata"
        );

        assertEquals("inv-1", invoice.id());
        assertEquals("user-1", invoice.userId());
        assertEquals("hash", invoice.paymentHash());
        assertEquals("preimage", invoice.preimage());
        assertEquals("payment-request", invoice.paymentRequest());
        assertEquals(LightningInvoice.InvoiceState.OPEN, invoice.invoiceState());
        assertEquals(5000L, invoice.amountSats());
        assertEquals(now, invoice.createdAt());
        assertEquals(3600L, invoice.expiry());
        assertEquals(0L, invoice.amountPaidSats());
        assertNull(invoice.settledAt());
        assertEquals("test memo", invoice.memo());
        assertEquals("ext-ref", invoice.externalReference());
        assertEquals("metadata", invoice.metadata());
    }

    @Test
    void withId_returnsNewWithUpdatedId() {
        LocalDateTime now = LocalDateTime.now();
        LightningInvoice invoice = new LightningInvoice(
                "inv-1", "user-1", "hash", "preimage", "payment-request",
                LightningInvoice.InvoiceState.OPEN, 5000L, now,
                3600L, 0L, null, "test memo", "ext-ref", "metadata"
        );

        LightningInvoice updated = invoice.withId("inv-2");
        assertEquals("inv-2", updated.id());
        assertEquals("user-1", updated.userId());
    }

    @Test
    void withState_returnsNewWithUpdatedState() {
        LocalDateTime now = LocalDateTime.now();
        LightningInvoice invoice = new LightningInvoice(
                "inv-1", "user-1", "hash", "preimage", "payment-request",
                LightningInvoice.InvoiceState.OPEN, 5000L, now,
                3600L, 0L, null, "test memo", "ext-ref", "metadata"
        );

        LightningInvoice settled = invoice.withState(LightningInvoice.InvoiceState.SETTLED);
        assertEquals(LightningInvoice.InvoiceState.SETTLED, settled.invoiceState());
        assertEquals("inv-1", settled.id());
    }

    @Test
    void invoiceState_enumValues() {
        assertEquals(4, LightningInvoice.InvoiceState.values().length);
        assertNotNull(LightningInvoice.InvoiceState.valueOf("OPEN"));
        assertNotNull(LightningInvoice.InvoiceState.valueOf("ACCEPTED"));
        assertNotNull(LightningInvoice.InvoiceState.valueOf("SETTLED"));
        assertNotNull(LightningInvoice.InvoiceState.valueOf("CANCELED"));
    }
}
