#!/usr/bin/env bash
set -euo pipefail

JOB_KEY="${JOB_KEY:-622af9a3-42b1-40e6-9c1d-d425a94679a0:EMA:BINANCE:BINANCE:BTC/USDT:M1}"
INTERVAL="${INTERVAL:-2}"

while true; do
  echo "=== $(date -u +"%Y-%m-%dT%H:%M:%SZ") ==="
  docker compose exec -T postgres sh -lc "
psql -U quantor -d quantor -c \"
select status, lease_owner, lease_until, updated_at
from bot_instances
where job_key = '${JOB_KEY}';
\"
" | sed -n '1,10p'
  sleep "${INTERVAL}"
done
