SET search_path TO aratiri;

-- Execution consumers (W2) atomically claim PENDING transactions by flipping current_status to
-- EXECUTING before any LND call; only the claiming delivery may touch LND. EXECUTING never
-- appears in transaction_events (the event stream stays PENDING so settlement paths are
-- unchanged), and stale claims are reclaimed by TransactionReconciliationJob past min-age.
-- Encode the state machine at the DB boundary.
ALTER TABLE transactions
    ADD CONSTRAINT ck_transactions_current_status
        CHECK (current_status IN ('PENDING', 'EXECUTING', 'COMPLETED', 'FAILED'));
