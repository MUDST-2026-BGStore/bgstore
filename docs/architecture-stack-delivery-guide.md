# BGStore: architecture, technology stack, and CI/CD deployment guide

This guide describes the checked-in configuration reviewed on 15 October 2026. It does not certify the state of a running cluster, a successful CI run, or production readiness. Dependency versions describe this checkout, not the latest available releases.

## 1. Architecture

BGStore uses a **modular monolith**, a **backend-for-frontend (BFF)**, **OpenAPI contract-first development**, and **GitOps delivery**. See the [project overview](../README.md), [architecture](architecture.md), and [development guide](development-guide.md).

### Request flow and security

The deployed web container serves the Vue SPA through NGINX. NGINX proxies `/api`, `/auth`, `/oauth2`, `/login`, and `/logout` to the Spring Boot BFF. The BFF authenticates with Keycloak using the authorization-code flow and stores application sessions in Redis-compatible storage. Local Compose uses Redis; the Kubernetes platform configuration declares Valkey.

The browser does not receive OAuth access or refresh tokens. It carries an opaque session cookie; tokens remain server-side. The API configuration sets `HttpOnly=true` and `SameSite=Strict`, not `Lax`. Do not describe the cookie itself as encrypted: transport encryption through HTTPS is a separate property. The cookie's `Secure` flag is environment-configurable and defaults to `false` for local use, so HTTPS and secure-cookie configuration must be verified for a deployment.

Sources: [BFF authentication ADR](decisions/0002-bff-authentication.md), [Spring Security configuration](../apps/api/src/main/java/com/chanakanlabs/bgstore/security/SecurityConfiguration.java), [API properties](../apps/api/src/main/resources/application.properties), [NGINX configuration](../apps/web/nginx.conf), [Compose](../compose.yaml), and [platform applications](../deploy/argocd/platform.yaml).

PostgreSQL stores relational application data. The API exports traces, metrics, and logs over OTLP to the OpenTelemetry Collector. The collector forwards traces to Tempo and logs to Loki and exposes metrics for Prometheus to scrape. Prometheus also scrapes the API directly. Grafana provides the query and visualization interface.

Sources: [collector configuration](../infra/observability/otel-collector.yaml), [Prometheus configuration](../infra/observability/prometheus.yaml), and [API logging configuration](../apps/api/src/main/resources/logback-spring.xml).

### Module boundaries

Spring Modulith verifies module dependencies during tests. ArchUnit rules also restrict controller access to repositories/entities and transaction ownership, and prevent services from depending on MVC. These are **test-enforced boundaries**, not separate deployed services or compile-time isolation.

The current top-level module packages are `billing`, `branches`, `clients`, `contract`, `hello`, `identity`, `inventory`, `reservations`, `security`, `tables`, and `web`. The domain model's candidate modules are design terminology, not an exact inventory of implemented packages. In particular, play-session behavior lives within `reservations`; it is not a separate top-level play-session module.

Sources: [modular-monolith ADR](decisions/0003-modular-monolith.md), [domain model](domain-model.md), [architecture tests](../apps/api/src/test/java/com/chanakanlabs/bgstore/ArchitectureTest.java), and [API source](../apps/api/src/main/java/com/chanakanlabs/bgstore/).

### Contract-first development

The HTTP contract source of truth is [packages/contracts/openapi.yaml](../packages/contracts/openapi.yaml). Hey API generates the browser client into `apps/web/src/generated/api`. Gradle generates server interfaces and models under `apps/api/build/generated/openapi`; controllers implement the API behavior. Generated code is not edited manually.

Sources: [TypeScript generator configuration](../openapi-ts.config.ts), [API Gradle build](../apps/api/build.gradle.kts), and [development guide](development-guide.md).

## 2. CI/CD and deployment

GitHub Actions checks source and builds images; GHCR stores images; Helm defines application resources; Argo CD reconciles desired Kubernetes state from Git. The workflows do not run `kubectl apply` or `helm upgrade` to deploy the application.

The checked-in application delivery target is **dev**, in namespace `bgstore-dev`. Signed release publication is separate; production deployment wiring is not implemented by the release workflow.

Sources: [GitOps ADR](decisions/0004-gitops-delivery.md), [single-environment delivery ADR](decisions/0008-single-environment-trunk-delivery.md), and [deployment runbook](runbooks/deployment.md).

