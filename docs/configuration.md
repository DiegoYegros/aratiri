# Configuration Reference

Aratiri is configured through Spring Boot properties, usually supplied as environment variables. `.env.example` is a starting point for local Compose usage, while `src/main/resources/application.yml` is the source of defaults and property names.

## Required Core Settings

| Variable | Purpose |
| --- | --- |
| `SERVER_PORT` | HTTP port. Defaults to `2100`. |
| `ARATIRI_BASE_URL` | Public API base URL used for LNURL callback URLs, lightning addresses, and QR payloads. Not used for payment-request share links. |
| `ARATIRI_FRONTEND_BASE_URL` | Public frontend origin for owner-facing payment-request share URLs (`/pay/{publicId}`). Defaults to `http://localhost:3000`. |
| `ARATIRI_CORS_ALLOWED_ORIGINS` | Comma-separated allowed browser origins. |
| `JWT_SECRET` | HMAC signing secret for locally issued access tokens. Use a strong 256-bit-or-larger secret. |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap address. Use `kafka:29092` inside Compose and `localhost:9092` from the host. |

## Database And Flyway

| Variable | Default | Purpose |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres_db:5432/aratiri_db` | JDBC URL. |
| `SPRING_DATASOURCE_USERNAME` | `aratiri_user` | Database user. |
| `SPRING_DATASOURCE_PASSWORD` | `aratiri_password` | Database password. |
| `SPRING_DATASOURCE_DRIVER` | `org.postgresql.Driver` | JDBC driver class. |
| `SPRING_JPA_DEFAULT_SCHEMA` | `aratiri` | Hibernate default schema. |
| `SPRING_JPA_DDL_AUTO` | `validate` | Hibernate schema action. Prefer `validate`; Flyway owns migrations. |
| `SPRING_JPA_DATABASE_PLATFORM` | `org.hibernate.dialect.PostgreSQLDialect` | Hibernate dialect. |
| `SPRING_FLYWAY_SCHEMAS` | `aratiri` | Flyway schema list. |
| `SPRING_FLYWAY_DEFAULT_SCHEMA` | `aratiri` | Flyway default schema. |

Connection pool tuning:

| Variable | Default |
| --- | --- |
| `HIKARI_MAXIMUM_POOL_SIZE` | `20` |
| `HIKARI_MINIMUM_IDLE` | `2` |
| `HIKARI_IDLE_TIMEOUT` | `600000` |
| `HIKARI_MAX_LIFETIME` | `1800000` |
| `HIKARI_CONNECTION_TIMEOUT` | `30000` |

On the single-vCPU production box, set `HIKARI_MAXIMUM_POOL_SIZE=10`: ~10 connections is the
Hikari-recommended ceiling for one core; 20 only adds memory pressure. Tomcat request threads
are capped by `TOMCAT_THREADS_MAX` (default `100`, Spring Boot default would be `200`).

## LND And gRPC

| Variable | Purpose |
| --- | --- |
| `GRPC_CLIENT_LND_NAME` | LND gRPC host. |
| `GRPC_CLIENT_LND_PORT` | LND gRPC port, commonly `10009`. |
| `ADMIN_MACAROON_PATH` | Path to the hex-encoded LND admin macaroon file. |
| `LND_TLS_CERT_PATH` | Optional custom LND TLS certificate path. |
| `GRPC_TLS_ACTIVE` | Enables TLS. Defaults to `true`. |

If `LND_TLS_CERT_PATH` is blank and TLS is active, the app uses default transport security. If a path is provided, the file must exist and is used as the gRPC trust manager certificate.

## Authentication

| Variable | Default | Purpose |
| --- | --- | --- |
| `JWT_EXPIRATION` | `8400` | Access token lifetime in seconds. |
| `JWT_REFRESH_EXPIRATION` | `2592000` | Refresh token lifetime in seconds. |
| `GOOGLE_OAUTH_CLIENT_ID` | blank | Google SSO client ID. |
| `EMAIL_USERNAME` | none | SMTP username for verification and password reset emails. |
| `EMAIL_PASSWORD` | none | SMTP password. |
| `ARATIRI_MAIL_HOST` | `smtp.gmail.com` | SMTP server host. Configure if using a different email service. |
| `ARATIRI_MAIL_PORT` | `587` | SMTP server port. |

Trusted issuer/token exchange settings:

| Variable | Default | Purpose |
| --- | --- | --- |
| `ARATIRI_SECURITY_DEFAULT_PRINCIPAL_CLAIM` | `email` | Claim used as principal when issuer-specific config does not override it. |
| `ARATIRI_TOKEN_EXCHANGE_ENABLED` | `false` | Enables `POST /v1/auth/exchange`. |
| `ARATIRI_TOKEN_EXCHANGE_CLIENT_ID` | blank | Basic auth client id for token exchange. |
| `ARATIRI_TOKEN_EXCHANGE_CLIENT_SECRET` | blank | Basic auth client secret for token exchange. |
| `ARATIRI_TRUSTED_ISSUER` | `http://localhost:8000` | Trusted issuer string. |
| `ARATIRI_TRUSTED_ISSUER_JWK_SET_URI` | `http://localhost:8000/jwks.json` | JWKS endpoint. |
| `ARATIRI_TRUSTED_ISSUER_PRINCIPAL_CLAIM` | `email` | Principal claim for this issuer. |
| `ARATIRI_TRUSTED_ISSUER_NAME_CLAIM` | `name` | Display name claim for auto-provisioned users. |
| `ARATIRI_TRUSTED_ISSUER_AUTO_PROVISION` | `true` | Creates missing users from trusted tokens. |
| `ARATIRI_TRUSTED_ISSUER_AUTO_ACCOUNT` | `true` | Creates accounts for auto-provisioned users. |
| `ARATIRI_TRUSTED_ISSUER_PROVIDER` | `EXTERNAL` | Stored auth provider for trusted issuer users. |
| `ARATIRI_TRUSTED_ISSUER_DEFAULT_ROLE` | `USER` | Default role for auto-provisioned users. |

