# Contributing

Start with the [development guide](docs/development-guide.md) for the repository map, local workflow, and test commands.

## Workflow

1. Branch from `main` with a short descriptive name.
2. Keep the OpenAPI contract and domain language aligned with the implementation.
3. Add a failing test at the narrowest stable seam, implement the behavior, then refactor.
4. Run `pnpm check` and `pnpm e2e` before opening a pull request.
5. Use a Conventional Commit message and a matching pull-request title, for example `feat(reservations): prevent capacity overlap`.

Pull requests are squash-merged. `main` is protected: review, passing checks, resolved conversations, and linear history are required. Direct pushes and force pushes should be disabled in the GitHub ruleset.

## Commit types

Use `feat`, `fix`, `docs`, `refactor`, `test`, `build`, `ci`, `chore`, `perf`, `style`, or `revert`. Mark breaking changes with `!` and explain them in the footer.

Lefthook formats and lints staged changes, validates commit messages with Commitlint, and runs the complete check suite before push. Hooks improve feedback but CI remains authoritative.

## Design rules

- Keep domain behavior in Spring Modulith modules rather than controllers or persistence adapters.
- Expose cross-module behavior through deliberate module APIs and domain events.
- Treat PostgreSQL and the OpenAPI contract as explicit boundaries.
- Do not expose OIDC tokens to browser JavaScript; authentication terminates at the backend-for-frontend.
- Add dependencies only at the workspace or owning application level, never as undeclared transitive assumptions.
- Record materially new architecture choices as ADRs under `docs/decisions`.

## Database changes

Hibernate owns the schema. Tables come from the `@Entity` classes and `spring.jpa.hibernate.ddl-auto=update` applies them at startup, so a schema change means editing an entity, not writing DDL.

`update` only adds; it never drops, renames, or retypes anything that already exists. A rename or a type change has to be applied to shared environments by hand, and reference rows (the branch directory) are seeded from application code rather than the schema.
