SET search_path TO aratiri;

ALTER TABLE transactions
    ADD COLUMN current_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN current_amount BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN balance_after BIGINT,
    ADD COLUMN failure_reason TEXT,
    ADD COLUMN completed_at TIMESTAMP WITH TIME ZONE;

UPDATE transactions t
SET current_amount = t.amount + COALESCE((
    SELECT SUM(f.amount_delta)
    FROM transaction_events f
    WHERE f.transaction_id = t.id
    AND f.event_type = 'FEE_ADDED'
), 0);

WITH latest_events AS (
    SELECT DISTINCT ON (te.transaction_id)
        te.transaction_id,
        te.status AS event_status,
        te.balance_after,
        te.details
    FROM transaction_events te
    WHERE te.event_type = 'STATUS_CHANGED'
    ORDER BY te.transaction_id, te.created_at DESC, te.id DESC
)
UPDATE transactions t
SET
    current_status = le.event_status,
    balance_after = le.balance_after,
    failure_reason = le.details,
    completed_at = CASE WHEN le.event_status = 'COMPLETED' THEN CURRENT_TIMESTAMP ELSE NULL END
FROM latest_events le
WHERE t.id = le.transaction_id;

DROP INDEX IF EXISTS idx_transactions_user_created_at;
CREATE INDEX idx_transactions_user_created_at_id_desc
    ON transactions (user_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_transactions_user_status_created_at
    ON transactions (user_id, current_status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_transactions_reference_id
    ON transactions (reference_id);

CREATE TABLE payment_commands (
    id UUID PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    command_type VARCHAR(40) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    transaction_id VARCHAR(36),
    response_payload TEXT,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_payment_commands_user_idempotency UNIQUE (user_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_payment_commands_user_status
    ON payment_commands (user_id, status);

CREATE INDEX IF NOT EXISTS idx_payment_commands_transaction_id
    ON payment_commands (transaction_id);
