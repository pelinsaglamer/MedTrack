#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
if [ ! -f .env ]; then
  echo 'Copy .env.example to .env and set a local password first.' >&2
  exit 1
fi
set -a
. ./.env
set +a
case "${1:-}" in
  db) docker compose --env-file .env -f infrastructure/docker-compose.yml up -d --wait postgres ;;
  test)
    docker compose --env-file .env -f infrastructure/docker-compose.yml --profile test up -d --wait postgres-test
    export TEST_DB_URL="jdbc:postgresql://localhost:${TEST_DB_PORT:-5433}/medtrack_test"
    cd backend
    ./mvnw --batch-mode verify
    ;;
  run)
    export DB_URL="jdbc:postgresql://localhost:${DB_PORT:-5432}/${POSTGRES_DB}"
    cd backend
    exec ./mvnw spring-boot:run
    ;;
  stop) docker compose --env-file .env -f infrastructure/docker-compose.yml --profile test stop ;;
  *) echo 'Usage: scripts/dev.sh {db|test|run|stop}' >&2; exit 1 ;;
esac
