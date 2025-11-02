SET search_path TO aratiri;

-- This guarantees that any record must point to a valid transaction.
-- Nullable, allowing for manual adjustments. Not transaction-related.
ALTER TABLE account_entries
    ADD CONSTRAINT fk_account_entries_transaction
        FOREIGN KEY (transaction_id) REFERENCES transactions(id);

-- Making sure this table can only have one row.
ALTER TABLE invoice_subscription_state
    ADD CONSTRAINT singleton_check CHECK (id = 'singleton');