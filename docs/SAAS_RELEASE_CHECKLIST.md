# Quantor SaaS release checklist (MVP → first revenue)

Repo root: `./` (modules listed in `README.md`).

## 1) SaaS fundamentals (must-have)

### Identity & tenancy
- [ ] Every request is executed as a verified actor (**JWT**) with a stable `userId`.
- [ ] No endpoint accepts `userId` from the client body/query (only from JWT).
- [ ] Every DB row that is user-owned contains `user_id` (UUID) and is filtered server-side.

### Billing & entitlements
- [ ] Single source of truth: `subscriptions` table (LemonSqueezy webhook updates it).
- [ ] Entitlements enforce plan limits at the API layer before starting bot instances.
- [ ] Grace period rules are explicit (ACTIVE/TRIAL/grace until period end).

### Security
- [ ] **No default JWT secret** in config files.
- [ ] Secrets only via env / secret store; examples live in `config/*.example`.
- [ ] Webhook signature verification is mandatory.
- [ ] Rate limit on auth endpoints (/login, /register).

## 2) Reliability (first paying users)

### Observability
- [ ] `/actuator/health` and `/actuator/metrics` protected but accessible for ops.
- [ ] Structured logs include `userId`, `traceId`.
- [ ] Job idempotency is in place for webhooks & async jobs.

### Operations
- [ ] `quantor-cli preflight` validates config, DB connectivity, and required secrets.
- [ ] Runbook exists: `docs/RUNBOOK.md`.

## 3) Go-to-market (global)

- [ ] Pricing plans + limits documented (`PlanCatalog`).
- [ ] Landing → checkout → webhook → activated subscription flow validated end-to-end.
- [ ] Basic legal pages ready (Terms / Privacy / Risk Disclaimer).

## 4) QA gates

- [ ] `./scripts/qa.sh` passes.
- [ ] Local compose up + smoke test.
- [ ] At least one "happy path" integration test for: register → login → subscribe (webhook) → start bot.

---

If any item above is red, the product is not ready for paid traffic.
