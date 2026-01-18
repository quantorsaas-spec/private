#!/usr/bin/env bash
set -euo pipefail

API_PID_FILE="/tmp/quantor-api.pid"
WORKER_PID_FILE="/tmp/quantor-worker.pid"
POSTGRES_CONTAINER="${QUANTOR_POSTGRES_CONTAINER:-quantor-postgres-1}"

status_pidfile() {
  local file="$1" name="$2"
  if [[ -f "$file" ]]; then
    pid=$(cat "$file" || true)
    if [[ -n "$pid" ]] && kill -0 "$pid" >/dev/null 2>&1; then
      echo "$name: RUNNING (pid=$pid)"
    else
      echo "$name: DOWN (stale pid file: $pid)"
    fi
  else
    echo "$name: DOWN"
  fi
}

if docker ps --format '{{.Names}}' | grep -qx "$POSTGRES_CONTAINER"; then
  echo "DB: RUNNING ($POSTGRES_CONTAINER)"
else
  if docker ps -a --format '{{.Names}}' | grep -qx "$POSTGRES_CONTAINER"; then
    echo "DB: STOPPED ($POSTGRES_CONTAINER)"
  else
    echo "DB: MISSING ($POSTGRES_CONTAINER)"
  fi
fi

status_pidfile "$API_PID_FILE" "API"
status_pidfile "$WORKER_PID_FILE" "Worker"

code=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/actuator/health || true)
if [[ "$code" == "200" ]]; then
  echo "API health: UP"
else
  echo "API health: DOWN (http=$code)"
fi
