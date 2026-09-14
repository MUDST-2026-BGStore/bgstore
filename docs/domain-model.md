# Domain model

## Ubiquitous language

- **Location:** a physical store with its own opening hours, tables, inventory copies, and pricing policy. The design, the UI, and the `branches` module call this a **branch**; the two words name the same thing.
- **Client:** a person who may be a guest or a registered account. Registration enables self-service reservations and durable profile access.
- **Staff member:** an authenticated employee allowed to check clients in/out and operate sessions. A manager can administer policies and staff permissions.
- **Party:** one or more clients visiting or reserving together. A party may contain guests and registered clients.
- **Table:** a playable space at a location with a seating capacity and operational state.
- **Reservation:** a client's hold on one specific table for a time interval. Game selection is a separate check-in action.
- **Visit:** the store presence begun by staff check-in and ended by check-out.
- **Play session:** the billable interval for a party using a table. Only in-store sessions are in scope.
- **Game title:** language-aware catalog metadata for a board game, including the photos and how-to-play guide clients read before a visit.
- **Game copy:** a physical, location-owned copy with an availability and condition state.
- **Pricing policy:** a versioned rule that calculates a session fee from time, party, location, and adjustments.
- **Payment record:** an operational record of amount, method, and status; no card data is stored or processed.
- **Audit event:** an immutable record of a sensitive staff or manager action.

## Candidate modules

| Module              | Owns                                                                                 | Depends on                   |
| ------------------- | ------------------------------------------------------------------------------------ | ---------------------------- |
| Identity and access | app roles and external subject mapping                                               | Keycloak adapter             |
| Clients             | profiles and client history projection                                               | identity                     |
| Locations           | locations, contact details, coordinates, booking availability, tables, opening hours | none                         |
| Reservations        | capacity holds and reservation lifecycle                                             | clients, locations           |
| Visits              | check-in/out and party membership                                                    | clients, reservations        |
| Play sessions       | table assignment, timing, selected game copies                                       | visits, locations, inventory |
| Inventory           | game titles, localized metadata, physical copies                                     | locations                    |
| Billing             | pricing policies, calculated charges, payment records                                | play sessions                |
| Audit               | append-only security and operational events                                          | module events                |

## Account profile ownership

- Keycloak is the source of truth for the authenticated email, username, and
  password. `identity_accounts` is only the BGStore-side projection keyed by
  the immutable OIDC subject.
- The Clients module and BGStore PostgreSQL database own a client’s first name,
  last name, and phone number, collected during client onboarding.
- Profile images and password changes remain outside this slice until their
  explicit API contracts and provider adapters are introduced.

## Reserved table slots

- The Reservations module owns `table_reservation`: one row holds one table
  (by id, since `store_table` belongs to the Tables module) from `starts_at`
  to `ends_at`.
- The staff floor overview (`GET /floor-overview`) reads it: each table lists
  the slots that have not ended yet, and the status counts come from the
  tables' own status. Slots do not change a table's status by themselves.
- A reservation is created against one table after availability is checked. It
  starts in `Reserved`; staff may move it to `CheckedIn` and later
  `Completed`, or cancel it before the interval starts. Game copies are not
  reserved; the party chooses games during check-in.

## Initial invariants

1. A table cannot host overlapping active play sessions.
2. A location cannot accept reservations beyond available table capacity for the interval.
3. Only checked-in parties can start a play session.
4. A play session has at most one assigned table at a time; reassignments are audited.
5. A game copy can belong to at most one active play session.
6. Fee calculation uses the pricing-policy version effective when the session starts; later policy edits do not rewrite history.
7. Check-out closes active play and visit state only after the final fee is calculated or explicitly waived by an authorized role.
8. Client history is visible to authorized staff and records who accessed or changed sensitive data.

These are design inputs, not yet implemented feature promises. Workflow discovery should refine states, cancellation/no-show rules, rounding, tax, and reservation deposits before schema expansion.
