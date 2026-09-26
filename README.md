# BGStore

BGStore is a board-game store operations platform for table reservations, client check-in, in-store play sessions, game-copy tracking, pricing, and auditable client history. This repository is a production-shaped walking skeleton: authentication, browser-to-database connectivity, deployment, observability, and quality gates are wired before feature workflows are added.

## Architecture

- **Web:** Vue 3, TypeScript, Vite, Pinia, TanStack Query, Tailwind CSS, Vue I18n
- **API:** Java 21, Spring Boot, Spring Modulith, Spring Security BFF, Spring Data JPA (Hibernate)
- **Data:** PostgreSQL; Valkey/Redis protocol for shared sessions
- **Identity:** Keycloak over OIDC; the browser receives only a secure HTTP-only application session
- **Monorepo:** pnpm and Nx with the official Gradle integration; Gradle remains the Java build authority
- **Platform:** OCI containers, Helm, Argo CD, Gateway API, CloudNativePG, cert-manager
- **Telemetry:** OpenTelemetry, Prometheus, Loki, Tempo, Grafana

See the [development guide](docs/development-guide.md) for repository navigation and change routing, plus the [architecture](docs/architecture.md), [domain model](docs/domain-model.md), and [architecture decisions](docs/decisions/README.md).

## Start locally

Prerequisites are Docker, mise, and Corepack. The pinned toolchain is Node 24, pnpm 10, and Java 21.

```bash
mise install
corepack enable
pnpm install
cp .env.example .env
pnpm dev:infra
pnpm dev:api
```

In a second terminal:

```bash
pnpm dev:web
```

The development web server runs natively with Vite HMR, and the API runs
natively with Spring Boot DevTools. Keep both application commands running in
separate terminals while Docker provides only PostgreSQL, Redis, Keycloak, and
Mailpit. Stop those infrastructure containers with `pnpm dev:infra:stop` when
you are done.

Open <http://localhost:4200>. The seeded development accounts are:

| Role    | Username               | Password             |
| ------- | ---------------------- | -------------------- |
| Client  | `client@example.test`  | `ClientLocalOnly9!`  |
| Staff   | `staff@example.test`   | `StaffLocalOnly9!`   |
| Manager | `manager@example.test` | `ManagerLocalOnly9!` |

These credentials are local-only and must never be reused outside development.

To exercise the containerized walking skeleton and observability stack instead:

```bash
docker compose --profile app up --build --wait
```

Grafana is at <http://localhost:3000>, Prometheus at <http://localhost:9090>, Keycloak at <http://localhost:8081>, and Mailpit at <http://localhost:8025>. Grafana loads the provisioned `BGStore / Observability Overview` dashboard automatically. It includes API health, traffic, latency, errors, JVM/database health, Collector throughput, Loki logs, and Tempo traces.

The API emits telemetry only when it can reach the configured OTLP endpoint. To generate data locally, start the stack, open the web app, sign in with one of the test accounts, and exercise a few pages or API calls. You can also inspect the raw signals directly in Grafana Explore: Prometheus for metrics, Loki for logs, and Tempo for traces. The API's Prometheus scrape endpoint is <http://localhost:8080/actuator/prometheus>.

## Development commands

```bash
pnpm check              # all lint, type, test, coverage, contract, and Gradle checks
pnpm build              # production builds
pnpm e2e                # fast browser contract smoke test
pnpm format             # format supported source files
pnpm graph              # inspect the Nx project graph
pnpm nx run @mudst-2026-bgstore/contracts:generate
```

The OpenAPI document at `packages/contracts/openapi.yaml` is authoritative. Regenerate both sides after changing it; generated sources are not hand-edited.

## Testing

Web unit tests are Vitest, browser E2E is Playwright (`apps/web-e2e`), and API tests are JUnit 5 with Testcontainers. The `.agents/bin/verify <scope>` wrapper (`web`, `api`, `contract`, `deploy`, `e2e`, `affected`, `full`) runs the same commands CI runs and logs to a cached file.

Web unit tests:

```bash
pnpm nx test @mudst-2026-bgstore/web                       # full suite with coverage
cd apps/web && pnpm exec vitest run                        # direct runner, lists every file
pnpm nx test @mudst-2026-bgstore/web --run src/i18n-parity.spec.ts --coverage=false  # one file
```

Browser E2E runs in two modes. By default Playwright starts the built SPA preview on port 4300 and stubs the API in-browser, so it needs no backend; tests that require the real stack skip automatically:

```bash
pnpm e2e
```

Full-stack mode runs the same suite against the production-shaped Compose stack with real Keycloak, BFF, PostgreSQL, Redis, and the payment gateway:

```bash
docker compose --env-file .env.example --profile app up --build --wait --wait-timeout 300
BASE_URL=http://localhost:4200 BGSTORE_FULL_STACK=1 \
  pnpm exec playwright test --config=apps/web-e2e/playwright.config.mts
docker compose --env-file .env.example --profile app down --volumes
```

Reset the stack between full-stack runs: completed bookings hold their table slots, so a dirty database exhausts the tables that fit the reservation flow. When something else already owns host port 8081 (the browser-facing Keycloak port), remap it by setting both `KEYCLOAK_HOST_PORT` and `KEYCLOAK_PUBLIC_URL`, for example to `18081` and `http://localhost:18081`.

API tests need a working Docker daemon for Testcontainers:

```bash
apps/api/gradlew -p apps/api test                                                   # all
apps/api/gradlew -p apps/api test \
  --tests 'com.chanakanlabs.bgstore.reservations.ReservationApiIntegrationTest'      # one class
```

Do not run a direct Gradle test command while `.agents/bin/verify` is in progress; the wrapper stops the Gradle daemon when it exits and will kill the concurrent run.

## Delivery

CI runs unit, integration, architecture, browser, formatting, security, contract, Compose, and Helm checks. Conventional commits drive Release Please. Published releases produce multi-architecture images with SBOM and provenance, sign them keylessly with Cosign, and open a GitOps promotion PR containing immutable image digests.

Deployment defaults use `bgstore.chanakanlabs.com` and `auth.bgstore.chanakanlabs.com`. Complete the provider-specific DNS and secret-store steps in the [deployment runbook](docs/runbooks/deployment.md).

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md). This project is licensed under [Apache-2.0](LICENSE).
