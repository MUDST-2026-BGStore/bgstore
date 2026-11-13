#!/bin/sh
# Refresh ephemeral addresses after EC2 stop/start. IMDSv2 token stays in memory.
set -eu
token=$(curl --fail --silent --show-error --retry 5 --connect-timeout 3 --max-time 15 \
  -X PUT -H 'X-aws-ec2-metadata-token-ttl-seconds: 60' \
  http://169.254.169.254/latest/api/token)
metadata() {
  curl --fail --silent --show-error --retry 3 --connect-timeout 3 --max-time 10 \
    -H "X-aws-ec2-metadata-token: $token" "http://169.254.169.254/latest/meta-data/$1"
}
private_ip=$(metadata local-ipv4)
# A private-only node is valid; public-ipv4 returns 404 when no public IP exists.
public_ip=${K3S_FIXED_EXTERNAL_IP:-$(metadata public-ipv4 2>/dev/null || true)}
config_dir=${K3S_CONFIG_DIR:-/etc/rancher/k3s/config.yaml.d}
mkdir -p "$config_dir"
umask 077
config_tmp=$(mktemp "$config_dir/.aws-address.XXXXXX")
trap 'rm -f "$config_tmp"' EXIT
printf 'node-ip: "%s"\n' "$private_ip" > "$config_tmp"
# k3s otherwise prefers node-external-ip as its API advertise address. Pods must
# reach the API on the private interface; the AWS SG deliberately closes 6443.
printf 'advertise-address: "%s"\n' "$private_ip" >> "$config_tmp"
if [ -n "$public_ip" ]; then
  printf 'node-external-ip: "%s"\ntls-san:\n  - "%s"\n' "$public_ip" "$public_ip" >> "$config_tmp"
fi
mv "$config_tmp" "$config_dir/20-aws-address.yaml"
