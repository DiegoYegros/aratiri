# Debugging log ingestion

When Grafana Explore shows no results for the Loki data source, narrow the issue by validating each hop of the pipeline: the application, the OpenTelemetry Collector, and Loki. The steps below assume the observability profile from `docker-compose.yml` is running.

## 1. Inspect the Collector logs

The Collector now mirrors every log export to its own stdout using the `debug/logs` exporter. Check whether the Collector receives records:

```bash
docker compose logs -f otel-collector
```

If log batches arrive from any client, you will see JSON payloads with the resource attributes (including `service_name`). If nothing arrives, the problem is on the client side.

## 2. Send a manual test log through OTLP

Use the helper script to emit a log record over OTLP/HTTP to the Collector. This bypasses the application and confirms that the Collector forwards to Loki correctly.

```bash
./observability/scripts/send-test-log.sh
```

The script defaults to `http://localhost:4318/v1/logs`. Pass a different URL if you run it inside the Docker network:

```bash
SERVICE_NAME=my-debugger ./observability/scripts/send-test-log.sh http://otel-collector:4318/v1/logs
```

After sending the log:

- Recheck `docker compose logs -f otel-collector` to see the mirrored record.
- In Grafana Explore, query Loki with `{service_name="my-debugger"}` (or the default `debug-client`).

## 3. Validate Loki independently

If the Collector prints the test log but Loki still shows nothing, query Loki's API directly:

```bash
curl -G "http://localhost:3100/loki/api/v1/query" --data-urlencode 'query={service_name="debug-client"}'
```

A JSON response with the log entry confirms that Loki stored the record. If Loki is empty, inspect its container logs for ingestion errors:

```bash
docker compose logs -f loki
```

Once the end-to-end path works with the manual payload, re-run your Grafana query with the service name emitted by your application (`aratiri-app`). If the app logs still do not appear, focus on its OpenTelemetry Java agent configuration—missing instrumentation or unsupported logging frameworks are common causes.

## 4. Confirm the application-side exporter

The backend now ships logs to the Collector through the `OpenTelemetryAppender`, which only activates when the Spring configuration property `logging.otel.appender.enabled` evaluates to `true`. The Docker Compose stack sets `LOGGING_OTEL_APPENDER_ENABLED=true` for the backend container so the appender starts alongside the Java agent. Container logs from `aratiri-backend` should therefore include the usual console output **and** the Collector should mirror the same entries via the `debug/logs` exporter.

When you run the application outside Docker (for example, with `mvn spring-boot:run`), set the same environment variable if and only if you also attach the OpenTelemetry Java agent. Leaving it at the default `false` keeps Logback on the console-only setup, which avoids `NoClassDefFoundError` failures when the agent-provided classes are absent.
