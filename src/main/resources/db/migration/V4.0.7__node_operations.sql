SET search_path TO aratiri;

CREATE TABLE node_operations (
    id UUID PRIMARY KEY,
    transaction_id VARCHAR(36) NOT NULL UNIQUE,
    user_id VARCHAR(36) NOT NULL,
    operation_type VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    reference_id VARCHAR(128),
    request_payload TEXT NOT NULL,
    external_id VARCHAR(128),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_until TIMESTAMP WITH TIME ZONE,
    locked_by VARCHAR(128),
    last_error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_node_operations_runnable
    ON node_operations (status, next_attempt_at);

CREATE INDEX idx_node_operations_locked_until
    ON node_operations (locked_until);

CREATE INDEX idx_node_operations_type_status
    ON node_operations (operation_type, status);

CREATE UNIQUE INDEX ux_transaction_events_fee_per_transaction
    ON transaction_events (transaction_id)
    WHERE event_type = 'FEE_ADDED';
