#!/usr/bin/env bash
set -euo pipefail

API1_CONTAINER="${API1_CONTAINER:-rinha-api-1-native-local}"
API2_CONTAINER="${API2_CONTAINER:-rinha-api-2-native-local}"
LB_CONTAINER="${LB_CONTAINER:-rinha-lb-native-local}"

print_stat() {
  local container="$1"
  echo "[$container]"
  docker exec "$container" sh -c 'cat /sys/fs/cgroup/cpu.stat' || true
  echo
}

print_stat "$LB_CONTAINER"
print_stat "$API1_CONTAINER"
print_stat "$API2_CONTAINER"
