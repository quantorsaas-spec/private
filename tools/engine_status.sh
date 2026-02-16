#!/usr/bin/env bash
set -euo pipefail

API_URL="${API_URL:-http://localhost:8080}"
curl -sS "${API_URL}/api/v1/engine/status" | python3 -m json.tool
