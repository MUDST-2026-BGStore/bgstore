# BGStore agent guide

Read [the development guide](docs/development-guide.md) when orienting to the repository and [the domain model](docs/domain-model.md) before changing business behavior. Read the relevant ADR under `docs/decisions` for authentication, module boundaries, architecture, or delivery changes.

## Architecture and boundaries

- pnpm and Nx coordinate the monorepo; Gradle, Vite/Vitest, Playwright, Helm, and Docker Compose remain authoritative within their ecosystems.
- `apps/api`: Java 21/Spring Boot BFF and Spring Modulith modular monolith. OIDC tokens remain server-side; the browser uses the application session.
- `apps/web`: Vue 3/Vite SPA and generated OpenAPI client. `apps/web-e2e` owns Playwright browser coverage.
- `packages/contracts/openapi.yaml`: HTTP contract source of truth. Change it before API implementations or consumers.
- `deploy`: Helm application chart, environment values, Argo CD, and platform manifests. `infra`: local dependencies, Keycloak assets, and observability configuration.
- Preserve English/Thai support, Bangkok/THB assumptions, and the in-store-only session scope unless requirements explicitly change.

More specific instructions live in `apps/api/AGENTS.md`, `apps/web/AGENTS.md`, `deploy/AGENTS.md`, and `infra/AGENTS.md`; they apply only while working in those trees.

## Generated and persistent data

- Generate `apps/web/src/generated/api` with `pnpm nx run contracts:generate`; never hand-edit it.
- Gradle creates disposable OpenAPI and jOOQ sources under `apps/api/build/generated`.
- Add append-only Flyway migrations under `apps/api/src/main/resources/db/migration`; do not rewrite a migration used by a shared environment.
- Change `pnpm-lock.yaml` only with dependency manifests. Do not commit build output, reports, local `.env*`, rendered Helm YAML, or caches.

## Verification

Use `.agents/bin/verify <scope>` for concise output and a full cached log. Available scopes are `web`, `api`, `contract`, `deploy`, `e2e`, `affected`, and `full`.

- One web test: `pnpm nx test @mudst-2026-bgstore/web --run src/path/file.spec.ts`
- One API test/class: `apps/api/gradlew -p apps/api test --tests 'fully.qualified.TestName'`
- Contract change: `.agents/bin/verify contract`, regenerate the web client, and inspect its diff.
- Deployment/Compose change: `.agents/bin/verify deploy`.
- Changed projects during iteration: `.agents/bin/verify affected`.
- Handoff gate: `.agents/bin/verify full` (format, checks, builds, mock-backed browser tests, Helm lint/render for every environment, and Compose rendering).

API checks use Testcontainers. In an Amp orb, start the declared Docker daemon explicitly with `amp orb services ensure`; elsewhere, provide a working Docker daemon and follow `docs/runbooks/local-development.md` for Lima. Run the production-shaped full-stack browser path from `.github/workflows/ci.yml` when authentication, proxies, containers, or cross-process behavior changes.

Use Prettier for supported web/docs/config files and Gradle Spotless for Java/Kotlin Gradle files. Do not bypass configured Git hooks; use Conventional Commits.
