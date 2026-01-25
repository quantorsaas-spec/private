# Quantor — SaaS platform for traders (Java 21)

Quantor is a **SaaS trading platform**: API + worker + CLI tools + exchange adapters.
Goal: ship a secure, production-grade MVP fast and move to global market.

> ⚠️ Not financial advice. Live trading carries risk.

## Modules (repo root: `./`)
- `quantor-api/` — Spring Boot API (auth, billing, trading session control, admin)
- `quantor-worker/` — background worker (jobs / scheduling)
- `quantor-cli/` — operator & dev CLI (setup, config, preflight, doctor, telegram)
- `quantor-domain/` — core domain primitives
- `quantor-application/` — use-cases / orchestration / ports
- `quantor-infrastructure/` — adapters (exchanges, persistence, telegram, crypto, etc.)
- `quantor-saas-domain/`, `quantor-saas-infrastructure/` — SaaS entities (users, subscriptions, tokens)

## Stack
- Java **21** (enforced in `pom.xml`)
- Spring Boot 3.x
- Postgres 16
- Docker Compose (local)
- GCP (Cloud Run / Cloud SQL) — for production deployment

## Run locally
See `RUN_LOCAL.md` (authoritative).

## Security rules (non-negotiable)
- Trading API endpoints are **JWT-protected**. No client-provided `userId`.
- Do not commit real secrets (`.env`, `config/*.properties`, webhook secrets, JWT secrets).

## QA (what you run before pushing)
From repo root (`./`):

```bash
# fast checks
mvn -q -DskipTests=false test

# build all modules
mvn -q -DskipTests package
```

If Docker is available:

```bash
docker compose up -d --build
curl http://localhost:8080/actuator/health
```
