#!/usr/bin/env bash
set -euo pipefail

export LC_ALL=C
export LANG=C

COMPOSE_FILE="${COMPOSE_FILE:-submission/docker-compose.yml}"

if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "[verify] erro: compose nao encontrado em $COMPOSE_FILE"
  exit 1
fi

fail() {
  echo "[verify] erro: $1"
  exit 1
}

service_exists() {
  local svc="$1"
  grep -Eq "^  ${svc}:$" "$COMPOSE_FILE"
}

service_block_has() {
  local svc="$1"
  local pattern="$2"
  awk -v svc="$svc" -v pattern="$pattern" '
    BEGIN { in_service = 0 }
    $0 ~ "^  " svc ":$" { in_service = 1; next }
    in_service && $0 ~ "^  [^ ]" { in_service = 0 }
    in_service && $0 ~ pattern { found = 1 }
    END { exit(found ? 0 : 1) }
  ' "$COMPOSE_FILE"
}

sum_cpus() {
  awk '
    /^  [A-Za-z0-9_-]+:$/ { service=$1; sub(":", "", service); next }
    /^    cpus:/ {
      value=$2
      gsub(/"/, "", value)
      sum += value + 0
    }
    END { printf "%.6f", sum }
  ' "$COMPOSE_FILE"
}

sum_mem_mb() {
  awk '
    function to_mb(raw, unit, num) {
      unit = tolower(raw)
      gsub(/"/, "", unit)
      num = unit
      gsub(/[a-z]/, "", num)

      if (unit ~ /gb$/ || unit ~ /g$/) return (num + 0) * 1024
      if (unit ~ /mb$/ || unit ~ /m$/) return (num + 0)
      if (unit ~ /kb$/ || unit ~ /k$/) return (num + 0) / 1024
      return (num + 0) / (1024 * 1024)
    }

    /^    mem_limit:/ {
      sum += to_mb($2)
    }

    END { printf "%.6f", sum }
  ' "$COMPOSE_FILE"
}

service_exists "load-balancer" || fail "servico load-balancer ausente"
service_exists "api-1" || fail "servico api-1 ausente"
service_exists "api-2" || fail "servico api-2 ausente"

grep -Eq '^\s*-\s*"?9999:9999"?$' "$COMPOSE_FILE" || fail "porta externa 9999:9999 nao encontrada"

service_block_has "api-1" "^    ports:" && fail "api-1 nao deve expor portas no host"
service_block_has "api-2" "^    ports:" && fail "api-2 nao deve expor portas no host"

service_block_has "api-1" "^    expose:" || fail "api-1 deve expor porta interna"
service_block_has "api-2" "^    expose:" || fail "api-2 deve expor porta interna"

service_block_has "api-1" "8080" || fail "api-1 deve usar porta interna 8080"
service_block_has "api-2" "8080" || fail "api-2 deve usar porta interna 8080"

if grep -Eq '^\s*network_mode:\s*"?host"?$' "$COMPOSE_FILE"; then
  fail "network_mode host nao permitido"
fi

if grep -Eq '^\s*privileged:\s*true$' "$COMPOSE_FILE"; then
  fail "privileged=true nao permitido"
fi

cpu_total="$(LC_ALL=C sum_cpus)"
mem_total_mb="$(LC_ALL=C sum_mem_mb)"

awk -v total="$cpu_total" 'BEGIN { if (total > 1.000001) exit 1 }' || fail "CPU total excede 1.0 (atual: $cpu_total)"
awk -v total="$mem_total_mb" 'BEGIN { if (total > 350.000001) exit 1 }' || fail "memoria total excede 350MB (atual: ${mem_total_mb}MB)"

echo "[verify] ok: topologia e limites validados"
echo "[verify] cpu_total=$cpu_total"
echo "[verify] mem_total_mb=$mem_total_mb"
