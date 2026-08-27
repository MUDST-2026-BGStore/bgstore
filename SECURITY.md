# Security policy

## Reporting

Do not open a public issue for a vulnerability. Report it through GitHub's private vulnerability reporting for `MUDST-2026-BGStore/bgstore`. Include reproduction steps, affected versions, and impact. Maintainers should acknowledge reports within three business days.

Only the latest release and `main` receive security fixes while the project is pre-1.0.

## Baseline

- Secrets are injected at runtime and are never committed. Values in `.env.example` and the imported Keycloak realm are explicitly local-only.
- The browser uses an HTTP-only, Secure, SameSite session cookie; OIDC tokens stay server-side.
- Images run as non-root with a read-only root filesystem and dropped Linux capabilities.
- CI performs CodeQL and Trivy analysis. Release images include SBOM/provenance attestations and Cosign signatures.
- Production secret material is expected through External Secrets and a provider-backed secret manager.
