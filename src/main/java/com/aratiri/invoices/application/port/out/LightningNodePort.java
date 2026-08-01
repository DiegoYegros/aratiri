package com.aratiri.invoices.application.port.out;

import com.aratiri.invoices.domain.DecodedLightningInvoice;
import com.aratiri.invoices.domain.InvoiceCancelOutcome;
import com.aratiri.invoices.domain.LightningInvoiceCreation;
import com.aratiri.invoices.domain.LightningNodeInvoice;

import java.util.Optional;

public interface LightningNodePort {

    LightningInvoiceCreation createInvoice(long satsAmount, String memo, byte[] preimage, byte[] hash);

    /**
     * Creates an invoice with an explicit LND expiry (seconds). Callers that need the
     * invoice to expire no later than a payment-request deadline must pass that bound here.
     */
    LightningInvoiceCreation createInvoice(
            long satsAmount,
            String memo,
            byte[] preimage,
            byte[] hash,
            long expirySeconds
    );

    DecodedLightningInvoice decodePaymentRequest(String paymentRequest);

    Optional<LightningNodeInvoice> lookupInvoice(String paymentHash);

    /**
     * Cancels an open invoice on LND via invoicesrpc CancelInvoice.
     * Already-canceled succeeds as {@link InvoiceCancelOutcome#CANCELLED}.
     * Already-settled maps to {@link InvoiceCancelOutcome#ALREADY_SETTLED}.
     * Missing invoices map to {@link InvoiceCancelOutcome#NOT_FOUND}.
     */
    InvoiceCancelOutcome cancelInvoice(String paymentHash);
}
