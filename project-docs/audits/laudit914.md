# Football Next: StatScout 1.2 release-readiness audit

Date: 2026-09-14 PT
Scope: `201788b`, version 1.2, build 48
Decision: **Hold. Do not submit 1.2 as-is.**

No app source, project, backend, or data files were changed during this audit. The
only task-owned file is this audit.

## Decision

The build is stable in the main live-data journey, and the new Games experience is
substantially better than the previous audit. However, four high-confidence issues
would create wrong, misleading, or dead-end experiences for real users:

1. An expired cache can fall back to a four-team Week 1 bundle and save it as fresh
   current data.
2. StatScout+ copy promises game metrics that the unlocked game page does not show.
3. Game pages can say an advanced breakdown is still arriving when the request has
   already failed, with no visible retry state.
4. Metric drill-downs are dead when a profile is opened from the Compare tab.

The UI suite also does not currently provide automated coverage for the new Games,
game detail, or schedule paths. The live manual path passed, but this reduces release
confidence until the four issues are addressed and the new paths are rechecked.

## Verification evidence

| Check | Result |
| --- | --- |
| iOS unit tests on the leased simulator | 173 passed, 0 failed |
| Backend tests | 126 passed, 0 failed |
| Export script tests | 4 passed, 0 failed |
| Configured Release build | Succeeded, `** BUILD SUCCEEDED **` |
| Full iOS scheme test run | Timed out after 300 seconds while the UI runner was still active. No valid pass/fail result. |
| Live Week 1 schedule | 16 games returned, all with final scores, including OT labeling |
| Live player-game logs | 884 rows across 15 games with stats |
| Live game details | 15 game-detail rows for the games with published stats |
| Live freshness state | Core coverage complete at 15 of 15; NGS ready; optional PFR pending; server state degraded |

Commands and run details:

- `XcodeBuildMCP test_sim` with `-only-testing:StatScoutTests`: 173 passed.
- `PYTHONPATH=backend uv run --no-project --with-requirements backend/requirements.txt -- python -m pytest backend/tests`: 126 passed.
- `python3 -m unittest discover -s scripts/tests -v`: 4 passed.
- A Release build was compiled with the real app configuration and installed on the
  leased iPhone 17 Pro simulator. No production purchase key was used by the
  simulator.

## Manual runtime checks that passed

The configured Release app reached the live Supabase data and was driven through:

- Stats: current 2026 leaderboard and freshness caption loaded.
- Games: Week 1 selector, all 16 final games, scores, OT state, and stats-pending
  behavior loaded.
- Game detail: NE at SEA opened with the final score, win-probability chart, team
  efficiency, and box score data.
- Team navigation: game team link opened Seattle, and Schedule showed the Week 1
  result, future games, and the bye week.
- Normal Stats-originated player profile: tapping EPA/Play opened the EPA/Play
  leaderboard.

No crash was observed in these paths.

## Release-blocking findings

### P1-1. Expired current cache can serve an incomplete current season

Files: `StatScout/Services/PlayerCache.swift:81,93-103,195-211`,
`StatScout/ViewModels/DashboardViewModel.swift:1059-1067`

`players-current.plist` and `players-current.json` each contain 110 players from
2026 REG across only four teams: LA, NE, SEA, and SF. The current validator now
accepts any current snapshot with at least two teams, 20 players, all position
groups, and three required metrics. Therefore this shipped four-team snapshot passes
`PlayerSnapshotValidator.isCompleteCurrent`.

`DiskPlayerCache` expires the current cache after 48 hours. When that cache is absent
or expired, `TwoTierPlayerCache.loadCurrentPlayers` loads the bundled snapshot and
immediately saves it back to the expiring disk cache. `DashboardViewModel` ingests
that result before the network refresh completes.

User impact:

- A returning user after 48 hours can see only four teams and 110 players presented
  as the live 2026 leaderboard.
- A slow or failed network request leaves that incomplete snapshot on screen.
- Saving the bundle resets its cache age, so an offline user can continue seeing it
  as fresh for another 48 hours.
- Online users can see a visible flash of the stale, partial leaderboard before the
  live request replaces it.

This is a data-trust issue, not just a loading placeholder. The fallback needs to be
distinguished from a current server snapshot, or guarded by persisted freshness and
coverage metadata. Add a regression test for an expired disk cache with the shipped
bundle present.

### P1-2. StatScout+ game paywall promises metrics that are not in the unlock

Files: `StatScout/Views/PaywallView.swift:99-100,128-134`,
`StatScout/Views/TrialPitchSheet.swift:35-45`,
`StatScout/Views/GameDetailView.swift:553-595`,
`StatScout/Models/GameDetail.swift:31-64`,
`backend/ingest_game_details.py:70-72`

