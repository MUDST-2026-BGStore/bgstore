#!/usr/bin/env python3
"""Render the provisioned dashboard ConfigMap; no external Python dependencies."""
import json


def dashboard():
    panels = []

    def panel(title, exprs, x, y, w=8, h=6, kind="timeseries", unit=None,
              datasource="prometheus", description=None):
        queries = exprs if isinstance(exprs, list) else [(exprs, title)]
        item = {
            "id": len(panels) + 1, "title": title, "type": kind,
            "gridPos": {"x": x, "y": y, "w": w, "h": h},
            "datasource": {"type": datasource, "uid": datasource},
            "targets": [dict(refId=chr(65 + i), expr=expr, legendFormat=legend)
                        for i, (expr, legend) in enumerate(queries)],
            "fieldConfig": {"defaults": {"min": 0}, "overrides": []},
        }
        if unit:
            item["fieldConfig"]["defaults"]["unit"] = unit
        if description:
            item["description"] = description
        panels.append(item)
        return item

    api = 'job="theparlor-api",uri!~"/actuator.*"'
    count = f'http_server_requests_seconds_count{{{api}}}'
    duration = f'http_server_requests_seconds_sum{{{api}}}'
    rate = lambda metric: f'rate({metric}[$__rate_interval])'
    availability = panel("API scrape availability", 'up{job="theparlor-api"}', 0, 0, 4, 4,
                         "stat", description="Management endpoint reachability; workload readiness is shown separately.")
    availability["fieldConfig"]["defaults"]["mappings"] = [{"type": "value", "options": {
        "0": {"text": "DOWN", "color": "red"}, "1": {"text": "UP", "color": "green"}}}]
    panel("API requests / second", f'sum({rate(count)})', 4, 0, 4, 4, "stat", "reqps")
    errors = f'http_server_requests_seconds_count{{{api},status=~"5.."}}'
    panel("Server error ratio", f'(sum({rate(errors)}) or (sum({rate(count)}) * 0)) / clamp_min(sum({rate(count)}), 0.001)',
          8, 0, 4, 4, "stat", "percentunit")
    panel("Mean API latency", f'sum({rate(duration)}) / sum({rate(count)})',
          12, 0, 4, 4, "stat", "s",
          description="Request-weighted mean. Percentiles require histogram buckets. Health probes excluded.")
    ready = panel("Workloads ready / desired", [
        ('kube_deployment_status_replicas_available{namespace="theparlor-dev"} / kube_deployment_spec_replicas{namespace="theparlor-dev"}', '{{deployment}}'),
        ('kube_statefulset_status_replicas_ready{namespace="theparlor-dev"} / kube_statefulset_replicas{namespace="theparlor-dev"}', '{{statefulset}}'),
    ], 16, 0, 8, 4, "stat", "percentunit")
    for target in ready["targets"]:
        target["instant"] = True
    panel("API traffic by route and status", [
        (f'sum by (uri, status) ({rate(count)})', '{{uri}} {{status}}')],
        0, 4, 12, 7, unit="reqps")
    panel("Mean latency by route", [
        (f'sum by (uri) ({rate(duration)}) / sum by (uri) ({rate(count)})', '{{uri}}')],
        12, 4, 12, 7, unit="s")
    panel("EC2 CPU used", '1 - avg(rate(node_cpu_seconds_total{mode="idle"}[$__rate_interval]))',
          0, 11, unit="percentunit")
    panel("EC2 memory used", '1 - node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes',
          8, 11, unit="percentunit")
    panel("Root disk free", 'node_filesystem_avail_bytes{mountpoint="/",fstype!~"tmpfs|overlay"}',
          16, 11, unit="bytes")
    panel("JVM heap used / committed", [
        ('sum(jvm_memory_used_bytes{job="theparlor-api",area="heap"})', 'used'),
        ('sum(jvm_memory_committed_bytes{job="theparlor-api",area="heap"})', 'committed'),
    ], 0, 17, unit="bytes")
    panel("Database connection pool", [
        ('hikaricp_connections_active{job="theparlor-api"}', 'active'),
        ('hikaricp_connections_pending{job="theparlor-api"}', 'pending'),
        ('hikaricp_connections_max{job="theparlor-api"}', 'maximum'),
    ], 8, 17, description="API connection-pool saturation; database-internal metrics require a separate exporter.")
    panel("Container restarts (15m)", [
        ('sum by (container) (increase(kube_pod_container_status_restarts_total{namespace="theparlor-dev"}[15m]))', '{{container}}'),
    ], 16, 17)
    logs = panel("Application warnings and errors", '{namespace="theparlor-dev",level=~"WARN|ERROR"}',
                 0, 23, 24, 9, "logs", datasource="loki")
    logs["options"] = {"showTime": True, "wrapLogMessage": False,
                       "prettifyLogMessage": False, "sortOrder": "Descending", "enableLogDetails": True}
    logs["targets"][0]["queryType"] = "range"
    targets = panel("Telemetry targets", [('min by (job) (up{namespace="observability"})', '{{job}}')],
                    0, 32, 12, 5, "stat")
    targets["targets"][0]["instant"] = True
    alerts = panel("Firing alerts", 'ALERTS{alertstate="firing"}', 12, 32, 12, 5, "table")
    alerts["targets"][0].update(instant=True, format="table")
    traces = panel("Recent API traces", "", 0, 37, 24, 7, "table", datasource="tempo")
    traces["targets"] = [{"refId": "A", "queryType": "traceql",
                          "query": '{ resource.service.name = "theparlor-api" && span.uri =~ "/api/.*" && kind = server }', "limit": 20}]
    traces["transformations"] = [{"id": "organize", "options": {"excludeByName": {"nested": True}}}]
    panel("Telemetry container memory / limit", [
        ('sum by (container) (container_memory_working_set_bytes{namespace="observability",container!="",image!=""}) / sum by (container) (kube_pod_container_resource_limits{namespace="observability",resource="memory"})', '{{container}}'),
    ], 0, 44, 24, 6, unit="percentunit",
          description="Configured container memory limits; sustained saturation risks OOM termination.")
    return {"uid": "theparlor-overview", "title": "The Parlor — Operations",
            "schemaVersion": 39, "version": 1, "editable": False, "refresh": "30s",
            "time": {"from": "now-1h", "to": "now"}, "tags": ["theparlor"],
            "panels": panels, "links": [{"title": "Explore logs and traces",
                                        "url": "/explore", "type": "link"}]}


def configmap():
    return {"apiVersion": "v1", "kind": "ConfigMap",
            "metadata": {"name": "theparlor-dashboards", "namespace": "observability",
                         "labels": {"grafana_dashboard": "1"}},
            "data": {"theparlor-overview.json": json.dumps(dashboard(), ensure_ascii=False)}}


if __name__ == "__main__":
    print(json.dumps(configmap(), ensure_ascii=False))
