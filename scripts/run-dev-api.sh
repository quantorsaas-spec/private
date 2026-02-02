#!/usr/bin/env bash
set -euo pipefail

# Path: scripts/run-dev-api.sh
# Run Quantor API locally with dev profile.
#
# This script intentionally avoids "source config/secrets.properties" because that file is
# a Java properties file and may contain keys like "telegram.botToken" (invalid for bash).

cd "$(dirname "$0")/.."

# Load env vars from a shell-compatible env file if present.
if [[ -f config/.env ]]; then
  # shellcheck disable=SC1091
  set -a
  source config/.env
  set +a
elif [[ -f config/secrets.env ]]; then
  # shellcheck disable=SC1091
  set -a
  source config/secrets.env
  set +a
fi

PORT="${1:-18081}"

JWT_VAL="${QUANTOR_JWT_SECRET:-}"
TG_VAL="${QUANTOR_TELEGRAM_BOT_TOKEN:-}"

echo "JWT=${#JWT_VAL} TG=${#TG_VAL} ALLOW=${QUANTOR_TELEGRAM_ALLOWED_USER_IDS:-} PORT=${PORT}"

if [[ -n "${TG_VAL}" ]]; then
  echo "Check Telegram token..."
  curl -s "https://api.telegram.org/bot${TG_VAL}/getMe" | grep -q '"ok":true' \
    && echo "Telegram OK" || (echo "Telegram FAIL" && exit 1)
else
  echo "Telegram token is empty (QUANTOR_TELEGRAM_BOT_TOKEN). Bot will not start."
fi

echo "Free port ${PORT}..."
lsof -ti :"${PORT}" | xargs -r kill -TERM || true
sleep 1
lsof -ti :"${PORT}" | xargs -r kill -KILL || true

echo "Start API..."
java -jar quantor-api/target/quantor-api-1.0.0.jar \
  --spring.profiles.active=dev \
  --server.port="${PORT}" \
  --quantor.telegram.enabled=true
