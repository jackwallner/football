# Football Next: StatScout 1.2.1 release-readiness audit

Date: 2026-09-15 PT  
Scope: App Store Connect candidate `1.2.1` build `49` compared with the live `1.2` build `48`.  
Source comparison: `31dd40a` / `bba821b` against live source commit `201788b`.  
Decision: **Hold build 49. Do not allow this candidate to proceed to release as-is.**

## Executive decision

Build 49 fixes the three user-facing blockers found in the 1.2 audit:

- Game detail now distinguishes advanced-data loading from advanced-data failure and offers Try Again.
- Compare-originated player profiles now open metric and standard-stat leaderboards.
- StatScout+ copy no longer promises RYOE, separation, or YAC over expected that the game page does not display.

The Games list, score display, game detail, and Compare drill-downs were rechecked against the live feed. The candidate is otherwise stable, but it has one release-blocking upgrade-path regression. The cache fix removes the bundled opening-week resource, yet it does not invalidate the same incomplete snapshot that build 48 may already have written to an upgrading user's disk cache. Build 49 can therefore preserve the old four-team leaderboard indefinitely, even though its release notes say returning users now see their latest saved stats.

The deeper UX pass found additional problems that are not introduced by build 49. The floating tab bar hides data on every core screen, inactive tabs remain exposed to the accessibility tree, and historical season selectors do not consistently carry their context into team pages, player profiles, or drill-down leaderboards. These are present in the live source as well as the candidate, so they are not evidence that build 49 regressed from build 48. They are still meaningful readiness debt and should not be mistaken for a clean user experience after the cache fix.

## App Store Connect state

The ASC API was queried on 2026-09-15:

| Check | Result |
| --- | --- |
| App | `Football Next: StatScout`, app `6792930447` |
| Current live version | `1.2`, `READY_FOR_SALE`, build `48` |
| Candidate version | `1.2.1`, `WAITING_FOR_REVIEW`, build `49` attached |
| Candidate binary processing | `VALID` |
| In-app purchases | Monthly, yearly, and lifetime all `APPROVED` |
| Candidate localizations | 50, all with descriptions and release notes |
| Candidate screenshots | 8 in en-US, same set as live, zero duplicate checksums |
| Candidate binary plist | `CFBundleShortVersionString 1.2.1`, `CFBundleVersion 49` |

The submission is structurally ready for review. The hold is caused by the runtime upgrade path, not by a missing build or unsubmitted IAP.

## Verification evidence

### Build and tests

- Release simulator build with the football Supabase configuration succeeded.
- Focused iOS unit suite: **175 passed, 0 failed, 0 skipped**.
- The first test attempt failed before test execution because the Xcode test harness could not resolve `Gridiron_StatScout` while the session was configured for Release. Retrying with the scheme's Debug test configuration passed all 175 tests.
- No app source, project, backend, or data files were changed during this audit.

### Runtime comparison

The candidate and the exact live source commit were each built in Release configuration and run against the same live feed on the leased iPhone 17 Pro simulator.

| Journey | Result |
| --- | --- |
| Current Stats data | Candidate loaded Week 1, 16 games, and current player leaders |
| Games tab | Candidate and live both showed the same 16 final games, scores, and Final/OT labeling |
| Game detail | Candidate loaded NE at SEA with score, win probability, team efficiency, and box score |
| Compare to profile | Candidate opened Aaron Rodgers from Compare and displayed his Week 1 profile |
| Metric drill-down | Candidate opened EPA/Play from that profile and reached the EPA/Play leaderboard |
| Advanced failure state | Code path verified; a forced network failure was not simulated in the final runtime pass |

## Release-blocking finding

### P0-1. The 1.2.1 cache change does not invalidate an old 1.2 fallback snapshot

Files: `StatScout/Services/PlayerCache.swift:86,104-109,202-217`, `project.yml:23-25`, `StatScoutTests/OpeningWeekCacheTests.swift:21-38`

Build 48 shipped `players-current.json` and `players-current.plist`. The JSON asset in the live source contains **110 players from only four teams**, LA, NE, SEA, and SF. Build 48's `TwoTierPlayerCache` could load that asset when the disk cache was missing or older than 48 hours, then save it to the shared disk path `players-current.json`.

Build 49 makes three changes:

