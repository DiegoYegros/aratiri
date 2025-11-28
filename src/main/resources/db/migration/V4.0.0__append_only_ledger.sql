SET search_path TO aratiri;

CREATE TABLE IF NOT EXISTS account_entries (
    id VARCHAR(36) PRIMARY KEY,
    account_id VARCHAR(36) NOT NULL,
    transaction_id VARCHAR(36),
    delta_sats BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    entry_type VARCHAR(50) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_account_entries_accounts FOREIGN KEY (account_id) REFERENCES accounts(id)
);

CREATE INDEX IF NOT EXISTS idx_account_entries_account_created_at ON account_entries(account_id, created_at DESC);

CREATE TABLE IF NOT EXISTS transaction_events (
    id VARCHAR(36) PRIMARY KEY,
    transaction_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    status VARCHAR(20),
    amount_delta NUMERIC(20, 8),
    balance_after NUMERIC(20, 8),
    details TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transaction_events_transactions FOREIGN KEY (transaction_id) REFERENCES transactions(id)
);

CREATE INDEX IF NOT EXISTS idx_transaction_events_tx_created_at ON transaction_events(transaction_id, created_at DESC);

ALTER TABLE transactions DROP COLUMN IF EXISTS balance_after;
ALTER TABLE transactions DROP COLUMN IF EXISTS status;
ALTER TABLE transactions DROP COLUMN IF EXISTS failure_reason;
