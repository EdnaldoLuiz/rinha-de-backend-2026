#!/usr/bin/env bash
set -euo pipefail
mvn -q -pl app -am clean package
