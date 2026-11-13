# AWS deployment runbook

This directory manages the single-node **sandbox** deployment of The Parlor.
Application builds run locally through Nx. ARM64 images go to private ECR;
Argo CD reads a published Git snapshot and applies Kubernetes manifests.
`make aws-deploy` performs a manual sync with pruning disabled.

## Environment and access

The following identifiers were confirmed on 2026-11-13. Always query AWS before
using an address or starting a machine, especially after rebuilding a sandbox.

| Item                            | Value                                                             |
| ------------------------------- | ----------------------------------------------------------------- |
| AWS profile / account           | `chanakanlabs` / `416604074663`                                   |
| Region                          | `ap-southeast-7` (Thailand)                                       |
| Instance                        | `i-041c168130c959aec`, Name `devopsandbox`, `m8g.large`           |
| Operating system / architecture | Amazon Linux 2023 / ARM64                                         |
| Public address                  | `43.210.113.14` (existing Elastic IP)                             |
| Kubernetes                      | k3s `v1.36.4+k3s1`, one schedulable server/worker                 |
| Application namespace           | `theparlor-dev`                                                   |
| GitOps applications             | `theparlor-routing`, `theparlor-dev` in `argocd`                  |
| API image repository            | `416604074663.dkr.ecr.ap-southeast-7.amazonaws.com/theparlor/api` |
| Web image repository            | `416604074663.dkr.ecr.ap-southeast-7.amazonaws.com/theparlor/web` |

Required access includes AWS SSO, permission to start/stop the instance and push
ECR images, the existing EC2 SSH private key, and Git push access to the origin
repository. Argo CD also needs read access to that repository. Obtain credentials
from the operator; credentials and infrastructure state are excluded from Git.

## Tools and a clean checkout

Install mise, Docker with Buildx, Python 3.12–3.14 for Ansible, curl, Git, AWS CLI
v2, SSH and kubectl. Start Docker before building. `mise.toml` pins Node 26.10.0,
pnpm 10.33.0 and Java 21.0.2. Use those versions without upgrading dependencies.

```sh
mise trust
mise install
mise exec -- pnpm install --frozen-lockfile
make aws-tools
make aws-init
make aws-validate
```

`aws-tools` installs OpenTofu 1.13.8 and ansible-core 2.21.6 under ignored
`deploy/aws/.local/`. Recreate that virtual environment after moving a checkout:
its executable shebangs contain absolute paths. `TOFU_BIN` and
`ANSIBLE_PLAYBOOK_BIN` can point to existing compatible executables. If running
inside a constrained agent environment, set `NX_DAEMON=false` and
`NX_ISOLATE_PLUGINS=false`, and grant access to Docker and dependency caches.

A new worktree omits ignored infrastructure state, local credentials and
deployment metadata. Restore the existing sandbox's `terraform.tfstate` and any
operator-managed `*.auto.tfvars` from secure storage before using its outputs.
Keep state and backups private (mode 0600). Use one state writer at a time.
`make aws-init` downloads providers; it does not discover the existing stack.
An empty state followed by `aws-apply` can propose another environment.

For an existing environment without its state, inspect `imports.tf` and the
`adopt_existing` / `existing_resource_ids` variables, then review `aws-plan` and
use `aws-adopt`. That command refuses resource creation, updates or deletion.
For a separate environment follow `new-environment.tfvars.example` in a separate
directory with separate state. Provisioning and rebuilds require their own review.

## Start and identify the sandbox

```sh
export AWS_PROFILE=chanakanlabs
export AWS_REGION=ap-southeast-7
export AWS_INSTANCE_ID=i-041c168130c959aec
export SSH_KEY="$HOME/aws"  # override with the operator-provided key path

aws sso login --profile "$AWS_PROFILE"
aws sts get-caller-identity --profile "$AWS_PROFILE"
aws ec2 describe-instances --instance-ids "$AWS_INSTANCE_ID" \
  --query 'Reservations[0].Instances[0].{Name:Tags,State:State.Name,IP:PublicIpAddress}'
aws ec2 start-instances --instance-ids "$AWS_INSTANCE_ID"
aws ec2 wait instance-running --instance-ids "$AWS_INSTANCE_ID"
aws ec2 wait instance-status-ok --instance-ids "$AWS_INSTANCE_ID"
make aws-status
```