For local trusted issuer testing, see [Trusted Issuer Local Testing](trusted-issuers-local-testing.md).

### Public auth abuse controls

`POST /v1/auth/forgot-password` returns the same `200` for well-formed emails whether the account is absent, local, or federated. A reset token/email is created only for an existing eligible `LOCAL` account. Equalizing status and message does **not** prove perfect timing indistinguishability.

Sensitive public auth routes (`login`, `register`, `verify`, `forgot-password`, `reset-password`, `refresh`, `exchange`, `sso/google`) are additionally guarded by an in-process fixed-window rate limiter:

| Variable / property | Default | Purpose |
| --- | --- | --- |
| `ARATIRI_SECURITY_AUTH_RATE_LIMIT_ENABLED` / `aratiri.security.auth-rate-limit.enabled` | `true` | Enables the servlet filter for sensitive public auth POSTs and public payment-request GETs (`/r/{publicId}`). Disable only when a trusted gateway/WAF already enforces equivalent limits, or for tightly controlled local testing. |
| `ARATIRI_SECURITY_AUTH_RATE_LIMIT_REQUESTS_PER_WINDOW` / `aratiri.security.auth-rate-limit.requests-per-window` | `30` | Max allowed requests per key within one window. Must be `>= 1`. |
| `ARATIRI_SECURITY_AUTH_RATE_LIMIT_WINDOW` / `aratiri.security.auth-rate-limit.window` | `1m` | Fixed window duration (Spring `Duration`). Supported range: **`1ms`–`1d` inclusive**. Sub-millisecond positives (which truncate to 0ms), zero/negative values, durations above 1 day, and values that overflow millisecond conversion fail startup. |
| `ARATIRI_SECURITY_AUTH_RATE_LIMIT_MAXIMUM_KEYS` / `aratiri.security.auth-rate-limit.maximum-keys` | `100000` | Caffeine bound on distinct keys. Must be `>= 1`. |

Operational limitations (honest):

- Counters are **per JVM process**, reset on restart, and are **not** a distributed/global quota across replicas.
- Keys use server-observed `HttpServletRequest#getRemoteAddr()` plus method/path. Raw `X-Forwarded-For` is **not** trusted; meaningful client IPs require a trusted proxy that rewrites the remote address.
- This does **not** replace gateway/WAF rate limits, MFA, account lockout policy, or monitoring/alerting.
- Exceeded requests receive `429` with `Retry-After` and the standard JSON `ErrorResponse` body, without invoking auth controllers.
- The rate-limit servlet filter is ordered after the request `LogFilter` (`@Order(2)`). Spring Security's filter chain defaults to order `-100`, so Security may run first; for public auth routes that are `permitAll`, a 429 still short-circuits before controllers/auth services.

## Payments And Fees

