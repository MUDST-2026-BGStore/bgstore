# Agent instructions

These instructions apply to the whole repository. Read [the development guide](docs/development-guide.md) before making changes; it contains the human-facing map and workflow.

## Project shape

- This is a pnpm + Nx monorepo. Nx coordinates tasks; native tools remain authoritative.
- `apps/web` is the Vue 3/Vite frontend.
- `apps/api` is the Java 21/Spring Boot modular monolith built by Gradle.
- `packages/contracts/openapi.yaml` is the HTTP contract source of truth.
- `deploy/charts`, `deploy/environments`, `deploy/argocd`, and `deploy/platform` are the Kubernetes/GitOps boundary.
- `infra/local` and `infra/observability` define local dependencies and telemetry.

## Before editing

1. Inspect the relevant files and existing tests; do not assume a feature is absent until searching with `rg`.
2. Read `docs/domain-model.md` for terminology and invariants.
3. Read the relevant ADR in `docs/decisions` when changing architecture, authentication, module boundaries, or delivery.
4. Keep the requested scope focused. Do not rewrite unrelated user changes or generated output.

## Implementation rules

- Keep domain behavior inside Spring Modulith modules. Controllers and adapters should be thin.
- Keep the backend-for-frontend security model: OIDC tokens stay server-side; browser code uses the secure application session.
- Change `packages/contracts/openapi.yaml` before changing generated clients or contract-generated server code.
- Never hand-edit `apps/web/src/generated/api`; regenerate it from the OpenAPI contract.
- Add append-only Flyway migrations for shared database changes. Never modify an already-applied migration.
- Preserve English/Thai support, Bangkok/THB assumptions, and the current in-store-only session scope unless requirements change.
- Prefer existing dependencies and native project tooling over ad-hoc scripts or monorepo workarounds.
- Keep local Keycloak accounts, passwords, and Compose secrets clearly marked as test/local-only. Never introduce production credentials.

## Verification

Run the narrowest useful checks while iterating, then run the full gates before handoff:

```bash
pnpm check
pnpm build
pnpm e2e
helm lint deploy/charts/bgstore
docker compose --env-file .env.example config --quiet
```

For API changes, also run the relevant Gradle task (for example `apps/api/gradlew -p apps/api test`). Use real Testcontainers integration tests when behavior crosses PostgreSQL or Redis-compatible session storage. With Lima, follow `docs/runbooks/local-development.md` for `DOCKER_HOST` and Ryuk settings.

Do not hide, skip, or weaken a failing test to make a check green. If an environment limitation prevents a check, report the exact command and reason.

## Files and formatting

- Use `apply_patch` for intentional source edits.
- Run Prettier for supported JS/TS/Vue/Markdown/YAML files and Spotless for Java/Gradle files.
- Do not commit `dist`, `build`, `.nx`, test reports, local `.env`, or dependency caches.
- Do not edit lockfiles unless dependency manifests changed.
- Keep documentation links and commands valid after renames.

## Git and handoff

- Use Conventional Commits (`feat`, `fix`, `docs`, `test`, `refactor`, `build`, `ci`, `chore`, etc.).
- Let Lefthook run; do not bypass commit or push hooks with `--no-verify`.
- Before handoff, report changed files, checks run and their results, remaining limitations, and any required provider credentials or deployment decisions.
- Do not create external resources, publish images, alter cluster state, or push code unless the user explicitly requested that action.
