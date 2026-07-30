# Gridiron StatScout — Project Guide

Gridiron StatScout: NFL advanced-stats percentiles / player-comparison app (iOS).
XcodeGen project/scheme: `StatScout` (names kept to minimize churn), simulator
device `agent-football` (UDID `93E5EECA-F542-4715-BA93-0EE303BE70A8`). Bundle id
`com.jackwallner.football`, product name "Gridiron StatScout".

**Naming, as of 2026-07-29:** App Store name is **"Football Next: StatScout"** (ASC app
`6792930447`, draft 1.0, never released) — chosen for ASO. In-app it is still
`PRODUCT_NAME: "Gridiron StatScout"`, home-screen `StatScout`, paid tier `StatScout+`.
ASO plan: `aso-plan.md` · `docs/astro-aso-setup.md` · `docs/localization-aso.md`.

**This repo is NOT the fastlane template canonical source** — that lives in the
baseball StatScout repo. Metadata/screenshots here are app-specific.

**App Store reviews:** enjoyment funnel in `StatScout/Services/ReviewPromptTracker.swift`
(passive triggers: 3rd+ player profile open, Pro player comparison). feedback
`jackwallner+bb@gmail.com`. (New ASC record still to be created.)

## Backend / data pipeline (NFL)

StatScout is backed by a Supabase NFL dataset fed by a nightly pipeline (already live).

- **Data source**: `nflreadpy` (nflverse) — weekly player stats + Next Gen Stats, cloud-IP friendly, no API key. See `handoff/NFL_CONTRACT.md` for the metric/category/schema contract.
- **Tables**: `player_snapshots` (id, season, PK (id, season); metrics/standard_stats/games jsonb; player_type ∈ qb/rb/wr/te/def). `player_game_logs` (PK (player_id, season, game_date, player_type); columns `plays`, `touches`, metrics jsonb).
- **Categories** (jsonb `metrics[].category`, exact strings): `Passing`, `Rushing`, `Receiving`, `Defense`.
- **Supabase**: dedicated "Football" project ref `qwkmpwnhrejsuplcwxrb` (separate Supabase account from the other apps; Management API / CLI token does NOT reach it — apply schema via `psql` using `SUPABASE_DB_PASSWORD`). Creds in `~/.football_credentials` (SUPABASE_URL, SUPABASE_ANON_KEY, SUPABASE_SERVICE_ROLE_KEY, plus `sb_publishable_`/`sb_secret_` keys and `SUPABASE_DB_PASSWORD`). Migrations in `supabase/migrations/`.
- **Data refresh workflows**: nightly `.github/workflows/nightly-statcast.yml` (name kept); manual `gh workflow run nightly-statcast.yml`. `STATCAST_SEASON` env var name kept for workflow compatibility; season = year if month ≥ 9 else year − 1.
- **Historical bundle**: `StatScout/Data/players-historical.plist` (seasons 2020–2024) regenerated via `source ~/.football_credentials && python3 scripts/export_historical.py` (fetches `season=lt.2025`, then runs the swift plist converter).
- **TestFlight upload** sources the creds first: `source ~/.football_credentials && bash scripts/testflight.sh`.

---
Shared iOS conventions (build, simulator, release scripts, ASC key, review funnel, signing, gotchas):
always-loaded global CLAUDE.md + the `ios-dev` skill.

## Subagent delegation
Follow the global CLAUDE.md subagent rules: ask Jack for the model before spawning, spawn at most one at a time unless Jack explicitly approves more, and never allow a subagent to spawn another subagent.
