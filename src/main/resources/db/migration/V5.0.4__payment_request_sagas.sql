SET search_path TO aratiri;

-- Durable payment-request provisioning and cancellation sagas.
-- Intent (preimage/payment_hash + PROVISIONING) is committed before any LND AddInvoice.
-- CANCEL_PENDING hides BOLT11 immediately; a leased worker performs CancelInvoice.
-- PAID may supersede every other stored status when LND proves settlement.
-- EXPIRED remains derived from OPEN + expires_at (never stored).

ALTER TABLE payment_requests
    DROP CONSTRAINT ck_payment_requests_status;

ALTER TABLE payment_requests
    ADD CONSTRAINT ck_payment_requests_status CHECK (
        status IN ('PROVISIONING', 'OPEN', 'CANCEL_PENDING', 'CANCELLED', 'PAID', 'FAILED')
    );

-- Deterministic invoice secret (Base64 preimage; SHA-256 hex payment_hash already unique).
ALTER TABLE payment_requests
    ADD COLUMN preimage TEXT;

ALTER TABLE payment_requests
    ADD COLUMN provision_attempt_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE payment_requests
    ADD COLUMN provision_next_attempt_at TIMESTAMPTZ;

ALTER TABLE payment_requests
    ADD COLUMN provision_locked_until TIMESTAMPTZ;

ALTER TABLE payment_requests
    ADD COLUMN provision_locked_by VARCHAR(128);

ALTER TABLE payment_requests
    ADD COLUMN provision_last_error TEXT;

ALTER TABLE payment_requests
    ADD COLUMN cancel_attempt_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE payment_requests
    ADD COLUMN cancel_next_attempt_at TIMESTAMPTZ;

ALTER TABLE payment_requests
    ADD COLUMN cancel_locked_until TIMESTAMPTZ;

ALTER TABLE payment_requests
    ADD COLUMN cancel_locked_by VARCHAR(128);

ALTER TABLE payment_requests
    ADD COLUMN cancel_last_error TEXT;

-- Existing OPEN rows from V5.0.3 remain payable; saga columns stay null/zero.
-- New creates always set payment_hash + preimage while PROVISIONING.

CREATE INDEX idx_payment_requests_provision_due
    ON payment_requests (provision_next_attempt_at, id)
    WHERE status = 'PROVISIONING';

CREATE INDEX idx_payment_requests_cancel_due
    ON payment_requests (cancel_next_attempt_at, id)
    WHERE status = 'CANCEL_PENDING';

CREATE INDEX idx_payment_requests_provision_locked_until
    ON payment_requests (provision_locked_until)
    WHERE status = 'PROVISIONING' AND provision_locked_until IS NOT NULL;

CREATE INDEX idx_payment_requests_cancel_locked_until
    ON payment_requests (cancel_locked_until)
    WHERE status = 'CANCEL_PENDING' AND cancel_locked_until IS NOT NULL;

COMMENT ON COLUMN payment_requests.preimage IS
    'Base64 32-byte preimage committed before LND AddInvoice; payment_hash = SHA-256(preimage) hex.';

COMMENT ON TABLE payment_requests IS
    'Single-use Lightning payment links with durable PROVISIONING/CANCEL_PENDING sagas. EXPIRED is derived from expires_at while status remains OPEN; PAID wins over every other stored state when LND settles.';
