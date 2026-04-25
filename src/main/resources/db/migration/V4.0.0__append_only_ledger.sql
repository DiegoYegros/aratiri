SET search_path TO aratiri;

-- Major accounting model change: account balance becomes an append-only ledger.
--
-- AccountLedgerService appends one row per balance-affecting transaction and reads the latest
-- balance_after as the current balance. This gives an auditable trail, lets debit processors reject
-- overdrafts from the locked latest balance, and prevents retries from mutating a balance twice.
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

-- Transaction lifecycle becomes evented. Transactions keep the original intent amount/type/reference,
-- while transaction_events records PENDING/COMPLETED/FAILED transitions and later fee deltas.
-- V4.0.8 adds denormalized current_* columns for fast API reads without abandoning this history.
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

-- Remove mutable transaction state from the source table. The canonical lifecycle is now the ordered
-- transaction_events stream. These fields are intentionally reintroduced in V4.0.8 as a read model,
-- not as the primary accounting history.
ALTER TABLE transactions DROP COLUMN IF EXISTS balance_after;
ALTER TABLE transactions DROP COLUMN IF EXISTS status;
ALTER TABLE transactions DROP COLUMN IF EXISTS failure_reason;
