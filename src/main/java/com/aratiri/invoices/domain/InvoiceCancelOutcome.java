package com.aratiri.invoices.domain;

/**
 * Result of attempting to cancel an invoice on LND via invoicesrpc.
 * <ul>
 *   <li>{@link #CANCELLED} — CancelInvoice succeeded (including already-canceled).</li>
 *   <li>{@link #ALREADY_SETTLED} — invoice is settled on LND; do not treat as cancelled.</li>
 *   <li>{@link #NOT_FOUND} — invoice absent on LND; not payable on this node.</li>
 * </ul>
 */
public enum InvoiceCancelOutcome {
    CANCELLED,
    ALREADY_SETTLED,
    NOT_FOUND
}
