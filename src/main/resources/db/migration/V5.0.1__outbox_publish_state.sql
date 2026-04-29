ALTER TABLE outbox_events
    ADD COLUMN publish_status VARCHAR(32);

ALTER TABLE outbox_events
    ADD COLUMN publish_attempts INTEGER NOT NULL DEFAULT 0;

ALTER TABLE outbox_events
    ADD COLUMN last_error TEXT;

ALTER TABLE outbox_events
    ADD COLUMN next_attempt_at TIMESTAMP WITH TIME ZONE;

UPDATE outbox_events
SET publish_status = CASE
    WHEN processed_at IS NULL THEN 'PENDING'
    ELSE 'PUBLISHED'
END;

ALTER TABLE outbox_events
    ALTER COLUMN publish_status SET NOT NULL,
    ALTER COLUMN publish_status SET DEFAULT 'PENDING';

CREATE INDEX idx_outbox_events_publish_status_next_attempt
    ON outbox_events(publish_status, next_attempt_at, created_at);
