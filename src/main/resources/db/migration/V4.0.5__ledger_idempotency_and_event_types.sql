SET search_path TO aratiri;

-- Defensive normalization after the V4.0.2 satoshi conversion. These columns are nullable event
-- attributes, but when present they should be integer satoshi values.
ALTER TABLE transaction_events
    ALTER COLUMN amount_delta TYPE BIGINT USING amount_delta::bigint,
    ALTER COLUMN balance_after TYPE BIGINT USING balance_after::bigint;

-- AccountLedgerService treats (account_id, transaction_id) as the idempotency boundary for ledger
-- writes. If Kafka or a node operation retries the same transaction, the second append returns the
-- existing balance instead of creating a duplicate debit/credit.
CREATE UNIQUE INDEX IF NOT EXISTS ux_account_entries_account_tx
    ON account_entries(account_id, transaction_id)
    WHERE transaction_id IS NOT NULL;
