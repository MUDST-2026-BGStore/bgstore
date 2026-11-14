#!/usr/bin/env python3
"""Local commands for the AWS stack; run `make help` for entry points."""

import argparse
import configparser
import hashlib
import json
import os
from pathlib import Path
import platform
import secrets
import shlex
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parent
LOCAL = ROOT / ".local"
TOFU_VERSION = "1.13.8"
PLAN = LOCAL / "reviewed.tfplan"


def run(args, *, capture=False, cwd=ROOT, env=None, input_text=None):
    return subprocess.run(
        [str(arg) for arg in args], cwd=cwd, check=True, text=True,
        stdout=subprocess.PIPE if capture else None, env=env, input=input_text,
    ).stdout


def tofu(*args, capture=False):
    binary = os.environ.get("TOFU_BIN") or str(LOCAL / "bin/tofu")
    if not Path(binary).is_file():
        binary = shutil.which("tofu")
    if not binary:
        raise SystemExit("Run make aws-tools first, or set TOFU_BIN to your OpenTofu executable.")
    env = dict(os.environ)
    # AWS CLI accepts quoted SSO section names that the provider SDK does not.
    # Normalize a private local copy, leaving the user's AWS config unchanged.
    config_path = Path(env.get("AWS_CONFIG_FILE", str(Path.home() / ".aws/config")))
    if config_path.exists():
        config = configparser.RawConfigParser()
        config.read(config_path)
        changed = False
        for section in list(config.sections()):
            if section.startswith("sso-session "):
                name = section[len("sso-session "):]
                normalized = "sso-session " + name.strip("\"'")
                if normalized != section:
                    config[normalized] = dict(config[section])
                    config.remove_section(section)
                    changed = True
        if changed:
            normalized_path = LOCAL / "aws-config"
            with normalized_path.open("w") as output:
                config.write(output)
            normalized_path.chmod(0o600)
            env["AWS_CONFIG_FILE"] = str(normalized_path)
    return run([binary, *args], capture=capture, env=env)


def download(url, dest):
    run(["curl", "--fail", "--silent", "--show-error", "--location",
         "--connect-timeout", "15", "--max-time", "600", "--retry", "2",
         url, "--output", dest])


def tools():
    """Install into the ignored stack directory without changing system tools."""
    system = platform.system().lower()
    machine = {"arm64": "arm64", "aarch64": "arm64", "x86_64": "amd64"}.get(platform.machine())
    if system not in ["darwin", "linux"] or not machine:
        raise SystemExit("Install OpenTofu and Ansible manually on this platform.")
    bindir = LOCAL / "bin"
    bindir.mkdir(exist_ok=True)
    name = f"tofu_{TOFU_VERSION}_{system}_{machine}.zip"
    base = f"https://github.com/opentofu/opentofu/releases/download/v{TOFU_VERSION}"
    if not (bindir / "tofu").exists():
        download(f"{base}/{name}", LOCAL / name)
        download(f"{base}/tofu_{TOFU_VERSION}_SHA256SUMS", LOCAL / "SHA256SUMS")
        expected = next(line.split()[0] for line in (LOCAL / "SHA256SUMS").read_text().splitlines()
                        if line.split()[-1].lstrip("*") == name)
        if hashlib.sha256((LOCAL / name).read_bytes()).hexdigest() != expected:
            raise SystemExit("OpenTofu archive checksum did not match the official release.")
        with zipfile.ZipFile(LOCAL / name) as archive:
            (bindir / "tofu").write_bytes(archive.read("tofu"))
        (bindir / "tofu").chmod(0o755)
        (LOCAL / name).unlink()
    python = os.environ.get("ANSIBLE_PYTHON") or next(
        (shutil.which(name) for name in ["python3.14", "python3.13", "python3.12"] if shutil.which(name)), None)
    if not python:
        raise SystemExit("Install Python 3.12–3.14 for Ansible, or set ANSIBLE_PYTHON.")
    venv = LOCAL / "venv"
    if not (venv / "bin/ansible-playbook").exists():
        if shutil.which("uv"):
            env = dict(os.environ)
            env.setdefault("UV_CACHE_DIR", str(LOCAL / "uv-cache"))
            run(["uv", "venv", "--python", python, venv], env=env)
            run(["uv", "pip", "install", "--python", venv / "bin/python",
                 "-r", ROOT / "ansible/requirements.txt"], env=env)
        else:
            run([python, "-m", "venv", venv])
            run([venv / "bin/python", "-m", "pip", "install", "-r", ROOT / "ansible/requirements.txt"])
    tofu("version")
    print(f"Tools are installed in {LOCAL}; no global tools were changed.")


def plan():
    # Remove an earlier plan first, so a failed plan cannot leave an apparently
    # current file for the apply command.
    PLAN.unlink(missing_ok=True)
    tofu("plan", "-input=false", f"-out={PLAN}")
    PLAN.chmod(0o600)


