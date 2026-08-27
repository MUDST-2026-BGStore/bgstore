# ADR-0003: Modular monolith

Status: Accepted — 2026-08-25

## Decision

Implement the initial backend as a Spring Modulith modular monolith with PostgreSQL transactions and explicit module APIs/events.

## Rationale

The core store workflows share strong consistency rules. A modular monolith keeps those rules transactional while retaining seams for tests, ownership, and later extraction. Distributed services would add failure modes before scale or team boundaries require them.

## Consequences

Architecture verification runs in CI. Cross-module database access and package leakage are defects. Extraction remains possible, but is triggered by evidence rather than a planned microservice count.
