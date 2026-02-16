#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck disable=SC1090
source "${ROOT_DIR}/tools/auth.sh" >/dev/null

API_URL="${API_URL:-http://localhost:8080}"

JOB_KEY="${JOB_KEY:-622af9a3-42b1-40e6-9c1d-d425a94679a0:EMA:BINANCE:BINANCE:BTC/USDT:M1}"

curl -sS -i -X POST "${API_URL}/api/v1/engine/stop" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H 'Content-Type: application/json' \
  -d "{\"jobKey\":\"${JOB_KEY}\"}"