def adopt():
    plan()
    data = json.loads(tofu("show", "-json", PLAN, capture=True))
    mutations = [change["address"] for change in data.get("resource_changes", [])
                 if change["change"]["actions"] not in [["no-op"], ["read"]]]
    if mutations:
        PLAN.unlink(missing_ok=True)
        raise SystemExit("Adoption refused because AWS changes were proposed: " + ", ".join(mutations))
    print("Verified: the adoption plan only imports/reads existing resources and updates local state.")
    tofu("apply", "-input=false", PLAN)
    PLAN.unlink(missing_ok=True)


def connection():
    context = {}
    if (ROOT / "terraform.tfstate").exists() or (ROOT / ".terraform/terraform.tfstate").exists():
        outputs = json.loads(tofu("output", "-json", capture=True))
        context = outputs.get("aws_context", {}).get("value", {})
        context.setdefault("instance_id", outputs.get("instance_id", {}).get("value"))
    profile = os.environ.get("AWS_PROFILE") or context.get("profile", "chanakanlabs")
    region = os.environ.get("AWS_REGION") or context.get("region", "ap-southeast-7")
    instance = os.environ.get("AWS_INSTANCE_ID") or context.get("instance_id") or "i-0e352f3678fd3f205"
    # Read current AWS address each time: an ephemeral IP changes after stop/start.
    result = json.loads(run(["aws", "--profile", profile, "--region", region,
        "ec2", "describe-instances", "--instance-ids", instance, "--output", "json"], capture=True))
    machine = result["Reservations"][0]["Instances"][0]
    host = os.environ.get("SSH_HOST") or machine.get("PublicIpAddress") or machine["PrivateIpAddress"]
    return host, machine


def ssh_args():
    host, _ = connection()
    key = Path(os.environ.get("SSH_KEY", "~/aws")).expanduser()
    if not key.is_file():
        raise SystemExit(f"SSH private key not found: {key}. Set SSH_KEY to its path.")
    return ["ssh", "-i", str(key), "-o", "IdentitiesOnly=yes", "-o", "StrictHostKeyChecking=accept-new",
            "-o", f"UserKnownHostsFile={LOCAL / 'known_hosts'}", "-o", "ConnectTimeout=20",
            f"{os.environ.get('SSH_USER', 'ec2-user')}@{host}"]


def inventory():
    host, machine = connection()
    if machine["State"]["Name"] != "running":
        raise SystemExit("The instance must be running before machine setup. Review instance_state in OpenTofu.")
    context = json.loads(tofu("output", "-json", capture=True)).get("aws_context", {}).get("value", {})
    data = {"all": {"children": {"k3s_servers": {"hosts": {"devopsandbox": {
        "ansible_host": host,
        "ansible_user": os.environ.get("SSH_USER", "ec2-user"),
        "ansible_ssh_private_key_file": str(Path(os.environ.get("SSH_KEY", "~/aws")).expanduser()),
        "ansible_ssh_common_args": shlex.join(["-o", "IdentitiesOnly=yes", "-o", "StrictHostKeyChecking=accept-new",
            "-o", f"UserKnownHostsFile={LOCAL / 'known_hosts'}"]),
        "enable_ecr_credentials": context.get("enable_ecr", False),
    }}}}}}
    path = LOCAL / "inventory.json"
    path.write_text(json.dumps(data, indent=2) + "\n")
    path.chmod(0o600)
    return path


def ansible(*args):
    executable = os.environ.get("ANSIBLE_PLAYBOOK_BIN", str(LOCAL / "venv/bin/ansible-playbook"))
    if not Path(executable).is_file():
        raise SystemExit("Run make aws-tools first to install the local Ansible environment.")
    env = dict(os.environ)
    env["ANSIBLE_LOCAL_TEMP"] = str(LOCAL / "ansible-tmp")
    if "--syntax-check" in args:
        inventory_path = LOCAL / "syntax-inventory.json"
        inventory_path.write_text(json.dumps({"all": {"children": {"k3s_servers": {"hosts": {"syntax_check": {}}}}}}))
    else:
        inventory_path = inventory()
    return run([executable, "-i", inventory_path, ROOT / "ansible/site.yaml", *args], cwd=ROOT / "ansible", env=env)