### Continuous integration

[ci.yml](../.github/workflows/ci.yml) runs for pull requests and pushes to `main`. Its four jobs run independently:

| Job                  | Configured checks                                                                                                                                                                                                |
| -------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `quality`            | Install dependencies with the frozen lockfile; run `pnpm format:check` and `pnpm check`. These coordinate frontend lint/typecheck/tests and backend checks.                                                      |
| `browser`            | Install Chromium; start the Compose `app` profile; run Playwright with `BASE_URL` and `BGSTORE_FULL_STACK=1`; collect logs and reports.                                                                          |
| `delivery-manifests` | Lint the Helm chart, render every existing environment values file, and run a Trivy filesystem scan that fails on reported high/critical findings. Currently the environment values directory contains dev only. |
| `compose`            | Validate Compose configuration with `.env.example`.                                                                                                                                                              |

The browser suite combines mocked API UI tests with opt-in full-stack tests. The latter exercise local Keycloak login and API-backed mutations, including reservations, tables, games, and checkout using the bogus payment gateway. They assert visible/API outcomes, not direct database-row inspection, and do not exercise a live payment provider.

Sources: [Playwright configuration](../apps/web-e2e/playwright.config.mts), [full-stack tests](../apps/web-e2e/src/full-stack.spec.ts), and [walking-skeleton tests](../apps/web-e2e/src/walking-skeleton.spec.ts).

Vitest's configured coverage floors are **80% lines and 75% branches**; statements and functions have no explicit thresholds. Generated code and the frontend bootstrap/router are excluded from coverage. These are configured floors, not measured results from this review. See [Vite/Vitest configuration](../apps/web/vite.config.mts).

Helm lint/render and Compose validation are useful static checks. They do not prove that cluster CRDs, secrets, connectivity, capacity, or workloads are healthy.

### Trunk image build and dev deployment PR

[build.yml](../.github/workflows/build.yml) runs on selected application/package path changes pushed to `main`, and supports manual dispatch. It is not triggered by every `main` change.

1. The `images` job configures QEMU and Buildx, then builds API and web images for `linux/amd64` and `linux/arm64`.
2. It pushes `bgstore-api` and `bgstore-web` to `ghcr.io/mudst-2026-bgstore`, tagged `main-<full-commit-sha>`.
3. After image builds succeed, `bump-dev` updates the API/web tags in [dev values](../deploy/environments/dev/values.yaml) and creates or updates the `chore/dev-image-bump` pull request.
4. Merging that values change into `main` changes the desired dev deployment. Argo CD can then reconcile it.

**CI and the trunk build are separate workflows with no explicit dependency between them.** Do not describe the image build as waiting for CI to pass. Required merge checks, if configured, are a separate GitHub repository policy.

`main-<sha>` is a commit-derived tag, not an image digest and not inherently an immutable registry reference. The chart supports digest references, but dev values currently use tags. A registry digest such as `sha256:...` identifies image content; a source commit SHA does not serve that role.

The workflow describes human approval and merge as the deployment gate. At review time, the repository API reported auto-merge disabled, but access to branch-protection settings was denied. A bot author alone does not establish a mandatory code-review rule. Required reviews, required checks, and bypass permissions must be verified in GitHub settings.

