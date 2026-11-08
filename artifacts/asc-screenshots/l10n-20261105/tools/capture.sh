#!/bin/bash
set -euo pipefail
KIT="$(cd "$(dirname "$0")/.." && pwd)"
ROOT="$(cd "$KIT/../../.." && pwd)"
cd "$ROOT"
LOCALES=("${@:-it nl-NL}")
TEST_CONFIG=StatScoutUITests/StoreScreenshotLocales.json
BACKUP=$(mktemp)
cp "$TEST_CONFIG" "$BACKUP"
trap 'cp "$BACKUP" "$TEST_CONFIG"; rm -f "$BACKUP"' EXIT
SETS=(phone ipad)
if [ -n "${SCREENSHOT_SET:-}" ]; then
  case "$SCREENSHOT_SET" in phone|ipad) SETS=("$SCREENSHOT_SET") ;; *) echo "Invalid SCREENSHOT_SET" >&2; exit 1 ;; esac
fi
for SET in "${SETS[@]}"; do
  python3 - "$KIT/app.json" "$TEST_CONFIG" "$SET" "${LOCALES[@]}" <<'PY'
import json, sys
app, dest, device, *wanted = sys.argv[1:]
wanted = set(' '.join(wanted).split()) | {'en-US'}
labels = {'en-US': ('en', 'en_US', 'Following', 'Compare with another player', 'Year Compare'), 'de-DE': ('de', 'de_DE', 'Gefolgt', 'Mit anderem Spieler vergleichen', 'Jahr zu Jahr'), 'es-MX': ('es', 'es_MX', 'Siguiendo', 'Comparar con otro jugador', 'Año a año'), 'it': ('it', 'it_IT', 'Seguiti', 'Confronta con un altro giocatore', 'Per anno'), 'nl-NL': ('nl', 'nl_NL', 'Volgen', 'Vergelijk met een andere speler', 'Per jaar')}
allowed = {'en-US'} | {item['store_locale'] for item in json.load(open(app))['targets']}
if wanted - allowed:
    raise SystemExit(f'Unknown locales: {wanted - allowed}')
data = []
for store in ['en-US'] + sorted(wanted - {'en-US'}):
    language, region, following, compare, years = labels[store]
    data.append(dict(store=store, language=language, region=region, following=following, compare=compare, years=years, board='standard' if device == 'phone' else 'advanced', scenes=list(range(1, 8)) if device == 'phone' else [1]))
with open(dest, 'w') as f:
    json.dump(data, f, ensure_ascii=False, indent=2)
PY
  if [ "$SET" = phone ]; then DEVICE='iPhone 17 Pro Max'; else DEVICE='iPad Pro 13-inch (M5)'; fi
  ID_FILE=$(mktemp)
  gh-sim test --scheme StatScoutUITests --device "$DEVICE" --only StatScoutUITests/LocalizedStoreScreenshotUITests --id-file "$ID_FILE"
  ID=$(cat "$ID_FILE"); rm -f "$ID_FILE"
  python3 - "$HOME/.gh-sim/results/$ID/attachments" "$KIT/raw" "$SET" "$TEST_CONFIG" <<'PY'
import json, re, shutil, sys
from pathlib import Path
src, dest, device = map(Path, sys.argv[1:4])
locales = json.load(open(sys.argv[4]))
manifest = json.loads((src / 'manifest.json').read_text())
count = 0
for entry in manifest:
    for item in entry.get('attachments', []):
        name = item.get('suggestedHumanReadableName', '')
        match = re.search(r'(en-US|es-MX|de-DE|it|nl-NL)__(\d\d)', name)
        source = src / item.get('exportedFileName', '')
        if match and source.is_file() and source.suffix.lower() == '.png':
            target = dest / match[1] / device / (match[2] + '.png')
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)
            count += 1
print(f'{device}: sorted {count} captures')
expected = sum(len(item['scenes']) for item in locales)
if count != expected:
    raise SystemExit(f'Expected {expected} screenshot attachments, found {count}')
PY
done
