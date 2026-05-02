# rinha-backend-2026

set -a
source .env.local
set +a
./scripts/verify-rules.sh

Base inicial da solução competitiva da Rinha de Backend 2026.

## Módulos
- `app`: API principal (`/ready` e `/fraud-score`)
- `tools/preprocessor`: gera índice binário `fraud-index.dat`
- `tools/inspector`: verificador de índice

## Build JVM
```bash
./scripts/build-jvm.sh
```

## Build Native
```bash
./scripts/build-native.sh
```

## Gerar índice binário
```bash
./scripts/preprocess-index.sh
```

## Run da app (JVM)
```bash
java -jar app/target/app-0.1.0-SNAPSHOT.jar
```

## Run da app usando índice binário
```bash
INDEX_BINARY_FILE=app/src/main/resources/fraud-index.dat \
java -jar app/target/app-0.1.0-SNAPSHOT.jar
```

## Ambiente local (LB + 2 APIs)
```bash
docker compose -f benchmark/docker-compose.local.yml up --build
```

O load balancer fica exposto em `http://localhost:9999` e encaminha para `api-1` e `api-2` em `:8080`.
