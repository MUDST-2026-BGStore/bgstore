# Active-session backend scaffold

The `playsessions` Spring Modulith module provides a structural starting point for
the active-session screen, following the existing reservation scaffold.

| Package       | Responsibility                                                 |
| ------------- | -------------------------------------------------------------- |
| `application` | Current-session query and client assistance-request interfaces |
| `domain`      | Session and THB fee-estimate read models                       |
| `spi`         | Participant-scoped lookup and durable assistance-request ports |

This is a scaffold: no beans, controllers, HTTP endpoints, database adapters, or
migrations are supplied. The frontend continues using its existing fixture.
Interfaces do not enforce authorization or persist requests until implemented.

## Implementation sequence

1. Define endpoints and error responses in `packages/contracts/openapi.yaml`,
   then regenerate the transport types. Keep the BFF cookie and CSRF model.
2. Implement the application service using the server-side current identity and
   completed onboarding. Verify active party membership on reads and requests.
3. Add append-only migrations and adapters with atomic membership/activity checks
   and idempotent request recording. Test against real PostgreSQL with Testcontainers.
4. Obtain estimates from billing using the session's captured pricing-policy
   version. Use decimal THB amounts and absolute timestamps; display Bangkok time.
   Agree rounding and fee rules before implementing calculations.
5. Connect the UI and handle no active session, stale sessions, failed requests,
   and pending staff requests. An end-playing request does not stop the timer or
   billing; staff must close play through an authorized operational workflow after
   final fee calculation or waiver. Define request delivery and audit behavior
   before enabling the buttons against the backend.

See [domain invariants](domain-model.md), [BFF authentication](decisions/0002-bff-authentication.md),
and [authorization policy](decisions/0005-application-authorization-policy.md).
