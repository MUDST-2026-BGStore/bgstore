# The Parlor

Vue and Spring Boot/Spring Modulith in an Nx/pnpm monorepo.
One board game business with multiple branches.

Agent instructions: [AGENTS.md](AGENTS.md). Frontend rules:
[apps/web/AGENTS.md](apps/web/AGENTS.md).

AWS deployment, prerequisites, verification, recovery and shutdown:
[deployment runbook](deploy/aws/README.md).

## Local development

With mise and Docker installed:

```sh
mise install
make configure
make bootstrap
make dev
```

Run `make help` for available workflows. Use `make up` for the containerized stack
and `make down` to stop it. Local credentials are in the ignored `.env`.

## Checks

```sh
pnpm nx run api:test
pnpm nx run api:lint
pnpm test
pnpm typecheck
pnpm lint:contract
```

Backend integration tests require Docker. Edit the API contract in `openapi/` and
run `pnpm generate:api` to regenerate bindings. Project tasks and configuration
live in Nx, `package.json`, the Makefile and each project's build configuration.
