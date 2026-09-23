# API agent guide

This is a Java 21 Spring Boot BFF organized as Spring Modulith modules under `src/main/java/com/chanakanlabs/bgstore`. Treat each top-level package with `package-info.java` as a module boundary; `ArchitectureTest` verifies those dependencies.

- Put business rules and authorization at the start of application/domain service commands. Controllers map the generated HTTP interfaces and stay thin.
- Use `identity.AccessPolicy`: `requireStaffOrManager()` for operations and `requireManager()` for administration. UI visibility is not authorization.
- Keep browser authentication session-based. Changes to login, callback, CSRF, logout, or token handling require `docs/decisions/0002-bff-authentication.md`.
- Contract interfaces/models come from `packages/contracts/openapi.yaml` into `build/generated/openapi`; jOOQ types come from Flyway inputs into `build/generated/jooq`. Edit neither output.
- Schema changes are new Flyway migrations plus the owning persistence mapping. Use Testcontainers integration tests when PostgreSQL, Redis-compatible sessions, Flyway, or module wiring matters.

Fast loop: `apps/api/gradlew -p apps/api test --tests 'fully.qualified.TestName'`. API gate: `.agents/bin/verify api`, which includes tests, coverage thresholds, architecture checks, compilation, and Spotless through Gradle `check`.