The advanced box-score paywall says it includes EPA, CPOE, RYOE, separation, and
YAC over expected for every player and every game. The trial sheet and the general
feature list repeat the RYOE promise.

The actual Pro game tables show:

- Passing: dropbacks, EPA, EPA per dropback, success rate, CPOE.
- Rushing: carries, EPA, EPA per carry, success rate.
- Receiving: targets, EPA, EPA per target, success rate, average depth of target.

The game-detail model and backend player-detail payload do not contain RYOE,
separation, or YAC over expected. A user can pay from this entry point and never
receive the advertised metrics. This is a monetization and trust blocker. Either
make the copy match the existing fields or ship the promised fields before release.

### P1-3. Advanced game detail has no distinct loading or error state

File: `StatScout/Views/GameDetailView.swift:54-75,177-218`

The game detail and player-log requests start concurrently. `loadDetail` uses
`try?`, keeps no detail-loading state, and records no detail error. If logs arrive
first, the body enters the `detail == nil && !logs.isEmpty` branch and displays:
"Advanced breakdown on the way."

That branch is used both while the detail request is still running and after it has
failed. A transient timeout or offline response therefore claims that the breakdown
will arrive within a few hours, even if the backend already has the row. There is no
retry control or failure explanation. The box score can be visible underneath, which
also makes the page shift when the advanced cards later arrive.

Track advanced-detail loading and failure separately from the box score. Show a
loading state while it is in flight, and a retryable error when the request fails.

### P1-4. Compare-originated player profiles have dead metric drill-downs

Files: `StatScout/Views/RootTabView.swift:262-276,509-543`,
`StatScout/Views/PlayerProfileView.swift:925-962,1311-1315`

The Compare tab intentionally installs only `PlayerProfileDestination`, because
Compare owns its own comparison destinations. It does not install the
`MetricRoute` or `StandardStatRoute` destinations that are included in
`StandardDestinations` for Stats, Games, Trends, and Teams.

`PlayerProfileView` still renders tappable `MetricRoute` and `StandardStatRoute`
links. This makes those rows appear interactive in a profile reached from Compare,
but the Compare navigation stack has no destination for them.

Runtime reproduction in the configured Release app:

1. Open Compare.
2. Follow Aaron Rodgers and open his player profile.
3. Tap the visible EPA/Play row.
4. The profile remains on screen and no leaderboard opens.

Control check: opening Tyler Shough from Stats and tapping EPA/Play correctly opened
the EPA/Play leaderboard. Register the missing route types on the Compare stack, or
split the shared destination modifier so comparison routes are not duplicated.

## Release-confidence gap

The CI workflow deliberately runs only `StatScoutTests` (`.github/workflows/ci.yml:39-45,68-72`).
The full `StatScout` scheme still includes the UI target (`project.yml:153-157`), but
the full simulator run timed out after five minutes in this environment. The UI
suite has no coverage for the new Games tab, GameDetailView, or TeamScheduleView.

This timeout is documented as a known XCUITest harness problem, not evidence of an
app crash. It does mean a green CI result does not validate the new navigation and
content paths. After fixes, add or run focused UI coverage for:

- Week selector and all game states, including a final game with stats pending.
- Game detail loading, success, unavailable-detail, and box-score states.
- Game-to-team-to-schedule navigation and bye display.
- Compare-to-profile metric and standard-stat drill-downs.
- A small-width iPhone and an iPad layout.

## Secondary observations

These did not block the decision by themselves, but should remain on the follow-up
list:

- `StatcastAPI.fetchGames` decodes leniently and returns an empty array if every row
  is malformed (`StatcastAPI.swift:309-317`). GamesView can then read that as no games
  rather than a data-format failure. Current live rows decode correctly.
- Game box-score rows fall back to `Player <id>` when the current player snapshot
  does not contain the logged player (`GameDetailView.swift:809-810`). This is most
  visible offline or before the current player load finishes.
- The live server status was degraded only because optional PFR enrichment was
  pending. Core coverage was complete, and the main freshness caption correctly
  stayed calm. Recent-form empty states still inspect the raw partial status, so a
  genuinely empty recent-form surface can say data is still arriving even when core
  game coverage is complete.
- The Games copy says player stats usually post within two hours. Week 1 included
  several later publishes, so "within a few hours" would set a better expectation.

## Required before release

1. Fix the current-season bundle/cache fallback and add the expired-cache regression
   test.
2. Correct the advanced box-score paywall and trial copy, or add the missing metrics.
3. Add explicit advanced-detail loading and retryable failure states.
4. Register metric and standard-stat destinations for Compare-originated profiles.
5. Re-run the focused live Release journey and verify the new UI paths on small and
   large devices.

Until then, 1.2 build 48 is not release ready.
