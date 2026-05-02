#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BENCH_DIR="$ROOT_DIR/benchmark"
COMPOSE_FILE="$BENCH_DIR/docker-compose.local.yml"

# Caminho do repo oficial da Rinha 2026 já clonado localmente.
# Exemplo:
# export RINHA_2026_REPO="$HOME/dev/rinha-de-backend-2026"
RINHA_2026_REPO="${RINHA_2026_REPO:-}"

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

echo "[k6] validando regras do compose local..."
COMPOSE_FILE="$COMPOSE_FILE" "$ROOT_DIR/scripts/verify-rules.sh"

echo "[k6] gerando indice binario..."
"$ROOT_DIR/scripts/preprocess-index.sh"

echo "[k6] buildando jar JVM..."
"$ROOT_DIR/scripts/build-jvm.sh"

echo "[k6] subindo ambiente local..."
docker compose -f "$COMPOSE_FILE" up --build -d

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

popd >/dev/null

echo "[k6] resultado salvo em benchmark/results/results-$STAMP.json"