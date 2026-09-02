# ADR-0005: Application authorization policy

Status: Accepted — 2026-09-02

## Decision

The BFF translates provider claims into BGStore application roles only in the
identity module. Domain services use the identity module's `AccessPolicy` to
enforce the following policies:

- authenticated identity: `CurrentIdentityProvider.currentIdentity()`
- completed client profile: the clients module's onboarding filter
- staff or manager: `AccessPolicy.requireStaffOrManager()`
- manager only: `AccessPolicy.requireManager()`

Controllers do not decide roles, and Vue treats the `/me` role list as display
state only. The browser never becomes the authorization authority.

## Rationale

Game catalogue, visit, and reservation operations will all require staff-facing
authorization. One service-layer seam keeps those rules transport-independent,
prevents individual controllers from drifting, and works for future jobs or
alternate adapters that invoke the same behavior.

## Consequences

- New operational commands call the relevant `AccessPolicy` method before
  changing state and document a `403` response in the OpenAPI contract.
- A `CLIENT` role alone is never sufficient for operational or administrative
  writes.
- Vue may hide unavailable actions after reading `/me`, but the API returns
  `403` when a caller lacks the required role.
- Each protected command needs tests for allowed staff/manager access and a
  denied client request.
