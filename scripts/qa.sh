#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
cd "$ROOT"

echo "[QA] 1) Repo hygiene"

# Windows ADS artifacts (should never exist in repo snapshots)
if find . -type f \( -name '*Zone.Identifier*' -o -name '*#Uf03aZone.Identifier' \) | grep -q .; then
  echo "ERROR: Windows Zone.Identifier artifacts found" >&2
  find . -type f \( -name '*Zone.Identifier*' -o -name '*#Uf03aZone.Identifier' \) -print >&2
  exit 1
fi

# IDE / local state
for d in .idea data; do
  if [ -d "$d" ]; then
    echo "ERROR: $d/ should not be committed" >&2
    exit 1
  fi
done

# secrets
if [ -f .env ]; then
  echo "ERROR: .env must not be committed (keep .env.example only)" >&2
  exit 1
fi

echo "[QA] 2) Build + tests"
command -v mvn >/dev/null 2>&1 || {
  echo "ERROR: mvn not found. Install Maven (or add Maven Wrapper)" >&2
  exit 1
}

# full reactor (compiles API + worker + cli + modules)
mvn -q -DskipTests=false test
mvn -q -DskipTests package

echo "[QA] 3) Basic config sanity"
python3 - <<'PY'
import pathlib
root = pathlib.Path('quantor-api/src/main/resources')
for p in sorted(root.glob('application*.yml')):
    txt = p.read_text(encoding='utf-8')
    if not txt.strip():
        raise SystemExit(f"EMPTY: {p}")
    print('OK', p)
PY

echo "[QA] 4) Optional docker smoke (if available)"
if command -v docker >/dev/null 2>&1; then
  if docker compose version >/dev/null 2>&1; then
    echo "[QA] docker compose config"
    docker compose config >/dev/null
    echo "[QA] docker compose up (detached)"
    docker compose up -d --build
    echo "[QA] health"
    for i in $(seq 1 60); do
      curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null && break
      sleep 1
    done
    curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null
    echo "[QA] docker compose down"
    docker compose down -v
  else
    echo "[QA] docker present but docker compose missing - skipping"
  fi
else
  echo "[QA] docker not present - skipping"
fi

echo "[QA] OK"
