# Pre-submit review, 1.2.9 (build 77), 2026-11-07

Scope: app sources changed since the live App Store version, 1.2.8 (build 74,
commit `aba3560`), to `origin/main` at `ce1df6ae`. Docs, fastlane, qa, tests,
android, backend and screenshot tooling are excluded.

Files reviewed: `Info.plist`, `Services/StoreService.swift` (live-season clock),
`Services/PlayerCache.swift`, `Services/ScreenshotFixtureAPI.swift`,
`ViewModels/DashboardViewModel.swift` (recent-form slots, season rollover,
archived followed players), `Views/HotColdView.swift`, `Views/TeamView.swift`,
`Views/FollowPlayersSheet.swift`, `Views/SavantMenus.swift`,
`Views/SettingsView.swift`, `Views/Components.swift`,
`Views/YearComparisonView.swift`, `Resources/Localizable.xcstrings`.

No P0 or P1 issues were found, so this pass changes no code.

## Findings

| Severity | Location | What happens | Status |
| --- | --- | --- | --- |
| P2 | `StatScout/Views/CompareView.swift:165` | Compare's followed list shows only players with a row in the selected season, while the Follow Players sheet (new `followedPlayers(ids:)` fallback) also lists retired or inactive followed players. The two counts can differ. This is deliberate (Compare needs a comparable season row) and nothing breaks. | Not fixed (by design) |
| P2 | `StatScout/ViewModels/DashboardViewModel.swift:675` | Recent-form data is now cached per (season, phase, window) slot and is cleared only on a data-revision change. At most 2 seasons x 3 phases x 3 windows, so growth is bounded and small. | Not fixed (no impact) |

## Checked and clean

- Crashes and data loss: no new force unwraps, indexing or decoding of saved
  data. The screenshot fixture's 18-name and 18-team arrays match its
  `0..<18` range and the fixture is compiled only in DEBUG
  (`StatScoutApp.swift:32`). `LiveSeasonClock` (`StoreService.swift:59`)
  locks every read and write; `NSLock.withLock` is available on the iOS 17
  target. The recent-form task's `defer` skips cleanup only when the task was
  cancelled, and both cancel sites (`invalidateRecentFormCache`,
  `reloadRecentForm`) already remove the slot, so no slot is left loading.
- Purchase and paywall: `StoreService` purchase, restore, offering and
  paywall code are unchanged since 1.2.8. The only entitlement change is
  `HotColdView.swift:201`, which moves Trends back to the live season when Pro
  lapses. It cannot lock out a paid user: `trendsSeason(clamping:)` returns the
  same season when `isSeasonLocked` is false, and the pending-unlock resume
  sets the season after a purchase regardless of the order in which `isPro`
  arrives.
- Free-tier leak: the archived followed-player rows
  (`DashboardViewModel.swift:1773`) are shown only as follow/unfollow rows in
  the Follow Players sheet, which open no profile and add nothing to
  `playerHistories`, so no past season unlocks.
- State: Team's Recent roster loads and reads the same slot (selected season
  and phase, roster window), and Trends loads and reads its own season and
  phase, so neither screen clears the other. The season rollover
  (`DashboardViewModel.swift:1387`) moves only screens still on the old live
  season, re-clamps and reloads once.
- App Review: the new Language row opens `UIApplication.openSettingsURLString`
  (Apple's own route), `UIPrefersShowingLanguageSettings` is a documented key,
  and the new strings have no prices, platform links or health claims.
  `python3 Tools/Localization/check.py`: 900 keys, 4 languages, 0 problems.

## Tests

`gh-sim test --scheme StatScout --only StatScoutTests` on `ce1df6ae` plus this report
(run `football@20261107-222733-0237-claude-mbpro3a06d-1107-223015-21542`, GitHub
Actions 42455014286, Xcode 27.1, iPhone 17 iOS 27.2): 273 passed, 0 failed.
