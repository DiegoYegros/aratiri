# Configuration Reference

Aratiri is configured through Spring Boot properties, usually supplied as environment variables. `.env.example` is a starting point for local Compose usage, while `src/main/resources/application.yml` is the source of defaults and property names.

## Required Core Settings

| Variable | Purpose |
| --- | --- |
| `SERVER_PORT` | HTTP port. Defaults to `2100`. |
| `ARATIRI_BASE_URL` | Public base URL used for LNURL callback URLs, lightning addresses, and QR payloads. |
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
| `HIKARI_IDLE_TIMEOUT` | `600000` |
| `HIKARI_MAX_LIFETIME` | `1800000` |
| `HIKARI_CONNECTION_TIMEOUT` | `30000` |

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

## Webhooks

Webhook delivery behavior is mostly code-defined:

| Property / Variable | Default | Purpose |
| --- | --- | --- |
| `aratiri.webhooks.delivery.fixed-delay-ms` | `5000` | Delivery worker schedule. |

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
