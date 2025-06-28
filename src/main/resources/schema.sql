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
    bitcoin_address VARCHAR(100) UNIQUE NOT NULL,
    alias           VARCHAR(100) UNIQUE NOT NULL,
    balance         BIGINT              NOT NULL DEFAULT 0
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
    amt_paid_sats   BIGINT        NOT NULL DEFAULT 0,
    settled_at      TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE TABLE TRANSACTIONS
(
    id            CHAR(36) PRIMARY KEY,
    user_id       CHAR(36)      NOT NULL, -- i know
    amount        DECIMAL(20, 8) NOT NULL,
    balance_after DECIMAL(20, 8) NOT NULL,
    currency      VARCHAR(10) NOT NULL CHECK (currency IN ('USDT', 'USDC', 'PYG', 'USD', 'BTC')),
    type          VARCHAR(30)   NOT NULL CHECK (type IN ('INVOICE_CREDIT', 'LN_PAYMENT_DEBIT', 'ONCHAIN_DEPOSIT', 'INTERNAL_TRANSFER_CREDIT', 'INTERNAL_TRANSFER_DEBIT')),
    status        VARCHAR(20)   NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    description   VARCHAR(255),
    reference_id  CHAR(36),
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES USERS (id)
);
COMMENT ON TABLE TRANSACTIONS IS 'Records all financial transactions for user accounts, including credits and debits.';
COMMENT ON COLUMN TRANSACTIONS.amount IS 'A positive value for credits, a negative value for debits.';
COMMENT ON COLUMN TRANSACTIONS.balance_after IS 'The final balance of the user''s account AFTER this transaction.';
COMMENT ON COLUMN TRANSACTIONS.type IS 'The specific reason for the transaction.';
COMMENT ON COLUMN TRANSACTIONS.status IS 'The current state of the transaction.';
COMMENT ON COLUMN TRANSACTIONS.description IS 'A human-readable description of the transaction.';
COMMENT ON COLUMN TRANSACTIONS.reference_id IS 'An ID pointing to a related source object (e.g., an ID from a LIGHTNING_INVOICES table).';

ALTER TABLE ACCOUNTS
    ADD COLUMN user_id CHAR(36) UNIQUE NOT NULL;
ALTER TABLE ACCOUNTS
    ADD CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES USERS (id);