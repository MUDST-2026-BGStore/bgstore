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

## Isolated full-stack browser tests

Run the production-shaped browser suite and collect frontend and API coverage
with:

```bash
.agents/bin/verify full-stack
```

The wrapper uses one uniquely named Compose project per invocation, so its
PostgreSQL and other named volumes are isolated from local development and
removed on exit. It starts only the web/mailpit dependency tree (PostgreSQL,
Redis, Keycloak, API, web, and Mailpit); observability services are not
started. Web and Keycloak ports are selected dynamically and bound only to
`127.0.0.1` (rather than assuming ports 4200/8081 are free).
`.env.example` supplies local defaults; exported environment variables override
them. A disposable realm import allowlists only that run's exact callback and
logout URLs. Override the bounded run duration with
`BGSTORE_FULL_STACK_TIMEOUT_SECONDS` (default 1500 seconds, excluding a
bounded cleanup/report phase).

Playwright output and Compose diagnostics are written under
`apps/web-e2e/test-output/playwright/{report,output}`, frontend source coverage
under `apps/web-e2e/test-output/coverage`, and the API JaCoCo report under
`apps/api/build/reports/jacoco/e2e`. The API is stopped before its execution
data and matching executable JAR are copied for report generation; the
disposable Compose project and volumes are then removed even when the test
command fails.

Append Playwright filters for focused diagnosis, for example
`.agents/bin/verify full-stack --grep='clients cannot check in'`. CI runs the
unfiltered suite. Stateful runs do not retry against partially mutated data;
rerun the verifier to get a fresh database instead.

Frontend coverage merges Chromium default-page execution with zero-count entries
for every emitted JavaScript chunk, including unopened lazy Vue routes. Source
maps report bundled Vue/TypeScript script code; generated clients, dependencies,
tests, styles, and unbundled/tree-shaken code are outside that metric. Normal
production builds do not publish these source maps. API E2E coverage uses the
container's matching class files and remains separate from the unit-test report.
It excludes generated OpenAPI and jOOQ classes, but includes authentication and
security code that the unit-test coverage gate excludes.

The expiry test expires real Redis session hashes while keeping browser cookies,
checks that the API rejects them, then verifies Keycloak SSO renews the BFF
session and returns to the same active-session or checkout route.
Spring Boot 4 binds the namespace through `spring.session.data.redis.namespace`;
the older `spring.session.redis.namespace` property is ignored. Deploying the
correction moves sessions from the default namespace to `bgstore:session`, so
previously signed-in users must sign in again.

Chromium, Firefox, and WebKit run functional checks; fixed-time screenshot
baselines cover Chromium desktop and Pixel 7 layouts. The four visual tests on
Firefox/WebKit are intentionally skipped. Client checkout requests staff
settlement; payment processing is exercised through staff checkout. Zones,
manual walk-in console controls, console search/filtering, and asynchronous
gateway callbacks are not implemented features and are not claimed as covered.

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