def application_secrets():
    ssh = ssh_args()
    run([*ssh, "sudo k3s kubectl create namespace theparlor-dev --dry-run=client -o yaml | sudo k3s kubectl apply -f -"])
    existing = run([*ssh, "sudo k3s kubectl -n theparlor-dev get secret theparlor-runtime --ignore-not-found -o name"], capture=True)
    if existing.strip():
        print("theparlor-runtime already exists; its credentials were left unchanged.")
        return
    path = LOCAL / "theparlor-runtime.json"
    if not path.exists():
        value = {
            "apiVersion": "v1", "kind": "Secret", "type": "Opaque",
            "metadata": {"name": "theparlor-runtime", "namespace": "theparlor-dev"},
            "stringData": {
                "SPRING_DATASOURCE_URL": "jdbc:postgresql://postgres:5432/theparlor",
                "KC_DB_URL": "jdbc:postgresql://postgres:5432/keycloak",
                "POSTGRES_USER": "theparlor", "POSTGRES_PASSWORD": secrets.token_urlsafe(36),
                "KC_BOOTSTRAP_ADMIN_USERNAME": "admin", "KC_BOOTSTRAP_ADMIN_PASSWORD": secrets.token_urlsafe(36),
                "THEPARLOR_OIDC_CLIENT_SECRET": secrets.token_urlsafe(36),
                "VALKEY_HOST": "valkey", "VALKEY_PASSWORD": secrets.token_urlsafe(36),
            },
        }
        path.write_text(json.dumps(value, indent=2) + "\n")
        path.chmod(0o600)
    run([*ssh, "sudo k3s kubectl apply -f -"], input_text=path.read_text())
    print(f"Created theparlor-runtime; its private recovery copy is {path} (ignored by Git).")


OMISE_KEYS = ("THEPARLOR_OMISE_PUBLIC_KEY", "THEPARLOR_OMISE_SECRET_KEY")


def omise_keys(env=None, dotenv=ROOT.parent.parent / ".env"):
    """The Omise keys from the environment, else the repository's .env; never printed."""
    values = {name: (env if env is not None else os.environ).get(name, "").strip() for name in OMISE_KEYS}
    if not all(values.values()) and dotenv.is_file():
        for line in dotenv.read_text().splitlines():
            name, _, value = line.partition("=")
            if name.strip() in OMISE_KEYS and not values[name.strip()]:
                values[name.strip()] = value.strip().strip("\"'")
    public, secret = values[OMISE_KEYS[0]], values[OMISE_KEYS[1]]
    if not public or not secret:
        raise SystemExit("Set THEPARLOR_OMISE_PUBLIC_KEY and THEPARLOR_OMISE_SECRET_KEY in your "
                         "environment or the repository .env; the AWS API pays through Omise.")
    if not public.startswith("pkey_") or not secret.startswith("skey_"):
        raise SystemExit("The Omise keys must be a public key (pkey_...) and a secret key (skey_...).")
    if ("_test_" in public) != ("_test_" in secret):
        raise SystemExit("The Omise public and secret keys must both be test keys or both be live keys.")
    return values


