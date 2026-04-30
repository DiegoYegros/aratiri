# Append-only ledger

Aratiri records all balance changes as `account_entries` — an append-only table with per-entry `delta` amounts and a computed `balance_after`. The current balance is derived from the latest entry rather than stored in a mutable balance column.

This was chosen over a mutable balance column because append-only records provide a full audit trail, make reconciliation straightforward, and eliminate the risk of lost updates under concurrent payment processing. Each entry is idempotent per account and transaction, so retries are safe.
