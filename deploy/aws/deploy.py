#!/usr/bin/env python3
"""Publish an isolated sandbox snapshot and exercise the real Argo CD flow."""

import argparse
import datetime
import ipaddress
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import time

import manage
import observability

REPO = manage.ROOT.parent.parent
METADATA = manage.LOCAL / "deployment.json"


def publish_snapshot(tag, branch):
    if not re.fullmatch(r"[A-Za-z0-9_.-]+", tag):
        raise SystemExit("An image tag may contain letters, numbers, dots, dashes and underscores.")
    if not re.fullmatch(r"codex/[A-Za-z0-9/_-]+", branch):
        raise SystemExit("Use a codex/ branch for a rehearsal snapshot.")
    outputs = json.loads(manage.tofu("output", "-json", capture=True))
    repositories = outputs.get("ecr_repository_urls", {}).get("value", {})
    if set(repositories) != {"api", "web"}:
        raise SystemExit("Provision the ECR repositories before publishing a deployment snapshot.")
    host, _ = manage.connection()
    address = ipaddress.ip_address(host)
    if address.version != 4 or address.is_private:
        raise SystemExit("The rehearsal needs a public IPv4 address.")
    # The sandbox's real DNS records (devopsandbox.chanakanlabs.com) already
    # point at the Elastic IP; manifests keep these names, so nothing is rewritten.
    # Set SANDBOX_HOSTNAMES=sslip to rehearse on IP-based names instead.
    domain = "devopsandbox.chanakanlabs.com"
    configured = {"theparlor": f"bgstore.{domain}", "auth": f"auth.{domain}", "argocd": f"argocd.{domain}"}
    if os.environ.get("SANDBOX_HOSTNAMES") == "sslip":
        hosts = {name: f"{name}.{address}.sslip.io" for name in configured}
    else:
        hosts = configured
    source = manage.LOCAL / "deployment-source"
    if source.exists():
        shutil.rmtree(source)
    source.mkdir(mode=0o700)
    paths = subprocess.check_output(["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"], cwd=REPO).split(b"\0")
    for raw in paths:
        if not raw:
            continue
        relative = Path(os.fsdecode(raw))
        if any(part in {".git", ".local", ".terraform", ".aws", ".ssh", "node_modules"} for part in relative.parts):
            raise SystemExit(f"Refusing to publish a private/generated path: {relative}")
        if relative.name in {".env", ".env.local"} or relative.name.endswith((".tfstate", ".tfplan", ".auto.tfvars", ".auto.tfvars.json")):
            raise SystemExit(f"Refusing to publish a private/generated file: {relative}")
        original = REPO / relative
        if not original.exists():
            continue
        if original.is_symlink():
            raise SystemExit(f"Review symlinks before publishing the snapshot: {relative}")
        destination = source / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(original, destination)

    # IP-based DNS lets the rehearsal obtain real HTTPS certificates without
    # changing records in the user's external DNS account.
    directories = [source / "deploy/k8s/overlays/aws", source / "deploy/aws/kubernetes"]
    for directory in directories:
        for path in directory.rglob("*.yaml"):
            text = path.read_text()
            for name, hostname in hosts.items():
                text = text.replace(configured[name], hostname)
            if path.name == "application.yaml":
                text = text.replace("targetRevision: main", f"targetRevision: {branch}")
                text = text.replace("skip-reconcile: 'true'", "skip-reconcile: 'false'")
            path.write_text(text)
    kustomization = source / "deploy/k8s/overlays/aws/kustomization.yaml"
    text = kustomization.read_text()
    for app, repository in repositories.items():
        # Match the established base image names; only the AWS overlay changes.
        text = re.sub(rf"newName: \S*/theparlor-{app}\b", f"newName: {repository}", text)
    text = re.sub(r"newTag: \S+", f"newTag: {tag}", text)
    kustomization.write_text(text)
    manage.run(["kubectl", "kustomize", source / "deploy/k8s/overlays/aws"], capture=True)
    manage.run(["kubectl", "kustomize", source / "deploy/aws/kubernetes/routing"], capture=True)

    # A temporary index leaves the user's branch, index and uncommitted work
    # untouched while publishing exactly the source copied above.
    git_dir = manage.run(["git", "rev-parse", "--absolute-git-dir"], cwd=REPO, capture=True).strip()
    index = manage.LOCAL / "deployment.index"
    index.unlink(missing_ok=True)
    env = dict(os.environ, GIT_INDEX_FILE=str(index))
    git = ["git", f"--git-dir={git_dir}", f"--work-tree={source}"]
    manage.run([*git, "read-tree", "HEAD"], env=env)
    manage.run([*git, "add", "-A", "--", "."], env=env, cwd=source)
    tree = manage.run([*git, "write-tree"], env=env, capture=True).strip()
    remote = manage.run(["git", "ls-remote", "origin", f"refs/heads/{branch}"], cwd=REPO, capture=True).strip()
    if remote:
        manage.run(["git", "fetch", "origin", f"refs/heads/{branch}"], cwd=REPO)
        parent = remote.split()[0]
    else:
        parent = manage.run(["git", "rev-parse", "HEAD"], cwd=REPO, capture=True).strip()
    commit = manage.run(["git", "commit-tree", tree, "-p", parent, "-m", "deploy: rehearse The Parlor on a fresh AWS sandbox"], cwd=REPO, capture=True).strip()
    manage.run(["git", "push", "origin", f"{commit}:refs/heads/{branch}"], cwd=REPO)
    index.unlink(missing_ok=True)
    metadata = {"branch": branch, "commit": commit, "image_tag": tag, "hosts": hosts, "source": str(source)}
    METADATA.write_text(json.dumps(metadata, indent=2) + "\n")
    METADATA.chmod(0o600)
    print(json.dumps({key: value for key, value in metadata.items() if key != "source"}, indent=2))


