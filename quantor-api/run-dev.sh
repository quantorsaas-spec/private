#!/usr/bin/env bash
set -euo pipefail

PORT="${PORT:-8081}"
APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo ">> Quantor API dev runner"
echo ">> Port: ${PORT}"
echo ">> Dir:  ${APP_DIR}"

PID="$(sudo lsof -tiTCP:${PORT} -sTCP:LISTEN || true)"
if [[ -n "${PID}" ]]; then
  echo ">> Port ${PORT} is busy by PID=${PID}. Stopping..."
  sudo kill -15 "${PID}" || true
  sleep 1
  if sudo kill -0 "${PID}" 2>/dev/null; then
    echo ">> Still alive. Killing hard..."
    sudo kill -9 "${PID}" || true
  fi
fi

cd "${APP_DIR}"
exec mvn -DskipTests spring-boot:run -Dspring-boot.run.profiles=dev
