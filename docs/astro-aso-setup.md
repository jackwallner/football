# Astro ASO setup — Football Next: StatScout

> Playbook: `~/ios/archive/aso/2026-05/astro-global-aso-go-2026.md` · say **"go"** to run the
> full 91-store / 50-locale pipeline.

Last **go** run: **not yet** (en-US staged manually 2026-07-29; global pass deferred pending
approval)

## App

| Field | Value |
|-------|-------|
| App Store name | Football Next: StatScout |
| ASC app ID | `6792930447` |
| Astro app ID | `119` (temporary placeholder — Astro has not indexed the real listing) |
| Bundle ID | `com.jackwallner.football` |
| Draft ASC version | **1.0** (`PREPARE_FOR_SUBMISSION`) |
| Live ASC version | none — never released |

## Current draft metadata (en-US)

| Field | Value | Count |
|-------|-------|------:|
| **Name** | Football Next: StatScout | 24/30 |
| **Subtitle** | Advanced NFL Stats & Analytics | 30/30 |
| **Keywords** | `gen,cpoe,yac,percentile,rankings,compare,player,passing,rushing,receiving,defense,qb,fantasy,teams` | 98/100 |
| **Screenshots** | 8 frames, 1320x2868, RGB | |

Keywords intentionally **omit** `nfl`, `football`, `stats`, `analytics`, `advanced` — all
indexed via name/subtitle (dedupe pass). They also omit `epa`: pop 44, but the SERP is 100%
Environmental Protection Agency and HVAC exam-prep apps.

## US Astro strategy

- **Defend:** brand terms (`statscout`, `football next`), the metric cluster (`cpoe`, `yac`,
  `percentile`)
- **Push:** `football analytics` and `nfl analytics` — every app currently ranking there has 0-1
  ratings
- **Walls (do not field-slot):** `epa`, `dfs`, `snap share`, `american football`, `sports
  analytics`, `football app`
- Full tag breakdown and competitor tiers: [`../aso-plan.md`](../aso-plan.md)

## Commands

```bash
source ~/.football_credentials

# en-US metadata + screenshots to the draft
eval "$(python3 scripts/asc-ensure-draft-version.py | grep '^export ')"
SKIP_SCREENSHOTS=false ./scripts/upload-appstore-metadata.sh

# Re-optimize ranks (7-14 days after the listing is live)
./scripts/astro-optimize.sh

# Full global go (only after en-US is approved)
python3 scripts/aso-apply-locale-optimizations.py
./scripts/astro-sync-all-stores.sh
./scripts/astro-prune-all-stores.sh
python3 scripts/astro-tier1-second-pass.py
./scripts/asc-finish-missed.sh
```

## Repo gotchas found during the 2026-07-29 pass

- `fastlane/Fastfile` review notes and `fastlane/metadata/review_information/notes.txt` were
  **baseball copy** ("read-only baseball statistics viewer", Hitting/Pitching/Fielding). Fixed.
- `fastlane/Fastfile` `fill_deprecated_locales` pointed at `com.jackwallner.baseball`. Fixed.
- `scripts/.asc-state.json` held baseball's appId and versions. Now football's.
- The 49 non-en-US metadata folders are stale baseball text. Parked in
  `fastlane/metadata-locales-parked/`. See [`localization-aso.md`](localization-aso.md).

Backups and restore: [`localization-aso.md`](localization-aso.md).