1. It removes the current-season resource from `project.yml`.
2. It keeps the same `players-current.json` disk path, but makes it non-expiring with `maxAge: nil`.
3. It loads that disk file with `loadPlayersIgnoringAge()` and validates it with the opening-week rule of at least two teams, 20 players, all position groups, and the three EPA metrics.

The old four-team, 110-player snapshot satisfies that validator. No migration or provenance marker distinguishes a real server snapshot from the old bundled fallback. A user upgrading from 1.2 can therefore follow this path:

1. Build 48 writes the bundled four-team snapshot to its disk cache after a failed or incomplete refresh.
2. The user installs 1.2.1 over the existing app data.
3. Build 49 loads the same file before its network refresh.
4. If the refresh is slow or unavailable, the user sees the four-team opening-week leaderboard. Because the candidate cache no longer expires, it can remain there indefinitely.

Even when the network is available, the stale leaderboard can flash before the new response replaces it. This is a direct upgrade-path regression and leaves the release note claim, “Returning after a few days now shows your latest saved stats,” untrue for affected users.

The new tests cover an old real server snapshot and a missing snapshot, but not the exact 1.2 fallback artifact. `testExpiredSavedSnapshotIsKeptAndNotRestamped` actually confirms that an accepted old file is retained, so it does not protect this migration case.

Required fix:

- Add an upgrade migration that deletes or quarantines the pre-1.2.1 current cache, or add cache metadata that records server provenance and rejects the old bundled fallback.
- Add a regression test using the 110-row, four-team artifact, with no network response, and assert that it cannot populate the current leaderboard after upgrading.
- Verify the normal upgrade path with stale cache, offline mode, and a slow successful refresh. The user must never see a four-team snapshot presented as the current season.

Do not fix this only by raising the team-count threshold to 30. A genuine early-week server snapshot can contain fewer than 30 teams, so the cache needs provenance or coverage coherence rather than a calendar-blind threshold.

## Secondary regression and readiness findings

### P1-1. A fresh offline install has no current-season snapshot

The current-season resource is now absent from the candidate, and `loadCurrentPlayers()` returns an empty array when there is no saved server snapshot. On a fresh install without network access, the current board has no data. If the provider returns an empty successful response, the load can fall back to bundled historical data; if the provider fails with a connection error, the current implementation leaves the player list empty and shows the connection error. Build 48 at least supplied a partial opening-week current snapshot, although that fallback became wrong later in the season.

This is the safer data-integrity choice, but it is still a worse first-run experience for an offline user. Decide the intended behavior explicitly. If offline first launch matters, ship a tagged snapshot with an honest coverage boundary and never promote it into the server cache. Otherwise, make the current-season empty state clearly say that the first refresh requires a connection and do not imply that current stats are available offline.

### P2-1. The candidate removes all App Store promotional text

ASC comparison:

- Live 1.2: `promotionalText` populated for all 50 localized version records.
- Candidate 1.2.1: `promotionalText` is empty for all 50 records.

The candidate retains its name, subtitle, description, support URL, marketing URL, privacy URL, release notes, and screenshots. This is not an in-app blocker, but it removes the live product-page conversion copy during the candidate's review period. Restore or replace the promotional text with 1.2.1-specific copy before release.

## Confirmed fixes from the prior audit

### Game detail loading and failure state

`GameDetailView` now tracks `isDetailLoading` and `detailFailed` independently from the box score. When player logs arrive first, the candidate shows “Loading advanced breakdown.” If the advanced request fails, it shows a clear failure message and a Try Again action instead of claiming that the data is still on the way. See `StatScout/Views/GameDetailView.swift:62-75,200-216`.

The live-data success path was verified on NE at SEA. The forced failure branch was source-reviewed but not network-fault injected in the final runtime pass.

### Compare navigation

Compare now installs `StandardDestinations`, including metric and standard-stat routes, instead of only the player profile route. The candidate runtime check opened Aaron Rodgers from Compare, tapped EPA/Play, and reached the correct leaderboard. See `StatScout/Views/RootTabView.swift:262-276`.

### StatScout+ game copy

The candidate now names metrics present in the actual game-detail payload and tables: EPA, success rate, CPOE, and depth of target. The prior RYOE, separation, and YAC over expected promises are removed from the paywall and trial sheet. See `StatScout/Views/PaywallView.swift:99-134` and `StatScout/Views/TrialPitchSheet.swift`.

