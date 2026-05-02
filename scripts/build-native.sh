#!/usr/bin/env bash
set -euo pipefail

if ! command -v native-image >/dev/null 2>&1; then
  echo "native-image não encontrado no PATH. Instale GraalVM com Native Image para este build."
  exit 1
fi

mvn -q -pl app -am -Pnative clean package
