#!/usr/bin/env bash
set -euo pipefail

API_PID_FILE="/tmp/quantor-api.pid"
WORKER_PID_FILE="/tmp/quantor-worker.pid"
POSTGRES_CONTAINER="${QUANTOR_POSTGRES_CONTAINER:-quantor-postgres-1}"

stop_pidfile() {
  local file="$1" name="$2"
  if [[ -f "$file" ]]; then
    pid=$(cat "$file" || true)
    if [[ -n "$pid" ]] && kill -0 "$pid" >/dev/null 2>&1; then
      echo "Stopping $name (pid=$pid)"
      kill "$pid" || true
      # give it a moment
      sleep 2
      kill -9 "$pid" >/dev/null 2>&1 || true
    fi
    rm -f "$file"
  else
    echo "$name: not running (no pid file)"
  fi
}

stop_pidfile "$WORKER_PID_FILE" "Worker"
stop_pidfile "$API_PID_FILE" "API"

if docker ps --format '{{.Names}}' | grep -qx "$POSTGRES_CONTAINER"; then
  echo "Stopping Postgres container ($POSTGRES_CONTAINER)"
  docker stop "$POSTGRES_CONTAINER" >/dev/null || true
fi

echo "Done."