These AWS CLI commands use the exported profile and region. SSO expiration is an
authentication failure: refresh the session before continuing. The connection
helper queries the current IP on each call and accepts `SSH_HOST`, `SSH_USER` and
`SSH_KEY` overrides. Set `AWS_INSTANCE_ID` explicitly when recovering a checkout
without state; the helper contains a historical fallback instance ID.

The infrastructure default is `instance_state = "stopped"`. Direct CLI power
changes temporarily differ from that desired state; an infrastructure apply can
stop the machine. Avoid infrastructure applies during an application deployment.

## Platform prerequisites

On a configured server, inspect `make aws-status` before changing the platform.
Use `make aws-setup-check` to preview Ansible changes. `make aws-setup` configures
k3s, private ECR pulls through the EC2 role, Argo CD, Envoy Gateway and
cert-manager. It preserves installed k3s and Argo CD versions by default.

Ansible's GitOps setup registers paused applications targeting `main`. It also
retires named legacy Argo Application objects without cascading deletion of
their workloads. Rerunning setup after a rehearsal can reset the application
source configuration; review the playbook before doing so.

The AWS security group must permit HTTP/HTTPS and operator SSH. The Kubernetes
API and Argo NodePort remain private. `make aws-argocd` opens an SSH tunnel to
`http://localhost:8085`. `make aws-kubeconfig` saves private administrator
credentials; use those with `make aws-kube-tunnel` for local kubectl access.

The runtime Secret `theparlor-runtime` contains database credentials, the OIDC
client secret, Keycloak bootstrap credentials and the Valkey password.
`make aws-secrets` leaves an existing Secret unchanged; on first creation it
generates credentials and writes a private recovery copy under `.local/`.
Preserve that Secret together with the database. Replacing only its passwords
breaks access to existing PostgreSQL/Keycloak data.

## Build, publish and deploy a rehearsal

Use a new tag for every image build; the ECR repositories enforce immutable tags.
Keep the source tree unchanged between building images and publishing the Git
snapshot. The snapshot publisher includes tracked and untracked non-ignored
files, so inspect `git status --short` and remove unintended files first.

```sh
export NX_DAEMON=false
export NX_ISOLATE_PLUGINS=false
export IMAGE_TAG="rehearsal-$(date +%Y%m%d-%H%M%S)-$(git rev-parse --short HEAD)"

make aws-validate
pnpm nx run aws-infra:test
make aws-build-images
make aws-publish-images
make aws-publish-snapshot
make aws-deploy
```

Export `IMAGE_TAG` explicitly. The snapshot script has a historical fixed default
tag; the Makefile's image-build default is `dev`. Using those defaults can deploy
an older image or fail on an immutable ECR tag.

The build helper runs `api:build` and `web:build`, packages their outputs through
the Dockerfiles' `artifacts-runtime` stages, and builds `linux/arm64` images.
It also checks that the nginx runtime user can read the packaged page and access
the asset directory. The web build uses a same-origin API base URL. Application tests are excluded
from this packaging path; run them separately when required by your release.

The publisher reads ECR URLs from OpenTofu outputs and pushes an isolated commit
to `codex/aws-rehearsal-YYYYMMDD`. It leaves the current branch and index intact.
Pass `--branch codex/<unique-name>` directly to
`python3 deploy/aws/deploy.py publish-snapshot` when a separate branch is needed.
The generated source and `.local/deployment.json` must remain available for sync.

Within the snapshot, the publisher:

1. Substitutes current-IP `sslip.io` names for the sandbox's configured domains.
2. Points the AWS overlay at ECR and the selected immutable image tag.
3. Points both Argo applications at the snapshot branch and enables comparison.
4. Renders both Kustomize trees before publishing.

The sync helper preserves an existing runtime Secret, applies the project and
applications, syncs routing before the application at the exact snapshot commit,
then waits for PostgreSQL, API, web, Keycloak and gateway certificates. Sync uses
`prune: false`; obsolete resources can remain and need deliberate review.
Check Valkey, Mailpit and overall application health separately as shown below.

