SET search_path TO aratiri;

ALTER TABLE users
    ADD CONSTRAINT chk_users_auth_provider
        CHECK (auth_provider IN ('LOCAL', 'GOOGLE', 'EXTERNAL')),
    ADD CONSTRAINT chk_users_role
        CHECK (role IN ('USER', 'ADMIN', 'SUPERADMIN', 'VIEWER'));

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_users
        FOREIGN KEY (user_id) REFERENCES users(id),
    ADD CONSTRAINT chk_transactions_type
        CHECK (type IN (
                        'LIGHTNING_CREDIT',
                        'LIGHTNING_DEBIT',
                        'INVOICE_CREDIT',
                        'INVOICE_DEBIT',
                        'ONCHAIN_DEBIT',
                        'ONCHAIN_CREDIT'
            )),
    ADD CONSTRAINT chk_transactions_status
        CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    ADD CONSTRAINT chk_transactions_currency
        CHECK (currency IN ('PYG', 'USD', 'USDT', 'USDC', 'BTC')),
    ADD CONSTRAINT chk_transactions_amount_nonnegative
        CHECK (amount >= 0);

DROP INDEX IF EXISTS idx_transactions_user_id;
CREATE INDEX idx_transactions_user_created_at ON transactions(user_id, created_at);
CREATE INDEX idx_transactions_status_created_at ON transactions(status, created_at);

ALTER TABLE accounts
    ADD CONSTRAINT uk_accounts_bitcoin_address UNIQUE (bitcoin_address);

ALTER TABLE lightning_invoices
    ADD CONSTRAINT chk_lightning_invoices_state
        CHECK (invoice_state IN ('OPEN', 'ACCEPTED', 'SETTLED', 'CANCELED', 'UNRECOGNIZED')),
    ADD CONSTRAINT chk_lightning_invoices_amounts_nonnegative
        CHECK (amount_sats >= 0 AND amt_paid_sats >= 0);

DROP INDEX IF EXISTS idx_lightning_invoices_payment_hash;
ALTER TABLE lightning_invoices
    ADD CONSTRAINT uk_lightning_invoices_payment_hash UNIQUE (payment_hash);