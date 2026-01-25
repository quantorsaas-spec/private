# Quantor Distributed Tracing (OpenTelemetry + Jaeger)

This project uses **Micrometer Tracing** bridged to **OpenTelemetry**, exporting spans via **OTLP**.

## 1) Start Jaeger (OTLP enabled)

```bash
docker compose -f docker-compose.observability.yml up -d
```

Open Jaeger UI:
- http://localhost:16686

## 2) Configure OTLP endpoint

Set the OTLP exporter endpoint for both API and worker:

```bash
set QUANTOR_OTLP_ENDPOINT=http://localhost:4317
```

Optional sampling (0.0..1.0):

```bash
set QUANTOR_TRACING_SAMPLE_PROBABILITY=1.0
```

## 3) Run API + Worker

- API: `quantor-api`
- Worker: `quantor-worker`

## 4) See an end-to-end trace

1. Send a request with a stable request id:

```bash
curl -H "X-Request-Id: demo-123" -H "Authorization: Bearer <TOKEN>" \
  -X POST http://localhost:8080/api/v1/engine/start \
  -H "Content-Type: application/json" \
  -d '{"strategyId":"ema","symbol":"BTCUSDT","interval":"1m","lookback":200,"periodMs":1000}'
```

2. API writes a DB command with `request_id` and `traceparent`.
3. Worker claims the command and continues the trace:
   - span name: `bot.command.process`

In Jaeger, search for service:
- `quantor-api`
- `quantor-worker`

You should see **one trace** containing spans from both services.
