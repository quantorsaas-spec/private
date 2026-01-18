#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${QUANTOR_ROOT:-$HOME/projects/quantor/Quantor}"
POSTGRES_CONTAINER="${QUANTOR_POSTGRES_CONTAINER:-quantor-postgres-1}"
API_DIR="$ROOT_DIR/quantor-api"
WORKER_DIR="$ROOT_DIR/quantor-worker"
CLI_DIR="$ROOT_DIR/quantor-cli"

API_LOG="${QUANTOR_API_LOG:-/tmp/quantor-api.log}"
WORKER_LOG="${QUANTOR_WORKER_LOG:-/tmp/quantor-worker.log}"

API_PID_FILE="/tmp/quantor-api.pid"
WORKER_PID_FILE="/tmp/quantor-worker.pid"

need_cmd() { command -v "$1" >/dev/null 2>&1 || { echo "Missing required command: $1" >&2; exit 1; }; }

is_pid_running() {
  local pid="$1"
  [[ -n "$pid" ]] && kill -0 "$pid" >/dev/null 2>&1
}

wait_port() {
  local host="$1" port="$2" name="$3" tries="${4:-60}"
  for i in $(seq 1 "$tries"); do
    if (echo >"/dev/tcp/${host}/${port}") >/dev/null 2>&1; then
      echo "$name is listening on ${host}:${port}"
      return 0
    fi
    sleep 1
  done
  echo "Timeout waiting for $name on ${host}:${port}" >&2
  return 1
}

wait_http_200() {
  local url="$1" name="$2" tries="${3:-60}"
  for i in $(seq 1 "$tries"); do
    code=$(curl -s -o /dev/null -w "%{http_code}" "$url" || true)
    if [[ "$code" == "200" ]]; then
      echo "$name is healthy ($url)"
      return 0
    fi
    sleep 1
  done
  echo "Timeout waiting for $name health at $url" >&2
  return 1
}

start_postgres() {
  echo "==> Starting Postgres ($POSTGRES_CONTAINER)"
  if ! docker ps -a --format '{{.Names}}' | grep -qx "$POSTGRES_CONTAINER"; then
    echo "ERROR: Docker container '$POSTGRES_CONTAINER' not found." >&2
    echo "Hint: run your compose once or create the container first." >&2
    exit 1
  fi
  docker start "$POSTGRES_CONTAINER" >/dev/null
  docker update --restart unless-stopped "$POSTGRES_CONTAINER" >/dev/null || true
  wait_port 127.0.0.1 5432 "Postgres" 60
  echo "Postgres OK"
}

start_api() {
  echo "==> Starting API"
  if [[ -f "$API_PID_FILE" ]]; then
    old_pid=$(cat "$API_PID_FILE" || true)
    if is_pid_running "$old_pid"; then
      echo "API already running (pid=$old_pid)."
      return 0
    fi
  fi

  (cd "$API_DIR" && mvn -DskipTests -q clean package)

  # Start in background, capture PID
  (cd "$API_DIR" && nohup mvn -q spring-boot:run >"$API_LOG" 2>&1 & echo $! >"$API_PID_FILE")

  # Wait for health
  wait_http_200 "http://127.0.0.1:8080/actuator/health" "API" 90
}

start_worker() {
  echo "==> Starting Worker"
  if [[ -f "$WORKER_PID_FILE" ]]; then
    old_pid=$(cat "$WORKER_PID_FILE" || true)
    if is_pid_running "$old_pid"; then
      echo "Worker already running (pid=$old_pid)."
      return 0
    fi
  fi

  (cd "$WORKER_DIR" && mvn -DskipTests -q clean package)

  (cd "$WORKER_DIR" && nohup mvn -q spring-boot:run >"$WORKER_LOG" 2>&1 & echo $! >"$WORKER_PID_FILE")

  # Worker has no HTTP; just wait a moment and check process is alive
  sleep 2
  pid=$(cat "$WORKER_PID_FILE" || true)
  if ! is_pid_running "$pid"; then
    echo "Worker failed to start. Tail logs:" >&2
    tail -n 120 "$WORKER_LOG" >&2 || true
    exit 1
  fi
  echo "Worker running (pid=$pid)"
}

print_summary() {
  echo ""
  echo "==> Summary"
  echo "Root:   $ROOT_DIR"
  echo "DB:     $POSTGRES_CONTAINER (5432)"
  echo "API:    http://127.0.0.1:8080 (log: $API_LOG)"
  echo "Worker: (log: $WORKER_LOG)"
  echo ""
  echo "Useful commands:"
  echo "  ./dev-status.sh"
  echo "  tail -n 200 $API_LOG"
  echo "  tail -n 200 $WORKER_LOG"
  echo "  ./dev-down.sh"
}

main() {
  need_cmd docker
  need_cmd mvn
  need_cmd curl

  [[ -d "$ROOT_DIR" ]] || { echo "ERROR: QUANTOR root not found: $ROOT_DIR" >&2; exit 1; }
  [[ -d "$API_DIR" ]] || { echo "ERROR: API dir not found: $API_DIR" >&2; exit 1; }
  [[ -d "$WORKER_DIR" ]] || { echo "ERROR: Worker dir not found: $WORKER_DIR" >&2; exit 1; }

  start_postgres
  start_api
  start_worker
  print_summary
}

main "$@"
