# File: tools/engine.sh
#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLS_DIR="${ROOT}/tools"

API_URL="${API_URL:-http://localhost:8080}"
LAST_JOBKEY_FILE="${LAST_JOBKEY_FILE:-${TOOLS_DIR}/.engine_last_job_key}"

ensure_token() {
  if [[ -z "${TOKEN:-}" ]]; then
    # shellcheck source=/dev/null
    source "${TOOLS_DIR}/auth.sh" >/dev/null
  fi
}

save_last_jobkey() {
  local jobKey="$1"
  if [[ -n "${jobKey}" ]]; then
    printf '%s\n' "${jobKey}" > "${LAST_JOBKEY_FILE}"
  fi
}

load_last_jobkey() {
  if [[ -f "${LAST_JOBKEY_FILE}" ]]; then
    cat "${LAST_JOBKEY_FILE}"
  else
    echo ""
  fi
}

extract_jobkey_from_json() {
  local json="$1"
  python3 - <<'PY' "${json}"
import json, sys
s = sys.argv[1].strip()
try:
  obj = json.loads(s)
  print(obj.get("jobKey",""))
except Exception:
  print("")
PY
}

# Caller tag that API can log (helps identify who issued start/stop)
caller_tag() {
  local tty
  tty="$(tty 2>/dev/null || echo 'no-tty')"
  echo "wsl user=$(id -un) uid=$(id -u) tty=${tty} pid=$$ ppid=$PPID pwd=$(pwd)"
}

usage() {
  cat <<EOF
Usage:
  ./tools/engine.sh start
  ./tools/engine.sh stop [jobKey]
  ./tools/engine.sh status [jobKey]

Env overrides (optional):
  STRATEGY_ID, SYMBOL, INTERVAL, LOOKBACK, PERIOD_MS
  API_URL
EOF
}

cmd="${1:-}"
shift || true

case "${cmd}" in
  start)
    ensure_token

    STRATEGY_ID="${STRATEGY_ID:-EMA}"
    SYMBOL="${SYMBOL:-BTC/USDT}"
    INTERVAL="${INTERVAL:-1m}"
    LOOKBACK="${LOOKBACK:-200}"
    PERIOD_MS="${PERIOD_MS:-1000}"

    payload="$(cat <<JSON
{"strategyId":"${STRATEGY_ID}","symbol":"${SYMBOL}","interval":"${INTERVAL}","lookback":${LOOKBACK},"periodMs":${PERIOD_MS}}
JSON
)"

    echo "[engine] start payload: ${payload}"

    CALLER="$(caller_tag)"
    resp="$(curl -sS -X POST "${API_URL}/api/v1/engine/start" \
      -H "Authorization: Bearer ${TOKEN}" \
      -H 'Content-Type: application/json' \
      -H "X-Caller: ${CALLER}" \
      -d "${payload}")"

    echo "[engine] response:"
    echo "${resp}" | python3 -m json.tool 2>/dev/null || echo "${resp}"

    jobKey="$(extract_jobkey_from_json "${resp}")"
    if [[ -z "${jobKey}" ]]; then
      echo "ERROR: could not parse jobKey from response"
      exit 1
    fi

    save_last_jobkey "${jobKey}"
    echo "[engine] saved last jobKey: ${jobKey}"
    ;;

  stop)
    ensure_token

    jobKey="${1:-}"
    if [[ -z "${jobKey}" ]]; then
      jobKey="$(load_last_jobkey)"
    fi
    if [[ -z "${jobKey}" ]]; then
      echo "ERROR: no last jobKey found. Run: ./tools/engine.sh start"
      exit 1
    fi

    CALLER="$(caller_tag)"
    resp="$(curl -sS -X POST "${API_URL}/api/v1/engine/stop" \
      -H "Authorization: Bearer ${TOKEN}" \
      -H 'Content-Type: application/json' \
      -H "X-Caller: ${CALLER}" \
      -d "{\"jobKey\":\"${jobKey}\"}")"

    echo "${resp}" | python3 -m json.tool 2>/dev/null || echo "${resp}"
    ;;

  status)
    jobKey="${1:-}"
    if [[ -z "${jobKey}" ]]; then
      jobKey="$(load_last_jobkey)"
    fi
    if [[ -z "${jobKey}" ]]; then
      echo "ERROR: no jobKey provided and no last jobKey saved"
      exit 1
    fi

    # Надёжно: читаем статус из БД (то, что реально делает worker)
    docker compose exec -T postgres sh -lc "
psql -U quantor -d quantor -c \"
select status, lease_owner, lease_until, updated_at
from bot_instances
where job_key = '${jobKey}';
\"
"
    ;;

  *)
    usage
    exit 1
    ;;
esac
