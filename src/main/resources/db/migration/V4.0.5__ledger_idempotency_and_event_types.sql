SET search_path TO aratiri;

ALTER TABLE transaction_events
    ALTER COLUMN amount_delta TYPE BIGINT USING amount_delta::bigint,
    ALTER COLUMN balance_after TYPE BIGINT USING balance_after::bigint;

CREATE UNIQUE INDEX IF NOT EXISTS ux_account_entries_account_tx
    ON account_entries(account_id, transaction_id)
    WHERE transaction_id IS NOT NULL;
