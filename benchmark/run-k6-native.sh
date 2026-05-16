#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

export COMPOSE_FILE="${COMPOSE_FILE:-$SCRIPT_DIR/docker-compose.native.yml}"
export API1_CONTAINER="${API1_CONTAINER:-rinha-api-1-native-local}"
export API2_CONTAINER="${API2_CONTAINER:-rinha-api-2-native-local}"
export LB_CONTAINER="${LB_CONTAINER:-rinha-lb-native-local}"

export INDEX_MODE="${INDEX_MODE:-ivf}"
export IVF_CLUSTERS="${IVF_CLUSTERS:-4096}"
export IVF_FAST_NPROBE="${IVF_FAST_NPROBE:-8}"
export IVF_FULL_NPROBE="${IVF_FULL_NPROBE:-32}"
export IVF_SAMPLE_SIZE="${IVF_SAMPLE_SIZE:-50000}"
export IVF_ITERATIONS="${IVF_ITERATIONS:-12}"
export IVF_BBOX_REPAIR="${IVF_BBOX_REPAIR:-0}"
export IVF_STATS="${IVF_STATS:-0}"
export FALLBACK_STATS="${FALLBACK_STATS:-0}"
export RUNTIME_MISMATCH_DEBUG="${RUNTIME_MISMATCH_DEBUG:-0}"
export RUNTIME_DEBUG_TX_IDS="${RUNTIME_DEBUG_TX_IDS:-}"
export SERVER_THREADS="${SERVER_THREADS:-1}"
export SEARCH_WARMUP_ITERATIONS="${SEARCH_WARMUP_ITERATIONS:-20000}"

exec "$SCRIPT_DIR/run-k6.sh"
