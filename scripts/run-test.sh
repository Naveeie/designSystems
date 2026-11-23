#!/usr/bin/env bash
# Runs one load-test pass locally, no Docker.
# Usage: scripts/run-test.sh naive|pessimistic|atomic
set -euo pipefail

STRATEGY="${1:?usage: scripts/run-test.sh naive|pessimistic|atomic}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PG_FORMULA=postgresql@16
PG_BIN="$(brew --prefix "$PG_FORMULA")/bin"

# 1. PostgreSQL: install if missing, start, create role and database if missing
brew list --versions "$PG_FORMULA" >/dev/null 2>&1 || brew install "$PG_FORMULA"
brew services start "$PG_FORMULA" >/dev/null
until "$PG_BIN/pg_isready" -q; do sleep 1; done

"$PG_BIN/psql" -d postgres -qtAc "select 1 from pg_roles where rolname='flashsale'" | grep -q 1 \
  || "$PG_BIN/psql" -d postgres -qc "create user flashsale with password 'flashsale'"
"$PG_BIN/psql" -d postgres -qtAc "select 1 from pg_database where datname='flashsale'" | grep -q 1 \
  || "$PG_BIN/psql" -d postgres -qc "create database flashsale owner flashsale"

# 2. Build the jar and start the app in the background, so there is one process to track
cd "$ROOT"
mvn -q -B package -DskipTests
java -jar target/flashsale-0.0.1-SNAPSHOT.jar --booking.strategy="$STRATEGY" > app.log 2>&1 &
APP_PID=$!
trap 'kill "$APP_PID" 2>/dev/null || true' EXIT

# Wait up to 60s for the app to answer, and fail fast if it crashed
for _ in $(seq 60); do
  curl -sf localhost:8080/stats >/dev/null 2>&1 && break
  kill -0 "$APP_PID" 2>/dev/null || { echo "App exited. See $ROOT/app.log"; exit 1; }
  sleep 1
done

# 3. Load test, then print the result
echo "== strategy: $STRATEGY"
k6 run k6/buy.js
echo "== stats"
curl -s localhost:8080/stats
echo
