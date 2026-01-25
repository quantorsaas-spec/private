#!/usr/bin/env bash
set -euo pipefail

CONTAINER=quantor-api
PORT=8080

echo "== Checking container =="
docker inspect "$CONTAINER" >/dev/null 2>&1 || {
  echo "Container $CONTAINER not found"
  exit 1
}

echo "== Fetching actuator mappings from inside container =="
docker exec -i "$CONTAINER" sh -lc "
  wget -qO- http://127.0.0.1:${PORT}/actuator/mappings \
  | sed -n '
    s/.*\"predicate\":\"{\\([^}]*\\)}\".*/\\1/p
  ' \
  | grep '/api/v1' \
  | sort -u
"
