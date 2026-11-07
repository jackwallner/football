# State machine fixes, 2026-11-06

Fixes for the claims confirmed in `state-machines-2026-11-06-review.md` (audit `state-machines-2026-11-06.md`, repro `state-machines-2026-11-06-repro.swift`), in the review's ranked order. No verify doc existed, so this is the first fix pass.

**Summary: 4 in scope (1 CONFIRMED, 3 CONFIRMED-MINOR). 4 fixed, 0 held, 0 skipped. 1 sub-claim (SM-3 Compare) was NOT A BUG and left alone; the 7 product calls were left alone. 0 UNVERIFIED.**

| id | verdict | outcome | commit | regression test | what changed |
|---|---|---|---|---|---|
| SM-1 | CONFIRMED | fixed | `0150ac2` | `RecentFormCacheTests` (`testTeamRecentRosterSurvivesTrendsOnAnotherSeason` on the shipping 5-week roster vs 3-week Trends defaults, `testTeamRecentRosterSurvivesTrendsOnPlayoffs`, plus a same-context control and an invalidation check) | The recent-form cache held one season/phase context, and a load for another context cleared every window. It is now keyed by (season, phase, window), so Trends on last season or Playoffs no longer empties the Team page's Recent roster ("No players found"), and vice versa. A new data revision still clears every slot. Loading state is per slot (`isRecentFormLoading(season:phase:)`), and TeamView reads `hasRecentForm(window:)` instead of the raw dictionary. |
| SM-2 | CONFIRMED-MINOR | fixed | `254e8f4` | `FollowedPlayersTests.testFreeUserCanSeeFollowedPlayerWithoutLiveRow` (plus a Pro control and an unknown-id case) | A free user's followed player with no live-season row had no row in the Follow sheet, so he could never be unfollowed. The sheet now asks the view model to look up the latest past-season row for unresolved followed ids (same source as the Pro history load, once per id per process), kept apart from `playerHistories` so nothing past the free tier unlocks. The resolution moved from a private view property to `DashboardViewModel.followedPlayers(ids:)` so it is testable. No stored data format changed. |
| SM-4 | CONFIRMED-MINOR | fixed | `9d8287f` | `SeasonRolloverTests` (`testLiveSeasonFollowsTheClockWithinOneProcess`, `testSuspendedProcessMovesToTheNewSeasonOnForeground`, `testProUserOnAnOlderSeasonKeepsIt`) | `StatScoutSeason.current` and `.free` were `static let`, frozen per process. They are now read from the clock (memoized for 60 s, swappable in tests via `liveSeasonClock.setNow`). On foreground, a process whose live season changed moves any screen still on the old live season forward and reloads, so the free season and the live fetch both follow the calendar, as a fresh launch does. A Pro user on an older season keeps it. |
| SM-3 (Trends) | CONFIRMED-MINOR | fixed | `795f131` | `TrendsSeasonClampTests.testLapsedUserIsMovedOffTheLockedTrendsSeason` | Trends keeps its own season `@State`, and nothing re-clamped it when Pro lapsed mid-session. It now moves back to the live season on any `isPro` change that locks its season (`DashboardViewModel.trendsSeason(clamping:)`). |
| SM-3 (Compare) | NOT A BUG | not in scope | | | Compare blurs, disables and hides the locked cards. Unchanged. |

Regression tests and "fails without the fix": the SM-1 scenarios are the audit repro and the reviewer's 5-week variant, both recorded failing on the old code (review runs `...-122714-22369` and `...-130421-300`); the new file asserts the same reads through the new slot API. SM-2's test is the reviewer's `testSM2FreeUserCannotSeeFollowedPlayerWithoutLiveRow` (recorded failing, `[1]` vs `[1, 2]`) routed through the new lookup. SM-4 and SM-3 had no seam on the old code (a `static let` and view `@State`), so their tests use the seams the fixes add; on the old code they do not compile rather than fail. The audit repro suite was never added to the test target here; it stays in `project-docs/audits/` only.

## Held for Jack

None.

## Skipped

None.

## Test results

| run | tree | GitHub run | result |
|---|---|---|---|
| baseline | `3864d3f` (before any fix) | `base-imaca79f-1106-165712-13134` (actions run 42321308513) | `tests: Passed, 262 passed, 0 failed, 0 skipped`. No test failed before the fixes. |
| final | `795f131` (all four fixes) | `football@20261106-165533-7748-claude-imaca79f-1106-184408-27525` (actions run 42330693446) | `tests: Passed, 273 passed, 0 failed, 0 skipped`. The 262 baseline tests plus the 11 new regression tests; none newly fail. |
