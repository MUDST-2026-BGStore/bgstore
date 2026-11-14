#!/bin/sh
# Persistent observability with an authenticated HTTPS Grafana entrypoint.
set -eu
cd "$(dirname "$0")"
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
k3s kubectl create namespace observability --dry-run=client -o yaml | k3s kubectl apply -f -
# api-ingress permits the management port only from namespaces with this label.
k3s kubectl label namespace observability theparlor.io/observability=true --overwrite
python3 admin-secret.py
helm upgrade --install monitoring kube-prometheus-stack \
  --repo https://prometheus-community.github.io/helm-charts --version 92.10.1 \
  --namespace observability --create-namespace --values monitoring.yaml --wait --timeout 10m
python3 dashboards.py | k3s kubectl apply -f -
k3s kubectl -n observability wait --for=condition=Available alertmanager/monitoring-kube-prometheus-alertmanager --timeout=300s
k3s kubectl -n observability wait --for=condition=Available prometheus/monitoring-kube-prometheus-prometheus --timeout=300s
helm upgrade --install loki loki \
  --repo https://grafana.github.io/helm-charts --version 7.3.0 \
  --namespace observability --values loki.yaml --wait --timeout 10m
helm upgrade --install tempo tempo \
  --repo https://grafana-community.github.io/helm-charts --version 3.2.0 \
  --namespace observability --values tempo.yaml --wait --timeout 10m
helm upgrade --install alloy alloy \
  --repo https://grafana.github.io/helm-charts --version 1.13.1 \
  --namespace observability --values alloy.yaml --wait --timeout 10m
# Certificate issuance uses the HTTP listener and ClusterIssuer provisioned by
# the shared routing deployment. Its certificate readiness is checked by sync.
k3s kubectl create namespace gateway --dry-run=client -o yaml | k3s kubectl apply -f -
python3 gateway.py
k3s kubectl apply --server-side --field-manager=theparlor-observability -f public.yaml
