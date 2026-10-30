<!-- nx configuration start-->
<!-- Leave the start & end comments to automatically receive updates. -->

# General Guidelines for working with Nx

- For navigating/exploring the workspace, invoke the `nx-workspace` skill first - it has patterns for querying projects, targets, and dependencies
- When running tasks (for example build, lint, test, e2e, etc.), always prefer running the task through `nx` (i.e. `nx run`, `nx run-many`, `nx affected`) instead of using the underlying tooling directly
- Prefix nx commands with the workspace's package manager (e.g., `pnpm nx build`, `npm exec nx test`) - avoids using globally installed CLI
- You have access to the Nx MCP server and its tools, use them to help the user
- For Nx plugin best practices, check `node_modules/@nx/<plugin>/PLUGIN.md`. Not all plugins have this file - proceed without it if unavailable.
- NEVER guess CLI flags - always check nx_docs or `--help` first when unsure

## Scaffolding & Generators

- For scaffolding tasks (creating apps, libs, project structure, setup), ALWAYS invoke the `nx-generate` skill FIRST before exploring or calling MCP tools

## When to use nx_docs

- USE for: advanced config options, unfamiliar flags, migration guides, plugin configuration, edge cases
- DON'T USE for: basic generator syntax (`nx g @nx/react:app`), standard commands, things you already know
- The `nx-generate` skill handles generator discovery internally - don't call nx_docs just to look up generator syntax

<!-- nx configuration end-->

# Repository guidance

- Scaffold services inside existing backend modules with `pnpm nx g @theparlor/backend-tools:backend-service --module=<module> --name=<service> --dry-run --no-interactive` before writing repetitive boilerplate. Use `--interfaceName=<Name>` for an optional public module interface and `--write` for a transactional command; omit `--dry-run` after reviewing the output. Business rules, OpenAPI contracts and migrations still require deliberate implementation.
- Do not create documentation files, audits, reports or ADRs unless the user explicitly asks. Keep necessary explanations next to the code and maintain this file only for durable agent instructions.
- The Parlor is one business with multiple branches. Administrators manage business-wide settings and assignments; managers operate across branches; staff access requires explicit branch assignments. Do not treat branches as tenants.
- Inspect the implementation before editing. Preserve existing API behavior, database semantics and Spring Modulith boundaries. Treat unfinished controllers and workflow scaffolds as unfinished; do not imply that checkout, payment or booking flows work merely because their contracts exist.
- OpenAPI sources live in `openapi/`; edit those and regenerate bindings with `pnpm generate:api`. Do not hand-edit generated Java or TypeScript. Shared policy constants belong in `shared`; domain-specific limits stay with their domain. Keep API constraints aligned when changing them.
- Add new Flyway migrations for schema changes; never rewrite applied migrations. JPA annotations do not migrate deployed databases. Run the relevant tests and module verification for backend changes; Docker is needed for integration tests.
- Keep the backend's scalar-FK module boundaries and catalog aggregate ownership. Do not expose module internals or bypass domain mutation methods to simplify a refactor.
- Use `make help` for local workflows and Nx for project tasks. `make configure` creates local `.env` values; never commit secrets. `make seed-dev` replaces development business data while retaining accounts; unverified prices, editions and designer credits must stay null.
- Frontend-specific product and design rules live in `apps/web/AGENTS.md`.
