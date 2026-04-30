# Outbox pattern for reliable event publication

Aratiri writes domain events to an `outbox_events` table in the same database transaction as the business change, then `OutboxEventJob` publishes them to Kafka. This ensures at-least-once delivery without distributed transactions.

This was chosen over publishing to Kafka directly from HTTP request threads because Kafka can be unavailable. With the outbox, the user request can return a 202 as soon as intent is durably recorded. If Kafka is down, the job retries with backoff. Duplicate domain events are keyed for idempotent consumption.
