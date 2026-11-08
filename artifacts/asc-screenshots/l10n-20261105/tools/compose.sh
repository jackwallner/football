#!/bin/bash
set -euo pipefail
KIT="$(cd "$(dirname "$0")/.." && pwd)"
ROOT="$(cd "$KIT/../../.." && pwd)"
PY="$KIT/.venv/bin/python"
mkdir -p "$KIT/sheets"
result=0
for pair in it:it nl-NL:nl; do
  locale=${pair%%:*}; lang=${pair#*:}
  "$PY" "$KIT/tools/reskin.py" --config "$KIT/frames.json" --raw-root "$KIT/raw" --headers "$KIT/copy/headers.json" --locale "$locale" --out "$KIT/out"
  "$PY" "$KIT/tools/sheet.py" --config "$KIT/frames.json" --locale "$locale" --out-root "$KIT/out"
  "$PY" "$KIT/tools/gate.py" --config "$KIT/frames.json" --raw-root "$KIT/raw" --headers "$KIT/copy/headers.json" --out-root "$KIT/out" --locale "$locale" --catalog "$ROOT/StatScout/Resources/Localizable.xcstrings" --lang "$lang" || result=1
done
cp "$KIT"/out/sheets/*.jpg "$KIT/sheets/"
exit "$result"
