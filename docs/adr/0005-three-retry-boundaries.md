# Three retry boundaries

Aratiri has three independent retry boundaries: (1) outbox publication retries database-to-Kafka delivery, (2) node operations retry LND side effects with leases and status inspection, and (3) webhook delivery retries outbound HTTP callbacks independently of the core payment flow.

This was chosen over a single unified retry mechanism because each boundary has different failure modes, timeouts, and recovery strategies. Decoupling them prevents a stuck webhook from blocking payment confirmation, and vice versa. `TransactionReconciliationJob` provides a repair path if a payment result was missed by the normal worker path.
