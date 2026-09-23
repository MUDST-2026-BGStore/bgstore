# Delivery agent guide

`charts/bgstore` owns application Kubernetes resources; `environments/{dev,staging,production}.yaml` supplies environment values; `argocd` and `platform` own GitOps/platform wiring. Read `docs/decisions/0004-gitops-delivery.md` and `docs/runbooks/deployment.md` before changing those boundaries.

- Keep reusable behavior in the chart and environment differences in values files. Render every environment after chart or values changes.
- Preserve the BFF routing model: the web origin proxies application/API and OIDC callback traffic; secrets come from Kubernetes/External Secrets, not committed values.
- Do not commit rendered manifests or mutate a cluster as verification.

Run `.agents/bin/verify deploy` for Helm lint, all environment renders, and Docker Compose configuration validation.
