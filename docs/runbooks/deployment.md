# Deployment runbook

## Provider decisions required

Before deploying, choose the Kubernetes provider, DNS provider, storage class, secret manager, and backup destination. These choices are intentionally not guessed by the repository.

1. Install Argo CD and apply `deploy/argocd/platform.yaml`, then `deploy/argocd/applications.yaml`.
2. Create a `letsencrypt-production` ClusterIssuer with the correct DNS-01 or HTTP-01 solver and contact address.
3. Point `bgstore.chanakanlabs.com` and `auth.bgstore.chanakanlabs.com` at the relevant Envoy Gateway load-balancer addresses. Use ExternalDNS only after its provider credentials and ownership policy are defined.
4. Configure the External Secrets ClusterSecretStore named `bgstore`. The remote `environments/production/bgstore-api` secret requires `POSTGRES_USER`, `POSTGRES_PASSWORD`, `REDIS_PASSWORD`, and `KEYCLOAK_CLIENT_SECRET`. The chart derives a separate CloudNativePG `kubernetes.io/basic-auth` secret from those database fields.
5. Add `environments/production/keycloak-database` to the same provider with `POSTGRES_USER=keycloak` and a generated `POSTGRES_PASSWORD`. External Secrets maps it to the Keycloak chart and CloudNativePG database secret.
6. Configure CloudNativePG object-store backups, recovery testing, retention, and a provider storage class before production data is admitted.
7. Replace the seeded local Keycloak realm with an exported, reviewed production realm. Configure the OIDC redirect URI as `https://bgstore.chanakanlabs.com/login/oauth2/code/keycloak`, enable email verification with a production SMTP provider, and set the login theme to `bgstore`. A published release builds, signs, and promotes the matching `bgstore-keycloak` image through the platform manifest.
8. Merge a release promotion PR and verify Argo health, Gateway routes, certificate readiness, and telemetry.

## Verification

```bash
helm lint deploy/charts/bgstore
helm template bgstore deploy/charts/bgstore \
  --namespace bgstore \
  --values deploy/environments/production.yaml
kubectl -n bgstore get applications,pods,httproutes
kubectl -n keycloak get gateway,httproute,certificate
```

## Rollback

Revert the digest promotion commit. Argo CD will restore the prior immutable image. A database migration rollback is a separate, reviewed recovery operation; migrations are designed expand-first so application rollback remains possible.
