SET search_path TO aratiri;

-- Durable execution log for LND side effects.
--
-- The outbox delivers committed domain events to Kafka; node_operations owns the next step:
-- converting those events into a single durable unit of work against LND. PaymentConsumer enqueues
-- one row per transaction, and NodeOperationJob uses leases/attempt counts/statuses to safely resume
-- after crashes without relying on Kafka redelivery alone.
CREATE TABLE node_operations (
    id UUID PRIMARY KEY,
    -- One external side-effect workflow per Aratiri transaction.
    transaction_id VARCHAR(36) NOT NULL UNIQUE,
    user_id VARCHAR(36) NOT NULL,
    -- LIGHTNING_PAYMENT calls LND SendPayment; ONCHAIN_SEND calls LND SendCoins.
    operation_type VARCHAR(40) NOT NULL,
    -- PENDING/IN_PROGRESS are retryable worker states; BROADCASTED records an on-chain txid before
    -- local confirmation; UNKNOWN_OUTCOME means LND may have acted but Aratiri could not prove it.
    status VARCHAR(40) NOT NULL,
    -- For Lightning payments, this is the payment hash used to query LND before retrying.
    reference_id VARCHAR(128),
    -- Canonical request body needed to replay the LND call after process/Kafka failure.
    request_payload TEXT NOT NULL,
    -- External LND result id, currently the on-chain txid once SendCoins returns.
    external_id VARCHAR(128),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Worker lease fields prevent multiple app instances from executing the same node operation.
    locked_until TIMESTAMP WITH TIME ZONE,
    locked_by VARCHAR(128),
    last_error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE
);

-- Claim runnable work by status and due time.
CREATE INDEX idx_node_operations_runnable
    ON node_operations (status, next_attempt_at);

-- Find stale IN_PROGRESS leases that should be reclaimed.
CREATE INDEX idx_node_operations_locked_until
    ON node_operations (locked_until);

-- Admin/API filtering for /v1/admin/node-operations.
CREATE INDEX idx_node_operations_type_status
    ON node_operations (operation_type, status);

-- A Lightning routing fee can only be recorded once per transaction. Retries may see the same
-- succeeded payment more than once, but transaction current_amount and the event stream must not
-- accumulate duplicate FEE_ADDED rows.
CREATE UNIQUE INDEX ux_transaction_events_fee_per_transaction
    ON transaction_events (transaction_id)
    WHERE event_type = 'FEE_ADDED';
