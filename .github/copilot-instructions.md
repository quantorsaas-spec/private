<ВСТАВЬ СОДЕРЖИМОЕ ИЗ МОЕГО copilot-instructions.md>
cat > .github/copilot-instructions.md <<'EOF'
# Quantor Copilot Instructions

## Non-negotiables
- Never commit secrets. All secrets must come from env vars or secret managers.
- Trading core is fail-closed: if entitlement cannot be proven -> BLOCKED.
- Live trading must be disabled by default (config: liveRealTradingEnabled=false).
- No "forcePaid" bypass in production paths.

## Architecture
- Modules: quantor-domain, quantor-application, quantor-infrastructure, quantor-api, quantor-worker, quantor-cli.
- Ports/Adapters: application defines ports, infrastructure implements adapters.

## Coding rules
- Java 21, Spring Boot.
- Prefer small PRs, no refactors without tests.
- Any change to billing/subscription gate requires tests.

## Security
- Never log secrets.
- Keep webhook signature verification strict (fail closed).
EOF
