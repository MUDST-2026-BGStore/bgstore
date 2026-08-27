# ADR-0004: GitOps delivery

Status: Accepted — 2026-08-25

## Decision

Publish immutable, signed OCI images and promote their digests through pull requests. Argo CD reconciles Helm-rendered application environments and separately managed platform services.

## Rationale

Git provides reviewable desired state, an audit trail, drift repair, and rollback. Splitting application and operator lifecycles prevents an ordinary app release from upgrading databases, identity, or cluster controllers.

## Consequences

CI never directly mutates a production cluster. Provider-specific secrets, DNS, storage classes, backups, and cluster registration remain explicit deployment prerequisites.
