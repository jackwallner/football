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

The current-season resource is now absent from the candidate, and `loadCurrentPlayers()` returns an empty array when there is no saved server snapshot. On a fresh install without network access, the app can only fall back to historical data. Build 48 at least supplied a partial opening-week current snapshot, although that fallback became wrong later in the season.

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

## Required before release

1. Fix the old-cache upgrade path and add the regression test.
2. Decide and verify the fresh offline first-launch behavior.
3. Restore or update ASC promotional text.
4. Upload a replacement build and repeat the upgrade, offline, Games, game-detail, Compare, and paywall checks.
5. Reconfirm the replacement build is attached to the `1.2.1` version before review.

Until the old-cache migration is fixed and verified, build 49 is not release ready.
