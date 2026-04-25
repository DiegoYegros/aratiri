SET search_path TO aratiri;

-- Unify monetary units to satoshis (BIGINT).
--
-- Earlier transactions stored BTC-denominated NUMERIC values. The payment, invoice, LND, and ledger
-- code operate in satoshis, so this conversion removes decimal ambiguity and avoids rounding behavior
-- in debit/credit processors.
ALTER TABLE transactions
    ALTER COLUMN amount TYPE BIGINT
        USING (amount * 100000000)::BIGINT;

ALTER TABLE transaction_events
    ALTER COLUMN amount_delta TYPE BIGINT
        USING (amount_delta * 100000000)::BIGINT;

ALTER TABLE transaction_events
    ALTER COLUMN balance_after TYPE BIGINT
        USING (balance_after * 100000000)::BIGINT;

-- Query support for rebuilding transaction aggregates and finding status transitions during
-- reconciliation/statistics reads.
CREATE INDEX IF NOT EXISTS idx_transaction_events_tx_status
    ON transaction_events(transaction_id, status);
