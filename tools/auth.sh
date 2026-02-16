#!/usr/bin/env bash
set -euo pipefail

EMAIL="${EMAIL:-test+1@quantor.local}"
PASS="${PASS:-Password123!}"
API_URL="${API_URL:-http://localhost:8080}"

curl -sS -X POST "${API_URL}/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"${EMAIL}\",\"password\":\"${PASS}\"}" > /tmp/auth.json

export TOKEN="$(python3 - <<'PY'
import json
print(json.load(open("/tmp/auth.json"))["accessToken"])
PY
)"

python3 - <<'PY'
import os
t=os.environ.get("TOKEN","")
print("TOKEN_len=", len(t), "JWT_parts=", len(t.split(".")))
PY