| Variable | Default | Purpose |
| --- | --- | --- |
| `ARATIRI_PAYMENT_DEFAULT_FEE_LIMIT_SAT` | `50` | Default Lightning routing fee limit for LND sends. |
| `ARATIRI_PAYMENT_DEFAULT_TIMEOUT_SECONDS` | `200` | Default Lightning send timeout. |
| `ARATIRI_PAYMENT_LIGHTNING_FEE_FIXED_SAT` | `0` | Fixed platform fee on Lightning debits. |
| `ARATIRI_PAYMENT_LIGHTNING_FEE_PERCENT` | `0` | Percentage platform fee on Lightning debits. |
| `ARATIRI_PAYMENT_ONCHAIN_FEE_FIXED_SAT` | `0` | Fixed platform fee on on-chain debits. |
| `ARATIRI_PAYMENT_ONCHAIN_FEE_PERCENT` | `0` | Percentage platform fee on on-chain debits. |

Payment APIs require an `Idempotency-Key` header for:

- `POST /v1/payments/invoice`
- `POST /v1/payments/onchain`
- `POST /v1/lnurl/pay`

## Node Operations

`node_operations` is the durable worker table for LND side effects.

| Property / Variable | Default | Purpose |
| --- | --- | --- |
| `aratiri.node-operations.fixed-delay-ms` | `1000` | Worker schedule and retry delay. |
| `aratiri.node-operations.batch-size` | `10` | Operations claimed per batch. |
| `aratiri.node-operations.lease-seconds` | `300` | Worker lease duration. |
| `aratiri.node-operations.lightning-max-attempts` | `5` | Max Lightning attempts before failed or unknown outcome handling. |
| `aratiri.node-operations.onchain-max-attempts` | `5` | Max on-chain attempts before unknown outcome handling. |

These are Spring properties. Set them as environment variables with relaxed binding if needed, for example `ARATIRI_NODE_OPERATIONS_BATCH_SIZE`.

## Payment Request Sagas

Shareable payment requests commit durable `PROVISIONING` intent (cryptographically random preimage + SHA-256 payment hash) before any LND `AddInvoice`. A leased worker looks up by payment hash, then adds the invoice only if absent, and finalizes `OPEN`. Cancellation first transitions payable `OPEN`/`PROVISIONING` to `CANCEL_PENDING` (hiding BOLT11), then a leased worker performs lookup/cancel and finalizes `CANCELLED`. `PAID` wins over every other stored status when LND proves settlement. `EXPIRED` is derived only from `OPEN`. Terminal provisioning exhaustion becomes inspectable `FAILED` (same-key replay never silently mints a replacement).

| Property / Variable | Default | Purpose |
| --- | --- | --- |
| `aratiri.payment-requests.saga.fixed-delay-ms` | `1000` | Saga worker schedule. |
| `aratiri.payment-requests.saga.batch-size` | `10` | Rows claimed per batch. |
| `aratiri.payment-requests.saga.lease-seconds` | `300` | Worker lease duration. |
| `aratiri.payment-requests.saga.provision-max-attempts` | `10` | Attempts before `FAILED`. |
| `aratiri.payment-requests.saga.cancel-max-attempts` | `10` | Attempts before exhausted (row remains `CANCEL_PENDING`). |
| `aratiri.payment-requests.saga.backoff-base-ms` | `1000` | Exponential backoff base. |
| `aratiri.payment-requests.saga.backoff-max-ms` | `60000` | Exponential backoff cap. |

Observability:

- Micrometer gauges: `aratiri.payment_requests.provisioning.due|in_progress|failed`, `aratiri.payment_requests.cancellation.due|in_progress|exhausted` (Actuator `/actuator/metrics` / Prometheus).
- Admin: `GET /v1/admin/payment-request-sagas/status`, `/failed`, `/exhausted-cancellations`.

HTTP contract notes:

- Create: `201` when synchronously `OPEN`, `202` + `Location` while `PROVISIONING`, same-key replay `202` while pending / `200` once `OPEN`/terminal, payload conflict `409`.
- Cancel: `202` for `CANCEL_PENDING` (including replay), `200` once `CANCELLED`, `409` for `PAID`/`EXPIRED`/`FAILED`.
- BOLT11 appears only while effective status is `OPEN`. Preimage, lease fields, and diagnostics are never exposed on owner/public DTOs.

## Webhooks

