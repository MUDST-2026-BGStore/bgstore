#!/usr/bin/env python3
"""Live end-to-end smoke check, executed on the EC2 host via SSH."""
import base64
import json
import subprocess
import time
import urllib.parse
import urllib.request


def kubectl(*args):
    return json.loads(subprocess.check_output(["k3s", "kubectl", *args, "-o", "json"]))


def endpoint(name, port, namespace="observability"):
    service = kubectl("-n", namespace, "get", "service", name)
    assert service["spec"]["type"] == "ClusterIP", f"Unexpected public service: {name}"
    return f'http://{service["spec"]["clusterIP"]}:{port}'


def get(url, headers=None):
    with urllib.request.urlopen(urllib.request.Request(url, headers=headers or {}), timeout=20) as response:
        return json.load(response)


def eventually(description, check, timeout=240):
    deadline = time.monotonic() + timeout
    error = None
    while time.monotonic() < deadline:
        try:
            result = check()
            assert result, description
            print("PASS:", description, flush=True)
            return result
        except (AssertionError, OSError, ValueError, KeyError) as exc:
            error = exc
            time.sleep(10)
    raise AssertionError(f"{description}: {error}")


def main():
    def workloads_ready():
        pods = kubectl("-n", "observability", "get", "pods")["items"]
        assert pods, "No observability pods"
        for pod in pods:
            if pod["status"]["phase"] == "Succeeded":
                continue
            assert any(c["type"] == "Ready" and c["status"] == "True"
                       for c in pod["status"].get("conditions", [])), pod["metadata"]["name"]
            for container in pod["status"].get("containerStatuses", []):
                assert container.get("lastState", {}).get("terminated", {}).get("reason") != "OOMKilled", container["name"]
        return True

    eventually("All observability workloads Ready without OOM restarts", workloads_ready)
    namespace = kubectl("get", "namespace", "observability")
    assert namespace["metadata"]["labels"].get("theparlor.io/observability") == "true"
    prometheus = endpoint("monitoring-kube-prometheus-prometheus", 9090)
    loki = endpoint("loki-gateway", 80)
    tempo = endpoint("tempo", 3200)
    grafana = endpoint("monitoring-grafana", 80)
    api = endpoint("api", 8080, "theparlor-dev")
    # Read-only real application requests produce HTTP metrics and real spans.
    for _ in range(3):
        get(api + "/api/v1/games?page=0&size=1")
        time.sleep(2)

    def query(expr):
        result = get(prometheus + "/api/v1/query?" + urllib.parse.urlencode({"query": expr}))
        assert result["status"] == "success", result
        return result["data"]["result"]

    eventually("API is scraped", lambda: query('up{job="theparlor-api"} == 1'))
    eventually("Real API request metrics", lambda: query('http_server_requests_seconds_count{job="theparlor-api",uri="/api/v1/games"}'))
    for metric in ["node_memory_MemAvailable_bytes", "node_filesystem_avail_bytes", "kube_deployment_status_replicas_available",
                   "jvm_memory_used_bytes", "hikaricp_connections_active"]:
        eventually("Dashboard metric " + metric, lambda metric=metric: query(metric))

    def healthy_targets():
        targets = get(prometheus + "/api/v1/targets")["data"]["activeTargets"]
        down = [(t["labels"].get("job"), t.get("lastError")) for t in targets if t["health"] != "up"]
        assert not down, down
        jobs = {t["labels"].get("job") for t in targets}
        assert {"alloy", "tempo", "loki"}.issubset({job.rsplit("/", 1)[-1] for job in jobs if job}), jobs
        return True

    eventually("All scrape targets healthy, including telemetry collectors", healthy_targets)
    # Nginx access logs provide a deterministic log event without restarting a
    # workload or forcing an application error on an otherwise quiet system.
    start = time.time_ns()
    with urllib.request.urlopen(endpoint("web", 8080, "theparlor-dev") + "/", timeout=20) as response:
        assert response.status == 200
        response.read()
    params = urllib.parse.urlencode({"query": '{namespace="theparlor-dev",service="web"}',
                                    "start": start, "limit": 1})
    eventually("Fresh application logs arrive through Alloy in Loki",
               lambda: get(loki + "/loki/api/v1/query_range?" + params)["data"]["result"])
    search = urllib.parse.urlencode({"q": '{ resource.service.name = "theparlor-api" && span.uri =~ "/api/.*" && kind = server }', "limit": 5})
    eventually("Real API traces arrive in Tempo", lambda: get(tempo + "/api/search?" + search).get("traces"))

    secret = kubectl("-n", "observability", "get", "secret", "grafana-admin")["data"]
    user = base64.b64decode(secret["username"]).decode()
    password = base64.b64decode(secret["password"]).decode()
    headers = {"Authorization": "Basic " + base64.b64encode(f"{user}:{password}".encode()).decode()}
    eventually("Grafana database healthy", lambda: get(grafana + "/api/health")["database"] == "ok")
    eventually("Operations dashboard automatically provisioned",
               lambda: len(get(grafana + "/api/dashboards/uid/theparlor-overview", headers)["dashboard"]["panels"]) >= 15)
    for uid in ["prometheus", "loki", "tempo"]:
        eventually("Grafana datasource " + uid,
                   lambda uid=uid: get(grafana + f"/api/datasources/uid/{uid}/health", headers)["status"] == "OK")
    claims = kubectl("-n", "observability", "get", "pvc")["items"]
    assert len(claims) == 6 and all(c["status"]["phase"] == "Bound" for c in claims), [c["metadata"]["name"] for c in claims]
    print("PASS: all six telemetry PVCs are Bound", flush=True)
    rules = get(prometheus + "/api/v1/rules")["data"]["groups"]
    assert any(g["name"] == "theparlor" and all(r["health"] == "ok" for r in g["rules"]) for g in rules), rules
    print("PASS: application and capacity alert rules loaded and evaluated", flush=True)


if __name__ == "__main__":
    main()
