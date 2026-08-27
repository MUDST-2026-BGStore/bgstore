# ADR-0002: Backend-for-frontend authentication

Status: Accepted — 2026-08-25

## Decision

Use Keycloak as the initial OIDC provider and Spring Boot as a backend-for-frontend OAuth client. Store tokens server-side and issue only an HTTP-only application session cookie to the browser.

## Rationale

The domain needs staff and client roles, self-registration, and a future migration path. OIDC isolates identity-provider details behind Spring Security. The BFF limits token exposure in browser JavaScript and enables same-origin CSRF and session controls.

## Consequences

The API and web tier must share a public origin. A shared session store is required when the API scales horizontally. Provider-specific claims are translated at the security adapter boundary so Keycloak can later be replaced by Ory, a managed provider, or another conforming system.
