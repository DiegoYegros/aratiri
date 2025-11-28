SET search_path TO aratiri;

--  Unify monetary units to satoshis (BIGINT)
ALTER TABLE transactions
    ALTER COLUMN amount TYPE BIGINT
        USING (amount * 100000000)::BIGINT;

ALTER TABLE transaction_events
    ALTER COLUMN amount_delta TYPE BIGINT
        USING (amount_delta * 100000000)::BIGINT;

ALTER TABLE transaction_events
    ALTER COLUMN balance_after TYPE BIGINT
        USING (balance_after * 100000000)::BIGINT;

-- Add performance index for transaction reconciliation job
CREATE INDEX IF NOT EXISTS idx_transaction_events_tx_status
    ON transaction_events(transaction_id, status);