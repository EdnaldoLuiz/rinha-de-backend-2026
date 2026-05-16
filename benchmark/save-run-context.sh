#!/usr/bin/env bash
# save-run-context.sh - Save the context of a benchmark run for later analysis.
set -euo pipefail

OUT_DIR="${1:-benchmark/results/context-$(date +%Y%m%d-%H%M%S)}"
mkdir -p "$OUT_DIR"

echo "[context] saving to $OUT_DIR"

git rev-parse HEAD > "$OUT_DIR/git-head.txt" 2>/dev/null || true
git status --short > "$OUT_DIR/git-status.txt" 2>/dev/null || true
git diff > "$OUT_DIR/git-diff.patch" 2>/dev/null || true

cp benchmark/docker-compose.local.yml "$OUT_DIR/docker-compose.local.yml" 2>/dev/null || true
cp benchmark/haproxy.cfg "$OUT_DIR/haproxy.cfg" 2>/dev/null || true
cp "$RINHA_2026_REPO/test/test.js" "$OUT_DIR/test.js" 2>/dev/null || true
if [[ -n "${RESULT_FILE:-}" && -f "$RESULT_FILE" ]]; then
  cp "$RESULT_FILE" "$OUT_DIR/results.json" 2>/dev/null || true
fi

sha256sum app/src/main/resources/fraud-index.dat > "$OUT_DIR/index-local.sha256" 2>/dev/null || true
sha256sum "$RINHA_2026_REPO/test/test.js" > "$OUT_DIR/test-js.sha256" 2>/dev/null || true

docker exec rinha-api-1-local env | sort > "$OUT_DIR/api1-env.txt" 2>/dev/null || true
docker exec rinha-api-2-local env | sort > "$OUT_DIR/api2-env.txt" 2>/dev/null || true

docker exec rinha-api-1-local sha256sum /app/resources/fraud-index.dat > "$OUT_DIR/api1-index.sha256" 2>/dev/null || true
docker exec rinha-api-2-local sha256sum /app/resources/fraud-index.dat > "$OUT_DIR/api2-index.sha256" 2>/dev/null || true

docker inspect rinha-api-1-local rinha-api-2-local rinha-lb-local \
  --format '{{.Name}} NanoCpus={{.HostConfig.NanoCpus}} Memory={{.HostConfig.Memory}} Image={{.Image}} NetworkMode={{.HostConfig.NetworkMode}}' \
  > "$OUT_DIR/docker-inspect-summary.txt" 2>/dev/null || true

docker compose -f benchmark/docker-compose.local.yml ps > "$OUT_DIR/docker-compose-ps.txt" 2>/dev/null || true

grep -R "localhost:9999\|localhost:8081\|BASE_URL\|fraud-score" -n \
  "$RINHA_2026_REPO/test/test.js" benchmark/*.js benchmark/**/*.js \
  > "$OUT_DIR/url-grep.txt" 2>/dev/null || true

echo "[context] done"