The Application definitions enable
[Argo CD server-side diff](https://argo-cd.readthedocs.io/en/release-3.3/user-guide/diff-strategies/)
so Kubernetes-defaulted Gateway/HTTPRoute/StatefulSet fields compare correctly.
Old generated ConfigMaps can still appear as resources requiring pruning. Review
their ownership and active pod/workload references before removing only those
superseded objects; the deployment command keeps global pruning disabled.

Publishing changes GitHub and ECR; syncing changes the server. These operations
require the corresponding operator authorization. Rebuilding images needs a new
tag and a matching snapshot publication before syncing again.

## DNS, HTTPS and identity

For rehearsals, names have the form `theparlor.<public-ip>.sslip.io`,
`auth.<public-ip>.sslip.io` and `argocd.<public-ip>.sslip.io`. The public IP must
resolve correctly, and port 80 must be reachable for Let's Encrypt HTTP-01.
Certificates terminate at the shared Envoy Gateway on port 443. A changed IP
requires another snapshot and certificate checks. Existing Keycloak realms also
need their client redirect URIs updated explicitly.

For permanent domains, replace the three
`*.devopsandbox.chanakanlabs.com` hostnames in routing, certificates and AWS
application configuration, point DNS to the server, publish immutable image
references, and sync the published Git revision. The checked-in AWS overlay
still has GHCR `dev` placeholders; the rehearsal publisher performs the ECR
substitution. Deploying that overlay from `main` unchanged is a separate path.

Keycloak's init container prepares redirect/logout URIs and sandbox SMTP settings
for a first realm import. Startup import skips a realm that already exists;
changing the ConfigMap alone does not update an existing realm. Use the Keycloak
Admin API/UI to update the BFF client when changing origins, without deleting
users or reimporting the realm destructively. See
[Keycloak import behavior](https://www.keycloak.org/server/importExport).

## Verify the deployment

Use the hostnames in `deploy/aws/.local/deployment.json`. Successful Kubernetes
sync alone does not demonstrate working external routing.

```sh
make aws-status
export APP_ORIGIN=https://theparlor.43.210.113.14.sslip.io
export AUTH_ORIGIN=https://auth.43.210.113.14.sslip.io

curl --fail --show-error "$APP_ORIGIN/"
curl --fail --show-error "$APP_ORIGIN/api/v1/branches"
curl --fail --show-error "$APP_ORIGIN/api/v1/games"
curl --fail --show-error "$APP_ORIGIN/api/auth/session"
curl --fail --show-error "$AUTH_ORIGIN/realms/theparlor/.well-known/openid-configuration"
curl --silent --show-error --dump-header - --output /dev/null \
  "$APP_ORIGIN/api/auth/login/keycloak"
```

Validate HTTPS with the default trust store. Confirm that HTTP redirects to
HTTPS, the Vue app loads an actual asset and a deep link, JSON routes return JSON,
OIDC discovery reports the public auth issuer, and the login redirect reaches
the Keycloak form with the expected HTTPS callback. Check Secure/HttpOnly session
cookies and load the page in a browser. Complete an authorized test-account login
when validating the callback/session flow; a rendered form only proves the start
of that flow.

Over SSH or the kube tunnel, inspect:

```sh
kubectl -n argocd get applications
kubectl -n theparlor-dev get pods,pvc
kubectl -n gateway get gateway,certificates
kubectl -n theparlor-dev logs deployment/api --tail=100
kubectl -n theparlor-dev exec postgres-0 -- sh -c \
  'psql -U "$POSTGRES_USER" -d theparlor -c "SELECT version,description,success FROM flyway_schema_history ORDER BY installed_rank;"'
```

When using SSH, prefix kubectl with `sudo k3s`. Confirm that live image IDs match
the ECR digests, both Argo applications reference the intended commit, every
required pod is Ready, and migrations succeeded. An empty catalog can be valid
on an unseeded database. `make seed-dev` resets local business data; avoid using
it as a deployment health check on the server.

## Backup, recovery and shutdown

Before a release, save database dumps, the runtime Secret and Argo application
definitions to private storage. Dumps and Secret JSON contain sensitive data.
The sandbox uses node-local storage: a persistent volume survives a normal
stop/start, while losing the node/root volume can lose the database.

Keep the last known working Git revision and image digests. For an application
rollback, manually sync that revision in Argo CD with pruning disabled, then
repeat readiness and HTTP checks. A Git/image rollback does not reverse Flyway
migrations; review database compatibility and use an approved restore procedure
when required. Existing ECR lifecycle policy keeps ten images per repository,
so verify rollback images still exist. Argo CD manual-sync behavior is described
in its [sync documentation](https://argo-cd.readthedocs.io/en/stable/user-guide/auto_sync/).

Always stop the test instance after collecting evidence, including when a build,
sync or smoke check fails:

```sh
aws ec2 stop-instances --instance-ids "$AWS_INSTANCE_ID"
aws ec2 wait instance-stopped --instance-ids "$AWS_INSTANCE_ID"
aws ec2 describe-instances --instance-ids "$AWS_INSTANCE_ID" \
  --query 'Reservations[0].Instances[0].State.Name' --output text
```

Require the final output to be `stopped`. Close SSH tunnels. Retain necessary
private recovery files outside a disposable worktree before removing it.
Stopping EC2 ends instance compute billing; EBS, retained images and allocated
Elastic IPs can continue billing. See the
[EC2 lifecycle documentation](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-lifecycle.html).
Releasing an Elastic IP changes the deployment's address and needs separate
approval.

## Deployment readiness boundaries

This sandbox uses the `dev` profile, Mailpit for captured email, and the bogus
development card gateway. Real customer use requires an explicit production
profile/payment configuration, real mail delivery, suitable secrets management,
off-node backups and a recovery plan. The AWS tracing endpoint also assumes an
`observability` namespace with Tempo; inspect its presence before relying on
traces. Those services and operational policies need independent validation.

## Rehearsal: 2026-11-13

The existing server was started from `stopped`. Application source was
`feffb73`; deployment corrections are isolated in the deployment worktree and
the published `codex/aws-rehearsal-20261113` snapshot branch. The final image tag
is `rehearsal-20261113-feffb73-r2`. The exact snapshot commit is recorded in the
operator's ignored `.local/deployment.json`.

The initial deployment failed its live checks. Three deployment defects were
reproduced and corrected:

| Failure                                                                                                               | Correction                                                                                                                                 |
| --------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| API startup could not bind its port because Service links injected `API_PORT=tcp://...` (and similarly `VALKEY_PORT`) | Disable Service-link injection for the AWS API pod; DNS service names remain configured explicitly                                         |
| API/Keycloak could not reach PostgreSQL; API could not reach Valkey                                                   | Restore peer selectors after Kustomize's base label transformation; retain the destination workload selectors and restricted allowed peers |
| Web pod was Ready while `/` returned 403: host artifacts had mode 0600/0700 and root ownership                        | Copy artifacts with nginx ownership, verify readability during image builds, and probe `index.html` for readiness                          |

The port failure was reproduced with the actual API image and a Kubernetes-style
environment variable; removing that variable allowed port 8080 initialization.
Rendered-manifest tests failed on all three blocked connections before the fix
and passed afterward. The nginx runtime-UID check failed on the original image
and passed on the corrected image.

Successful checks included:

- Pinned dependency install, Nx API/web builds, ARM64 packaging and ECR pushes.
- OpenTofu validation/formatting and Ansible syntax validation.
- Eight deployment-tooling tests, including rendered-manifest connectivity checks.
- Argo routing/application sync and rollout readiness for all six application pods.
- All nine Flyway migrations applied successfully to the previously empty
  application database; PostgreSQL's persistent volume remained Bound.
- Three valid HTTPS certificates and HTTP-to-HTTPS redirect behavior.
- Browser loading of the Vue page, actual JavaScript asset and `/account` deep link.
- JSON responses from branches, games, anonymous session and CSRF endpoints.
- Public OIDC issuer, login redirect, callback URI, S256 PKCE and Keycloak login form.
- Secure/HttpOnly/SameSite=Lax session cookie and no browser runtime or first-party HTTP errors.
- BFF client secret and redirect URI matched the deployed API configuration;
  existing runtime Secret values remained unchanged.

Server-side diff resolved API-default noise. Two superseded generated Keycloak
ConfigMaps were removed after checking Argo ownership and confirming that active
pods/deployments/statefulsets did not reference them. Global pruning stayed off.

The application database has no seeded business catalog. A full customer login
through the authenticated callback was not exercised because no test account was
provided. Real payment/mail delivery, Tempo export, backup restoration, a new
infrastructure provision and capacity/failover tests were outside this rehearsal.
The configured Tempo service was absent and trace export logged DNS failures.
The tested deployment is suitable for continuing sandbox validation; production
prerequisites above remain separate work.

Local evidence and private recovery files are under `.local/`: `build-images*.log`,
`publish-images*.log`, `deploy*.log`, `final-tooling-tests.log`, `smoke-results.json`,
browser screenshots and `pre-deploy-*` backups. Preserve these privately before
discarding the worktree; they are deliberately excluded from Git.
