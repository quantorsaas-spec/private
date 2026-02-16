#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck disable=SC1090
source "${ROOT_DIR}/tools/auth.sh" >/dev/null

API_URL="${API_URL:-http://localhost:8080}"

# можно переопределять через env:
# ENGINE_START_PAYLOAD='{"strategyId":"EMA","symbol":"BTC/USDT","interval":"1m","lookback":200,"periodMs":1000}'
ENGINE_START_PAYLOAD="${ENGINE_START_PAYLOAD:-{\"strategyId\":\"EMA\",\"symbol\":\"BTC/USDT\",\"interval\":\"1m\",\"lookback\":200,\"periodMs\":1000}}"

curl -sS -i -X POST "${API_URL}/api/v1/engine/start" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H 'Content-Type: application/json' \
  -d "${ENGINE_START_PAYLOAD}"
