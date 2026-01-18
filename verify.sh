#!/usr/bin/env bash
set -euo pipefail
ROOT="${1:-.}"
MANIFEST="$ROOT/manifest.json"
if [[ ! -f "$MANIFEST" ]]; then
  echo "manifest.json not found in $ROOT" >&2
  exit 1
fi
python3 - <<'PY'
import json, hashlib, os, sys, pathlib
root = pathlib.Path(sys.argv[1])
m = json.loads((root/"manifest.json").read_text(encoding="utf-8"))
bad=[]
for f in m["files"]:
    p = root / f["path"]
    if not p.exists():
        bad.append("MISSING: "+f["path"]); continue
    h=hashlib.sha256(p.read_bytes()).hexdigest()
    if h!=f["sha256"]:
        bad.append("BADHASH: "+f["path"])
if bad:
    print("FAILED")
    print("\n".join(bad))
    sys.exit(1)
print(f"OK - files verified: {m['file_count']}")
PY
