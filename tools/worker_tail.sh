#!/usr/bin/env bash
set -euo pipefail

JOB_KEY="${JOB_KEY:-622af9a3-42b1-40e6-9c1d-d425a94679a0:EMA:BINANCE:BINANCE:BTC/USDT:M1}"
docker compose logs -f --tail=200 worker | egrep -i "Session started|Session stopped|No session|ERROR|Exception|${JOB_KEY}"
