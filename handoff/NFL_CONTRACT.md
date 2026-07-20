# NFL Conversion Contract (shared between backend + iOS)

App: **Gridiron StatScout** (working name), NFL analytics/percentiles app cloned from the
Baseball Savvy StatScout codebase. Same architecture: nightly Python ingest → Supabase →
SwiftUI iOS app reading `player_snapshots` + `player_game_logs` via PostgREST.

## Supabase
- URL: `https://ucoveqbyfuxqvpysmixu.supabase.co` (shared project "Sports"; the `briefings`/
  `source_*` tables in it belong to another app, DO NOT TOUCH).
- Creds file: `~/.football_credentials` (SUPABASE_URL, SUPABASE_ANON_KEY, SUPABASE_SERVICE_ROLE_KEY).
- Tables (already created):
  - `player_snapshots` — same shape as baseball (id bigint, season int, PK (id, season);
    name, team, position, handedness, image_url, player_type, source, metrics jsonb,
    standard_stats jsonb, games jsonb).
  - `player_game_logs` — PK (player_id, season, game_date, player_type); columns
    `plays` (was plate_appearances) and `touches` (was batted_ball_events), metrics jsonb.

## Data source
- `nflreadpy` (nflverse). Verified working in `backend/.venv` (Python 3.14):
  - `load_player_stats([season])` — weekly rows, 145 cols (passing_*, rushing_*, receiving_*, def_*, fg_*, ...).
  - `load_nextgen_stats(stat_type='passing'|'rushing'|'receiving')` — NGS advanced metrics; week 0 rows = season aggregate.
  - `load_schedules([season])` — `game_id` → `gameday` date mapping.
  - `load_players()` — headshots, positions.
- No API key needed. Data is a static parquet mirror, cloud-IP friendly (works in GitHub Actions).

## Season
- NFL season label = starting year. Season 2025 ran Sep 2025 – Feb 2026 and is the most
  recent complete season. Rule: `season = year if month >= 9 else year - 1` (today, July 2026 → 2025).
- `STATCAST_SEASON` env var name is kept (workflow compatibility) and overrides.

## Player IDs
- nflverse `player_id` is GSIS, e.g. `"00-0034796"`. DB id is bigint:
  `id = int(gsis_id.split('-')[-1])` (e.g. 34796). Collision-free in practice.

## player_type (snapshot + game log rows)
Position-group based, lowercase: `"qb"`, `"rb"`, `"wr"`, `"te"`, `"def"`, `"k"`.
Derived from `position_group`. One snapshot row per player per season.

## Metric categories (jsonb `metrics[].category`, exact strings)
- `"Passing"` — QBs. Metrics (id → label): pass_yards→"Pass Yds", pass_tds→"Pass TD",
  cmp_pct→"Cmp%", ypa→"Y/A", int_rate→"INT%" (inverted), passer_rating→"Rating",
  passing_epa→"EPA/Play", cpoe→"CPOE", avg_time_to_throw→"Time to Throw",
  aggressiveness→"Aggressiveness", avg_intended_air_yards→"Intended Air Yds", sack_rate→"Sack%" (inverted).
- `"Rushing"` — RBs + rushing QBs. rush_yards→"Rush Yds", rush_tds→"Rush TD", ypc→"Y/C",
  rushing_epa→"Rush EPA", rush_first_downs→"Rush 1D", explosive_rush_rate→"Explosive%",
  fumble_rate→"Fumble%" (inverted), rush_yoe (NGS rush_yards_over_expected)→"RYOE" if available.
- `"Receiving"` — WR/TE/RB with targets. receptions→"Rec", rec_yards→"Rec Yds",
  rec_tds→"Rec TD", yac→"YAC", target_share→"Target Share", wopr→"WOPR", racr→"RACR",
  receiving_epa→"Rec EPA", catch_pct→"Catch%", avg_separation→"Separation" (NGS),
  avg_yac_above_expectation→"YAC+" (NGS).
- `"Defense"` — DEF players. tackles→"Tackles", sacks→"Sacks", def_ints→"INT",
  passes_defended→"PD", forced_fumbles→"FF", tfl→"TFL", qb_hits→"QB Hits".

Percentiles: computed within (season, category) among **qualified** players
(thresholds: Passing ≥ 150 attempts, Rushing ≥ 80 carries, Receiving ≥ 40 targets,
Defense ≥ 300 defensive snaps or ≥ 8 games). Inverted metrics: lower raw value = higher
percentile. Percentile int 1–100. `value` is the formatted raw stat string (e.g. "4,918", "68.3%").
A player appears in every category they qualify for (rushing QB gets Passing + Rushing metrics).

## metrics jsonb element shape (same as baseball)
`{"id": "...", "label": "...", "value": "...", "percentile": 87, "category": "Passing"}`

## standard_stats jsonb
`[{"id": "...", "label": "...", "value": "..."}]` — games, totals per relevant categories
(G, Cmp/Att, Yds, TD, INT for QB; Car, Yds, TD for RB; Rec/Tgt, Yds, TD; etc.)

## games jsonb (snapshot) — keep same shape as baseball
`[{"id","date","opponent","summary","percentile_delta","key_metric"}]` — may be empty [].

## player_game_logs
One row per player per game (per side: player_type as above). `game_date` from schedule
`gameday`. `plays` = pass attempts + carries + targets (offensive involvement),
`touches` = completions + carries + receptions. `metrics` jsonb: flat dict of per-game raw
stats (passing_yards, passing_tds, interceptions, rushing_yards, carries, receptions,
targets, receiving_yards, epa_total, ...). Powers "Recent Form" (last 7/15/30 days windows;
iOS side may reinterpret as last 1/3/5 games).

## Teams
32 NFL abbreviations (nflverse): ARI ATL BAL BUF CAR CHI CIN CLE DAL DEN DET GB HOU IND
JAX KC LA LAC LV MIA MIN NE NO NYG NYJ PHI PIT SEA SF TB TEN WAS.

## Free vs Pro season gating (iOS)
- `StatScoutSeason.current = 2025`, `free = 2025`; historical seasons (Pro) = 2020–2024.
- Backend backfills seasons 2020–2025.
