# Aratiri

A Spring Boot 4.0 service that gives a custodial platform a Bitcoin Lightning and on-chain API without Aratiri holding custody. The embedding platform owns users and balances, Aratiri owns the LND bridge.

## Setup

- **Java 25**. The Gradle toolchain auto-downloads it via Foojay resolver
- **Docker**. Runs PostgreSQL, Kafka, and Testcontainers-backed integration tests
- **LND node**, reachable over gRPC with admin macaroon

```bash
git clone https://github.com/DiegoYegros/aratiri.git
cd aratiri
cp .env.example .env   # edit: JWT_SECRET, ARATIRI_BASE_URL, LND paths, DB/kafka hosts
# Start infrastructure:
docker compose --profile db-only up -d
docker compose --profile kafka-only up -d
# Or for full stack:
docker compose --profile full-stack up --build -d
```

Required env vars: `JWT_SECRET`, `ARATIRI_BASE_URL`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS`, `GRPC_CLIENT_LND_NAME`, `GRPC_CLIENT_LND_PORT`, `ADMIN_MACAROON_PATH`. Optional: `GOOGLE_OAUTH_CLIENT_ID`, `EMAIL_USERNAME`/`EMAIL_PASSWORD`, `NOSTR_ACTIVE`.

## Development Commands

```
./gradlew clean build              # compile, test, sonarlint, spotbugs, jacoco, verifyProto (use this before every commit)
./gradlew bootRun                  # run the app locally (needs infrastructure up)
./gradlew test                     # unit + integration tests only (Testcontainers spins up DB/kafka)
./gradlew sonarlintMain            # static analysis (main)
./gradlew spotbugsMain             # bug pattern detection (main)
./gradlew jacocoTestReport         # coverage report (build/reports/jacoco/)
./gradlew jacocoTestCoverageVerification  # enforces 90% line coverage
./gradlew verifyProtoClassesInBootJar     # ensures lnrpc/routerrpc/invoicesrpc in boot jar
docker compose --profile full-stack up --build -d   # containerized run on port 2100
```

## Project Structure

```
src/main/java/com/aratiri/
├── AratiriApplication.java        # @SpringBootApplication entry point
├── accounts/                      # user accounts, QR payloads, deposit addr, fiat views
├── admin/                         # LND node/channel/peer admin, webhook mgmt, stats
├── auth/                          # email login, Google SSO, trusted issuers, JWT refresh
├── decoder/                       # BOLT11/LNURL/on-chain/alias/LUD16 resolution
├── generaldata/                   # supported currencies, BTC price caching
├── invoices/                      # LND invoice creation, decode, webhook emission
├── lnurl/                         # public LNURL-pay metadata/callback + authenticated pay
├── payments/                      # Lightning/on-chain/LNURL payments with idempotency keys
├── requests/                      # shareable single-use payment links, provision/cancel saga
├── transactions/                  # append-only tx read model, lifecycle events, settlement
├── webhooks/                      # event construction, endpoint subscription, delivery retry
├── bitcoin/                       # satoshi/BTC amounts and Bech32 (LNURL/Nostr) codec
├── errors/                        # transport-neutral ApplicationException (prefer context domain exceptions)
├── infrastructure/                # cross-cutting adapters (see Architecture)
│   ├── persistence/jpa/entity/    # JPA entities (@Getter/@Setter, never @Data)
│   ├── persistence/jpa/repository/# Spring Data repositories
│   ├── messaging/                 # Kafka consumers (execution consumers + event publisher), listeners
│   ├── scheduling/                # @Scheduled jobs (reconcile, reconnect, webhooks, sagas, price-cache warmer)
│   ├── web/                       # global exception handler, ErrorResponse, request context
│   ├── grpc/                      # gRPC interceptors for LND calls
│   ├── filter/                    # servlet filters (JWT, CORS, etc.)
│   └── configuration/             # @Configuration classes, bean wiring

