SET search_path TO aratiri;

-- Make invoice.settled outbox insertion idempotent at the database boundary.
-- Keep the earliest row per (Invoice, aggregate_id, invoice.settled) when duplicates exist.

DELETE FROM outbox_events a
    USING outbox_events b
WHERE a.aggregate_type = 'Invoice'
  AND a.event_type = 'invoice.settled'
  AND b.aggregate_type = 'Invoice'
  AND b.event_type = 'invoice.settled'
  AND a.aggregate_id = b.aggregate_id
  AND a.id > b.id;

CREATE UNIQUE INDEX uq_outbox_events_invoice_settled
    ON outbox_events (aggregate_type, aggregate_id, event_type)
    WHERE aggregate_type = 'Invoice' AND event_type = 'invoice.settled';

COMMENT ON INDEX uq_outbox_events_invoice_settled IS
    'Deterministic idempotency for Invoice/invoice.settled outbox facts across listener replay, saga recovery, and concurrent settlement paths.';
