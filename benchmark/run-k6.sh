#!/usr/bin/env bash
# run-k6.sh - Run the official k6 benchmark against a local environment.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BENCH_DIR="$ROOT_DIR/benchmark"
COMPOSE_FILE="$BENCH_DIR/docker-compose.local.yml"

export INDEX_MODE="${INDEX_MODE:-ivf}"
export IVF_CLUSTERS="${IVF_CLUSTERS:-4096}"
export IVF_FAST_NPROBE="${IVF_FAST_NPROBE:-8}"
export IVF_FULL_NPROBE="${IVF_FULL_NPROBE:-32}"
export IVF_SAMPLE_SIZE="${IVF_SAMPLE_SIZE:-50000}"
export IVF_ITERATIONS="${IVF_ITERATIONS:-12}"
export IVF_STATS="${IVF_STATS:-0}"
export IVF_BBOX_REPAIR="${IVF_BBOX_REPAIR:-0}"
export FALLBACK_STATS="${FALLBACK_STATS:-0}"
export RUNTIME_MISMATCH_DEBUG="${RUNTIME_MISMATCH_DEBUG:-0}"
export RUNTIME_MISMATCH_FILE="${RUNTIME_MISMATCH_FILE:-}"
export RUNTIME_DEBUG_TX_IDS="${RUNTIME_DEBUG_TX_IDS:-}"
export SERVER_THREADS="${SERVER_THREADS:-4}"

PREFLIGHT="${PREFLIGHT:-1}"
REBUILD="${REBUILD:-1}"
PREBUILD_LOCAL_INDEX="${PREBUILD_LOCAL_INDEX:-0}"
BUILD_LOCAL_JAR="${BUILD_LOCAL_JAR:-0}"
SAVE_CONTEXT="${SAVE_CONTEXT:-1}"

# Caminho do repo oficial da Rinha 2026 já clonado localmente.
# Exemplo:
# export RINHA_2026_REPO="$HOME/dev/rinha-de-backend-2026"
RINHA_2026_REPO="${RINHA_2026_REPO:-}"
export RINHA_2026_REPO

if [[ -z "$RINHA_2026_REPO" ]]; then
  echo "[k6] erro: defina RINHA_2026_REPO apontando para o repo oficial da rinha 2026"
  exit 1
fi

if [[ ! -d "$RINHA_2026_REPO" ]]; then
  echo "[k6] erro: diretorio nao encontrado: $RINHA_2026_REPO"
  exit 1
fi

if [[ ! -f "$RINHA_2026_REPO/test/test.js" ]]; then
  echo "[k6] erro: script oficial nao encontrado em $RINHA_2026_REPO/test/test.js"
  exit 1
fi

if [[ ! -f "$RINHA_2026_REPO/test/test-data.json" ]]; then
  echo "[k6] erro: massa oficial nao encontrada em $RINHA_2026_REPO/test/test-data.json"
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "[k6] erro: docker nao encontrado no PATH"
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "[k6] erro: jq nao encontrado no PATH"
  exit 1
fi

cleanup() {
  echo "[k6] derrubando ambiente local..."
  docker compose -f "$COMPOSE_FILE" down -v --remove-orphans >/dev/null 2>&1 || true
}
# trap cleanup EXIT

echo "[k6] config: INDEX_MODE=$INDEX_MODE IVF_CLUSTERS=$IVF_CLUSTERS IVF_FAST_NPROBE=$IVF_FAST_NPROBE IVF_FULL_NPROBE=$IVF_FULL_NPROBE IVF_SAMPLE_SIZE=$IVF_SAMPLE_SIZE IVF_ITERATIONS=$IVF_ITERATIONS"
echo "[k6] runtime: SERVER_THREADS=$SERVER_THREADS IVF_STATS=$IVF_STATS IVF_BBOX_REPAIR=$IVF_BBOX_REPAIR FALLBACK_STATS=$FALLBACK_STATS RUNTIME_MISMATCH_DEBUG=$RUNTIME_MISMATCH_DEBUG"

if [[ "$PREFLIGHT" != "0" ]]; then
  echo "[k6] validando regras do compose local..."
  COMPOSE_FILE="$COMPOSE_FILE" "$ROOT_DIR/scripts/verify-rules.sh"
