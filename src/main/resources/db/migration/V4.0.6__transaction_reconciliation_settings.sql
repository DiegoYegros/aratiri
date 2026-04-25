SET search_path TO aratiri;

-- Runtime knob for TransactionReconciliationJob. Pending Lightning debits are only reconciled after
-- this age so normal in-flight payments are not prematurely failed while LND is still settling or
-- reporting payment status.
ALTER TABLE node_settings
    ADD COLUMN transaction_reconciliation_min_age_ms BIGINT NOT NULL DEFAULT 300000;

-- Negative ages would make reconciliation thresholds nonsensical and could immediately sweep fresh
-- pending transactions.
ALTER TABLE node_settings
    ADD CONSTRAINT ck_node_settings_transaction_reconciliation_min_age_ms
        CHECK (transaction_reconciliation_min_age_ms >= 0);
