#!/usr/bin/env bash
set -euo pipefail

JOB_KEY="${JOB_KEY:-622af9a3-42b1-40e6-9c1d-d425a94679a0:EMA:BINANCE:BINANCE:BTC/USDT:M1}"
LIMIT="${LIMIT:-10}"

docker compose exec -T postgres sh -lc "
psql -U quantor -d quantor -c \"
select id, command, status, attempts, request_id, traceparent, created_at
from bot_commands
where bot_instance_id = (
  select id from bot_instances where job_key = '${JOB_KEY}'
)
order by created_at desc
limit ${LIMIT};
\"
"
