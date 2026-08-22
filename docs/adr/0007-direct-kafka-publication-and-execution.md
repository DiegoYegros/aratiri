# Direct Kafka publication and consumer-side execution (replacing the outbox and node_operations)

Status: Accepted

Date: 2026-08

## Context

Aratiri originally published domain events through a transactional outbox: application services wrote `outbox_events` rows in the same DB transaction as state changes, and `OutboxEventJob` polled the table every second and forwarded rows to Kafka. LND payment side effects were then driven by a second durable work table, `node_operations`, claimed and executed by `NodeOperationJob` with leases and attempt limits.

This had two costs. The outbox added a polling hop with its own lease/fence machinery, and `node_operations` duplicated the same claim/lease pattern with a schema that had to be migrated, queried by admin APIs, and reconciled. The workstreams W1 and W2 removed both tables and moved publication and execution fully onto Kafka.

## Decision

1. **Direct publication after commit.** Application services publish domain events to Kafka via `EventPublisher`/`KafkaEventPublisher`. Inside a Spring transaction the send is deferred to an `afterCommit` callback; outside a transaction it is immediate. A failed send is logged and the event is lost, then repaired by consumer-side idempotency plus `TransactionReconciliationJob`. This changes the delivery guarantee from at-least-once (outbox) to at-most-once publish with idempotent consumption.

2. **Execution consumers instead of `node_operations`.** `LightningPaymentExecutionConsumer` (on `payment.initiated`) and `OnChainSendExecutionConsumer` (on `onchain.payment.initiated`) run LND side effects directly with `@RetryableTopic` backoff (5 attempts, 1s × 2.0, DLT). The transaction row is the idempotency anchor.

3. **DB-level execution claim.** Before any LND interaction an execution consumer atomically claims the transaction (`current_status` PENDING → EXECUTING, conditional UPDATE); exactly one concurrent delivery wins, losers skip without touching LND. A retryable failure releases the claim so the next redelivery can retry; a crashed owner leaves the claim behind and `TransactionReconciliationJob` reclaims it past `node_settings.transaction_reconciliation_min_age_ms` via LND state checks (lightning: `findPayment`; on-chain: wallet label lookup `aratiri-onchain-<transactionId>`).

4. **Dead-letter topic is the unknown-outcome boundary.** After max attempts the message is dead-lettered and the `@DltHandler` emits the existing `node_operation.unknown_outcome` webhook with the same payload shape as before; `operationId` is now the `transactionId` and `externalId` is always null (the wallet label lookup is the on-chain source of truth).

5. **Historical designs are superseded.** ADR 0003 (outbox pattern) and ADR 0005 (three retry boundaries) describe the previous architecture and are retained for history. The current reliability model is: idempotent consumers + direct post-commit publish + reconciliation repair, plus webhook delivery's own retry boundary.

## Consequences

- Removed tables: `outbox_events` (V6.0.0) and `node_operations` (V6.0.1). Removed admin surface: `GET /v1/admin/node-operations`.
- Kafka publish failures after commit are no longer retried at the publisher; `TransactionReconciliationJob` fails/confirms stuck PENDING or stale EXECUTING transactions from LND state, which bounds the data-loss window.
- `NotificationConsumer` deduplicates user-visible pushes in memory (5-minute TTL, 10k-entry cap) since the outbox's `publishOnce` guarantee is gone. This is at-most-once-within-a-window: duplicates separated by more than the TTL (or evicted by the cap) can still be delivered.
- Deployment note: Kafka availability is now on the critical path for LND side effects; the reconciliation job is the safety net, so `transaction_reconciliation_min_age_ms` should be tuned deliberately.
- Known race window: `TransactionReconciliationJob` can reconcile an on-chain debit while a live execution still holds the EXECUTING claim past `transaction_reconciliation_min_age_ms` (default 300s). If the wallet label is not yet visible in LND (slow node) the job fails the transaction and the owner then broadcasts, so money moves under a FAILED transaction. Mitigation: keep the min-age well above the expected `SendCoins` duration, treat the job as a repair path for crashed/stuck owners, and monitor LND health so reconciliation only runs against a responsive node.