def payment_secrets():
    """Copies the Omise keys into theparlor-runtime. Unlike generated credentials they can be
    replaced at any time, so this runs on every deploy."""
    values = omise_keys()
    patch = json.dumps({"stringData": values})
    ssh = ssh_args()
    # The keys travel on stdin, never on a command line or in a local file.
    result = run([*ssh, "sudo k3s kubectl -n theparlor-dev patch secret theparlor-runtime "
                  "--type=merge --patch-file=/dev/stdin"], input_text=patch, capture=True)
    mode = "test" if "_test_" in values[OMISE_KEYS[0]] else "LIVE"
    if "no change" in result:
        print(f"Omise {mode} keys in theparlor-runtime are already current.")
        return
    print(f"Omise {mode} keys stored in theparlor-runtime.")
    # Environment variables are read at start-up; a running API keeps the old keys until restarted.
    run([*ssh, "if sudo k3s kubectl -n theparlor-dev get deployment api -o name >/dev/null 2>&1; "
         "then sudo k3s kubectl -n theparlor-dev rollout restart deployment/api; fi"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["tools", "init", "fmt", "validate", "plan", "adopt", "apply",
        "setup-check", "setup", "inventory", "ssh", "argocd", "kube-tunnel", "kubeconfig", "status",
        "secrets", "gitops-enable", "build-images", "publish-images"])
    args = parser.parse_args()
    LOCAL.mkdir(exist_ok=True, mode=0o700)
    os.umask(0o077)
    if args.command == "tools":
        tools()
    elif args.command == "init":
        tofu("init", "-input=false")
    elif args.command == "fmt":
        tofu("fmt", "-check", "-recursive")
    elif args.command == "validate":
        tofu("validate")
        ansible("--syntax-check")
    elif args.command == "plan":
        plan()
    elif args.command == "adopt":
        adopt()
    elif args.command == "apply":
        if not PLAN.exists():
            raise SystemExit("Run make aws-plan and review the proposed changes first.")
        tofu("apply", "-input=false", PLAN)
        PLAN.unlink(missing_ok=True)
    elif args.command == "inventory":
        print(inventory())
    elif args.command == "setup-check":
        ansible("--check")
    elif args.command == "setup":
        ansible()
    elif args.command == "secrets":
        application_secrets()
        payment_secrets()
    elif args.command == "gitops-enable":
        # Enable comparisons, not sync. Publishing and manual sync stay explicit.
        run([*ssh_args(), "sudo k3s kubectl -n argocd annotate application theparlor-dev theparlor-routing argocd.argoproj.io/skip-reconcile-"])
        print("Reconciliation enabled. Review both applications and sync routing, then theparlor-dev, in Argo CD.")
    elif args.command == "build-images":
        tag = os.environ.get("IMAGE_TAG")
        if not tag:
            raise SystemExit("Set IMAGE_TAG to the immutable deployment tag.")
        env = dict(os.environ, NX_DAEMON="false", NX_ISOLATE_PLUGINS="false", VITE_API_BASE_URL="")
        for app in ["api", "web"]:
            run(["pnpm", "nx", "run", f"{app}:build", "--output-style=stream"], cwd=ROOT.parent.parent, env=env)
            artifacts = f"apps/{app}/build/libs" if app == "api" else "apps/web/dist"
            run(["docker", "buildx", "build", "--platform", "linux/arm64", "--load", "--progress=plain",
                 "--target", "artifacts-runtime", "--build-context", f"artifacts={artifacts}",
                 "--file", f"deploy/docker/{app}.Dockerfile",
                 "--tag", f"theparlor-{app}:{tag}", "."], cwd=ROOT.parent.parent)
            if app == "api":
                # The JVM runs as UID 10001 and fails at startup on an unreadable jar.
                run(["docker", "run", "--rm", "--entrypoint", "sh", f"theparlor-api:{tag}", "-c", "test -r /app/app.jar"])
            if app == "web":
                # Exercise the runtime UID: nginx's /health endpoint also passes
                # when host artifacts are root-owned and unreadable (umask 077).
                run(["docker", "run", "--rm", "--entrypoint", "sh", f"theparlor-web:{tag}", "-c",
                     "test -r /usr/share/nginx/html/index.html && test -x /usr/share/nginx/html/assets"])
    elif args.command == "publish-images":
        tag = os.environ.get("IMAGE_TAG")
        if not tag:
            raise SystemExit("Set IMAGE_TAG to the tag used by make aws-build-images.")
        outputs = json.loads(tofu("output", "-json", capture=True))
        repositories = outputs.get("ecr_repository_urls", {}).get("value", {})
        if not repositories:
            raise SystemExit("Enable ECR and apply the infrastructure plan before publishing images.")
        context = outputs["aws_context"]["value"]
        password = run(["aws", "--profile", context["profile"], "--region", context["region"],
                        "ecr", "get-login-password"], capture=True)
        docker_config = LOCAL / "docker"
        docker_config.mkdir(exist_ok=True, mode=0o700)
        env = dict(os.environ, DOCKER_CONFIG=str(docker_config))
        registry = next(iter(repositories.values())).split("/")[0]
        run(["docker", "login", "--username", "AWS", "--password-stdin", registry], input_text=password, env=env)
        for app, repository in repositories.items():
            run(["docker", "tag", f"theparlor-{app}:{tag}", f"{repository}:{tag}"])
            run(["docker", "push", f"{repository}:{tag}"], env=env)
    elif args.command == "ssh":
        run(ssh_args())
    elif args.command == "argocd":
        # Reuse the existing NodePort on loopback. The AWS SG never opens it.
        print("Argo CD: http://localhost:8085 (SSH tunnel; Ctrl+C to close).", flush=True)
        ssh = ssh_args()
        run(ssh[:-1] + ["-N", "-L", "127.0.0.1:8085:127.0.0.1:31080", "-o", "ExitOnForwardFailure=yes", ssh[-1]])
    elif args.command == "kube-tunnel":
        print("Kubernetes API tunnel on https://127.0.0.1:6443; Ctrl+C to close.", flush=True)
        ssh = ssh_args()
        run(ssh[:-1] + ["-N", "-L", "127.0.0.1:6443:127.0.0.1:6443", "-o", "ExitOnForwardFailure=yes", ssh[-1]])
    elif args.command == "kubeconfig":
        path = LOCAL / "kubeconfig"
        path.write_text(run([*ssh_args(), "sudo cat /etc/rancher/k3s/k3s.yaml"], capture=True))
        path.chmod(0o600)
        print(f"Saved admin credentials to {path} (mode 0600, ignored by Git).")
        print(f"Run make aws-kube-tunnel, then: KUBECONFIG={shlex.quote(str(path))} kubectl get nodes")
    elif args.command == "status":
        run([*ssh_args(), "sudo k3s kubectl get nodes -o wide; sudo k3s kubectl get pods -A; sudo k3s kubectl get applications.argoproj.io -A"])


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as error:
        sys.exit(error.returncode)
