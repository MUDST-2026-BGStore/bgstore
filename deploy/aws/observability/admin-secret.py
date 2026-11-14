#!/usr/bin/env python3
"""Create Grafana bootstrap credentials once, preserving them across upgrades."""
import base64
import json
import secrets
import subprocess


def main():
    command = ["k3s", "kubectl", "-n", "observability"]
    existing = subprocess.check_output([*command, "get", "secret", "grafana-admin", "--ignore-not-found", "-o", "name"])
    if existing.strip():
        return
    legacy = subprocess.check_output([*command, "get", "secret", "monitoring-grafana", "--ignore-not-found", "-o", "name"])
    if legacy.strip():
        raise SystemExit("Adopt the existing Grafana login into grafana-admin before continuing; refusing to rotate credentials.")
    document = {"apiVersion": "v1", "kind": "Secret",
                "metadata": {"name": "grafana-admin", "namespace": "observability"},
                "type": "Opaque", "data": {
                    "username": base64.b64encode(b"admin").decode(),
                    "password": base64.b64encode(secrets.token_urlsafe(32).encode()).decode(),
                }}
    # Credentials never appear in command arguments, source files or output.
    subprocess.run([*command, "create", "-f", "-"], input=json.dumps(document), text=True, check=True)


if __name__ == "__main__":
    main()
