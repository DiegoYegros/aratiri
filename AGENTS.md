# Aratiri

A Spring Boot 4.0 service that gives a custodial platform a Bitcoin Lightning and on-chain API surface without Aratiri holding custody — the embedding platform owns users and balances, Aratiri owns the LND bridge.

## Setup

- **Java 25** — Gradle toolchain auto-downloads it via Foojay resolver
- **Docker** — for PostgreSQL, Kafka, and Testcontainers-backed integration tests
- **LND node** — reachable over gRPC with admin macaroon

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
├── transactions/                  # append-only tx read model, lifecycle events, settlement
├── webhooks/                      # event construction, endpoint subscription, delivery retry
├── infrastructure/                # shared adapters (see Architecture)
│   ├── persistence/jpa/entity/    # JPA entities (@Getter/@Setter, never @Data)
│   ├── persistence/jpa/repository/# Spring Data repositories
│   ├── messaging/                 # Kafka consumers, producers, outbox job
│   ├── nodeoperations/            # LND payment/side-effect workers
│   ├── scheduling/                # @Scheduled jobs (retry, reconcile, reconnect)
│   ├── web/                       # global exception handler, request context
│   ├── grpc/                      # gRPC interceptors for LND calls
│   ├── filter/                    # servlet filters (JWT, CORS, etc.)
│   └── configuration/             # @Configuration classes, bean wiring
├── shared/                        # constants, exception classes, utility functions

src/main/proto/                    # LND .proto files → generated lnrpc, routerrpc, invoicesrpc
src/test/java/com/aratiri/         # mirrors main; AbstractIntegrationTest + *IntegrationTest per context
config/spotbugs/                   # SpotBugs exclude rules
```

## Architecture

Aratiri is a modular monolith with ports-and-adapters (hexagonal) architecture (ADR 0001). Each bounded context lives under `src/main/java/com/aratiri/{context}/`. The table below describes what each module does and how it talks to others — internal class layout is discovered by reading the code.

| Module | Responsibility | Communicates via |
|--------|---------------|------------------|
| `auth` | Identity, JWT issuance/validation, SSO, token exchange | HTTP → PostgreSQL, Google OAuth (HTTP), trusted issuer JWKs |
| `accounts` | User accounts, deposit addresses, fiat balance views | HTTP (controllers) → `accounts.application.port.in` |
| `invoices` | Create LND invoices, persist local records, emit webhooks | HTTP → `invoices.application.port.in` → `LightningNodeClientAdapter` (gRPC) |
| `payments` | Idempotent pay commands, outbox events | HTTP → `payments.application.port.in` → PostgreSQL + outbox → Kafka |
| `transactions` | Read model, settlement processors, ledger entries | Kafka consumers → `transactions.application.port.in` |
| `decoder` | Unified BOLT11/LNURL/on-chain/alias/NIP-05 resolution | HTTP, external HTTP (NIP-05, LNURL), Nostr relay (WebSocket) |
| `lnurl` | LNURL-pay callback and execution | HTTP (public + authenticated) |
| `webhooks` | Event → signed delivery with retry | Kafka events → `webhooks.application` → HTTP outbound |
| `admin` | LND node ops, channels, peers, wallet, settings, stats | HTTP → `admin.application.port.in` → `LightningNodeClientAdapter` (gRPC) |
| `infrastructure` | Shared adapters: JPA repos, Kafka, LND gRPC, scheduling | Implements `application.port.out` interfaces for all modules |

**Request-to-settlement flow** (see README for diagrams):

1. **HTTP controllers** (thin `*API` classes) validate auth and call `application.port.in` interfaces
2. **Application services** write intent + outbox events to PostgreSQL in one transaction, then return 202
3. **OutboxEventJob** (scheduled) publishes unprocessed outbox rows to Kafka
4. **Kafka consumers** (in `infrastructure/messaging/`) convert events to work items (node operations, settlements, webhooks)
5. **NodeOperationJob** (scheduled) claims and executes LND gRPC side effects with retries and leases
6. **LND listeners** (`LightningListener`, `OnChainTransactionListener`) stream invoice/chain events back into application services
7. **Webhook jobs** send signed HMAC-SHA256 callbacks independently of core payment flow

**Three retry boundaries** (ADRs 0003, 0005): outbox→Kafka, node operations, webhook deliveries — each independent with own backoff/retry logic. `TransactionReconciliationJob` provides a repair path.

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

- JPA entities use `@Getter` and `@Setter` — **never `@Data`**. Fields `id`, `createdAt`, `updatedAt` must have no setter.
- Use `@Builder` on entities for test construction only.
- Lombok is `compileOnly` with `annotationProcessor`.
- Tests mirror the main package structure. Integration tests extend `AbstractIntegrationTest`.
- Use `2-space` indentation for `.java` files.
- Import order: Java standard library → third-party → `com.aratiri` — no strict enforcement beyond `spotless` which is not configured; follow surrounding convention.
- Error handling: domain exceptions from `shared/exception/`, caught by the global exception handler in `infrastructure/web/`.
- Configuration: `application.properties` or `application.yml` under `src/main/resources/`; environment-specific overrides via env vars (`SPRING_*`, `ARATIRI_*`).

## Repository Rules

- Never commit `.env` files or files containing secrets (macaroons, TLS certs, passwords, API keys).
- Always run `./gradlew clean build` before pushing — this is the same command CI runs.
- Never use `@Data` on JPA entities; use `@Getter`/`@Setter`.
- All database schema changes must have a corresponding Flyway migration under `src/main/resources/db/migration/`.
- Generated protobuf classes (`lnrpc`, `routerrpc`, `invoicesrpc`) must never be committed — they are generated by `protobuf` Gradle plugin.
- Do not call LND gRPC directly from HTTP request threads; use the outbox → Kafka → node_operations worker path.
- New domain events must have a deterministic `event_key` for idempotent consumption.
- Payment commands must be idempotent by user + `Idempotency-Key`.
- Branch from `master`, target PRs to `master`.
