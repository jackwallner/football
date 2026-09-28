# 1.2 release-readiness audit (caudit914)

Date: 2026-09-14 (evening PT, during Week 1 MNF publish)
Scope: `39ce5b7` (1.1.3 build 42) to `201788b` (1.2 build 48). 70 files, +9,027 / -296.
Method: read every changed app file, ran all test suites, queried the live Supabase data
the app reads, and drove a Debug build against production data on a leased simulator.
No code, data or config was changed.

## Verdict

**Not ready to submit as-is.** The build is stable (every test green, no crash seen, the
new Games tab and game pages work against live data), but three issues would ship a
visibly wrong or misleading experience to real users:

1. **P0** A returning user whose cache is older than 48 hours, or anyone offline, is shown
   the bundled Week 1 snapshot (110 players, 4 teams, 2 games) as the live season, and the
   app then re-saves it as fresh.
2. **P0** The Advanced Box Scores paywall promises RYOE, separation and YAC over expected.
   The screen it unlocks shows none of them.
3. **P1** Game pages flash "Advanced breakdown on the way" before the breakdown loads, and
   show it permanently if that one request fails.

Each fix is small. After fixing 1 to 3, the rest are polish, fine to ship with.

## Evidence

| Check | Result |
|---|---|
| iOS unit tests (`StatScoutTests`, leased sim) | 173 passed, 0 failed |
| Backend tests (`backend/.venv`, `pytest backend/tests`) | 126 passed |
| `scripts/tests` | 4 passed |
| CI on the last 5 pushes | all green |
| Debug build with prod Supabase config | succeeds |
| `ScreenshotFixtureAPI`, `-StartTab`, `-MockProducts` | all `#if DEBUG`, compiled out of Release |
| Live `data_refresh_status` (2026) | Week 1, 15/15 games, NGS ready, PFR pending, state `degraded` (maps to `.partial`, caption stays calm) |
| Publisher runs since 2026-09-12 | 10 degraded (published), 1 failed (`APIError` 06:03 UTC 9/14, the gateway timeout fixed in `633d4af`), 13 unchanged |
| Stats lag, final whistle to publish | 1 PM games about 2h, 4 PM games about 4h, SNF about 2.3h |
| `games` table | 272 REG rows, Week 1 all 16 finals posted, DEN at KC score in, stats pending (correct "Stats arriving") |
| `game_details` | 15 of 15 games with stats have a breakdown |
| `fetchGameLogs(gameId:)` | uses `player_game_logs_game_id_idx`, 2.7 ms |

Runtime screens verified on iPhone 17 Pro Max against production: onboarding page 1,
Stats (League leaders, "Week 1 · 15 games · Updated 13h ago"), Games list (all Week 1
finals, Final/OT, "Stats arriving" on DEN at KC, footer copy), DEN at KC detail (stats
pending notice, records 0-1 / 1-0), BUF at HOU detail (win probability chart, team
efficiency with percentile bars, game leaders, team stats).

Not verified at runtime: Teams week-status grid, Trends early-season board, Following,
Pro player efficiency tables, iPad, small phones. The machine's CoreSimulator service
became unresponsive under load from parallel sessions (`simctl launch` hung for 60s+).
Also note: another session was running XcodeBuildMCP tests on `agent-sim-1` under the
same `football` lease during this audit. A second lease (`football-audit`) was used.

## Findings

### P0-1. Bundled Week 1 snapshot is served as the live season

- `StatScout/Data/players-current.plist` in 1.2 is 110 players, 2026 REG, **4 teams**
  (NE, SEA, SF, LA). In 1.1.3 it was 1,308 players of 2025, which never qualified as
  current.
- `PlayerSnapshotValidator.isCompleteCurrent` (`PlayerCache.swift:204`) was relaxed from
  30 teams to `teams >= 2 && count >= 20`, so that bundle now passes.
- `TwoTierPlayerCache.loadCurrentPlayers` (`PlayerCache.swift:93`): when the disk cache
  is older than 48h (`DiskPlayerCache` throws on age), it falls back to the bundle **and
  saves it to disk**, resetting the file's age.
- `DashboardViewModel.performLoad` (`DashboardViewModel.swift:1059`) ingests that cache
  immediately when `players` is empty.

Failure scenario: a fan opens the app in Week 6 after three days away. The leaderboard
renders Brock Purdy #1 with 205 pass yards from a 6-QB list, under a caption restored from
the cached status ("Week 5 · 78 games"). Online, the network load replaces it after a few
seconds (a visible flash of wrong data). Offline, it stays, and the re-saved bundle is
treated as fresh for another 48h. Observed directly: a fresh install before the network
load showed "Week 1 · 2 games · Updated Sep 12" with six QBs.

Fix direction: drop `players-current.plist` from the bundle for the season (or never
re-save a bundle into the current cache), and/or require the fallback to be at least as
new as the persisted `DataFreshness` coverage.

### P0-2. Paywall copy overpromises the Advanced Box Scores unlock

- `PaywallView.swift` `.advancedBoxScore` subtitle: "Every player in every game: EPA,
  CPOE, RYOE, separation and YAC over expected, beside the box score."
- Paywall feature list and `TrialPitchSheet`: "EPA, CPOE and RYOE for every player,
  every game."
