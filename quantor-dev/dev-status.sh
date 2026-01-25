#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

LOG_DIR="$ROOT_DIR/.dev/logs"
PID_DIR="$ROOT_DIR/.dev/pids"

compose() {
  if docker compose version >/dev/null 2>&1; then
    docker compose "$@"
  else
    docker-compose "$@"
  fi
}

status_pid() {
  local name="$1"
  local pidfile="$PID_DIR/${name}.pid"
  if [[ -f "$pidfile" ]]; then
    local pid
    pid="$(cat "$pidfile" 2>/dev/null || true)"
    if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
      echo "$name: RUNNING (pid=$pid)"
      return 0
    fi
  fi
  echo "$name: STOPPED (no pidfile)"
  return 1
}

echo "==> Docker"
compose ps || true

echo

echo "==> Processes"
status_pid api || true
status_pid worker || true

echo

echo "==> Logs"
if [[ -f "$LOG_DIR/worker.log" ]]; then
  tail -n 200 "$LOG_DIR/worker.log"
else
  echo "(no worker log at $LOG_DIR/worker.log)"
fi
