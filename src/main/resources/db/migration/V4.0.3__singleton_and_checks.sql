SET search_path TO aratiri;

-- Ledger entries normally correspond to a transaction because transaction processors use the
-- transaction id as their idempotency key. The column remains nullable so a future/manual adjustment
-- flow can append an accounting entry that is intentionally not tied to a user-facing transaction.
ALTER TABLE account_entries
    ADD CONSTRAINT fk_account_entries_transaction
        FOREIGN KEY (transaction_id) REFERENCES transactions(id);

-- The LND stream cursor is process-wide, not per user. Enforce the singleton id used by
-- LightningListener and OnChainTransactionListener.
ALTER TABLE invoice_subscription_state
    ADD CONSTRAINT singleton_check CHECK (id = 'singleton');