src/main/proto/                    # LND .proto files → generated lnrpc, routerrpc, invoicesrpc
src/test/java/com/aratiri/         # mirrors main; AbstractIntegrationTest + *IntegrationTest per context
config/spotbugs/                   # SpotBugs exclude rules
```

## Architecture

Aratiri is a modular monolith with ports-and-adapters (hexagonal) architecture (ADR 0001). Each bounded context lives under `src/main/java/com/aratiri/{context}/`. The table below describes what each module does and how it talks to others. Internal class layout is discovered by reading the code.

| Module | Responsibility | Communicates via |
|--------|---------------|------------------|
| `auth` | Identity, JWT issuance/validation, SSO, token exchange; publishes `email.requested` for register/reset | HTTP → PostgreSQL, Google OAuth (HTTP), trusted issuer JWKs, Kafka (`email.requested`) |
| `accounts` | User accounts, deposit addresses, fiat balance views | HTTP (controllers) → `accounts.application.port.in` |
| `invoices` | Create LND invoices, persist local records, emit webhooks | HTTP → `invoices.application.port.in` → `LightningNodeClientAdapter` (gRPC) |
| `payments` | Idempotent pay commands, payment intent events | HTTP → `payments.application.port.in` → PostgreSQL, then direct Kafka publish after commit |
| `requests` | Shareable single-use payment links; provision/cancel saga intent | HTTP → `requests.application.port.in` → PostgreSQL + Kafka (`payment-request.provision.requested`, `payment-request.cancel.requested`) |
| `transactions` | Read model, settlement processors, ledger entries | Kafka consumers → `transactions.application.port.in` |
| `decoder` | Unified BOLT11/LNURL/on-chain/alias/NIP-05 resolution | HTTP, external HTTP (NIP-05, LNURL), Nostr relay (WebSocket) |
| `lnurl` | LNURL-pay callback and execution | HTTP (public + authenticated) |
| `webhooks` | Event → signed delivery with retry | `webhook.event.created` Kafka events → `webhooks.application` → HTTP outbound |
| `admin` | LND node ops, channels, peers, wallet, settings, stats | HTTP → `admin.application.port.in` → `LightningNodeClientAdapter` (gRPC) |
| `bitcoin` | Satoshi/BTC amount helpers and Bech32 codec | Used by accounts, decoder, lnurl protocol paths |
| `errors` | Transport-neutral `ApplicationException` | Thrown by application/adapters; mapped in `infrastructure.web` |
| `infrastructure` | Cross-cutting adapters: JPA repos, Kafka, LND gRPC, scheduling, web | Implements `application.port.out` interfaces for all modules |

**Request-to-settlement flow** (see README for diagrams):

1. **HTTP controllers** (thin `*API` classes) validate auth and call `application.port.in` interfaces
2. **Application services** write intent to PostgreSQL in one transaction and publish domain events directly to Kafka via `EventPublisher` (afterCommit callback when a transaction is active), then return 202
3. **Execution consumers** (`LightningPaymentExecutionConsumer`, `OnChainSendExecutionConsumer` in `infrastructure/messaging/consumer/`) run LND payment side effects with `@RetryableTopic` backoff; other Kafka consumers handle settlements, webhook event creation (`WebhookEventConsumer`), payment-request saga provisioning/cancellation (`PaymentRequestProvisionConsumer`, `PaymentRequestCancelConsumer`), and email (`EmailConsumer`)
4. **LND listeners** (`LightningListener`, `OnChainTransactionListener`) stream invoice/chain events back into application services
5. **Webhook jobs** send signed HMAC-SHA256 callbacks independently of core payment flow; `PaymentRequestSagaJob` is the lease-safe backstop for the payment-request saga (lost-event repair, terminal-state authority)

**Reliability**: events are published after commit; a failed Kafka send is logged (event lost) and repaired by consumer-side idempotency plus `TransactionReconciliationJob`. Execution consumers are idempotent via the transaction row; exhausted retries dead-letter and emit `node_operation.unknown_outcome` webhooks. Webhook event creation and email are at-most-once (exhausted records dead-letter and are logged; `webhook_events` dedupes redelivery on `event_key`). Webhook delivery keeps its own retry logic. `BtcPriceCacheWarmer` warm-refreshes the price caches on a fixed delay (10s default) so request threads rarely pay external latency.

**PostgreSQL** is the source of truth. `account_entries` is append-only (ADR 0002). Mutations go through repository adapters implementing `application.port.out` interfaces.

## Conventions

**Package layout per bounded context:**
```
{context}/
├── {Context}API.java                  # @RestController, thin adapter
├── application/
│   ├── port/in/{UseCase}.java         # incoming port interface
│   ├── port/out/{Repository}.java     # outgoing port interface for infrastructure
│   └── service/{UseCase}Service.java  # use case implementation
├── domain/
│   ├── {Entity}.java, {ValueObject}.java, {Enum}.java
│   └── exception/
└── infrastructure/
    └── {Adapter}.java                 # implements port.out (JPA, gRPC, HTTP clients)
```

- JPA entities use `@Getter` and `@Setter`, never `@Data`. Fields `id`, `createdAt`, `updatedAt` must have no setter.
- Use `@Builder` on entities for test construction only.
- Lombok is `compileOnly` with `annotationProcessor`.
- Tests mirror the main package structure. Integration tests extend `AbstractIntegrationTest`.
- Use `2-space` indentation for `.java` files.
- Import order: Java standard library → third-party → `com.aratiri`. No strict enforcement beyond `spotless` which is not configured; follow surrounding convention.
- Error handling: prefer context-owned exceptions under `{context}/domain/exception/`; `errors.ApplicationException` is the transport-neutral catch-all (optional HTTP status hint) mapped by `infrastructure/web/GlobalExceptionHandler`. HTTP `ErrorResponse` lives in `infrastructure/web/`.
- Configuration: `application.properties` or `application.yml` under `src/main/resources/`; environment-specific overrides via env vars (`SPRING_*`, `ARATIRI_*`).

## Repository Rules

- Never commit `.env` files or files containing secrets (macaroons, TLS certs, passwords, API keys).
- Always run `./gradlew clean build` before pushing. This is the same command CI runs.
- Never use `@Data` on JPA entities; use `@Getter`/`@Setter`.
- All database schema changes must have a corresponding Flyway migration under `src/main/resources/db/migration/`.
- Generated protobuf classes (`lnrpc`, `routerrpc`, `invoicesrpc`) must never be committed because the `protobuf` Gradle plugin generates them.
- Do not call LND gRPC directly from HTTP request threads; use the Kafka execution-consumer path (`PaymentExecutionService`).
- New domain events must have a deterministic `event_key` for idempotent consumption.
- Payment commands must be idempotent by user + `Idempotency-Key`.
- Branch from `master`, target PRs to `master`.