def sync(routing_only=False):
    metadata = json.loads(METADATA.read_text())
    source = Path(metadata["source"])
    if not routing_only:
        observability.setup(source / "deploy/aws/observability")
    manage.application_secrets()
    if not routing_only:
        manage.payment_secrets()
    ssh = manage.ssh_args()
    definitions = "\n---\n".join((source / "deploy/aws/kubernetes" / name).read_text() for name in ["project.yaml", "application.yaml"])
    manage.run([*ssh, "sudo k3s kubectl apply --server-side --field-manager=theparlor-bootstrap -f -"], input_text=definitions)
    for app in (["theparlor-routing"] if routing_only else ["theparlor-routing", "theparlor-dev"]):
        operation = json.dumps({"operation": {"sync": {"revision": metadata["commit"], "prune": False}}})
        command = "sudo k3s kubectl -n argocd patch application " + app + " --type=merge -p " + shlex.quote(operation)
        manage.run([*ssh, command])
        deadline = time.monotonic() + 900
        last_phase = None
        while time.monotonic() < deadline:
            data = json.loads(manage.run([*ssh, f"sudo k3s kubectl -n argocd get application {app} -o json"], capture=True))
            state = data.get("status", {}).get("operationState", {})
            phase = state.get("phase", "Pending")
            if phase != last_phase:
                print(f"{app}: {phase}", flush=True)
                last_phase = phase
            revision = state.get("syncResult", {}).get("revision")
            if phase == "Succeeded" and revision == metadata["commit"]:
                break
            if phase in ["Failed", "Error"] and revision == metadata["commit"]:
                raise SystemExit(f"{app}: {state.get('message', phase)}")
            time.sleep(5)
        else:
            raise SystemExit(f"Timed out waiting for {app} to sync.")
    if not routing_only:
        manage.run([*ssh, "set -e; sudo k3s kubectl -n theparlor-dev rollout status statefulset/postgres --timeout=300s; "
                "sudo k3s kubectl -n theparlor-dev rollout status deployment/api --timeout=300s; "
                "sudo k3s kubectl -n theparlor-dev rollout status deployment/web --timeout=180s; "
                "sudo k3s kubectl -n theparlor-dev rollout status deployment/keycloak --timeout=300s"])
    if not routing_only:
        observability.check(source / "deploy/aws/observability")
    manage.run([*ssh, "sudo k3s kubectl -n gateway wait --for=condition=Ready certificate --all --timeout=600s"])
    if not routing_only:
        observability.check_public()
    print(f"Deployment ready at https://{metadata['hosts']['theparlor']}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["publish-snapshot", "sync", "sync-routing"])
    parser.add_argument("--tag", default=os.environ.get("IMAGE_TAG", "rehearsal-20261105-r1"))
    parser.add_argument("--branch", default="codex/aws-rehearsal-" + datetime.date.today().strftime("%Y%m%d"))
    args = parser.parse_args()
    os.umask(0o077)
    if args.command == "publish-snapshot":
        publish_snapshot(args.tag, args.branch)
    else:
        sync(routing_only=args.command == "sync-routing")


if __name__ == "__main__":
    main()