| Property / Variable | Default | Purpose |
| --- | --- | --- |
| `aratiri.webhooks.delivery.fixed-delay-ms` | `5000` | Delivery worker schedule. |
| `aratiri.webhooks.destination.allow-http` | `false` | **Unsafe / lab-only.** When `true`, allows `http://` webhook URLs. Production must keep `false` (HTTPS only). |
| `aratiri.webhooks.destination.allow-private-networks` | `false` | **Unsafe / lab-only.** When `true`, skips rejection of loopback, RFC1918, link-local, CGNAT, ULA, and other special ranges. Production must keep `false`. |
| `aratiri.webhooks.destination.allowed-hosts` | empty | Optional allowlist of exact hosts or `*.suffix` wildcard suffixes (not regex). When non-empty, destinations outside the list are rejected. Compared after host normalization (IDN/punycode, case, trailing dots, optional IPv6 brackets). Malformed entries are ignored per request (fail closed for matching), not at startup. Narrows which names may be chosen; does **not** pin DNS or prevent rebinding of an allowlisted/compromised name. |

Destination policy is enforced when admins create/update endpoints and again immediately before every outbound delivery. Invalid destinations are fail-closed: create/update returns HTTP 400 without persisting; delivery performs no HTTP send and enters the normal failure/retry path.

Default policy permits ordinary public HTTPS webhooks and rejects internal/special destinations (loopback, RFC1918, link-local/cloud metadata, multicast, IPv6 ULA, IPv4-mapped/compatible private/special addresses, NAT64 `64:ff9b::/32`, 6to4 `2002::/16`, Teredo `2001:0::/32`, CGNAT `100.64.0.0/10`, documentation/benchmarking/reserved ranges, userinfo, fragments, zone/scope ids, non-absolute URIs, and ambiguous/alternative IP literals).

**DNS rebinding residual:** Java `HttpClient` re-resolves the hostname at connect time and does not provide a portable way to pin the validated addresses while preserving TLS hostname verification/SNI. Send-time validation reduces the TOCTOU window but does not eliminate rebinding. A non-empty `allowed-hosts` list only restricts which names may be configured; it does not pin DNS and does not stop an allowlisted or compromised name from rebinding between validation and connect.

Delivery requests include:

- `X-Aratiri-Event-Id`
- `X-Aratiri-Event-Type`
- `X-Aratiri-Delivery-Id`
- `X-Aratiri-Timestamp`
- `X-Aratiri-Signature`

The signature is `v1=` plus the HMAC-SHA256 hex digest of `timestamp.eventId.payload` using the endpoint signing secret.

## Currency Data

| Variable | Default | Purpose |
| --- | --- | --- |
| `ARATIRI_ACCOUNTS_FIAT_CURRENCIES` | `usd,pyg,ars,eur` | Supported fiat currencies for account and price views. |
| `ARATIRI_CURRENCY_CONVERSION_API_URL` | CoinGecko simple price URL | Current BTC price provider template. |
| `ARATIRI_CURRENCY_CONVERSION_HISTORY_API_URL` | CoinGecko market chart URL | Historical BTC price provider template. |
| `ARATIRI_CURRENCY_CONVERSION_FALLBACK_API_URL` | jsDelivr currency API URL | Fallback current BTC price provider template. |
| `ARATIRI_CURRENCY_CONVERSION_CACHE_CURRENT_TTL_SECONDS` | `10` | Current price cache TTL. |
| `ARATIRI_CURRENCY_CONVERSION_CACHE_HISTORY_TTL_SECONDS` | `300` | Historical price cache TTL. |

## Nostr And Decoder

| Variable | Default | Purpose |
| --- | --- | --- |
| `NOSTR_ACTIVE` | `true` | Enables Nostr lookup support. |
| `NOSTR_RELAY_URL` | `wss://relay.primal.net` | Relay used for npub profile lookup. |
| `NOSTR_RETRY_MAX` | `5` | Maximum Nostr reconnect retries. |
| `NOSTR_RETRY_INITIAL_DELAY` | `2000` | Initial retry delay in milliseconds. |
| `NOSTR_RETRY_MAX_DELAY` | `300000` | Maximum retry delay in milliseconds. |

When disabled, decoder behavior falls back to no-op Nostr adapters.

## Peer Management

| Property / Variable | Default | Purpose |
| --- | --- | --- |
| `aratiri.peer.management.interval` | `86400000` | Automatic peer management schedule. |
| `aratiri.peer.management.target.count` | `20` | Desired connected peer count. |

Automatic peer management only runs when the persisted `node_settings.auto_manage_peers` value is enabled through the admin API.
