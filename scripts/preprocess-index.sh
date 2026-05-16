#!/usr/bin/env bash
# preprocess-index.sh - Preprocess the index for faster search.
set -euo pipefail

export INDEX_MODE="${INDEX_MODE:-ivf}"
export IVF_CLUSTERS="${IVF_CLUSTERS:-4096}"
export IVF_FAST_NPROBE="${IVF_FAST_NPROBE:-8}"
export IVF_FULL_NPROBE="${IVF_FULL_NPROBE:-32}"
export IVF_SAMPLE_SIZE="${IVF_SAMPLE_SIZE:-50000}"
export IVF_ITERATIONS="${IVF_ITERATIONS:-12}"

echo "[preprocess] INDEX_MODE=$INDEX_MODE IVF_CLUSTERS=$IVF_CLUSTERS IVF_FAST_NPROBE=$IVF_FAST_NPROBE IVF_FULL_NPROBE=$IVF_FULL_NPROBE IVF_SAMPLE_SIZE=$IVF_SAMPLE_SIZE IVF_ITERATIONS=$IVF_ITERATIONS"

mvn -q -pl tools/preprocessor -am clean package
java -jar tools/preprocessor/target/preprocessor-0.1.0-SNAPSHOT-all.jar
