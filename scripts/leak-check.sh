cat > scripts/leak-check.sh <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

# Fail if we detect likely real secrets committed into git working tree/index.
# Allow docs/examples with placeholders like "change_me" or empty values.

bad=0

echo "== Leak check (likely real secrets) =="

# 1) PEM private keys
if git grep -n -I -E "-----BEGIN (RSA|EC|OPENSSH|DSA) PRIVATE KEY-----" -- .; then
  echo "BAD: private key material found"
  bad=1
fi

# 2) OpenAI keys (classic sk-*)
if git grep -n -I -E "\bsk-[A-Za-z0-9]{20,}\b" -- .; then
  echo "BAD: OpenAI-like key found"
  bad=1
fi

# 3) Telegram bot tokens: digits:letters
if git grep -n -I -E "\b[0-9]{6,12}:[A-Za-z0-9_-]{20,}\b" -- .; then
  echo "BAD: Telegram bot token-like string found"
  bad=1
fi

# 4) AWS Access Key ID
if git grep -n -I -E "\bAKIA[0-9A-Z]{16}\b" -- .; then
  echo "BAD: AWS access key id found"
  bad=1
fi

# 5) Binance keys (heuristic: non-empty values >= 20 chars, excluding change_me)
if git grep -n -I -E "\b(BINANCE_API_KEY|BINANCE_API_SECRET)\s*=\s*([A-Za-z0-9_=-]{20,})\b" -- . | grep -vi "change_me"; then
  echo "BAD: Binance-like secret value found"
  bad=1
fi

if [ "$bad" -ne 0 ]; then
  echo "FAIL"
  exit 1
fi

echo "OK"
EOF

chmod +x scripts/leak-check.sh
