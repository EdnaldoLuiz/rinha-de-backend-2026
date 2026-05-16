#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
BENCH_DIR="$ROOT_DIR/benchmark"

export API1_CONTAINER="${API1_CONTAINER:-rinha-api-1-native-local}"
export API2_CONTAINER="${API2_CONTAINER:-rinha-api-2-native-local}"
export LB_CONTAINER="${LB_CONTAINER:-rinha-lb-native-local}"
export COMPOSE_FILE="${COMPOSE_FILE:-$SCRIPT_DIR/docker-compose.native.yml}"

export INDEX_MODE="${INDEX_MODE:-ivf}"
export IVF_CLUSTERS="${IVF_CLUSTERS:-4096}"
export IVF_FAST_NPROBE="${IVF_FAST_NPROBE:-32}"
export IVF_FULL_NPROBE="${IVF_FULL_NPROBE:-32}"
export IVF_SAMPLE_SIZE="${IVF_SAMPLE_SIZE:-50000}"
export IVF_ITERATIONS="${IVF_ITERATIONS:-12}"
export IVF_BBOX_REPAIR="${IVF_BBOX_REPAIR:-0}"
export IVF_STATS="${IVF_STATS:-0}"
export FALLBACK_STATS="${FALLBACK_STATS:-0}"
export RUNTIME_MISMATCH_DEBUG="${RUNTIME_MISMATCH_DEBUG:-0}"
export SERVER_THREADS="${SERVER_THREADS:-1}"
SAVE_CONTEXT="${SAVE_CONTEXT:-1}"

RINHA_2026_REPO="${RINHA_2026_REPO:-}"
export RINHA_2026_REPO

if [[ -z "$RINHA_2026_REPO" ]]; then
  echo "[k6-fast] erro: defina RINHA_2026_REPO apontando para o repo oficial da rinha 2026"
  exit 1
fi

if [[ ! -f "$RINHA_2026_REPO/test/test.js" ]]; then
  echo "[k6-fast] erro: script oficial nao encontrado em $RINHA_2026_REPO/test/test.js"
  exit 1
fi

echo "[k6-fast] config: INDEX_MODE=$INDEX_MODE IVF_CLUSTERS=$IVF_CLUSTERS IVF_FAST_NPROBE=$IVF_FAST_NPROBE IVF_FULL_NPROBE=$IVF_FULL_NPROBE IVF_SAMPLE_SIZE=$IVF_SAMPLE_SIZE IVF_ITERATIONS=$IVF_ITERATIONS"
echo "[k6-fast] runtime: SERVER_THREADS=$SERVER_THREADS IVF_STATS=$IVF_STATS IVF_BBOX_REPAIR=$IVF_BBOX_REPAIR FALLBACK_STATS=$FALLBACK_STATS RUNTIME_MISMATCH_DEBUG=$RUNTIME_MISMATCH_DEBUG"

for container in "$API1_CONTAINER" "$API2_CONTAINER" "$LB_CONTAINER"; do
  if ! docker ps --format '{{.Names}}' | grep -Fxq "$container"; then
    echo "[k6-fast] erro: container nao esta em execucao: $container"
    echo "[k6-fast] suba primeiro com ./benchmark/run-k6-native.sh"
    exit 1
  fi
done

echo "[k6-fast] conferindo /ready..."
curl -sf http://localhost:9999/ready >/dev/null

echo "[k6-fast] conferindo config dos containers (sem restart)..."
for container in "$API1_CONTAINER" "$API2_CONTAINER"; do
  actual_mode="$(docker exec "$container" printenv INDEX_MODE 2>/dev/null || true)"
  actual_clusters="$(docker exec "$container" printenv IVF_CLUSTERS 2>/dev/null || true)"
  actual_fast="$(docker exec "$container" printenv IVF_FAST_NPROBE 2>/dev/null || true)"
  actual_full="$(docker exec "$container" printenv IVF_FULL_NPROBE 2>/dev/null || true)"
  if [[ "$actual_mode" != "$INDEX_MODE" || "$actual_clusters" != "$IVF_CLUSTERS" || "$actual_fast" != "$IVF_FAST_NPROBE" || "$actual_full" != "$IVF_FULL_NPROBE" ]]; then
    echo "[k6-fast] erro: config divergente em $container: mode=$actual_mode clusters=$actual_clusters fast=$actual_fast full=$actual_full"
    echo "[k6-fast] dica: exporte as envs corretas e rode ./benchmark/run-k6-native.sh para rebuild consistente"
    exit 1
  fi
done

echo "[k6-fast] processo PID1:"
for container in "$API1_CONTAINER" "$API2_CONTAINER"; do
  cmdline="$(docker exec "$container" sh -c 'xargs -0 echo </proc/1/cmdline' 2>/dev/null || true)"
  if [[ -z "$cmdline" ]]; then
    cmdline="$(docker inspect "$container" --format 'Path={{.Path}} Args={{json .Args}}' 2>/dev/null || true)"
  fi
  echo "$container | $cmdline"
done

echo "[k6-fast] rodando teste oficial sem rebuild/restart..."
pushd "$RINHA_2026_REPO" >/dev/null
rm -f test/results.json
k6 run test/test.js

if [[ ! -f test/results.json ]]; then
  echo "[k6-fast] erro: test/results.json nao foi gerado"
  exit 1
fi

echo "[k6-fast] resultado:"
cat test/results.json | jq

mkdir -p "$BENCH_DIR/results"
STAMP="$(date +%Y%m%d-%H%M%S)"
cp test/results.json "$BENCH_DIR/results/results-$STAMP.json"
RESULT_FILE="$BENCH_DIR/results/results-$STAMP.json"
popd >/dev/null

if [[ "$SAVE_CONTEXT" == "1" ]]; then
  RESULT_FILE="$RESULT_FILE" \
  COMPOSE_FILE="$COMPOSE_FILE" \
  API1_CONTAINER="$API1_CONTAINER" \
  API2_CONTAINER="$API2_CONTAINER" \
  LB_CONTAINER="$LB_CONTAINER" \
  "$BENCH_DIR/save-run-context.sh" "$BENCH_DIR/results/context-$STAMP" >/dev/null 2>&1 || true
fi

echo "[k6-fast] resultado salvo em benchmark/results/results-$STAMP.json"
if [[ "$SAVE_CONTEXT" == "1" ]]; then
  echo "[k6-fast] contexto salvo em benchmark/results/context-$STAMP"
fi
