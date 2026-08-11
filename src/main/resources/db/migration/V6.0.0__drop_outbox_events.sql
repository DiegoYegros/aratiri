SET search_path TO aratiri;

-- Workstream 1: remove the transactional outbox. Application services now publish domain events
-- directly to Kafka after the DB commit (see KafkaEventPublisher); consumer-side idempotency and
-- TransactionReconciliationJob are the repair paths. node_operations is untouched (W2 scope).

DROP INDEX IF EXISTS idx_outbox_events_processed_at;
DROP INDEX IF EXISTS idx_outbox_events_publish_status_next_attempt;
DROP INDEX IF EXISTS uq_outbox_events_invoice_settled;
DROP INDEX IF EXISTS idx_outbox_events_claimable;

DROP TABLE IF EXISTS outbox_events;
