#!/usr/bin/env bash
set -euo pipefail

# Moves Windows "Mark of the Web" artifacts (*Zone.Identifier*) that sometimes appear in zips.
# This is safe: it does NOT delete anything; it relocates them into .dev/motw-trash/.
#
# Usage:
#   ./scripts/cleanup-motw.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

COUNT=$(find . -type f -name '*Zone.Identifier*' | wc -l | tr -d ' ')
if [[ "$COUNT" == "0" ]]; then
  echo "No *Zone.Identifier* artifacts found."
  exit 0
fi

TRASH_DIR=".dev/motw-trash"
mkdir -p "$TRASH_DIR"

echo "Found $COUNT *Zone.Identifier* artifact(s). Moving to $TRASH_DIR (no data loss)..."
while IFS= read -r f; do
  # Keep relative path so we can restore if needed
  rel="${f#./}"
  dest="$TRASH_DIR/$rel"
  mkdir -p "$(dirname "$dest")"
  echo "$f -> $dest"
  mv "$f" "$dest"
done < <(find . -type f -name '*Zone.Identifier*' -print)

echo "Done. (If you want to restore: move files back from $TRASH_DIR)"
