SET search_path TO aratiri;

-- Webhook endpoints managed by admin
CREATE TABLE webhook_endpoints (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    url TEXT NOT NULL,
    signing_secret VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_success_at TIMESTAMP WITH TIME ZONE,
    last_failure_at TIMESTAMP WITH TIME ZONE
);

-- Subscriptions linking endpoints to event types
CREATE TABLE webhook_endpoint_subscriptions (
    id UUID PRIMARY KEY,
    endpoint_id UUID NOT NULL REFERENCES webhook_endpoints(id) ON DELETE CASCADE,
    event_type VARCHAR(80) NOT NULL,
    UNIQUE (endpoint_id, event_type)
);

-- Webhook events (deterministic, idempotent via event_key)
CREATE TABLE webhook_events (
    id UUID PRIMARY KEY,
    event_key VARCHAR(180) NOT NULL UNIQUE,
    event_type VARCHAR(80) NOT NULL,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(36),
    external_reference VARCHAR(128),
    payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Delivery attempts per event per endpoint
CREATE TABLE webhook_deliveries (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES webhook_events(id) ON DELETE CASCADE,
    endpoint_id UUID NOT NULL REFERENCES webhook_endpoints(id) ON DELETE CASCADE,
    event_type VARCHAR(80) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_until TIMESTAMP WITH TIME ZONE,
    locked_by VARCHAR(128),
    response_status INTEGER,
    response_body TEXT,
    last_error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at TIMESTAMP WITH TIME ZONE
);

-- Transaction correlation fields for client integration
ALTER TABLE transactions
    ADD COLUMN external_reference VARCHAR(128),
    ADD COLUMN metadata TEXT;

-- Invoice correlation fields for client integration
ALTER TABLE lightning_invoices
    ADD COLUMN external_reference VARCHAR(128),
    ADD COLUMN metadata TEXT;

-- Indexes for webhook delivery job and lookups
CREATE INDEX idx_webhook_deliveries_runnable ON webhook_deliveries (status, next_attempt_at);
CREATE INDEX idx_webhook_deliveries_endpoint_created ON webhook_deliveries (endpoint_id, created_at DESC);
CREATE INDEX idx_webhook_events_type_created ON webhook_events (event_type, created_at DESC);
CREATE INDEX idx_transactions_user_external_reference ON transactions (user_id, external_reference);