## Exhaustive UX pass

### P1-2. The floating tab bar occludes content on every core screen

This was visible in the candidate runtime pass on September 15:

- Stats rows at the bottom of the first viewport sit behind the translucent tab bar.
- Games rows, including the lower team names and scores, sit behind it.
- Game detail's Team efficiency rows are covered before the user scrolls.
- Settings' Data Updates row is covered after opening Settings.

`RootTabView` overlays `floatingTabBar` in a bottom-aligned `ZStack` and ignores the bottom safe area (`RootTabView.swift:119-132`). The child screens add `Color.clear.frame(height: 88)` at the end of their scroll content, but that only gives the user room to scroll past the bar. It does not inset the initial viewport or make the bar stop covering content. The same layout exists in the live source.

This is P1 because it hides real data and controls across the primary journeys on first render. Use a real bottom safe-area inset for the bar, or hide the bar once a pushed destination is visible. Remove the compensating spacers after the content inset is real, then recheck Stats, Games, Game detail, Settings, Compare, and Dynamic Type.

### P1-3. Inactive tabs remain in the accessibility hierarchy

The tab implementation keeps all five tabs mounted and sets inactive views to zero opacity, disables hit testing, and applies `.accessibilityHidden` (`RootTabView.swift:114-126`). On the candidate runtime, `snapshot_ui` while Stats was visible still returned targets from Games, Trends, Teams, and Compare. The captured tree had 102 targets and 916 elements, with duplicate labels including 16 `Forward` controls, 15 `Final` labels, 13 dates, four `Add` controls, and four `Unlock StatScout+` controls.

The visual screen is not showing all of those controls, so this is primarily a VoiceOver and accessibility-navigation failure. It exposes irrelevant duplicate content and can make the active screen difficult to understand. The source comparison shows the pattern is also in live, not a build 49-only change.

Fix by removing inactive tab content from the accessibility hierarchy in a way that survives iOS 26, or by conditionally rendering the active tab while preserving navigation state elsewhere. Verify the resulting AX tree, rotor order, and activation behavior with VoiceOver rather than relying only on `.accessibilityHidden`.

### P1-4. Changing the season on a team page can relabel stale roster data

`StandardDestinations` creates `TeamView` with a one-time `players` array from `viewModel.players(forTeam:)` and a one-time `season` value (`RootTabView.swift:513-527`). `TeamView` stores both as immutable values (`TeamView.swift:3-8`). Its season menu changes `viewModel.selectedSeason` (`TeamView.swift:695-711`), but the roster, filters, sort metrics, TeamRankingsCard, and TeamStandardCard continue to read the original `players` array. `leaguePlayers` does update from the view model, so the page can mix a new season's league context with the old season's team rows.

The result is a high-risk paid feature defect: a user selects another season and can see a new season in the shared navigation state while the team roster and cards still represent the season from when the page was opened. The same source path is present in live.

Make team players a derived lookup keyed by `team`, selected season, and selected phase. Add a test that opens a team, changes season, changes phase, and asserts the displayed players, team cards, empty state, and sort options all change together.

### P1-5. Historical player profiles change metrics without changing all dependent context

`PlayerProfileView` changes `displayedPlayer` when its season picker changes (`PlayerProfileView.swift:72-106`), but several dependent surfaces still use the initial `player` or initial `allPlayers`:

- The identity strip, navigation title, favorite action, recent-form card, and primary category use the initial player.
- Standard-stat percentiles, recent-form percentile curves, and the comparison pool use the initial season's `allPlayers` cohort (`PlayerProfileView.swift:125-136, 994-1000, 1115-1123`).
- A player who changed teams can therefore show one season's numbers under another season's team, headshot, or comparison context.

The drill-down routes have a second context problem. `MetricRoute` and `StandardStatRoute` carry a season but no phase (`RootTabView.swift:8-22`). Their destinations call `players(forSeason:)` without the profile's phase (`RootTabView.swift:530-547`). A metric opened from a playoff profile can therefore land on the regular-season leaderboard for that year, depending on the tab's current phase.