Workflow-run approval is also distinct from PR review. [GitHub's current documentation](https://docs.github.com/en/actions/how-tos/writing-workflows/choosing-when-your-workflow-runs/triggering-a-workflow) says `opened`, `synchronize`, and `reopened` pull-request events created with `GITHUB_TOKEN` create workflow runs in an approval-required state. A user with write access can approve those runs; that action is not approval of the code change.

**Manual-dispatch caveat:** the image job tags the checked-out `inputs.ref`, but `bump-dev` uses `github.sha`. If a different commit is selected through `ref`, the deployment PR can reference a tag different from the image just built. Verify the tag before merging a manually triggered bump PR; this review does not change the workflow.

### Argo CD reconciliation and rollout

The [dev Application](../deploy/argocd/applications.yaml) tracks `main`, renders [the BGStore Helm chart](../deploy/charts/bgstore/Chart.yaml) with dev values, and targets `bgstore-dev`. It enables automated sync with `prune` and `selfHeal`, plus namespace creation.

Argo CD applies the desired Kubernetes resources. The Kubernetes node's kubelet/container runtime pulls the GHCR images; Argo CD does not pull application images itself.

The chart declares Deployments and readiness probes. It does not prove zero-downtime rollout: dev uses one replica per workload, disables autoscaling, and the templates do not specify an explicit rollout strategy or a PodDisruptionBudget. Availability also depends on startup, capacity, dependencies, probes, and database compatibility.

Sources: [default chart values](../deploy/charts/bgstore/values.yaml), [dev values](../deploy/environments/dev/values.yaml), [API workload](../deploy/charts/bgstore/templates/api.yaml), [web workload](../deploy/charts/bgstore/templates/web.yaml), [routing](../deploy/charts/bgstore/templates/routing.yaml), and [security resources](../deploy/charts/bgstore/templates/security.yaml).

The [platform applications](../deploy/argocd/platform.yaml) declare components including cert-manager, CloudNativePG, External Secrets, Envoy Gateway, Valkey, Keycloak, and observability services. These manifests describe desired state, not evidence that those services are installed or healthy. Some prerequisites, including CloudNativePG CRDs, are handled out of band.

### Release publication and supply-chain metadata

[Release Please](../.github/workflows/release-please.yml) runs on `main` and calls [publish.yml](../.github/workflows/publish.yml) when it creates a release. The publish workflow also supports published-release events.

The publish workflow builds API, web, and custom Keycloak images for both supported architectures. All receive the release tag; API and web additionally receive `latest`. Each build requests an SBOM and `provenance: mode=max`. Cosign signs all three image digests using GitHub OIDC, and the workflow appends the digests to release notes.

These are separate release builds, not a digest-only promotion of the already-built trunk images. Recording signed digests is not the same as verifying them: the workflow has no explicit signature-verification step, and this guide does not claim cluster-side signature enforcement or a certified SLSA level. It does not update a production environment.

## 3. Deliverable groups and evidence

The groups below preserve the draft's phase structure. They organize repository evidence; they do not establish historical order, acceptance, or completion of every requirement.

| Group                                         | Available evidence                                                                                                                                                                                                                                                                                                                                                                                                          | Qualification                                                                                                                                                                                     |
| --------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1. Requirements, domain modeling, and UX/UI   | [Requirements links](system-requirements.md), [domain model and invariants](domain-model.md), [CSS design tokens](../apps/web/src/styles.css)                                                                                                                                                                                                                                                                               | The SRS is labeled draft. Linked diagrams are references, not proof of approval. The CSS comment identifies Figma variable naming; this review did not compare its values with the Figma library. |
| 2. Design system and repository foundation    | [Design-system research](active-session-design-system-research.md), [UI types](../apps/web/src/components/ui/types.ts), [pagination](../apps/web/src/components/ui/UiPagination.vue), [text input](../apps/web/src/components/ui/UiTextInput.vue), [select](../apps/web/src/components/ui/UiSelect.vue), [Nx configuration](../nx.json), [baseline migration](../apps/api/src/main/resources/db/migration/V1__baseline.sql) | Component existence and research do not establish full accessibility conformance or design acceptance.                                                                                            |
| 3. Runnable applications and automated checks | [Scripts](../package.json), [API entry point](../apps/api/src/main/java/com/chanakanlabs/bgstore/BgstoreApiApplication.java), [web entry point](../apps/web/src/main.ts), [frontend test example](../apps/web/src/api-error.spec.ts), [architecture tests](../apps/api/src/test/java/com/chanakanlabs/bgstore/ArchitectureTest.java), [CI](../.github/workflows/ci.yml)                                                     | Tests are configured and implemented; this document review does not report a new passing run.                                                                                                     |
| 4. Containers and image publication           | [API Dockerfile](../apps/api/Dockerfile), [web Dockerfile](../apps/web/Dockerfile), [Keycloak Dockerfile](../infra/keycloak/Dockerfile), [Compose](../compose.yaml), [trunk build](../.github/workflows/build.yml), [release publication](../.github/workflows/publish.yml)                                                                                                                                                 | Container configuration is not evidence that a particular image has been built, scanned, or successfully deployed.                                                                                |
| 5. Integration skeleton and Kubernetes GitOps | [Flyway migrations](../apps/api/src/main/resources/db/migration/), [full-stack tests](../apps/web-e2e/src/full-stack.spec.ts), [Helm chart](../deploy/charts/bgstore/), [Argo applications](../deploy/argocd/applications.yaml), [platform applications](../deploy/argocd/platform.yaml)                                                                                                                                    | The checkout contains 26 migration files, V1–V26. That count does not prove applied migrations or domain-feature completion. Production readiness and live cluster health remain unverified.      |

Payment is an explicit example of the distinction between a walking skeleton and a production integration: the repository defines a [payment-gateway seam](decisions/0006-payment-gateway-seam.md) and [bogus card simulation](decisions/0007-bogus-card-simulation.md), not a live card-acquiring integration.

## 4. Technology matrix

### Backend

Versions below are directly declared unless marked managed. Spring Boot's version must not be reused as the version of Spring Data, Hibernate, Flyway, or JUnit.

| Technology                     | Version/status                                                                  | Source                                                                                             |
| ------------------------------ | ------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------- |
| Java / Eclipse Temurin         | Java toolchain 21; container JDK/JRE tags `21-*-alpine`                         | [Gradle](../apps/api/build.gradle.kts), [Dockerfile](../apps/api/Dockerfile)                       |
| Spring Boot                    | 4.1.1                                                                           | [Gradle](../apps/api/build.gradle.kts)                                                             |
| Spring Modulith                | BOM 2.1.0                                                                       | [Gradle](../apps/api/build.gradle.kts)                                                             |
| Spring Data JPA / Hibernate    | Dependency-management supplied; individual versions not resolved in this review | [Gradle](../apps/api/build.gradle.kts)                                                             |
| jOOQ                           | 3.21.7 declared for plugin/runtime/codegen                                      | [Gradle](../apps/api/build.gradle.kts)                                                             |
| Flyway                         | Boot-managed starter and PostgreSQL support                                     | [Gradle](../apps/api/build.gradle.kts), [migrations](../apps/api/src/main/resources/db/migration/) |
| Error Prone core / NullAway    | 2.50.0 / 0.13.8                                                                 | [Gradle](../apps/api/build.gradle.kts)                                                             |
| Spotless                       | Gradle plugin 8.10.0                                                            | [Gradle](../apps/api/build.gradle.kts)                                                             |
| Testcontainers / JUnit Jupiter | Managed test dependencies; individual versions not resolved in this review      | [Gradle](../apps/api/build.gradle.kts)                                                             |
| ArchUnit                       | Used by architecture tests; do not label its version “Latest”                   | [Architecture tests](../apps/api/src/test/java/com/chanakanlabs/bgstore/ArchitectureTest.java)     |

### Frontend and monorepo tooling

Package ranges describe allowed updates. The lockfile records the versions selected for a frozen install. pnpm itself is selected through `packageManager`, not an installed lockfile dependency.

Sources: [package.json](../package.json), [pnpm-lock.yaml](../pnpm-lock.yaml), [Nx configuration](../nx.json), [Vite/Vitest configuration](../apps/web/vite.config.mts), and [Playwright configuration](../apps/web-e2e/playwright.config.mts).

| Technology                           | Declared range/pin             | Lockfile resolution |
| ------------------------------------ | ------------------------------ | ------------------- |
| Vue                                  | `^3.5.13`                      | 3.5.41              |
| Vite                                 | `^8.0.0`                       | 8.2.2               |
| TanStack Vue Query                   | `^5.102.3`                     | 5.102.3             |
| Vue Router                           | `^4.6.4`                       | 4.6.4               |
| Vue I18n                             | `^11.4.9`                      | 11.4.9              |
| Tailwind CSS / Vite plugin           | `^4.3.3` each                  | 4.3.3 each          |
| Hey API OpenAPI TS                   | `^0.99.0`                      | 0.99.0              |
| Vitest                               | `~4.1.0`                       | 4.1.11              |
| Playwright Test                      | `^1.37.0`                      | 1.62.1              |
| Nx                                   | `23.1.1`                       | 23.1.1              |
| pnpm                                 | `packageManager: pnpm@10.33.0` | Not applicable      |
| Lefthook                             | `^2.1.10`                      | 2.1.10              |
| Commitlint CLI / conventional config | `^21.2.2` each                 | 21.2.2 each         |

[Lefthook](../lefthook.yml) runs pre-commit checks, commit-message validation, and a pre-push `pnpm check`. [Commitlint configuration](../commitlint.config.mjs) defines the Conventional Commits preset. The SPA uses [Vue Router](../apps/web/src/router.ts) and [Vue I18n](../apps/web/src/i18n.ts) for routing and English/Thai localization.

### Local infrastructure and container images

These are image tags in [Compose](../compose.yaml), unless another source is given. They are not necessarily the versions of services deployed by the separate Kubernetes platform charts.

| Technology                      | Configured tag/version                                                                         |
| ------------------------------- | ---------------------------------------------------------------------------------------------- |
| PostgreSQL                      | `18.1-alpine`                                                                                  |
| Redis                           | `8.4-alpine`                                                                                   |
| Keycloak                        | `26.7.1`, in the [custom image Dockerfile](../infra/keycloak/Dockerfile)                       |
| Mailpit                         | `v1.31.0`                                                                                      |
| OpenTelemetry Collector Contrib | `0.157.0`                                                                                      |
| Prometheus                      | `v3.13.0-distroless`                                                                           |
| Grafana Loki                    | `3.7.2`                                                                                        |
| Grafana Tempo                   | `2.10.7`                                                                                       |
| Grafana                         | `13.1.0`                                                                                       |
| NGINX unprivileged              | `1.29.5-alpine`, in the [web Dockerfile](../apps/web/Dockerfile)                               |
| Node.js                         | `24.19.0-alpine` in the [web Dockerfile](../apps/web/Dockerfile); CI also selects Node 24.19.0 |

### Delivery and security tooling

| Technology                                   | Version/status                                                                                                    | Source                                                      |
| -------------------------------------------- | ----------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------- |
| Helm                                         | CI selects v3.19.0                                                                                                | [CI](../.github/workflows/ci.yml)                           |
| Argo CD                                      | Application manifests are present; an Argo CD server version of 3.1 is not established by those manifests         | [Applications](../deploy/argocd/applications.yaml)          |
| Gateway API                                  | Resources use API version `gateway.networking.k8s.io/v1`; this is not an installed controller/CRD release version | [Routing](../deploy/charts/bgstore/templates/routing.yaml)  |
| Envoy Gateway / CloudNativePG / cert-manager | Chart revisions v1.9.1 / 0.29.1 / v1.21.2                                                                         | [Platform applications](../deploy/argocd/platform.yaml)     |
| Valkey                                       | Platform chart revision 0.12.0; not the local Redis image version                                                 | [Platform applications](../deploy/argocd/platform.yaml)     |
| Release Please                               | Workflow automation; do not use vendor name “Google” as a version                                                 | [Release workflow](../.github/workflows/release-please.yml) |
| Trivy                                        | Action ref `aquasecurity/trivy-action@v0.36.0`; not a verified scanner-binary version                             | [CI](../.github/workflows/ci.yml)                           |
| CodeQL                                       | Workflow/action configuration; not a version called “Latest”                                                      | [CodeQL workflow](../.github/workflows/codeql.yml)          |
| Cosign                                       | Installer action ref `sigstore/cosign-installer@v4.1.2`; the workflow does not pin `cosign-release`               | [Publish workflow](../.github/workflows/publish.yml)        |

A chart version, action reference, API version, application version, and image tag identify different things. Do not substitute one for another. Tags and action version refs are not immutable commit/digest pins.

## 5. Link and maintenance conventions

- Links are relative to this document's directory, `docs/`. For example, use `[architecture](architecture.md)` for another document here, `[README](../README.md)` for the repository root, and `[API build](../apps/api/build.gradle.kts)` for application source.
- Use forward slashes, preserve filename case, and omit machine-specific drive letters, usernames, and `file:///` prefixes.
- Do not use leading `/` for portable repository links: some renderers treat it as the website root, not the repository root.
- Prefer whole-file links or Markdown heading anchors over line numbers. `#L10-L20` can be useful in GitHub source views, but is renderer-dependent and drifts as source changes. Exact historical line citations require a revision-pinned reference.
- Keep source documents authoritative. When behavior changes, update the owning configuration/ADR/runbook first, then refresh this overview.
- Use the [verification wrapper](../.agents/bin/verify) for repository checks. Its `full` scope covers formatting, quality checks, builds, mock-backed browser tests, Helm rendering, and Compose validation. The full-stack OIDC/container browser path in CI is a separate verification path; a mock-backed run alone does not cover it.
