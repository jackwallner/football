# Football Next: StatScout improvement plan

Date: 2026-08-17
Scope: Review only. No app, backend, project, or release files were changed.

## Review evidence

- Built and launched the app on a headless iOS Simulator. The onboarding and dashboard flows opened successfully.
- The default onboarding layout showed ellipses in feature bullets on pages two and three, including the Stats and Trends descriptions.
- The selected unit suite passed: 125 tests passed, 0 failed, 0 skipped in 21.3 seconds.
- The test build emitted 96 warnings, mostly Swift 6 actor-isolation warnings in UI tests, plus a StoreKit deprecation warning.
- The backend schema and pipeline already distinguish regular season and postseason rows.

## Prioritized plan

### P1: Make Recent Form phase-correct

- [ ] Thread `SeasonPhase` through player and team game-log requests, models, view routes, and cache keys.
  - `PlayerProfileView` carries the profile phase, but its fetch closure accepts only player ID and season (`StatScout/Views/RootTabView.swift:447-463`).
  - `StatcastAPI.fetchGameLogs` filters only by player ID and season (`StatScout/Services/StatcastAPI.swift:81-89`).
  - `PlayerGameLog` has no `season_type` property (`StatScout/Models/PlayerGameLog.swift:5-28`).
  - Team game-log closures have the same omission (`StatScout/Views/RootTabView.swift:481-488`, `StatScout/Views/TeamFormCard.swift:499-509`).
  - The database contract uses regular and postseason keys (`handoff/NFL_CONTRACT.md:13-19`).
- Verify with a fixture containing both `REG` and `POST` rows. A playoff profile or team page must never include regular-season games in its recent window. Include the phase in the request filter, model, route, and `recentLogsKey`.

### P1: Make the first-run value proposition readable

- [ ] Remove onboarding truncation at the default device size. Shorten the copy or allow the card to reflow vertically instead of relying on a fixed composition.
  - The affected copy is in `StatScout/StatScoutApp.swift:432-444`.
  - The tight card layout is in `StatScout/StatScoutApp.swift:452-505`.
  - The simulator run visibly truncated “Stats: sort the league...” and “The Trends board...” bullets.
- Add snapshots for the default iPhone size, the smallest supported iPhone, landscape, and the largest Dynamic Type sizes. No primary benefit should end in an ellipsis.

### P1/P2: Reduce perceived launch and history-load cost

- [ ] Measure release cold launch, current-season readiness, and historical-season readiness separately.
- [ ] Index player history by season and phase instead of repeatedly flattening all histories in computed properties such as `seasonPlayers`, `players(forSeason:)`, `eligibleMetrics`, and leaderboard inputs (`StatScout/ViewModels/DashboardViewModel.swift:303-312`, `:499-552`).
- [ ] Coalesce or cancel overlapping foreground refresh and history-load tasks. Keep the visible progress state tied to the active task.
- Existing test comments report a 33k-row historical decode, roughly 45 seconds for direct debug launch and about three seconds in release. Keep this as a release benchmark, not a debug-test timeout workaround (`StatScoutUITests/SeasonPickerUITests.swift:16-26`).

### P2: Define one product and entitlement matrix

- [ ] Write down which screens and controls are free, trial-only, and StatScout+. Use that matrix to drive gates, settings copy, onboarding, and paywall copy.
- `SettingsView` currently describes the free tier only as historical seasons and year-over-year comparisons (`StatScout/Views/SettingsView.swift:96-100`). The paywall also promises Trends, recent form, head-to-head comparisons, and team features (`StatScout/Views/PaywallView.swift:121-126`). Audit every promise against the actual locked control.
- Clarify the terminology for recent windows. Player and team pages use last 3, 5, or 8 games, while Trends uses league-anchored weeks (`StatScout/Models/RecentForm.swift:231-250`, `StatScout/Views/HotColdView.swift:459-461`). Update onboarding and paywall text so “games” and “weeks” are not interchangeable.

### P2: Improve accessibility and adaptive layout coverage

- [ ] Test VoiceOver, Dynamic Type, iPhone SE-width layouts, landscape, and iPad before release.
- Review fixed metric and table columns, especially the 70-point metric label and 72-point value column in `StatScout/Views/Components.swift:104-170`, plus the several `lineLimit` and `minimumScaleFactor` uses in segmented controls and paywalls.
- Preserve full metric names, values, percentile labels, and selected-state announcements at larger text sizes. Avoid shrinking semantic text below a readable size just to preserve the compact table geometry.

### P2: Audit claims about offline behavior and data privacy

- [ ] Verify that “Saved offline - works on the road” in `StatScout/Views/PlayerProfileView.swift:384-387` matches what is available after a cold launch without network access, including freshness and current-season coverage.
- [ ] Reword the review prompt claim that “your scouting data stays on your phone” (`StatScout/Views/ReviewPromptSheet.swift:136-140`) if it means preferences or cache rather than all data used by the app. State clearly what is stored locally and what is fetched from Supabase.

### P2: Turn the passing test suite into a release signal

- [ ] Add unit fixtures for `REG` and `POST` game-log isolation, phase-aware cache keys, season switching, and offline cache behavior.
- [ ] Add UI coverage for onboarding readability, the free-to-Pro path, restore purchases, and the paywall with StoreKit test products loaded.
- [ ] Fix the 96 Swift 6 UI-test warnings, or isolate the known warnings with an explicit migration issue. A green test count with actor-isolation warnings is not a clean release signal.

### P3: Remove stale release documentation

- [ ] Update or archive the baseball-era documents before the next release review. `APP_STORE_SUBMISSION.md`, `docs/design.md`, `handoff/STATSCOUT_SAVANT_HANDOFF.md`, and parts of `README.md` still describe baseball, MLB, xwOBA, or baseball bundle identifiers. This creates a release and metadata risk for the football app.

## Suggested execution order

1. Fix and test phase-aware game logs.
2. Fix onboarding truncation and align feature terminology.
3. Establish release performance benchmarks and add history indexing only where measured.
4. Audit entitlements, offline claims, Dynamic Type, and VoiceOver.
5. Clean up release documentation and make the test suite warning-free.
