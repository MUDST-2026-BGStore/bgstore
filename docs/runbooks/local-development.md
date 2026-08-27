# Local development runbook

## Healthy path

1. Run `mise install`, `corepack enable`, and `pnpm install`.
2. Copy `.env.example` to `.env`.
3. Start dependencies with `docker compose up -d postgres redis keycloak mailpit`.
4. Start the API with `pnpm dev:api` and the web app with `pnpm dev:web`.
5. Verify `http://localhost:8080/actuator/health` and sign in from `http://localhost:4200`.

## Common failures

- Testcontainers tests fail closed when Docker is unavailable. Start the Docker daemon before running `pnpm check`.
- With Lima's rootless Docker template, point Testcontainers at the socket with
  `DOCKER_HOST=unix://${HOME}/.lima/docker/sock/docker.sock` and set
  `TESTCONTAINERS_RYUK_DISABLED=true`. The latter is a Lima-only workaround;
  CI keeps Ryuk enabled.
- If OIDC redirects use the wrong host, confirm both public and internal Keycloak URLs in `.env`.
- If a generated client differs from the contract, run `pnpm nx run contracts:generate` and commit the generated result.
- If local data is disposable, use `docker compose down --volumes`; this permanently removes local database and telemetry volumes.
