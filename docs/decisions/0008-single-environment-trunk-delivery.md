# ADR-0008: Single-environment trunk delivery

Status: Accepted — 2026-09-25

## Context

ADR-0004 described promotion through pull requests for a multi-environment
future, but the live cluster is a single devopsandbox node running one
environment. The staging and production environments, their digest-pinning
promotion PR, and the release-triggered rebuild of every component existed as
unused machinery — a half-workaround rather than an intentional design.

## Decision

- One live environment: **dev**, a plain Argo CD Application (`bgstore-dev`)
  tracking the chart at main with `deploy/environments/dev/values.yaml`.
- **Trunk builds deploy themselves.** Every push to main builds `bgstore-api`
  and `bgstore-web` under a content-addressed `main-<sha>` tag (trunk-images
  workflow). The workflow then opens a pull request bumping `images.tag` in
  dev's values file to that exact commit; merging the pull request is the
  deployment. Argo CD picks up the merged values change and rolls dev.
  (The ApplicationSet generators do not expose the rendered revision as a
  template parameter in Argo CD 3.1, so pinning happens in the values file
  rather than the generator template.)
- **A release is a signed promotion, not a rebuild for dev.** Tagging a release
  builds and cosign-signs versioned multi-arch images (`vX.Y.Z` and `latest`)
  and records their digests in the release notes. No promotion PR is created;
  `latest` points at the newest release.
- **Platform services stay separately pinned** (ADR-0004 still applies): the
  Keycloak theme image tag in `deploy/argocd/platform.yaml` is bumped manually
  when a release carries theme changes, then applied like any other platform
  manifest.
- Staging and production do not exist yet. Their delivery is the documented
  next step: reintroduce an ApplicationSet over `deploy/environments/*` (one
  directory per environment) or per-environment Applications, and extend the
  release workflow to promote the release digest to those environments through
  pull requests, completing ADR-0004.

## Consequences

- Merges to main reach dev within a build cycle; a brief ImagePullBackOff
  window while CI finishes building the pinned tag is expected and self-heals.
- Config-only changes still deploy through normal Argo CD sync of main.
- Dev always runs tested-by-CI trunk code; it is not a release mirror. To
  demo a specific version, tag a release and point dev at it explicitly.
