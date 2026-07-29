# Backend ingestion (NFL)

The backend is serverless / free-tier friendly. A scheduled GitHub Actions
workflow runs `backend/ingest.py`, pulls NFL data from the nflverse mirror via
[`nflreadpy`](https://github.com/nflverse/nflreadpy), computes within-category
percentiles among qualified players, and upserts mobile-ready player snapshots
into Supabase Postgres. No API key is required for the data source.

## Local setup

```bash
python -m venv backend/.venv
source backend/.venv/bin/activate
pip install -r backend/requirements.txt
cp backend/.env.example backend/.env   # fill in SUPABASE_URL + SUPABASE_SERVICE_ROLE_KEY
```

Run a season snapshot ingest:

```bash
python backend/ingest.py --season 2025 --season-type all
```

Backfill and validate every supported snapshot season (2000 through current):

```bash
python scripts/backfill_historical.py
```

Use `--validate-only` to audit existing Supabase rows without re-ingesting.

Run the per-game logs ingest (Recent Form data):

```bash
python backend/ingest_game_logs.py --season 2025          # incremental
python backend/ingest_game_logs.py --season 2025 --full   # full re-ingest
```

## Season rule

NFL season label = starting year. `season = year if month >= 9 else year - 1`
(UTC). `STATCAST_SEASON` env var (kept for workflow compatibility) overrides;
`--season N` overrides both.

## Data contract

The iOS app reads `player_snapshots` via Supabase REST. Each row has
PK `(id, season, season_type)`:

- `id`: bigint from the nflverse GSIS id (`"00-0034796"` -> `34796`)
- `season_type`: `REG` or `POST`; each phase is aggregated and ranked separately
- `name`, `team`, `position`, `player_type` (`qb`/`rb`/`wr`/`te`/`def`/`k`)
- `handedness` (always `""` for NFL), `image_url` (nflverse headshot)
- `metrics`: JSON array of `{id, label, value, percentile, category}` where
  `category` is `Passing` / `Rushing` / `Receiving` / `Defense`
- `standard_stats`: JSON array of `{id, label, value}` counting totals
- `games`: JSON array (currently empty `[]`)

Percentiles are computed within `(season, season_type, category)` among **qualified**
players (Passing >= 150 attempts, Rushing >= 80 carries, Receiving >= 40
targets, Defense >= 8 games). Inverted metrics (INT%, Sack%, Fumble%) rank
lower raw values higher. Postseason uses smaller phase-appropriate qualification
floors. For 2003 through 2008, nflverse targets are unavailable, so receiving
qualification falls back to receptions and target-derived metrics are omitted.

`player_game_logs` (PK `(player_id, season, season_type, game_date, player_type)`) holds one
row per player per game with `plays`, `touches`, and a flat `metrics` jsonb of
per-game raw stats. `player_recent_form` stores league-anchored 3/5/8-week
aggregates for Trends.

## Data sources (nflreadpy)

- `load_player_stats([season])` — weekly box-score rows, split into REG and POST.
- `load_nextgen_stats(stat_type=...)` — season-level Next Gen Stats (week 0
  rows): CPOE, time-to-throw, aggressiveness, RYOE, separation, YAC+.
- `load_schedules([season])` — `game_id` -> `gameday` for per-game dates.
- `load_players()` — headshot URLs.
