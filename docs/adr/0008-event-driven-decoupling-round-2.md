# Event-driven decoupling round 2: webhook creation, payment-request saga, email, price-cache warmer

Status: Accepted

Date: 2026-08

## Context

ADR 0007 moved domain publication onto direct post-commit Kafka publish and execution onto Kafka consumers for LND payment side effects. Three flows still ran synchronous or job-polled work in the business path, and one read path still paid external latency on request threads:

1. **Webhook event creation** ran inside the business transaction: `WebhookEventService.createEventAndDeliveries` built the `webhook_events` row and matching `webhook_deliveries` before the transaction committed, holding locks for every event while the API response was still pending.
2. **Payment-request provisioning and cancellation** called LND from the HTTP request thread for the synchronous-`OPEN` fast path (create returned `201` when the invoice was minted in-line) and cancellation's synchronous `200` path.
3. **Email delivery** sent SMTP from the request thread: register and forgot-password blocked on the mail server inside the HTTP request.
4. **BTC price reads** fell to request-thread external HTTP on cache miss, so the first user of the day (or the first call after a TTL expiry) paid full currency-API latency.

## Decision

1. **Webhook event creation moves to `webhook.event.created`.** Business code publishes a `WebhookEventCreatedEvent` (via `KafkaWebhookEventPublisher`) after commit instead of writing the `webhook_events` row in the business transaction. `WebhookEventConsumer` (group `aratiri.kafka.groups.webhook-event`) rebuilds the row plus deliveries through the existing `createEventAndDeliveries`, which dedupes on the unique `event_key` — redelivery is safe. `@RetryableTopic` gives 5 attempts (1s × 2.0); after exhaustion the event is dead-lettered and **logged and lost**: webhook creation is at-most-once, the documented tradeoff for removing webhook work from the business transaction. `webhook_events`/`webhook_deliveries` are unchanged and remain the delivery/audit store. The admin `webhook.test` endpoint stays synchronous: it is a direct admin action against a specific endpoint and does not go through the topic.

2. **Payment-request saga becomes Kafka-driven with a retained job backstop.** Create commits `PROVISIONING` intent and publishes `payment-request.provision.requested`; cancel durably transitions to `CANCEL_PENDING` and publishes `payment-request.cancel.requested`. `PaymentRequestProvisionConsumer` and `PaymentRequestCancelConsumer` (groups `aratiri.kafka.groups.payment-request-provision` / `payment-request-cancel`) run the existing fenced saga claims off the HTTP thread — LND is no longer called from payment-request HTTP threads. `@RetryableTopic` (10 attempts, 1s × 2.0 cap 60s) redelivers; on exhaustion the record is dead-lettered and only logged. `PaymentRequestSagaJob` is **retained** as the lease-safe backstop because the at-most-once publish can lose an event entirely: the job scans `PROVISIONING`/`CANCEL_PENDING` rows on the same lease/fencing rules, so a lost or never-published event, a consumer that never started, or a crash after claim is repaired. The job is also the terminal-state authority (`FAILED` after `provision-max-attempts`; cancellation exhaustion keeps the row `CANCEL_PENDING` for operator retry). API contract change: create now returns `202` + `Location` for every newly created request — the synchronous `201` fast path is gone; replays return `202` while pending and `200` once `OPEN`/terminal.

3. **Email moves to `email.requested`.** Register and forgot-password publish a keyed `EmailRequestedEvent` (key `VERIFICATION:<to>` / `PASSWORD_RESET:<to>`) after commit instead of sending SMTP inline. `EmailRequestPublisherAdapter` preserves the fail-closed behavior: when SMTP is unconfigured it fails fast with the same HTTP 503 the synchronous sender threw, so the register/reset contract is unchanged. `EmailConsumer` (group `aratiri.kafka.groups.email`) sends with `@RetryableTopic` (5 attempts, 2s × 2.0); after exhaustion the message is dead-lettered and logged. Verification and reset codes remain stored in the DB, so a lost email is recoverable by the user re-requesting; the email itself is at-most-once.

4. **`BtcPriceCacheWarmer` warms the price caches.** A `@Scheduled(fixedDelay)` job (default 10s, `ARATIRI_PRICE_CACHE_WARM_FIXED_DELAY_MS`) refreshes `btcPriceCurrent` and `btcPriceHistory` for all configured fiat currencies using the same cached service the request path uses, so warm failures only leave the cache empty and the request path fills on demand. The scheduler never propagates exceptions.

## Consequences

- New topics: `webhook.event.created`, `payment-request.provision.requested`, `payment-request.cancel.requested`, `email.requested`. New consumer groups, all configurable: `aratiri.kafka.groups.webhook-event`, `.payment-request-provision`, `.payment-request-cancel`, `.email`.
- Webhook creation is now at-most-once: a dead-lettered `webhook.event.created` message is logged and never delivered. The unique `event_key` index still makes redelivery idempotent, and reconciliation-driven facts (transactions, invoices) can be re-emitted by their source flows.
- Payment-request create/cancel responses are asynchronous (`202`) with the saga row as the durable anchor; the job backstop means provisioning/cancellation still eventually happen even if Kafka is down at publish time or the consumer is lost.
- Email send is no longer on the request thread; SMTP outage no longer blocks register/forgot-password responses, but dead-lettered emails are only logged. The unconfigured-SMTP `503` is preserved at the HTTP layer.
- Price-read latency moves off the request path: the warmer keeps the caches fresh; the request path only fills on miss after a warm failure.
- Deployment: Kafka availability now also gates webhook event creation and payment-request provisioning/cancellation fast paths (the saga job and webhook-less transactions soften this). Monitor the DLTs (`...-dlt`) for webhook/email/saga loss.

### Known limitation: partial fan-out crash window for webhook deliveries

`WebhookEventService.createEventAndDeliveries` runs the `webhook_events` row insert and the per-endpoint `webhook_deliveries` fan-out as separate auto-commit transactions. A consumer crash between them leaves an event row whose deliveries were never (fully) created. Redelivery cannot repair this: the `event_key` dedupe swallows the replayed message at the existing row, and the DLT handler (which requires the row save to run in its own transaction so the duplicate-key violation can be caught) means the fan-out is never re-attempted.

Mitigation status: there is no repair scan for missing deliveries today. The loss is partial in two ways — it only occurs when the crash lands in the narrow window between the two transactions, and only for endpoints whose delivery row was not yet written. Business-flow webhooks (transactions, invoices) additionally have a re-publish repair path: a source flow re-emitting the same business event re-runs creation with the same `event_key`, re-creating the row and its deliveries — this repairs the crash-before-row-insert window; the mid-fan-out window remains unrepaired until a repair scan exists.
