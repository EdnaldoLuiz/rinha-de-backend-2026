#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

export REBUILD=0
export PREBUILD_LOCAL_INDEX=0
export BUILD_LOCAL_JAR=0
export PREFLIGHT="${PREFLIGHT:-0}"

exec "$SCRIPT_DIR/run-k6.sh"
