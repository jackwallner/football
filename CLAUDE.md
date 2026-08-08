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
- **Recent Form covers the newest two seasons** (as of 2026-08-07): the live season and the one before it. `DashboardViewModel.recentFormSeasons` is the single source of truth; `supportsRecentForm` hides the Recent/Both controls on anything older (missing data, not a Pro upsell), and Trends offers exactly those two, with the older one behind the paywall like every other past season. Everything before that was purged once, taking the database from 441 MB to 110 MB; a season of `player_game_logs` + `player_recent_form` is ~26 MB, so 2025+2026 stays around 136 MB against a 500 MB budget.
- **`backend/prune_history.py` is a manual tool, not a nightly step** (as of 2026-08-07). Nothing in the pipeline writes an old season (both the game-log ingest and the rollup run against the resolved current season), so through the 2026 season the prune would have nothing to delete — a nightly DELETE that is always a no-op is pure risk, and it would have fired on the Sept 9 opener and dropped 2025 while the app still called 2025 live. `--keep` defaults to 2 to match `recentFormSeasons`; pruning to 1 empties Trends for last season. Run it in a later offseason if size ever justifies it. Re-ingest an old season with `ingest_game_logs.py --season N --full` + `rollup_recent_form.py --season N`. `psql` host is `db.qwkmpwnhrejsuplcwxrb.supabase.co` (user `postgres`); DELETE alone doesn't shrink the DB, `vacuum full` does.
- **Historical bundle**: `StatScout/Data/players-historical.plist` (34,331 rows: seasons 2000–2025 plus the season-0 career rollup, verified 2026-08-07) regenerated via `source ~/.football_credentials && python3 scripts/export_historical.py`. Past seasons are the *only* thing the bundle provides — nothing in the app fetches history over the network (`fetchHistoricalPlayers` exists but is unreachable), so a season missing here is missing from the app. Ahead of a rollover, fold the outgoing season in early with `STATCAST_SEASON=<next> python3 scripts/export_historical.py --historical-only`; `TwoTierPlayerCache.loadHistoricalPlayers` filters out any season that is still live, so such a bundle is safe to ship months before the calendar catches up.
- **TestFlight upload** sources the creds first: `source ~/.football_credentials && bash scripts/testflight.sh`.

---
Shared iOS conventions (build, simulator, release scripts, ASC key, review funnel, signing, gotchas):
always-loaded global CLAUDE.md + the `ios-dev` skill.

## Subagent delegation
Follow the global CLAUDE.md subagent rules: ask Jack for the model before spawning, spawn at most one at a time unless Jack explicitly approves more, and never allow a subagent to spawn another subagent.
