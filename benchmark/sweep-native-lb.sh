#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/benchmark/docker-compose.native.yml"
RESULTS_DIR="$ROOT_DIR/benchmark/results"
OUT_CSV="$RESULTS_DIR/native-lb-sweep-$(date +%Y%m%d-%H%M%S).csv"
TMP_DIR="$(mktemp -d)"

configs=(
  "0.15 0.425"
  "0.20 0.400"
  "0.25 0.375"
  "0.30 0.350"
)

cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

require() {
  command -v "$1" >/dev/null 2>&1 || { echo "missing command: $1"; exit 1; }
}

require jq
require docker
require awk

rewrite_compose() {
  local lb="$1"
  local api="$2"
  awk -v lb="$lb" -v api="$api" '
    BEGIN { svc="" }
    /^  load-balancer:$/ { svc="lb" }
    /^  api-1:$/ { svc="api1" }
    /^  api-2:$/ { svc="api2" }
    /^  [^ ]/ { svc="" }
    {
      if ($0 ~ /^    cpus: /) {
        if (svc == "lb") {
          print "    cpus: \"" lb "\""
          next
        }
        if (svc == "api1" || svc == "api2") {
          print "    cpus: \"" api "\""
          next
        }
      }
      print
    }
  ' "$COMPOSE_FILE" > "$TMP_DIR/compose.tmp"
  mv "$TMP_DIR/compose.tmp" "$COMPOSE_FILE"
}

cpu_stat_triplet() {
  local container="$1"
  local stats
  stats="$(docker exec "$container" sh -c 'cat /sys/fs/cgroup/cpu.stat' 2>/dev/null || true)"
  local nr th
  nr="$(printf '%s\n' "$stats" | awk '$1=="nr_throttled"{print $2}')"
  th="$(printf '%s\n' "$stats" | awk '$1=="throttled_usec"{print $2}')"
  echo "${nr:-0} ${th:-0}"
}

run_and_collect() {
  local mode="$1"
  local log_file="$2"
  if [[ "$mode" == "rebuild" ]]; then
    REBUILD=1 "$ROOT_DIR/benchmark/run-k6-native.sh" >"$log_file" 2>&1
  else
    REBUILD=0 "$ROOT_DIR/benchmark/run-k6-native.sh" >"$log_file" 2>&1
  fi
}

latest_result_file() {
  local latest
  latest="$(ls -1t "$RESULTS_DIR"/results-*.json 2>/dev/null || true)"
  if [[ -z "$latest" ]]; then
    echo ""
    return
  fi
  echo "${latest%%$'\n'*}"
}

extract_metrics() {
  local json_file="$1"
  local log_file="$2"
  local p99 score fp fn complete
  p99="$(jq -r '.p99' "$json_file")"
  score="$(jq -r '.scoring.final_score' "$json_file")"
  fp="$(jq -r '.scoring.breakdown.false_positive_detections' "$json_file")"
  fn="$(jq -r '.scoring.breakdown.false_negative_detections' "$json_file")"
  complete="$(awk '
    /running \(1m[0-9.]*s\), [0-9]{3}\/[0-9]{3} VUs, [0-9]+ complete/ {
      for (i=1; i<=NF; i++) {
        if ($i=="complete") {
          print $(i-1);
          found=1;
        }
      }
    }
    END { if (!found) print "na" }
  ' "$log_file" | tail -n1)"
  echo "$p99;$score;$fp;$fn;$complete"
}

mkdir -p "$RESULTS_DIR"
echo "lb_cpu,api_cpu,run_type,run_idx,p99,score,fp,fn,complete,lb_delta_nr,lb_delta_th_usec,api1_delta_nr,api1_delta_th_usec,api2_delta_nr,api2_delta_th_usec,result_file,log_file" > "$OUT_CSV"

for cfg in "${configs[@]}"; do
  lb="$(echo "$cfg" | awk '{print $1}')"
  api="$(echo "$cfg" | awk '{print $2}')"
  echo "[sweep] config lb=$lb api=$api"
  rewrite_compose "$lb" "$api"

  docker compose -f "$COMPOSE_FILE" down -v --remove-orphans >/dev/null 2>&1 || true

  log="$TMP_DIR/rebuild-lb${lb}-api${api}.log"
  run_and_collect "rebuild" "$log"
  result="$(latest_result_file)"
  metrics="$(extract_metrics "$result" "$log")"
  echo "[sweep]   rebuild done: $result"

  read -r lb_nr_prev lb_th_prev <<< "$(cpu_stat_triplet rinha-lb-native-local)"
  read -r a1_nr_prev a1_th_prev <<< "$(cpu_stat_triplet rinha-api-1-native-local)"
  read -r a2_nr_prev a2_th_prev <<< "$(cpu_stat_triplet rinha-api-2-native-local)"
  echo "$lb,$api,rebuild,0,${metrics//;/,},0,0,0,0,0,0,$result,$log" >> "$OUT_CSV"

  for i in 1 2 3; do
    log="$TMP_DIR/fast${i}-lb${lb}-api${api}.log"
    run_and_collect "fast" "$log"
    result="$(latest_result_file)"
    metrics="$(extract_metrics "$result" "$log")"
    echo "[sweep]   fast $i done: $result"

    read -r lb_nr lb_th <<< "$(cpu_stat_triplet rinha-lb-native-local)"
    read -r a1_nr a1_th <<< "$(cpu_stat_triplet rinha-api-1-native-local)"
    read -r a2_nr a2_th <<< "$(cpu_stat_triplet rinha-api-2-native-local)"

    lb_d_nr=$((lb_nr - lb_nr_prev))
    lb_d_th=$((lb_th - lb_th_prev))
    a1_d_nr=$((a1_nr - a1_nr_prev))
    a1_d_th=$((a1_th - a1_th_prev))
    a2_d_nr=$((a2_nr - a2_nr_prev))
    a2_d_th=$((a2_th - a2_th_prev))

    echo "$lb,$api,fast,$i,${metrics//;/,},$lb_d_nr,$lb_d_th,$a1_d_nr,$a1_d_th,$a2_d_nr,$a2_d_th,$result,$log" >> "$OUT_CSV"

    lb_nr_prev="$lb_nr"
    lb_th_prev="$lb_th"
    a1_nr_prev="$a1_nr"
    a1_th_prev="$a1_th"
    a2_nr_prev="$a2_nr"
    a2_th_prev="$a2_th"
  done
done

echo "[sweep] finished: $OUT_CSV"
