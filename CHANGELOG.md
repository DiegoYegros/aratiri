# Changelog

All notable changes to this project are documented in this file.

## Unreleased

### Added

- Webhook event creation moved off the business transaction: business code publishes
  `webhook.event.created` (via `KafkaWebhookEventPublisher`) after commit and
  `WebhookEventConsumer` rebuilds the `webhook_events` row plus deliveries, deduping on the
  unique `event_key`. `webhook_events`/`webhook_deliveries` tables are unchanged and remain the
  delivery/audit store. Webhook creation is now at-most-once: after 5 retries the event is
  dead-lettered and logged (documented loss). The admin `webhook.test` endpoint stays synchronous.
  Known limitation: the row insert and the delivery fan-out are separate transactions — a consumer
  crash between them leaves an event row without its deliveries, and redelivery dedupes at the row
  (documented in ADR 0008).
- Payment-request saga is now Kafka-driven: create commits `PROVISIONING` and publishes
  `payment-request.provision.requested`; cancel durably transitions to `CANCEL_PENDING` and
  publishes `payment-request.cancel.requested`. `PaymentRequestProvisionConsumer` and
  `PaymentRequestCancelConsumer` run the fenced saga claims off the HTTP thread — LND is no longer
  called from payment-request HTTP threads. `PaymentRequestSagaJob` is retained as the lease-safe
  backstop (lost-event repair and terminal-state authority). Exhausted records dead-letter and are
  logged only.
- Email delivery is now Kafka-driven: register and forgot-password publish `email.requested`
  instead of sending SMTP inline; `EmailConsumer` retries 5× (2s × 2.0) and dead-letter-logs.
  The unconfigured-SMTP `503` is preserved at the HTTP layer.
- `BtcPriceCacheWarmer` (`@Scheduled`, 10s default) warm-refreshes `btcPriceCurrent` and
  `btcPriceHistory` so request threads rarely pay external currency-API latency.
- New env vars: `ARATIRI_KAFKA_GROUP_WEBHOOK_EVENT`, `ARATIRI_KAFKA_GROUP_PAYMENT_REQUEST_PROVISION`,
  `ARATIRI_KAFKA_GROUP_PAYMENT_REQUEST_CANCEL`, `ARATIRI_KAFKA_GROUP_EMAIL`,
  `ARATIRI_PRICE_CACHE_WARM_FIXED_DELAY_MS`.

### Removed (breaking)

- Dropped the transactional outbox: the `outbox_events` table (and its indexes, including the
  V5.0.5 partial unique index) is removed in migration `V6.0.0`. Application services now publish
  domain events directly to Kafka after the DB commit (`EventPublisher`/`KafkaEventPublisher`).
  Kafka publish failures after commit are logged and repaired by consumer-side idempotency and
  `TransactionReconciliationJob` — the publish guarantee changed from at-least-once to
  at-most-once.
- Dropped the `node_operations` work table and `NodeOperationJob` in migration `V6.0.1`. LND
  payment side effects now run directly in Kafka execution consumers
  (`LightningPaymentExecutionConsumer` on `payment.initiated`,
  `OnChainSendExecutionConsumer` on `onchain.payment.initiated`) with `@RetryableTopic` backoff.
  Before any LND call the consumer atomically claims the transaction
  (`current_status` `EXECUTING`); `TransactionReconciliationJob` reclaims stale claims.
- Removed the admin endpoint `GET /v1/admin/node-operations` and its DTOs.
- Removed the `aratiri.node-operations.*` configuration properties. Consumer groups are now
  configurable via `aratiri.kafka.groups.lightning-payment-execution` and
  `aratiri.kafka.groups.onchain-send-execution`.

### Changed

- Payment-request create no longer returns `201` on synchronous provisioning success: every newly
  created request now returns `202` + `Location`, and provisioning completes asynchronously via the
  saga consumer/job (API contract change). Cancel returns `202` for `CANCEL_PENDING` and `200` once
  `CANCELLED`; `409` for `PAID`/`EXPIRED`/`FAILED` is unchanged.
- `node_operation.unknown_outcome` webhook facts: `operationId` is now the `transactionId`
  (previously a node-operation row UUID) and `externalId` is always `null`. The event type,
  `event_key` scheme, and remaining fields are unchanged.
- On-chain sends are stamped with the wallet label `aratiri-onchain-<transactionId>` and recovered
  on redelivery by looking the label up in `GetTransactions` instead of re-broadcasting.
- `TransactionReconciliationJob` now also reconciles PENDING/EXECUTING `ONCHAIN_DEBIT`
  transactions: the wallet label lookup decides confirm (broadcast found) vs fail (not found).
  Note the race window: a live execution still holding the EXECUTING claim past
  `transaction_reconciliation_min_age_ms` can be failed by the job if the label is not yet
  visible in LND — keep the min-age above the expected `SendCoins` duration and treat
  reconciliation as a repair path for stuck owners.
- `NotificationConsumer` deduplicates user-visible WebSocket pushes with an in-memory 5-minute
  TTL cache to compensate for the removed outbox `publishOnce` guarantee. This is
  at-most-once-within-a-window, not a hard guarantee: duplicates past the TTL or the 10k-entry
  cache cap can still be delivered.
- Admin listing of transaction state now maps the worker-internal `EXECUTING` claim state to
  `PENDING` for user-facing APIs.
