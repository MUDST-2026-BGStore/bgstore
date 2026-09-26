# Deployment runbook

## Current delivery (single devopsandbox node)

Merges to main build `main-<sha>` images (trunk-images workflow) and open a
pull request bumping dev's per-component image tags. The PR is authored by
`github-actions[bot]`, so GitHub requires a human review on it; once its
checks are green, approve and squash-merge the pull request and Argo CD rolls
the build onto dev. Tagging a release builds signed versioned images and
records their digests in the release notes; it does not deploy anything. See
[ADR-0008](../decisions/0008-single-environment-trunk-delivery.md).

## Provider decisions required

Before deploying, choose the Kubernetes provider, DNS provider, storage class, secret manager, and backup destination. These choices are intentionally not guessed by the repository.

1. Install Argo CD and apply `deploy/argocd/platform.yaml`, then `deploy/argocd/applications.yaml`.
2. Create a `letsencrypt-production` ClusterIssuer with the correct DNS-01 or HTTP-01 solver and contact address.
3. Point the environment domains (dev currently `bgstore.devopsandbox.chanakanlabs.com` and `auth.devopsandbox.chanakanlabs.com`) at the relevant Envoy Gateway load-balancer addresses. Use ExternalDNS only after its provider credentials and ownership policy are defined.
4. Create the manual secrets the platform manifests expect (`bgstore-api`, `bgstore-database`, `keycloak-database`, `keycloak-bootstrap`) or configure an External Secrets ClusterSecretStore named `bgstore` before re-enabling `externalSecret` in an environment.
5. Configure CloudNativePG object-store backups, recovery testing, retention, and a provider storage class before production data is admitted.
6. Replace the seeded local Keycloak realm with an exported, reviewed realm. Configure the OIDC redirect URI as `https://<domain>/auth/callback/bgstore`, enable email verification with a production SMTP provider, and set the login theme to `bgstore`. When a release carries theme changes, bump the `bgstore-keycloak` image tag in `deploy/argocd/platform.yaml` and apply it.

## Adding staging or production

1. Add `deploy/environments/staging/` or `production/`; the ApplicationSet creates the Application automatically (namespace `bgstore-<environment>`).
2. Extend the release workflow to promote the signed release digest to those environments through pull requests, per ADR-0004. Dev keeps tracking trunk images.
3. Point the new environment domains at the gateway and verify certificate readiness.

## Verification

```bash
.agents/bin/verify deploy
kubectl -n bgstore-dev get applications,pods,httproutes
kubectl -n keycloak get gateway,httproute,certificate
```

## Rollback

Dev: revert the offending commit on main (Argo CD redeploys the prior `main-<sha>` image, which remains in the registry). When digest-pinned environments exist, revert the digest promotion commit instead. Flyway migrations are append-only and expand-first, so application rollback remains possible when the prior image can read the expanded schema; destructive cleanup is a separate, reviewed recovery operation.
