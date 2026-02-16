#!/usr/bin/env bash
set -euo pipefail

BASE="http://127.0.0.1:8080"
EMAIL="pavel+ok$(date +%s)@quantor.dev"
PASS="Test1234!"

echo "== wait health =="
for i in $(seq 1 60); do
  if curl -fsS "$BASE/actuator/health" >/dev/null; then
    echo "API_UP"
    break
  fi
  sleep 1
done

echo "== register =="
curl -fsS -X POST "$BASE/api/v1/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\",\"name\":\"Pavel\"}" >/dev/null

echo "== login =="
RESP="$(curl -fsS -X POST "$BASE/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}")"

TOKEN="$(echo "$RESP" | jq -r '.accessToken')"
RID="$(echo "$RESP" | jq -r '.refreshTokenId')"
RSEC="$(echo "$RESP" | jq -r '.refreshTokenSecret')"

echo "TOKEN_LEN=${#TOKEN} RID=$RID RSEC_LEN=${#RSEC}"

echo "== me before restart =="
curl -fsS -H "Authorization: Bearer $TOKEN" "$BASE/api/v1/auth/me" | jq .

echo "== refresh =="
NEW="$(curl -fsS -X POST "$BASE/api/v1/auth/refresh" \
  -H 'Content-Type: application/json' \
  -d "{\"refreshTokenId\":\"$RID\",\"refreshTokenSecret\":\"$RSEC\"}")"
NEW_TOKEN="$(echo "$NEW" | jq -r '.accessToken')"
echo "NEW_TOKEN_LEN=${#NEW_TOKEN}"

echo "== restart api =="
docker compose restart api >/dev/null

for i in $(seq 1 60); do
  if curl -fsS "$BASE/actuator/health" >/dev/null; then
    echo "API_UP_AFTER_RESTART"
    break
  fi
  sleep 1
done

echo "== me after restart (old access token) =="
curl -fsS -H "Authorization: Bearer $TOKEN" "$BASE/api/v1/auth/me" | jq .

echo "OK: smoke-auth"
