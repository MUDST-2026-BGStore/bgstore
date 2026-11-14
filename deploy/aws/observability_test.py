"""Validate the rendered chart contracts used by the AWS deployment."""
import base64
import configparser
import json
import runpy
import shutil
import subprocess
import unittest
from unittest.mock import patch

import manage
import observability


class GrafanaCredentialsTests(unittest.TestCase):
    def setUp(self):
        self.bootstrap = runpy.run_path(str(observability.ROOT / "admin-secret.py"))["main"]

    def test_existing_credentials_are_preserved_without_rotation(self):
        with patch("subprocess.check_output", return_value=b"secret/grafana-admin"), patch("subprocess.run") as run:
            self.bootstrap()
            run.assert_not_called()

    def test_new_install_creates_credentials_through_stdin(self):
        with patch("subprocess.check_output", return_value=b""), patch("subprocess.run") as run:
            self.bootstrap()
            secret = json.loads(run.call_args.kwargs["input"])
            self.assertEqual(secret["metadata"]["name"], "grafana-admin")
            self.assertGreater(len(base64.b64decode(secret["data"]["password"])), 32)
            self.assertNotIn(secret["data"]["password"], " ".join(run.call_args.args[0]))

    def test_existing_unmanaged_grafana_requires_explicit_adoption(self):
        with patch("subprocess.check_output", side_effect=[b"", b"secret/monitoring-grafana"]), patch("subprocess.run") as run:
            with self.assertRaisesRegex(SystemExit, "Adopt the existing Grafana login"):
                self.bootstrap()
            run.assert_not_called()


class GatewayAdoptionTests(unittest.TestCase):
    def setUp(self):
        self.module = runpy.run_path(str(observability.ROOT / "gateway.py"))

    def test_adoption_preserves_existing_listeners_and_checks_concurrent_changes(self):
        listeners = [{"name": "http", "protocol": "HTTP", "port": 80},
                     {"name": "legacy", "hostname": "keep.example.com", "protocol": "HTTPS", "port": 443}]
        gateway = {"metadata": {"resourceVersion": "42"}, "spec": {"listeners": listeners}}
        with patch("subprocess.check_output", return_value=json.dumps(gateway).encode()), patch("subprocess.run") as run:
            self.module["main"]()
            changes = json.loads(run.call_args.args[0][-1])
            self.assertEqual(changes[0], {"op": "test", "path": "/metadata/resourceVersion", "value": "42"})
            self.assertEqual(changes[1]["value"][:2], listeners)
            self.assertEqual(changes[1]["value"][2]["name"], "observe")

    def test_repeated_setup_preserves_an_existing_matching_listener(self):
        gateway = {"spec": {"listeners": [{"name": "http"}, self.module["listener"]()]}}
        with patch("subprocess.check_output", return_value=json.dumps(gateway).encode()), patch("subprocess.run") as run:
            self.module["main"]()
            run.assert_not_called()

    def test_fresh_bootstrap_leaves_shared_gateway_creation_to_routing(self):
        with patch("subprocess.check_output", return_value=b""), patch("subprocess.run") as run:
            self.module["main"]()
            run.assert_not_called()


@unittest.skipUnless(shutil.which("helm") and (manage.LOCAL / "venv/bin/python").is_file(),
                     "Install Helm and run aws-tools for chart-rendering checks")
class ObservabilityRenderingTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rendered = {}
        for release, chart, version in [
            ("monitoring", "prometheus-community/kube-prometheus-stack", "92.10.1"),
            ("loki", "grafana/loki", "7.3.0"),
            ("tempo", "grafana-community/tempo", "3.2.0"),
            ("alloy", "grafana/alloy", "1.13.1"),
        ]:
            output = subprocess.check_output([
                "helm", "template", release, chart, "--version", version,
                "--namespace", "observability", "--api-versions", "monitoring.coreos.com/v1/ServiceMonitor",
                "--values", str(observability.ROOT / (release + ".yaml")),
            ], text=True)
            cls.rendered[release] = cls.yaml(output)

    @staticmethod
    def yaml(text):
        return json.loads(subprocess.check_output([
            str(manage.LOCAL / "venv/bin/python"), "-c",
            "import json,sys,yaml; print(json.dumps(list(yaml.safe_load_all(sys.stdin))))",
        ], input=text, text=True))

    def resources(self, release, kind):
        return [r for r in self.rendered[release] if r and r.get("kind") == kind]

    def test_telemetry_services_are_private(self):
        for release in self.rendered:
            for service in self.resources(release, "Service"):
                with self.subTest(service=service["metadata"]["name"]):
                    self.assertEqual(service["spec"].get("type", "ClusterIP"), "ClusterIP")
            self.assertFalse(self.resources(release, "Ingress"))

    def test_public_route_exposes_only_grafana_with_tls_and_http_redirect(self):
        resources = self.yaml((observability.ROOT / "public.yaml").read_text())
        routing = self.yaml((manage.ROOT / "kubernetes/routing/gateway.yaml").read_text())
        gateway = next(r for r in routing if r["kind"] == "Gateway")
        listener = next(item for item in gateway["spec"]["listeners"] if item["name"] == "observe")
        adoption = runpy.run_path(str(observability.ROOT / "gateway.py"))
        self.assertEqual(listener, adoption["listener"]())
        self.assertTrue({"http", "theparlor", "keycloak", "argocd"}.issubset(
            {item["name"] for item in gateway["spec"]["listeners"]}))
        self.assertEqual(listener["hostname"], "observe.devopsandbox.chanakanlabs.com")
        self.assertEqual((listener["protocol"], listener["port"]), ("HTTPS", 443))
        self.assertEqual(listener["allowedRoutes"]["namespaces"]["selector"]["matchLabels"],
                         {"kubernetes.io/metadata.name": "observability"})
        certificate = next(r for r in resources if r["kind"] == "Certificate")
        self.assertEqual(certificate["spec"]["secretName"], listener["tls"]["certificateRefs"][0]["name"])
        self.assertEqual(certificate["spec"]["dnsNames"], [listener["hostname"]])
        routes = [r for r in resources if r["kind"] == "HTTPRoute"]
        backends = [b["name"] for r in routes for rule in r["spec"]["rules"] for b in rule.get("backendRefs", [])]
        self.assertEqual(backends, ["monitoring-grafana"])
        redirect = next(r for r in routes if r["metadata"]["name"] == "grafana-https-redirect")
        self.assertEqual(redirect["spec"]["rules"][0]["filters"][0]["requestRedirect"],
                         {"scheme": "https", "port": 443, "statusCode": 301})

    def test_grafana_public_url_requires_login_and_secure_cookies(self):
        configmap = next(r for r in self.resources("monitoring", "ConfigMap")
                         if "grafana.ini" in r.get("data", {}))
        config = configparser.ConfigParser()
        config.read_string(configmap["data"]["grafana.ini"])
        self.assertEqual(config["server"]["root_url"], "https://observe.devopsandbox.chanakanlabs.com/")
        self.assertTrue(config.getboolean("security", "cookie_secure"))
        self.assertFalse(config.getboolean("auth.anonymous", "enabled"))
        self.assertFalse(config.getboolean("users", "allow_sign_up"))

    def test_alertmanager_routes_reference_existing_receivers(self):
        secret = next(r for r in self.resources("monitoring", "Secret")
                      if "alertmanager.yaml" in r.get("data", {}))
        config = self.yaml(base64.b64decode(secret["data"]["alertmanager.yaml"]).decode())[0]
        names = {r["name"] for r in config["receivers"]}
        def check(route):
            if "receiver" in route:
                self.assertIn(route["receiver"], names)
            for child in route.get("routes", []):
                check(child)
        check(config["route"])

    def test_collectors_have_prometheus_discovery_labels(self):
        for release in ["loki", "tempo", "alloy"]:
            monitors = self.resources(release, "ServiceMonitor")
            self.assertTrue(monitors, release)
            for monitor in monitors:
                self.assertEqual(monitor["metadata"]["labels"]["release"], "monitoring")

    def test_aws_trace_endpoint_matches_a_real_tempo_service(self):
        rendered = subprocess.check_output(["kubectl", "kustomize", "deploy/k8s/overlays/aws"], text=True)
        config = next(r for r in self.yaml(rendered)
                      if r and r["kind"] == "ConfigMap" and r["metadata"]["name"] == "api-config")
        self.assertEqual(config["data"]["OTEL_EXPORTER_OTLP_TRACES_ENDPOINT"],
                         "http://tempo.observability.svc:4318/v1/traces")
        service = next(r for r in self.resources("tempo", "Service") if r["metadata"]["name"] == "tempo")
        self.assertIn(4318, [p["port"] for p in service["spec"]["ports"]])

    def test_six_components_have_persistent_storage(self):
        self.assertTrue(self.resources("monitoring", "PersistentVolumeClaim"))
        for kind in ["Prometheus", "Alertmanager"]:
            item = self.resources("monitoring", kind)[0]
            self.assertIn("volumeClaimTemplate", item["spec"]["storage"])
        for release in ["loki", "tempo", "alloy"]:
            self.assertTrue(any(r["spec"].get("volumeClaimTemplates")
                                for r in self.resources(release, "StatefulSet")), release)

    def test_dashboard_is_provisionable_with_stable_datasources(self):
        rendered = subprocess.check_output(["python3", str(observability.ROOT / "dashboards.py")], text=True)
        config = json.loads(rendered)
        self.assertEqual(config["metadata"]["labels"]["grafana_dashboard"], "1")
        dashboard = json.loads(config["data"]["theparlor-overview.json"])
        self.assertEqual(dashboard["uid"], "theparlor-overview")
        self.assertEqual({p["datasource"]["uid"] for p in dashboard["panels"]}, {"prometheus", "loki", "tempo"})
        self.assertEqual(len({p["id"] for p in dashboard["panels"]}), len(dashboard["panels"]))


if __name__ == "__main__":
    unittest.main()
