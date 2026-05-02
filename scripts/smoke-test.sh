#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:9999}"
READY_URL="${BASE_URL%/}/ready"
FRAUD_URL="${BASE_URL%/}/fraud-score"
PAYLOAD_FILE="${PAYLOAD_FILE:-resources/sample-request.json}"

if [[ ! -f "$PAYLOAD_FILE" ]]; then
  echo "[smoke] erro: payload nao encontrado em $PAYLOAD_FILE"
  exit 1
fi

ready_body_file="$(mktemp)"
fraud_body_file="$(mktemp)"
trap 'rm -f "$ready_body_file" "$fraud_body_file"' EXIT

ready_code="$(curl -sS -o "$ready_body_file" -w '%{http_code}' "$READY_URL")"
ready_body="$(cat "$ready_body_file")"

if [[ "$ready_code" != "200" ]]; then
  echo "[smoke] erro: /ready retornou HTTP $ready_code"
  echo "[smoke] body: $ready_body"
  exit 1
fi

if [[ "$ready_body" != *'"status":"ready"'* ]]; then
  echo "[smoke] erro: /ready nao retornou status ready"
  echo "[smoke] body: $ready_body"
  exit 1
fi

fraud_code="$(curl -sS -o "$fraud_body_file" -w '%{http_code}' \
  -X POST "$FRAUD_URL" \
  -H 'Content-Type: application/json' \
  --data-binary "@$PAYLOAD_FILE")"
fraud_body="$(cat "$fraud_body_file")"

if [[ "$fraud_code" != "200" ]]; then
  echo "[smoke] erro: /fraud-score retornou HTTP $fraud_code"
  echo "[smoke] body: $fraud_body"
  exit 1
fi

if [[ "$fraud_body" != *'"approved":'* ]] || [[ "$fraud_body" != *'"fraud_score":'* ]]; then
  echo "[smoke] erro: resposta de /fraud-score sem campos esperados"
  echo "[smoke] body: $fraud_body"
  exit 1
fi

approved="$(echo "$fraud_body" | sed -nE 's/.*"approved":(true|false).*/\1/p')"
score="$(echo "$fraud_body" | sed -nE 's/.*"fraud_score":([0-9]+\.[0-9]+|[0-9]+).*/\1/p')"

if [[ -z "$approved" || -z "$score" ]]; then
  echo "[smoke] erro: nao foi possivel parsear approved/fraud_score"
  echo "[smoke] body: $fraud_body"
  exit 1
fi

case "$score" in
  0|0.0|0.2|0.4|0.6|0.8|1|1.0)
    ;;
  *)
    echo "[smoke] erro: fraud_score invalido: $score"
    exit 1
    ;;
esac

if [[ "$approved" == "true" && "$score" =~ ^(0.6|0.8|1|1.0)$ ]]; then
  echo "[smoke] erro: approved=true com fraud_score=$score (inconsistente)"
  exit 1
fi

if [[ "$approved" == "false" && "$score" =~ ^(0|0.0|0.2|0.4)$ ]]; then
  echo "[smoke] erro: approved=false com fraud_score=$score (inconsistente)"
  exit 1
fi

echo "[smoke] ok: /ready e /fraud-score validados"
echo "[smoke] resposta fraud-score: $fraud_body"
