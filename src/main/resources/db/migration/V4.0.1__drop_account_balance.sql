SET search_path TO aratiri;

-- Remove the old mutable balance column. From V4.0.0 onward, AccountLedgerService derives an account's
-- balance from the latest account_entries.balance_after row, with one immutable entry per transaction.
ALTER TABLE accounts
    DROP COLUMN IF EXISTS balance;
