# Full codebase audit — 2026-09

## Scope and result

This audit covered the merged `main` branch, the web route/component graph, the
OpenAPI contract, Spring Modulith services/controllers, Flyway migrations,
security configuration, local Keycloak realm, deployment manifests, and the
existing automated checks.

The implemented code is now wired around one application shell and the API
enforces the same ownership boundaries as the UI. The repository is not yet a
complete implementation of the supplied BGStore requirements; the gaps below
are product work, not reasons to add mock data or pretend screens are live.

## Changes made in this pass

- Replaced the legacy masthead plus three divergent header components with one
  role-aware `AppNavbar` mounted by `App.vue`.
- Removed dead navigation entries and made the existing routes the single
  source of navigation truth.
- Converted staff-only routes (`/tables`, game create/edit) and client-only
  history routes to explicit role-selected screens with an access-denied state.
- Added a staff profile screen instead of exposing the client profile mutation
  form to staff.
- Redirected unauthenticated protected routes to the BFF login screen.
- Moved reservation identity lookup into the reservation service and enforced a
  client-only policy there; controllers no longer accept a caller-supplied
  subject.
- Added foreign keys from reservations to the identity and table records and a
  cursor-friendly ownership index in append-only migration V12.
- Removed runtime branch writes. Reference branches are owned by Flyway
  migration V3, so application startup is read-only with respect to them.
- Explicitly scoped the session cookie to `/`, which is required for the
  production `__Host-bgstore-session` cookie prefix.

## Requirement coverage

| Area                                    | Current state           | Audit assessment                                                                                            |
| --------------------------------------- | ----------------------- | ----------------------------------------------------------------------------------------------------------- |
| OIDC/BFF session                        | Implemented             | Browser receives the secure application session; API authorization remains server-side.                     |
| Client onboarding phone                 | Implemented             | Incomplete clients are gated by the API filter and can complete the client profile.                         |
| Branch/game browsing                    | Implemented             | Public catalogue reads work for guests and authenticated users.                                             |
| Staff game/table operations             | Implemented             | Service-level staff/manager policies are present and UI routes are role-aware.                              |
| Floor overview                          | Implemented             | Staff-only API and screen with pagination/filtering.                                                        |
| Client reservation history/cancellation | Partially implemented   | Read/cancel API and screens exist; creation and checkout lifecycle are absent.                              |
| Registration/login/password reset       | Identity-provider owned | The UI starts Keycloak flows; custom credential forms would violate the BFF boundary.                       |
| Profile photo and account edits         | Partial                 | Phone is application-owned; Keycloak-owned fields/password need an approved account-management integration. |
| Reservation creation                    | Missing                 | No `/reserve` screen or create-reservation API exists.                                                      |
| Visit/check-in/check-out                | Missing                 | No visit lifecycle module or API exists.                                                                    |
| Play session/fees/payment               | Missing                 | No play-session, billing, payment, or overtime implementation exists.                                       |
| Staff reservation/history workflows     | Missing                 | No staff reservation queue/history API or screen exists.                                                    |
| Branch map/geolocation/directions       | Missing                 | The contract has no coordinates, phone, or map URL fields.                                                  |
| Audit event history                     | Missing                 | The domain model names Audit, but no audit module or persistence exists.                                    |

## Remaining risks and follow-up order

1. Define reservation creation and visit lifecycle invariants in an ADR before
   implementing UI. The current reservation table stores dates and time slots
   as display strings, which is insufficient for overlap and timezone-safe
   enforcement.
2. Add the missing branch location fields to the contract and persistence
   before building map/directions UI.
3. Decide whether staff history is a separate operational projection or a
   privileged form of client history; then add a server-side policy and tests.
4. Add an audit module for security-sensitive state changes, especially
   reservation cancellation, check-in/out, inventory changes, and billing.
5. Integrate Keycloak account management only through a documented backend
   seam; do not move passwords or OIDC tokens into browser code.

## Verification baseline

Before this pass, the merged branch passed the repository check, build, E2E,
Prettier, OpenAPI validation, Helm lint, and Compose configuration gates. The
same gates must be rerun after these changes, with API tests including Flyway
V12 and reservation authorization coverage.

## Second-pass audit — implemented-scope corrections

A follow-up review of the implemented paths found and corrected four additional
correctness issues:

- First-time client-profile provisioning now uses an atomic PostgreSQL
  `ON CONFLICT DO NOTHING` insert, so simultaneous authenticated requests cannot
  create a duplicate profile.
- A null entry in the game `copies` list now becomes a structured validation
  error rather than an internal server error.
- Authenticated users are redirected away from `/login`, and a failure loading
  `/me` no longer hides public pages behind a sign-in wall.
- Existing branch address/hours and game image fields are now rendered by their
  detail screens instead of being silently discarded.

The remaining gaps in the coverage table are still unimplemented product scope,
not regressions in these existing flows.
