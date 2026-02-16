#!/usr/bin/env bash
set -euo pipefail

PIDS="$(sudo lsof -t -iTCP:8080 -sTCP:LISTEN -Pn || true)"
if [[ -z "${PIDS}" ]]; then
  echo "8080 free"
  exit 0
fi

echo "Killing listeners on 8080: ${PIDS}"
sudo kill -15 ${PIDS} || true
sleep 1

PIDS2="$(sudo lsof -t -iTCP:8080 -sTCP:LISTEN -Pn || true)"
if [[ -n "${PIDS2}" ]]; then
  echo "Force killing: ${PIDS2}"
  sudo kill -9 ${PIDS2} || true
fi

sudo lsof -iTCP:8080 -sTCP:LISTEN -Pn || echo "8080 free"
