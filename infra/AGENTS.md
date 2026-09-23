# Infrastructure agent guide

`local` seeds disposable development dependencies, `keycloak` owns the custom login theme, and `observability` configures the local telemetry stack. `compose.yaml` at the repository root wires these assets together.

- Credentials and users here are local/test fixtures only. Keep production secrets and real customer data out of the repository.
- Preserve the split between browser-visible and container-internal Keycloak URLs.
- Keep locale changes synchronized across English and Thai Keycloak message bundles.
- Validate Compose-backed edits with `.agents/bin/verify deploy`; use the production-shaped Compose path in `docs/runbooks/local-development.md` when service health or telemetry wiring changes.
