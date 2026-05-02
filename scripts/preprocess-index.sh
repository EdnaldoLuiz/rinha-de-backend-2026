#!/usr/bin/env bash
set -euo pipefail
mvn -q -pl tools/preprocessor -am clean package
java -jar tools/preprocessor/target/preprocessor-0.1.0-SNAPSHOT-all.jar
