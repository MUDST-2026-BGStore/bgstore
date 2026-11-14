#!/usr/bin/env python3
"""Adopt Grafana's listener without replacing the shared Gateway configuration."""
import json
import subprocess


def listener():
    # Keep aligned with the shared routing source; a rendering test checks this.
    return {
        "name": "observe", "hostname": "observe.devopsandbox.chanakanlabs.com",
        "protocol": "HTTPS", "port": 443,
        "tls": {"mode": "Terminate", "certificateRefs": [
            {"name": "observe-tls", "group": "", "kind": "Secret"}]},
        "allowedRoutes": {"namespaces": {"from": "Selector", "selector": {
            "matchLabels": {"kubernetes.io/metadata.name": "observability"}}}},
    }


def main():
    command = ["k3s", "kubectl", "-n", "gateway"]
    raw = subprocess.check_output([*command, "get", "gateway", "public", "--ignore-not-found", "-o", "json"])
    if not raw.strip():
        # Fresh deployments obtain the full Gateway, including this listener,
        # from deploy/aws/kubernetes/routing after platform provisioning.
        print("Grafana listener will be created by the shared routing deployment")
        return
    gateway = json.loads(raw)
    current = gateway["spec"]["listeners"]
    desired = [item for item in current if item["name"] != "observe"] + [listener()]
    if sorted(current, key=lambda item: item["name"]) == sorted(desired, key=lambda item: item["name"]):
        return
    # A partial server-side apply can migrate the old client-side field owner
    # and drop unrelated listeners. JSON Patch preserves the live listeners and
    # tests resourceVersion to refuse overwriting a concurrent routing change.
    patch = [{"op": "test", "path": "/metadata/resourceVersion", "value": gateway["metadata"]["resourceVersion"]},
             {"op": "replace", "path": "/spec/listeners", "value": desired}]
    subprocess.run([*command, "patch", "gateway", "public", "--type=json", "-p", json.dumps(patch)], check=True)


if __name__ == "__main__":
    main()
