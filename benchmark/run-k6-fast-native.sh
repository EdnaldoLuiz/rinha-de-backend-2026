#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

export REBUILD=0
export COMPOSE_FILE="${COMPOSE_FILE:-$SCRIPT_DIR/docker-compose.native.yml}"
export API1_CONTAINER="${API1_CONTAINER:-rinha-api-1-native-local}"
export API2_CONTAINER="${API2_CONTAINER:-rinha-api-2-native-local}"
export LB_CONTAINER="${LB_CONTAINER:-rinha-lb-native-local}"

exec "$SCRIPT_DIR/run-k6-native.sh"
