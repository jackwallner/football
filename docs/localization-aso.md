# Localization ASO — Football Next: StatScout

## Current state (2026-07-29)

**Only `en-US` is staged.** The 50-locale pass is deliberately deferred until the en-US listing
is approved.

The 49 non-English locale folders that shipped with this repo were **stale Baseball Savvy
StatScout metadata** (MLB Statcast, xwOBA, Japanese baseball copy). They were never football
content and would have created 49 baseball-branded localizations on the football listing.
They are parked, not deleted:

```
fastlane/metadata-locales-parked/     # 49 stale baseball locale dirs
```

Do **not** move them back into `fastlane/metadata/` and upload. They get regenerated from the
en-US source during the localization pass.

## Backups

| Snapshot | Path |
|----------|------|
| Pre-ASO (2026-07-29) | `fastlane/metadata.bak.pre-aso-20260729-*/` |

## Restore a snapshot

```bash
./scripts/restore-appstore-metadata.sh fastlane/metadata.bak.pre-aso-<timestamp>
eval "$(python3 scripts/asc-ensure-draft-version.py | grep '^export ')"
SKIP_SCREENSHOTS=true ./scripts/upload-appstore-metadata.sh
```

## Draft vs live

| Version | State | Notes |
|---------|-------|-------|
| **1.0** | `PREPARE_FOR_SUBMISSION` | Never released. en-US metadata + 8 screenshots uploaded 2026-07-29 |

State file: `scripts/.asc-state.json` (appId `6792930447`)

## Upload commands

```bash
source ~/.football_credentials

# en-US only (current posture): Deliverfile must list just en-US
eval "$(python3 scripts/asc-ensure-draft-version.py | grep '^export ')"
SKIP_SCREENSHOTS=false ./scripts/upload-appstore-metadata.sh

# Full gap-closure once locales are real (draft + API + deliver, all 50)
./scripts/asc-finish-missed.sh
```

Use `scripts/fastlane-bin.sh` (fastlane **2.234+**). Do not use `/usr/local/bin/fastlane` 2.230.

`fastlane/Deliverfile` lists all 50 deliver-supported languages. **That list is what creates
localizations in ASC.** During the en-US-only phase it was temporarily reduced to `en-US`; it has
since been restored to the full 50 for the localization pass. Scope it back down if you need
another en-US-only upload.

## Screenshot gotcha

ASC maps **both** 1290x2796 (6.7") and 1320x2868 (6.9") into the single `APP_IPHONE_67` display
type, capped at 10 images. Uploading both sets produces a jumbled, truncated 10. **Ship only the
8 frames at 1320x2868.** `fastlane/screenshots/en-US/` holds exactly those.

Deliver's retry logic can also leave duplicate images in the set when an upload verification
races. Verify the final set after upload; delete duplicates via
`DELETE /v1/appScreenshots/<id>`.

Screenshots must be **RGB with no alpha channel**. The Claude Design output is RGBA; flatten
before upload.

## Keyword dedupe rule

Apple indexes **name + subtitle + keywords** together.
`scripts/aso-apply-locale-optimizations.py` drops keyword tokens already present in name or
subtitle so the 100-char field is not wasted on duplicates. This is why the en-US field omits
`nfl` and `football`.

Apple tokenizes on **spaces**, so a compound like `nextgen` never matches the query "next gen
stats". Split it: `gen` in keywords + "Next" in the name + "Stats" in the subtitle.
