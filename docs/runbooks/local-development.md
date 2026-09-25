# Local development runbook

## Healthy path

1. Run `mise install`, `corepack enable`, and `pnpm install`.
2. Copy `.env.example` to `.env`.
3. Start only the infrastructure dependencies with `pnpm dev:infra`.
4. Start the API with `pnpm dev:api` and the web app with `pnpm dev:web`.
5. Verify `http://localhost:8080/actuator/health` and sign in from `http://localhost:4200`.

The development API and web app run on the host. Vite serves the web app with
HMR, while Spring Boot DevTools is available for automatic application
restarts when the IDE/compiler updates the API classpath. Docker is used only
for PostgreSQL, Redis, Keycloak, and Mailpit in this workflow. Stop the
infrastructure with `pnpm dev:infra:stop`.

## Observability

For the production-shaped, fully containerized path, start the application and
observability profiles together with:

```bash
docker compose --profile app up --build --wait
```

Open <http://localhost:3000/d/bgstore-overview/bgstore-observability-overview> for the provisioned dashboard. It reads metrics from Prometheus, logs from Loki, and traces from Tempo. The individual UIs are available at <http://localhost:9090>, <http://localhost:3100/ready>, and <http://localhost:3200/ready>.

The API sends OTLP over HTTP to `http://otel-collector:4318` in Compose. Generate traffic by signing in at <http://localhost:4200> and navigating through the application; an idle stack will have little or no application telemetry. The API's direct Prometheus endpoint is <http://localhost:8080/actuator/prometheus>.

Traces and metrics are wired and should appear in Tempo and Prometheus. The Loki panel is provisioned for future log records, but the current API does not yet bridge its ordinary SLF4J/Logback logs into the OpenTelemetry Logs SDK, so Loki may remain empty until a log appender/bridge is added.

For a Kubernetes deployment, Grafana, Prometheus, Loki, Tempo, and the Collector run in the `observability` namespace. The repository wires the API and Collector, but does not publish Grafana publicly. Use a temporary port-forward to inspect it:

```bash
kubectl -n observability port-forward svc/kube-prometheus-stack-grafana 3000:80
```

The Kubernetes Grafana dashboard must be provisioned separately in that Grafana instance; the file-mounted dashboard above is for the local Compose Grafana.

## Common failures

- Testcontainers tests fail closed when Docker is unavailable. Start the Docker daemon before running `pnpm check`.
- With Lima's rootless Docker template, point Testcontainers at the socket with
  `DOCKER_HOST=unix://${HOME}/.lima/docker/sock/docker.sock` and set
  `TESTCONTAINERS_RYUK_DISABLED=true`. The latter is a Lima-only workaround;
  CI keeps Ryuk enabled.
- If OIDC redirects use the wrong host, confirm both public and internal Keycloak URLs in `.env`.
- If the API fails to start with a schema error such as `missing column`, or Flyway reports a schema newer than the available migrations, Nx restored stale compiled output from its local cache (its Gradle source inputs do not match on Windows paths). `pnpm dev:api` skips the Nx cache for this reason; run any other Gradle-backed Nx target with `--skip-nx-cache` or call `apps/api/gradlew` directly.
- If a generated client differs from the contract, run `pnpm nx run contracts:generate` and commit the generated result.
- If local data is disposable, use `docker compose down --volumes`; this permanently removes local database and telemetry volumes.