fi

if [[ "$PREBUILD_LOCAL_INDEX" == "1" ]]; then
  echo "[k6] gerando indice local com a mesma config do Docker..."
  "$ROOT_DIR/scripts/preprocess-index.sh"
else
  echo "[k6] pulando indice local; Dockerfile.jvm e a fonte da verdade do indice do container"
fi

if [[ "$BUILD_LOCAL_JAR" == "1" ]]; then
  echo "[k6] buildando jar JVM local..."
  "$ROOT_DIR/scripts/build-jvm.sh"
fi

if [[ "$REBUILD" == "0" ]]; then
  echo "[k6] usando ambiente local existente (sem rebuild)..."
  docker compose -f "$COMPOSE_FILE" up -d
else
  echo "[k6] subindo ambiente local com rebuild..."
  docker compose -f "$COMPOSE_FILE" up --build -d
fi

echo "[k6] aguardando /ready..."
READY_URL="http://localhost:9999/ready"
MAX_ATTEMPTS=60
SLEEP_SECS=2

for ((i=1; i<=MAX_ATTEMPTS; i++)); do
  code="$(curl -s -o /tmp/rinha-ready.out -w "%{http_code}" "$READY_URL" || true)"
  if [[ "$code" =~ ^2 ]]; then
    echo "[k6] ambiente pronto"
    break
  fi

  if [[ "$i" -eq "$MAX_ATTEMPTS" ]]; then
    echo "[k6] erro: ambiente nao ficou pronto a tempo"
    echo "[k6] resposta final do /ready:"
    cat /tmp/rinha-ready.out || true
    echo
    echo "[k6] logs do compose:"
    docker compose -f "$COMPOSE_FILE" logs --no-color || true
    exit 1
  fi

  sleep "$SLEEP_SECS"
done

echo "[k6] conferindo config dos containers..."
for container in rinha-api-1-local rinha-api-2-local; do
  actual_clusters="$(docker exec "$container" printenv IVF_CLUSTERS 2>/dev/null || true)"
  actual_fast="$(docker exec "$container" printenv IVF_FAST_NPROBE 2>/dev/null || true)"
  actual_full="$(docker exec "$container" printenv IVF_FULL_NPROBE 2>/dev/null || true)"
  if [[ "$actual_clusters" != "$IVF_CLUSTERS" || "$actual_fast" != "$IVF_FAST_NPROBE" || "$actual_full" != "$IVF_FULL_NPROBE" ]]; then
    echo "[k6] erro: config divergente em $container: clusters=$actual_clusters fast=$actual_fast full=$actual_full"
    exit 1
  fi
done

echo "[k6] indices carregados:"
docker compose -f "$COMPOSE_FILE" logs --no-color api-1 api-2 \
  | grep "Loaded index" \
  | tail -n 4 || true

echo "[k6] rodando teste oficial..."
pushd "$RINHA_2026_REPO" >/dev/null
rm -f test/results.json

# O script oficial já aponta para http://localhost:9999/fraud-score
k6 run test/test.js

if [[ ! -f test/results.json ]]; then
  echo "[k6] erro: test/results.json nao foi gerado"
  echo "[k6] logs do compose:"
  docker compose -f "$COMPOSE_FILE" logs --no-color || true
  exit 1
fi

echo "[k6] resultado:"
cat test/results.json | jq

mkdir -p "$BENCH_DIR/results"
STAMP="$(date +%Y%m%d-%H%M%S)"
cp test/results.json "$BENCH_DIR/results/results-$STAMP.json"
RESULT_FILE="$BENCH_DIR/results/results-$STAMP.json"

popd >/dev/null

if [[ "$SAVE_CONTEXT" == "1" ]]; then
  RESULT_FILE="$RESULT_FILE" "$BENCH_DIR/save-run-context.sh" "$BENCH_DIR/results/context-$STAMP" >/dev/null 2>&1 || true
fi

echo "[k6] resultado salvo em benchmark/results/results-$STAMP.json"
if [[ "$SAVE_CONTEXT" == "1" ]]; then
  echo "[k6] contexto salvo em benchmark/results/context-$STAMP"
fi
