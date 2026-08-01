SET search_path TO aratiri;

-- Shareable, single-use Lightning payment requests (fixed satoshi amount).
-- public_id is the only externally visible identifier (opaque, non-enumerable).
-- One active Lightning invoice is linked via payment_hash (unique when present).
CREATE TABLE payment_requests (
    id                VARCHAR(36) PRIMARY KEY,
    public_id         VARCHAR(32) NOT NULL,
    user_id           VARCHAR(36) NOT NULL,
    amount_sats       BIGINT      NOT NULL,
    memo              VARCHAR(500),
    status            VARCHAR(20) NOT NULL,
    payment_hash      VARCHAR(64),
    payment_request   TEXT,
    invoice_id        VARCHAR(36),
    idempotency_key   VARCHAR(255) NOT NULL,
    idempotency_payload_hash VARCHAR(64) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL,
    expires_at        TIMESTAMPTZ NOT NULL,
    paid_at           TIMESTAMPTZ,
    cancelled_at      TIMESTAMPTZ,
    CONSTRAINT ck_payment_requests_amount_positive CHECK (amount_sats > 0),
    CONSTRAINT ck_payment_requests_status CHECK (status IN ('OPEN', 'PAID', 'CANCELLED')),
    CONSTRAINT uq_payment_requests_public_id UNIQUE (public_id),
    CONSTRAINT uq_payment_requests_user_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT uq_payment_requests_payment_hash UNIQUE (payment_hash),
    CONSTRAINT fk_payment_requests_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_payment_requests_user_created_at_id_desc
    ON payment_requests (user_id, created_at DESC, id DESC);

COMMENT ON TABLE payment_requests IS
    'Single-use fixed-amount Lightning payment links. EXPIRED is derived from expires_at while status remains OPEN; PAID wins over CANCELLED/expiry races.';