This is also present in live and is especially damaging because historical profiles and comparisons are part of the paid value proposition. Recompute every dependent cohort from the active season and phase, use `displayedPlayer` consistently, and carry both season and phase through every leaderboard route. Add tests for a player with a team change and for a playoff profile opening a metric and standard-stat drill-down.

### P1-6. Settings can become a navigation dead end

On the candidate simulator, tapping the gear pushed a Settings screen with a title but no visible Back button. The accessibility snapshot also had no Settings Back target. Switching to another bottom tab dismissed the screen, but there was no obvious way to return to the page that opened it.

The settings destination is presented from `HomeTabToolbar` with `navigationDestination(isPresented:)` (`RootTabView.swift:400-419`) and relies on the system navigation item. The custom navigation styling and persistent floating bar leave the pushed screen without a usable back affordance. This behavior is unchanged from live.

Add an explicit leading dismiss/back control and verify the gear-to-settings-to-back flow from every home tab, including VoiceOver. The Settings screen should also not inherit a root tab bar that visually suggests it is still at the root.

### P2-2. The Games schedule is not available after an offline relaunch

`DashboardViewModel.games` and `gameIdsWithStats` are memory-only (`DashboardViewModel.swift:389-397`). `loadGames()` goes directly to the provider and has no disk schedule cache (`DashboardViewModel.swift:401-439`). A user who previously viewed Games loses the schedule on relaunch without a connection, sees the load failure state, and cannot open a previously viewed game detail because the in-memory game is gone. This is code-confirmed, not fault-injected in the final runtime pass, and the path is unchanged from live.

Persist the schedule and stats-availability set by season, show the cached coverage and offline state, and retain a usable previously loaded game detail when the network is unavailable. This should be consistent with the product's existing promise that saved scouting data works offline.

### P2-3. Freshness metadata is not scoped to a season

`DataFreshnessCache` stores one global UserDefaults record (`DataFreshness.swift:334-355`). `DataCoverage` contains week, phase, and dates but no season, and `DashboardViewModel` restores the record without checking the current `freeSeason` (`DashboardViewModel.swift:322-330`). Around a season rollover, an offline launch can therefore display the prior season's cached freshness and coverage beside the new season shell until a successful status check completes. This is a conditional rollover issue, not a build 49-only regression.

Store the season with the freshness record and reject or relabel a record from another season. Add a rollover test with no network response.

### P3-1. Long team names are visibly truncated in the Games list

The narrow Games rows use a single line for each team name while reserving fixed width for status and score. On the iPhone runtime, names such as Tampa Bay Buccaneers were visibly clipped. The accessibility label retained the full name, so this is not a data-loss issue, but it makes scan-reading harder and makes the row look unfinished. Use an abbreviation consistently, allow a compact two-line team label, or give the name column more width on narrow phones.

## Finding classification

| Finding | Candidate regression from live | Release interpretation |
| --- | --- | --- |
| Old four-team current cache survives upgrade | Yes | P0 release hold |
| Fresh offline install has no current snapshot | Yes, deliberate tradeoff | P1 behavior decision |
| Promotional text removed from ASC | Yes | P2 metadata fix |
| Floating bar occludes content | No, present in live | P1 UX fix |
| Inactive tabs exposed to accessibility | No, present in live | P1 accessibility fix |
| Team season switch uses stale players | No, present in live | P1 paid-feature fix |
| Player historical context is inconsistent | No, present in live | P1 paid-feature fix |
| Settings has no visible back path | No, present in live | P1 navigation fix |
| Games schedule is not persisted offline | No, present in live | P2 continuity fix |
| Freshness metadata lacks season scope | No, present in live | P2 rollover fix |
| Long team names clip in Games | No, present in live | P3 polish |

## Required before release

1. Fix the old-cache upgrade path and add the regression test.
2. Decide and verify the fresh offline first-launch behavior.
3. Restore or update ASC promotional text.
4. Fix the P1 content occlusion, accessibility, historical-context, and Settings navigation defects, or explicitly accept them as live carryovers and track them for the next release rather than calling the UX clean.
5. Upload a replacement build and repeat the upgrade, offline, Games, game-detail, Compare, accessibility, Settings, and paywall checks.
6. Reconfirm the replacement build is attached to the `1.2.1` version before review.

Until the old-cache migration is fixed and verified, build 49 is not release ready.
