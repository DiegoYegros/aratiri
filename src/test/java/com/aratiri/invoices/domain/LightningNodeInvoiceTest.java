package com.aratiri.invoices.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LightningNodeInvoiceTest {

    @Test
    void constructor_storesAllFields() {
        LightningNodeInvoice invoice = new LightningNodeInvoice(
                "lnbc1...",
                LightningInvoice.InvoiceState.OPEN,
                0L,
                5000L
        );

        assertEquals("lnbc1...", invoice.paymentRequest());
        assertEquals(LightningInvoice.InvoiceState.OPEN, invoice.state());
        assertEquals(0L, invoice.amountPaidSats());
        assertEquals(5000L, invoice.valueSats());
    }
}
