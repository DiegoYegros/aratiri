CREATE SCHEMA IF NOT EXISTS aratiri;

SET search_path TO aratiri;

CREATE TABLE users (
                       id VARCHAR(36) PRIMARY KEY,
                       name VARCHAR(100) NOT NULL,
                       email VARCHAR(100) NOT NULL UNIQUE,
                       password VARCHAR(255),
                       auth_provider VARCHAR(50) NOT NULL,
                       created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                       role VARCHAR(50) NOT NULL DEFAULT 'USER'
);

CREATE TABLE accounts (
                          id VARCHAR(36) PRIMARY KEY,
                          bitcoin_address VARCHAR(255) NOT NULL,
                          balance BIGINT NOT NULL DEFAULT 0,
                          user_id VARCHAR(36) NOT NULL UNIQUE,
                          alias VARCHAR(255) NOT NULL UNIQUE,
                          CONSTRAINT fk_accounts_users FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE transactions (
                              id VARCHAR(36) PRIMARY KEY,
                              user_id VARCHAR(36) NOT NULL,
                              amount NUMERIC(20, 8) NOT NULL,
                              balance_after NUMERIC(20, 8),
                              type VARCHAR(30) NOT NULL,
                              status VARCHAR(20) NOT NULL,
                              currency VARCHAR(20) NOT NULL,
                              description TEXT,
                              reference_id VARCHAR(64),
                              failure_reason TEXT,
                              created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              CONSTRAINT uk_transactions_reference_user UNIQUE (reference_id, user_id)
);

CREATE INDEX idx_transactions_user_id ON transactions(user_id);

CREATE TABLE lightning_invoices (
                                    id VARCHAR(36) PRIMARY KEY,
                                    user_id VARCHAR(36) NOT NULL,
                                    payment_hash VARCHAR(64) NOT NULL,
                                    preimage VARCHAR(64) NOT NULL,
                                    payment_request VARCHAR(1000) NOT NULL,
                                    invoice_state VARCHAR(20) NOT NULL,
                                    amount_sats BIGINT NOT NULL,
                                    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                    expiry BIGINT NOT NULL,
                                    amt_paid_sats BIGINT NOT NULL DEFAULT 0,
                                    settled_at TIMESTAMP WITHOUT TIME ZONE,
                                    memo VARCHAR(500),
                                    CONSTRAINT fk_lightning_invoices_users FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_lightning_invoices_user_id ON lightning_invoices(user_id);
CREATE INDEX idx_lightning_invoices_payment_hash ON lightning_invoices(payment_hash);

CREATE TABLE outbox_events (
                               id UUID PRIMARY KEY,
                               aggregate_type VARCHAR(255) NOT NULL,
                               aggregate_id VARCHAR(255) NOT NULL,
                               event_type VARCHAR(255) NOT NULL,
                               payload TEXT NOT NULL,
                               created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               processed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_events_processed_at ON outbox_events(processed_at);

CREATE TABLE node_settings (
                               id VARCHAR(50) PRIMARY KEY,
                               auto_manage_peers BOOLEAN NOT NULL DEFAULT FALSE,
                               created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE refresh_tokens (
                                id VARCHAR(36) PRIMARY KEY,
                                user_id VARCHAR(36),
                                token VARCHAR(255) NOT NULL UNIQUE,
                                expiry_date TIMESTAMP WITH TIME ZONE NOT NULL,
                                CONSTRAINT fk_refresh_tokens_users FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);

CREATE TABLE password_reset_data (
                                     id VARCHAR(36) PRIMARY KEY,
                                     code VARCHAR(255) NOT NULL,
                                     user_id VARCHAR(36) NOT NULL,
                                     expiry_date TIMESTAMP WITHOUT TIME ZONE NOT NULL,
                                     CONSTRAINT fk_password_reset_users FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_password_reset_user_id ON password_reset_data(user_id);

CREATE TABLE verification_data (
                                   email VARCHAR(255) PRIMARY KEY,
                                   name VARCHAR(255),
                                   password VARCHAR(255),
                                   alias VARCHAR(255),
                                   code VARCHAR(255),
                                   expires_at TIMESTAMP WITHOUT TIME ZONE
);

CREATE TABLE invoice_subscription_state (
                                            id VARCHAR(50) PRIMARY KEY,
                                            add_index BIGINT NOT NULL DEFAULT 0,
                                            settle_index BIGINT NOT NULL DEFAULT 0
);