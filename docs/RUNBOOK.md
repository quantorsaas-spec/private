# Quantor Runbook (ops)

Minimal operational manual for MVP → first revenue.

## Services
- **API**: `quantor-api` (Spring Boot) — HTTP :8080
- **Worker**: `quantor-worker` (Spring Boot) — background jobs
- **Postgres**: local via Docker Compose; prod via Cloud SQL

## Health checks
- API: `GET /actuator/health`
- Worker: `GET /actuator/health`

## Local smoke test
From repo root (`./`):

```bash
docker compose up -d --build
curl -s http://localhost:8080/actuator/health
```

## Common incidents

### 1) Users cannot login (JWT errors)
Symptoms:
- 500 on `/auth/login` or `/auth/register`

Checks:
- Confirm `QUANTOR_JWT_SECRET` is set (dev profile can fall back, prod cannot).
- Look for log line: `using a dev fallback secret` (must never appear in prod).

Fix:
- Set `QUANTOR_JWT_SECRET` and restart the service.

### 2) LemonSqueezy webhooks not applying
Symptoms:
- User paid, but plan stays FREE.

Checks:
- Verify `QUANTOR_LEMONSQUEEZY_WEBHOOK_SECRET` is set.
- Check webhook endpoint is reachable from the internet (Cloud Run URL).
- Confirm signature verification logs.

Fix:
- Fix secret / webhook config and replay the event (idempotency expected).

### 3) Plan limits not enforced
Symptoms:
- User on FREE can start multiple bots.

Checks:
- Verify API calls `SubscriptionAccessService.assertCanStart(...)` before starting engines.
- Validate `PlanCatalog` limits.

Fix:
- Add enforcement in the start endpoint (do not trust the client).

## Backups
- Postgres: enable automated backups in Cloud SQL (prod).
