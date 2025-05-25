CREATE TABLE USERS
(
    id         CHAR(36) PRIMARY KEY,
    name       VARCHAR(100)        NOT NULL,
    password   VARCHAR(100)        NOT NULL,
    email      VARCHAR(100) UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ACCOUNTS
(
    id              CHAR(36) PRIMARY KEY,
    bitcoin_address VARCHAR(100) NOT NULL,
    balance         BIGINT       NOT NULL DEFAULT 0
);
CREATE TABLE LIGHTNING_INVOICES
(
    id              CHAR(36) PRIMARY KEY,
    user_id         CHAR(36)      NOT NULL,
    payment_hash    VARCHAR(64)   NOT NULL,
    preimage        VARCHAR(64)   NOT NULL,
    payment_request VARCHAR(1000) NOT NULL,
    invoice_state   VARCHAR(20)   NOT NULL CHECK (invoice_state IN ('OPEN', 'ACCEPTED', 'SETTLED', 'CANCELED')),
    amount_sats     BIGINT        NOT NULL,
    created_at      TIMESTAMP     NOT NULL,
    expiry          INT           NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users (id)
);

ALTER TABLE ACCOUNTS
    ADD COLUMN user_id CHAR(36) UNIQUE NOT NULL;
ALTER TABLE ACCOUNTS
    ADD CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES USERS (id);