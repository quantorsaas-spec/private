#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

LOG_DIR="$ROOT_DIR/.dev/logs"
PID_DIR="$ROOT_DIR/.dev/pids"
mkdir -p "$LOG_DIR" "$PID_DIR"

POSTGRES_HOST=${POSTGRES_HOST:-127.0.0.1}
POSTGRES_PORT=${POSTGRES_PORT:-5432}
POSTGRES_WAIT_SEC=${POSTGRES_WAIT_SEC:-30}

load_dotenv_if_present() {
  # Load ${ROOT_DIR}/.env if present so docker-compose and local Java share the same DB creds.
  # Only imports simple KEY=VALUE lines; ignores comments and empty lines.
  local dotenv="$ROOT_DIR/.env"
  if [ -f "$dotenv" ]; then
    # Safe enough for our repo: lines like KEY=VALUE.
    # Export everything defined in .env for this shell (no execution, no command substitution).
    while IFS= read -r line || [ -n "$line" ]; do
      line="${line%$'\r'}"
      [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]] && continue
      if [[ "$line" =~ ^[A-Za-z_][A-Za-z0-9_]*= ]]; then
        export "$line"
      fi
    done < "$dotenv"
  fi
}

export_spring_db_env_if_missing() {
  # Worker runs locally (java -jar), not inside docker-compose.
  # For stability, we derive Spring DB env from ${ROOT_DIR}/.env (and POSTGRES_* overrides)
  # instead of trusting whatever is already exported in the current shell.
  # If you *want* to keep your existing SPRING_DATASOURCE_* exports, set QUANTOR_KEEP_SPRING_DB_ENV=1.
  load_dotenv_if_present

  local db="${POSTGRES_DB:-quantor}"
  local user="${POSTGRES_USER:-quantor}"
  local pass="${POSTGRES_PASSWORD:-quantor}"

  local want_url="jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${db}"

  if [ "${QUANTOR_KEEP_SPRING_DB_ENV:-0}" != "1" ]; then
    # Override always.
    export SPRING_DATASOURCE_URL="$want_url"
    export SPRING_DATASOURCE_USERNAME="$user"
    export SPRING_DATASOURCE_PASSWORD="$pass"
  else
    # Keep existing, but still fill missing.
    : "${SPRING_DATASOURCE_URL:=$want_url}"
    : "${SPRING_DATASOURCE_USERNAME:=$user}"
    : "${SPRING_DATASOURCE_PASSWORD:=$pass}"
    export SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD
  fi

  # Safety net: if someone exported placeholders like <POSTGRES_USER>, wipe them.
  if [[ "${SPRING_DATASOURCE_USERNAME}" == *"<"* ]]; then
    export SPRING_DATASOURCE_USERNAME="$user"
  fi
  if [[ "${SPRING_DATASOURCE_PASSWORD}" == *"<"* ]]; then
    export SPRING_DATASOURCE_PASSWORD="$pass"
  fi
  if [[ "${SPRING_DATASOURCE_URL}" == *"<"* ]]; then
    export SPRING_DATASOURCE_URL="$want_url"
  fi
}

compose() {
  if docker compose version >/dev/null 2>&1; then
    docker compose "$@"
  else
    docker-compose "$@"
  fi
}

wait_for_port() {
  local host="$1" port="$2" timeout_sec="$3"
  local start
  start=$(date +%s)

  while true; do
    if (echo >/dev/tcp/"$host"/"$port") >/dev/null 2>&1; then
      return 0
    fi
    local now
    now=$(date +%s)
    if (( now - start >= timeout_sec )); then
      return 1
    fi
    sleep 1
  done
}

is_running() {
  local pidfile="$1"
  [[ -f "$pidfile" ]] || return 1
  local pid
  pid=$(cat "$pidfile" 2>/dev/null || true)
  [[ -n "$pid" ]] || return 1
  kill -0 "$pid" 2>/dev/null
}

start_bg() {
  local name="$1"; shift
  local log="$LOG_DIR/${name}.log"
  local pidfile="$PID_DIR/${name}.pid"

  if is_running "$pidfile"; then
    echo "$name already running (pid=$(cat "$pidfile"))"
    return 0
  fi

  echo "==> Starting $name"
  echo "    log: $log"

  nohup "$@" > "$log" 2>&1 &
  echo $! > "$pidfile"
}

start_postgres() {
  echo "==> Starting Postgres"

  if [[ "${QUANTOR_DB_RESET:-0}" == "1" ]]; then
    echo "--> QUANTOR_DB_RESET=1: dropping compose volumes (docker compose down -v)"
    compose down -v || true
  fi

  compose up -d postgres

  echo "Postgres is listening on ${POSTGRES_HOST}:${POSTGRES_PORT}"
  if ! wait_for_port "$POSTGRES_HOST" "$POSTGRES_PORT" "$POSTGRES_WAIT_SEC"; then
    echo "[ERROR] Postgres is NOT reachable on ${POSTGRES_HOST}:${POSTGRES_PORT} after ${POSTGRES_WAIT_SEC}s" >&2
    exit 1
  fi
  echo "Postgres OK (${POSTGRES_PORT})"
}

start_api() {
  if [[ "${QUANTOR_SKIP_API:-0}" == "1" ]]; then
    echo "==> Skipping API (QUANTOR_SKIP_API=1)"
    return 0
  fi

  # API may not compile in some dev steps; keep it isolated.
  # Run it from its module dir so Spring Boot resolves the main class.
  start_bg api bash -lc "cd '$ROOT_DIR/quantor-api' && mvn -DskipTests spring-boot:run"
}

start_worker() {
  if [[ "${QUANTOR_SKIP_WORKER:-0}" == "1" ]]; then
    echo "==> Skipping Worker (QUANTOR_SKIP_WORKER=1)"
    return 0
  fi

  echo "==> Starting worker"
  echo "    build log: $LOG_DIR/worker-build.log"

  # Build an executable boot jar, then run it. This avoids spring-boot:run issues on the parent POM.
  mvn -DskipTests -pl quantor-worker -am clean package > "$LOG_DIR/worker-build.log" 2>&1

  local jar="$ROOT_DIR/quantor-worker/target/quantor-worker-1.0.0.jar"
  if [[ ! -f "$jar" ]]; then
    echo "[ERROR] Worker jar not found: $jar" >&2
    exit 1
  fi

  # Ensure local worker uses the same DB credentials as docker-compose (.env).
  export_spring_db_env_if_missing
  start_bg worker java -jar "$jar"
}

# Load .env (if present) before booting anything so this shell sees the same vars as docker-compose.
load_dotenv_if_present

start_postgres
start_api
start_worker

echo "==> Done"
echo "- logs:   $LOG_DIR"
echo "- pids:   $PID_DIR"
