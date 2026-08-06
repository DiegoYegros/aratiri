SET search_path TO aratiri;

ALTER TABLE outbox_events
    ADD COLUMN locked_until TIMESTAMP WITH TIME ZONE,
    ADD COLUMN locked_by VARCHAR(128);

-- Claim path: due PENDING/FAILED rows, oldest first.
-- Keep existing idx_outbox_events_publish_status_next_attempt; add a tighter partial index for the worker.
CREATE INDEX idx_outbox_events_claimable
    ON outbox_events (created_at)
    WHERE processed_at IS NULL
      AND publish_status IN ('PENDING', 'FAILED');
