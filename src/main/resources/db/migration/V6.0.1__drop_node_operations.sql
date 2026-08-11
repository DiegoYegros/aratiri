SET search_path TO aratiri;

-- Workstream 2: remove the node_operations work table. LND payment execution now runs directly
-- in Kafka consumers (LightningPaymentExecutionConsumer, OnChainSendExecutionConsumer); the
-- transaction state is the redelivery idempotency anchor and on-chain label lookup replaces the
-- BROADCASTED recovery state. ux_transaction_events_fee_per_transaction (V4.0.7) belongs to
-- transaction_events and must stay.

DROP INDEX IF EXISTS idx_node_operations_runnable;
DROP INDEX IF EXISTS idx_node_operations_locked_until;
DROP INDEX IF EXISTS idx_node_operations_type_status;

DROP TABLE IF EXISTS node_operations;
