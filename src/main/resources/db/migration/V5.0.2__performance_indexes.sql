SET search_path TO aratiri;

-- Hot lookup from LND invoice subscription (InvoiceSettlementService.recordInvoiceStateUpdate)
CREATE INDEX idx_lightning_invoices_payment_request
    ON lightning_invoices (payment_request);

-- Bounded reconciliation sweep (TransactionReconciliationJob)
CREATE INDEX idx_transactions_pending_debit_created_at
    ON transactions (created_at)
    WHERE type = 'LIGHTNING_DEBIT' AND current_status = 'PENDING';
