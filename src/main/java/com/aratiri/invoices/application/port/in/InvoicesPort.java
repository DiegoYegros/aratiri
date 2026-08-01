package com.aratiri.invoices.application.port.in;

import com.aratiri.invoices.application.dto.DecodedInvoicetDTO;
import com.aratiri.invoices.application.dto.GenerateInvoiceDTO;
import com.aratiri.invoices.domain.CreatedLightningInvoice;
import com.aratiri.invoices.domain.InvoiceCancelOutcome;
import com.aratiri.invoices.domain.LightningNodeInvoice;

import java.util.Optional;

public interface InvoicesPort {
    GenerateInvoiceDTO generateInvoice(long satsAmount, String memo, String userId, String externalReference, String metadata);

    GenerateInvoiceDTO generateInvoice(String alias, long satsAmount, String memo, String externalReference, String metadata);

    /**
     * Creates and persists a Lightning invoice with an explicit expiry bound (seconds).
     * Used by payment requests so the invoice cannot outlive the shareable request.
     */
    CreatedLightningInvoice createInvoice(
            long satsAmount,
            String memo,
            String userId,
            String externalReference,
            String metadata,
            long expirySeconds
    );

    /**
     * Cancels the linked LND invoice so the BOLT11 is no longer payable.
     * See {@link InvoiceCancelOutcome} for settled/not-found semantics.
     */
    InvoiceCancelOutcome cancelInvoice(String paymentHash);

    DecodedInvoicetDTO decodeAratiriPaymentRequest(String paymentRequest, String userId);

    Optional<LightningNodeInvoice> lookupInvoice(String paymentHash);

    DecodedInvoicetDTO decodePaymentRequest(String invoice);

    boolean existsSettledInvoiceByPaymentHash(String paymentHash);
}
