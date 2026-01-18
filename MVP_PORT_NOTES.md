# Quantor MVP → Quantor SaaS port (applied)

This workspace contains a "maximum utility" port of the key MVP pieces into the current Quantor multi-module SaaS codebase.

## Security changes
- Removed `config/secrets.properties.bak`.
- Sanitized `config/secrets.properties` (cleared real Telegram token/chat id). Fill locally only.
- IMPORTANT: rotate any keys that ever existed in prior versions/archives.

## Build / structure
- Root `pom.xml` now includes all product modules:
  - quantor-api
  - quantor-worker
  - quantor-saas-domain
  - quantor-saas-infrastructure

## Risk guards (ported/added)
- Added:
  - `com.quantor.domain.risk.DailyLossGuard`
  - `com.quantor.domain.risk.MaxTradesGuard`
- Live engine supports optional guards via:
  - `LiveEngine.setDailyLossGuard(...)`
  - `LiveEngine.setMaxTradesGuard(...)`
- Worker bootstrap wires the guards from config keys (with safe defaults):
  - `DAILY_LOSS_LIMIT_PCT` (default 0.05)
  - `MAX_TRADES_PER_DAY` (default 25)

## SQLite secrets store fix
- Fixed placeholders in `UserSecretsStore` SQL (missing parameter + UPDATE clause).

