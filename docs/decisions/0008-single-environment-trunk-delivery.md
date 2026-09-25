# ADR-0008: Single-environment trunk delivery

Status: Accepted — 2026-09-25

## Context

ADR-0004 described promotion through pull requests for a multi-environment
future, but the live cluster is a single devopsandbox node running one
environment. The staging and production environments, their digest-pinning
promotion PR, and the release-triggered rebuild of every component existed as
unused machinery — a half-workaround rather than an intentional design.

## Decision

- One live environment: **dev**, tracked by the `bgstore` ApplicationSet. The
  ApplicationSet generates one Application per environment directory in
  `deploy/environments/` (each holding a `values.yaml`); today that is
  `dev/` only.
- **Trunk builds deploy themselves.** Every push to main builds `bgstore-api`
  and `bgstore-web` under a content-addressed `main-<sha>` tag (trunk-images
  workflow). The ApplicationSet pins `images.tag` to the exact main commit it
  renders, so merging to main deploys that commit to dev without a release.
- **A release is a signed promotion, not a rebuild for dev.** Tagging a release
  builds and cosign-signs versioned multi-arch images (`vX.Y.Z` and `latest`)
  and records their digests in the release notes. No promotion PR is created;
  `latest` points at the newest release.
- **Platform services stay separately pinned** (ADR-0004 still applies): the
  Keycloak theme image tag in `deploy/argocd/platform.yaml` is bumped manually
  when a release carries theme changes, then applied like any other platform
  manifest.
- Staging and production do not exist yet. Their delivery is the documented
  next step: re-add environment values files (the ApplicationSet adopts them)
  and extend the release workflow to promote the release digest to those
  environments through pull requests, completing ADR-0004.

## Consequences

- Merges to main reach dev within a build cycle; a brief ImagePullBackOff
  window while CI finishes building the pinned tag is expected and self-heals.
- Config-only changes still deploy through normal Argo CD sync of main.
- Dev always runs tested-by-CI trunk code; it is not a release mirror. To
  demo a specific version, tag a release and point dev at it explicitly.
