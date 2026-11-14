#!/usr/bin/env python3
"""Install and verify AWS observability and authenticated public Grafana."""
import argparse
from pathlib import Path
import subprocess
import tarfile
import tempfile
import urllib.error
import urllib.request

import manage

ROOT = Path(__file__).resolve().parent / "observability"
REMOTE = "/var/tmp/theparlor-observability"


def setup(source=ROOT):
    ssh = manage.ssh_args()
    # Transfer only the reviewed observability source, without credentials or
    # unrelated local artifacts. Ansible uses the same installer on fresh hosts.
    with tempfile.TemporaryFile() as archive:
        with tarfile.open(fileobj=archive, mode="w:gz") as tar:
            for path in sorted(source.iterdir()):
                if path.is_file():
                    tar.add(path, arcname=path.name)
        archive.seek(0)
        subprocess.run([*ssh, f"sudo install -d -m 0755 -o root -g root {REMOTE} && sudo tar -xzf - -C {REMOTE}"],
                       stdin=archive, check=True)
    manage.run([*ssh, f"sudo sh {REMOTE}/install.sh"])


def check(source=ROOT):
    # The checks execute inside the host and reach ClusterIP services. Telemetry
    # endpoints remain private; no NodePort, ingress or AWS firewall changes.
    manage.run([*manage.ssh_args(), "sudo python3 -"], input_text=(source / "check.py").read_text())


def check_public():
    url = "https://observe.devopsandbox.chanakanlabs.com"
    # Default TLS verification checks the public certificate and hostname.
    with urllib.request.urlopen(url + "/login", timeout=30) as response:
        assert response.status == 200 and b"Grafana" in response.read(), "Grafana login unavailable"
    try:
        urllib.request.urlopen(url + "/api/dashboards/uid/theparlor-overview", timeout=30)
    except urllib.error.HTTPError as error:
        assert error.code == 401, f"Unexpected unauthenticated response: {error.code}"
    else:
        raise AssertionError("Grafana dashboard was accessible without authentication")
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, request, file, code, message, headers, new_url):
            return None
    try:
        urllib.request.build_opener(NoRedirect).open(url.replace("https:", "http:") + "/login", timeout=30)
    except urllib.error.HTTPError as error:
        assert error.code == 301 and error.headers["Location"] == url + "/login", "HTTPS redirect missing"
    else:
        raise AssertionError("Plain HTTP was served without a redirect")
    print("PASS: public Grafana TLS, login page, authentication requirement and HTTP-to-HTTPS redirect")


def tunnel():
    ssh = manage.ssh_args()
    # kubectl owns the remote loopback listener for the lifetime of SSH.
    manage.run([*ssh[:-1], "-L", "127.0.0.1:3300:127.0.0.1:3000",
                "-o", "ExitOnForwardFailure=yes", ssh[-1],
                "sudo k3s kubectl -n observability port-forward --address=127.0.0.1 service/monitoring-grafana 3000:80"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["setup", "check", "public-check", "tunnel"])
    args = parser.parse_args()
    {"setup": setup, "check": check, "public-check": check_public, "tunnel": tunnel}[args.command]()


if __name__ == "__main__":
    main()
