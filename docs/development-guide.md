# Development guide

This is the orientation guide for contributors who are new to BGStore. Start here, then follow the repository boundary that owns the change.

## First 30 minutes

1. Read the [architecture](architecture.md), [domain model](domain-model.md), and [ADRs](decisions/README.md).
2. Install the pinned toolchain with `mise install`, enable Corepack, and run `pnpm install`.
3. Copy `.env.example` to `.env` and start the local dependencies with `docker compose up -d postgres redis keycloak mailpit`.
4. Run `pnpm dev:api` and `pnpm dev:web` in separate terminals.
5. Open `http://localhost:4200`, then run `pnpm check` and `pnpm e2e` before making changes.

The containerized path is useful when checking production boundaries: `docker compose --profile app up --build --wait`. Local Keycloak accounts are test fixtures only; their credentials must never be reused elsewhere.

## Where things live

| If you are changing…                    | Start here                                         | Then inspect                                                 |
| --------------------------------------- | -------------------------------------------------- | ------------------------------------------------------------ |
| A screen, route, or browser interaction | `apps/web/src/app/App.vue`                         | `apps/web/src`, `apps/web/vite.config.mts`                   |
| API data fetching in the web app        | `apps/web/src/queries`                             | `apps/web/src/generated/api` (generated; do not hand-edit)   |
| An HTTP endpoint or response shape      | `packages/contracts/openapi.yaml`                  | `apps/api` and the generated web client                      |
| Domain behavior                         | `apps/api/src/main/java/com/chanakanlabs/bgstore`  | The owning module package and its tests                      |
| Database tables or indexes              | `apps/api/src/main/resources/db/migration`         | jOOQ configuration in `apps/api/build.gradle.kts`            |
| Authentication, sessions, or CSRF       | `apps/api/.../security`                            | `docs/decisions/0002-bff-authentication.md`                  |
| Application role authorization          | `apps/api/.../identity/AccessPolicy`               | `docs/decisions/0005-application-authorization-policy.md`    |
| Unit/integration/architecture tests     | `apps/api/src/test` or `apps/web/src/**/*.spec.ts` | `apps/web-e2e/src` for browser behavior                      |
| Local services or seeded identities     | `compose.yaml`                                     | `infra/local`                                                |
| Application Kubernetes resources        | `deploy/charts/bgstore`                            | `deploy/environments`                                        |
| Cluster platform or GitOps wiring       | `deploy/argocd`                                    | `deploy/platform` and `docs/runbooks/deployment.md`          |
| Metrics, logs, or traces                | `infra/observability`                              | `apps/api` management and OTLP settings                      |
| A cross-cutting design decision         | `docs/decisions`                                   | Add an ADR before implementation when the choice is material |
| CI, releases, or security automation    | `.github/workflows`                                | `CONTRIBUTING.md` and `SECURITY.md`                          |

## Build and test commands

| Purpose                         | Command                                        |
| ------------------------------- | ---------------------------------------------- |
| All quality checks              | `pnpm check`                                   |
| Production builds               | `pnpm build`                                   |
| Frontend development            | `pnpm dev:web`                                 |
| Backend development             | `pnpm dev:api`                                 |
| Browser tests                   | `pnpm e2e`                                     |
| Formatting                      | `pnpm format`                                  |
| Project graph                   | `pnpm graph`                                   |
| API unit/integration tests only | `apps/api/gradlew -p apps/api test`            |
| API contract validation         | `apps/api/gradlew -p apps/api openApiValidate` |
| Frontend component tests        | `pnpm nx test @mudst-2026-bgstore/web`         |

The backend test suite uses Testcontainers for real PostgreSQL and Redis-compatible session storage. If using Lima locally, see the [local development runbook](runbooks/local-development.md).

## Change workflow

1. Confirm the terminology and invariant in `docs/domain-model.md`.
2. Change the owning boundary, keeping controllers thin and domain behavior inside a Spring Modulith module.
3. Add the narrowest test first; add an integration or browser test when the behavior crosses a process boundary.
4. If the HTTP shape changes, edit `packages/contracts/openapi.yaml`, regenerate clients/server code, and review the generated diff.
5. Add an append-only Flyway migration for schema changes. Never edit a migration that has reached a shared environment.
6. Run `pnpm check`, `pnpm e2e`, and the relevant deployment rendering checks.
7. Use a Conventional Commit and update an ADR or runbook when the operational or architectural behavior changes.

## Authorization policy

Use the identity module's `AccessPolicy` at the start of a domain service command:

- `requireStaffOrManager()` for operational behavior, including inventory,
  reservations, visits, and play sessions.
- `requireManager()` for administrative policies and staff permissions.

Do not enforce these policies only in a controller or Vue route guard. Vue can
use `/me.roles` to present navigation, while the backend remains authoritative.
Document the resulting `403` response in the OpenAPI contract and test a
denied client request alongside the allowed staff or manager path.

## Generated and derived files

- `apps/web/src/generated/api` is generated from the OpenAPI contract. Do not hand-edit it.
- `apps/api/build/generated` and application build output are disposable and ignored.
- `pnpm-lock.yaml` changes only when dependency manifests change; review dependency updates carefully.
- Helm output is rendered from `deploy/charts/bgstore` plus one environment values file; do not copy rendered YAML into the repository.

## Common troubleshooting

- A 401 from the web app is expected until you sign in through the BFF; browser JavaScript never receives OIDC tokens.
- If the API cannot reach Keycloak, compare `KEYCLOAK_PUBLIC_URL` (browser) with `KEYCLOAK_INTERNAL_URL` (BFF) in `.env`.
- If a Testcontainers test cannot start, start Docker and apply the Lima settings in the local runbook.
- If Nx reports a stale graph, run `pnpm nx reset` and retry; this only clears derived Nx state.
- If a generated client diff appears unexpectedly, validate the OpenAPI document before regenerating.

For deployment-specific questions, use the [deployment runbook](runbooks/deployment.md). For security reports, use [SECURITY.md](../SECURITY.md).
