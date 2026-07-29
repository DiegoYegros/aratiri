# ADR 0006: Deployment topology and service extraction

**Status:** Proposed / pending tutor approval

## Context

The approved project ficha proposes a microservice architecture. The current implementation is not a set of microservices: it is one Spring Boot deployable, built as one `bootJar`, with bounded-context packages sharing one process and one PostgreSQL schema. Kafka, the outbox, and ports-and-adapters provide asynchronous and modular boundaries, but they do not make independently deployable services.

Rewriting the application into distributed services now would add deployment, observability, network-failure, data-consistency, and test-environment work without evidence that those costs are necessary for the PFGR scope. The existing module boundaries are useful extraction seams and let the report describe the implementation accurately.

## Proposed decision

Deliver Aratiri as a single-deployable modular monolith unless the tutor requires physical microservices. Keep bounded contexts separated through application ports, events, and explicit ownership of domain behavior. The PFGR and diagrams must call the current topology a modular monolith, not a microservice architecture.

If the tutor requires microservices, extract incrementally from the following map rather than performing a full rewrite:

| Existing bounded context | Candidate service boundary | Intended ownership and integration |
|---|---|---|
| `auth` | Identity service | Users, verification, password reset, refresh tokens, and token issuance; expose identity contracts to other services. |
| `accounts` | Accounts and ledger service | Accounts and balance queries; extracted together with the ledger portion of `transactions` to keep balance invariants local. |
| `transactions` | Accounts and ledger service / transaction read service | Account entries stay with accounts; transaction projections may become a separate event-fed read service only if independently useful. |
| `payments` | Payment orchestration service | Idempotent payment commands and payment outbox events; communicate with ledger and node gateway through APIs or events. |
| `invoices` | Invoice service | Invoice lifecycle and invoice records; publish settlement events and call the node gateway through a stable contract. |
| `decoder`, `lnurl` | Payment-protocol edge service | BOLT11, LNURL, alias, NIP-05, and on-chain decoding and LNURL callbacks; mostly stateless protocol-facing behavior. |
| `admin` | LND gateway service | Node administration plus the LND-facing parts of `infrastructure`; exclusively own LND credentials and gRPC access. |
| `generaldata` | Pricing/reference-data service | Exchange-rate retrieval and caching; extract only if it needs independent scaling or reuse. |
| `webhooks` | Webhook delivery service | Endpoint subscriptions, signed delivery attempts, and retries; consume domain events asynchronously. |
| `infrastructure`, `shared` | Not services | Split adapters into the service that owns each use case; keep only small versioned contracts shared across deployables. |

Each extracted service must own its persistence rather than directly sharing Aratiri's schema. Cross-boundary workflows must use versioned APIs or events, with idempotency and observable retries.

## Consequences

- The implementation remains operationally simple: one deployment, one transaction boundary for ledger-critical changes, and one integration-test environment.
- Package boundaries, ports, Kafka events, and the outbox preserve a credible path to later extraction.
- The deployable cannot scale or fail independently by bounded context, and shared-process coupling remains possible; architecture checks and code review must protect module boundaries.
- The implementation differs from the ficha's proposed topology, so this decision must remain pending until the tutor accepts it or requests a smaller demonstrative extraction.
- A tutor requirement for physical microservices supersedes this proposal. The extraction map then supplies the migration order and scope.

## Extraction triggers

Extract a candidate only when at least one trigger is demonstrated:

- the tutor or PFGR evaluation criteria explicitly require independently deployable services;
- a bounded context needs materially different scaling, availability, or release cadence;
- LND credentials or another sensitive capability require process-level isolation;
- measured failures show that one module's resource use or outage harms unrelated workflows;
- separate teams need autonomous ownership and versioned contracts; or
- a domain boundary can own its data and tolerate distributed consistency without weakening ledger invariants.

Until one of these triggers applies, logical modularity is preferred over distribution.