- What the gated section in `GameDetailView.playerEfficiencyCards` actually shows:
  passers DB, EPA, EPA/DB, success rate, CPOE; rushers CAR, EPA, EPA/C, success rate;
  receivers TGT, EPA, EPA/T, success rate, aDOT. No RYOE, separation or YACOE.
  `game_details.players` has no such fields either.

Risk: a user pays for metrics that are not there (refund requests, 1-star reviews) and an
App Review 2.3.1 / 3.1.2 accuracy objection. Fix: name only EPA, success rate, CPOE and
depth of target.

### P1-1. "Advanced breakdown on the way" shows when it is not on the way

`GameDetailView.loadDetail` has no loading state and swallows errors with `try?`. The body
shows the "on the way" notice whenever `detail == nil` and `logs` is non-empty.

- Observed: BUF at HOU opened with the notice and box score; about 10s later the win
  probability and efficiency cards appeared above, pushing the box score down.
- If the `game_details` request fails (timeout, offline), the notice claims the data will
  come "within a few hours" although it already exists, until the user pulls to refresh.

Fix: track detail loading/error separately; show a spinner or nothing while loading and a
retry line on failure.

### P2-1. Box score rows can read "Player 12345"

`GameDetailView.nameText` names a line only from the loaded player snapshots. 40 of 884
2026 game-log lines (5 kickers, 35 special-teamers and depth players) have no snapshot, so
Receiving and Defense tables can show "Player 43210". Also, before the player load finishes
(Games tab opened on a cold start) every name falls back this way. `game_details.players`
already carries names (for example "J.Allen") but only for the Pro tables. Worth adding a
name to `player_game_logs` rows or the detail payload.

### P2-2. "Stats within about two hours" is optimistic for late games

Games footer and the "Stats arriving" notice say stats post within about two hours. Measured
Week 1: 1 PM slate about 2h, 4 PM slate about 4h (a partial 14/15 publish first), SNF about
2.3h, MNF still pending 20m after the final. "Usually within a few hours" is accurate.

### P2-3. "Newer stats available · Tap to load" can never appear in practice

`freshnessForDisplay` only marks a changed revision stale when the server status is
`.ready`. Every publish so far is `degraded` (PFR pending all week), which maps to
`.partial`. Harmless because the two-minute loop reloads on a new revision anyway, but the
stale state is effectively dead code this season.

### P2-4. Revision drift on a cold start leaves an empty app for up to ~6 minutes

`performLoad` discards the whole candidate if the status revision changes between the start
and end reads. With no cache (fresh install) nothing is ingested. The next automatic retry
goes through `refreshOnForeground`, whose `checkForUpdates` is throttled for 300s after the
load's forced check, so it returns `.throttled` and only reloads games. Rare (needs a publish
inside a load window) but possible on busy Sundays. Once P0-1 removes the bundle fallback,
this becomes the fresh-install path to watch.

### P2-5. Minor polish

- Games tab bar is now five 68pt buttons plus padding (360pt). Fits a 375pt phone with 7.5pt
  margins; not checked at runtime on a small phone.
- Teams grid says "Live" for a game past kickoff although there is no live feed (Games says
  "In progress").
- "Follow team" on a team page silently replaces the existing favorite team (one favorite).
- `GameDetailView` declares `paywallTrigger` and a `TrialPitchSheet` that nothing sets.
- `fetchGameIdsWithStats` relies on QB rows staying under PostgREST's 1,000-row cap (35 rows
  in Week 1, roughly 650 for a full season). Fine for 2026, fragile later.
- Venue string for HOU reads "Reliant Stadium" (upstream nflverse value; NRG Stadium since
  2014).
- Onboarding page 1 still lists four bullets without Games; page 2 has it. Fine.
- `StatsBoard` now persists and defaults to Standard; existing users who lived on Advanced
  land on Standard once. Intended per release notes.

## What looks solid

- Games tab: week selector centers the current week, slate ordering, bye line, finals with
  OT, favorite pin, polling backs off to 10 minutes when nothing is pending.
- Game page math is honest: team totals use net passing (sacks subtracted), never add
  receiving to passing, and label percentile bars clearly.
- Freshness caption degrades cleanly: PFR-pending `degraded` status reads as normal, only
  missing games or a failed/offline check get an icon.
- Refresh concurrency: single-flight `load`, `loadGames` and `checkForUpdates`; recent-form
  cache cleared only after a new revision is accepted; profile/team cards re-key on revision.
- Early-season Trends ranks levels instead of an empty "no movement" board, with a volume
  floor and a clear note on when movement starts.
- Standard leaderboard search keeps league rank; volume subline (att/car/tgt) makes Week 1
  small samples legible.
- Backend: atomic publish, unchanged-source short circuit, self-booked timer chain with cron
  backup, failure issue creation. Repo is public, so Actions minutes are free.
- Release notes, onboarding and Settings copy no longer say "nightly".

## Recommended order before submitting

1. Fix P0-1 (bundle fallback) and add a unit test: an expired disk cache plus the shipped
   bundle must not produce current-season players when persisted coverage is newer.
2. Fix P0-2 copy in `PaywallView.swift` (subtitle and feature list) and `TrialPitchSheet`.
3. Fix P1-1 loading/error state in `GameDetailView`.
4. Optionally soften the "two hours" copy (P2-2).
5. Re-verify on a simulator: Teams grid, Trends early board, Following, Pro game tables,
   an iPhone 17e / SE-width device, then ship a new TestFlight build.
