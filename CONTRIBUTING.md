# Contributing to Aratiri

## Prerequisites

- JDK 25 (auto-downloaded by Gradle via Foojay toolchain resolver)
- Docker and Docker Compose (for PostgreSQL, Kafka, and LND dependencies)
- Git

## Local Setup

```bash
git clone https://github.com/DiegoYegros/aratiri.git
cd aratiri
cp .env.example .env
# Edit .env with your configuration
docker compose up -d
```

Aratiri depends on PostgreSQL, Kafka, and an LND node. Docker Compose profiles are available for local development.

## Running Tests

```bash
./gradlew clean build
```

This runs:
- Compilation and unit tests (JUnit 5)
- SonarLint static analysis
- JaCoCo test coverage reports
- SpotBugs bug pattern detection
- Protobuf class verification in boot jar
- Flyway migration validation

Integration tests use Testcontainers to spin up PostgreSQL and Kafka containers automatically. No external database is needed for `./gradlew test`.

## Code Conventions

Aratiri follows a ports-and-adapters architecture. Each bounded context lives under `src/main/java/com/aratiri/{context}/`:

| Package | Purpose |
| --- | --- |
| `application/port/in` | Incoming use case interfaces |
| `application/port/out` | Outgoing infrastructure interfaces |
| `application/service` | Use case implementations |
| `api` | HTTP controller adapters |
| `domain` | Domain objects and enums |

Infrastructure is under `src/main/java/com/aratiri/infrastructure/`:
- `persistence/jpa/entity` — JPA entities (use `@Getter`/`@Setter`, never `@Data`)
- `persistence/jpa/repository` — Spring Data repositories
- `messaging` — Kafka consumers, producers, and outbox
- `nodeoperations` — LND side-effect workers
- `scheduling` — Scheduled jobs
- `web` — Global exception handler and request context
- `grpc` — gRPC interceptors

JPA entities use Lombok `@Getter` and `@Setter` — not `@Data`. Fields like `id`, `createdAt`, and `updatedAt` should be immutable (no setter). Use `@Builder` for test construction.

## Pull Request Process

1. Create a branch from `master`
2. Make changes, ensuring `./gradlew clean build` passes locally
3. Push your branch and open a pull request targeting `master`
4. CI runs tests, lint, and coverage checks on the PR
5. Request review and address feedback
6. Merge after approval

## Release Process

Aratiri uses [axion-release](https://github.com/allegro/axion-release-plugin) for semantic versioning:

1. Version is derived from git tags with `v` prefix (e.g., `v1.2.3`)
2. Tag a commit: `git tag v1.2.3 && git push --tags`
3. CI builds the tagged version, pushes a Docker image to Docker Hub, and deploys to the production server via SSH over Tailscale
