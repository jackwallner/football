# Review of the state machine audit, 2026-11-06

Independent review of `state-machines-2026-11-06.md` and its repro suite. Every claim was re-derived from the code at `67a4f8a` (app code identical to `a65b0d7`; the audit's `992e874` is not in this repo's history, and `git diff a65b0d7 HEAD -- StatScout StatScoutTests project.yml` is empty). `git stash push -u` found nothing to stash. No app code was changed.

**Summary: 4 bugs claimed (5 sub-claims, SM-3 covers two screens). 1 CONFIRMED, 3 CONFIRMED-MINOR, 1 sub-claim NOT A BUG, 0 UNVERIFIED. Outright false positives: 0 of 4 bugs, 1 of 5 sub-claims (20%). Symptom or impact misstated in 3 of 4 (SM-1 symptom, SM-2 and SM-4 impact).**

## Test runs

| run | suite | result |
|---|---|---|
| `football@20261106-122613-7079-claude-mbprof45a-1106-122714-22369` (Xcode 27.1, iPhone 17, iOS 27.2) | audit repro `StateMachineAuditRepro` | `Failed, 2 passed, 2 failed`. Same as recorded: both SM-1 tests fail on their final assertion, both controls pass. |
| `football@20261106-122613-7079-claude-mbprof45a-1106-130421-300` (same toolchain) | reviewer seams `StateMachineReviewChecks` (copied in, run, removed) | `Failed, 2 passed, 2 failed`. Both claim tests fail on their final assertion; every precondition, including `recentFormByWindow[5] == nil`, passed. Both controls pass. |

The reviewer suite: `testSM1DefaultWindowsTeamRosterLosesData` (SM-1 on the shipping default windows, roster `.five` against Trends `.three`), `testSM1TeamTaskRerunHeals` (control), `testSM2FreeUserCannotSeeFollowedPlayerWithoutLiveRow` (verbatim copy of the sheet's `followed` expression over a free model that loaded only the live season), `testSM2ProUserFallbackWorks` (control: same data after `loadHistoricalIfNeeded`).

## Claims

| id | verdict | user route (shipping UI) | impact | evidence |
|---|---|---|---|---|
| SM-1 | CONFIRMED | Pro, any iPhone. Trends tab, season pill to last season (or phase pill to Playoffs, offered all year: `SavantMenus.swift:112`). Teams tab, a team, Roster tab, Recent segment. Trends tab. Teams tab. | Pro users who use both Trends off the live regular season and a team's Recent roster in one session. The paid roster shows **"No players found / No players match the selected category for this team"**, not a column of dashes as the audit says. Heals on any roster control change, on popping and reopening the team page (new `@State`), or on the next data revision. Low-moderate: a paid feature looks empty and the message blames the data. | One context string clears every window on mismatch, `DashboardViewModel.swift:662,789-795`. Tabs stay mounted, `RootTabView.swift:96-107`. Trends reloads on return because `isActive` is in its task id, `HotColdView.swift:182-190`; TeamView's task id has no `isActive`, `TeamView.swift:325-328`. Symptom: TeamView opens on `rosterWindow = .five` (`TeamView.swift:28`) while Trends uses `recentWindow = .three` (`DashboardViewModel.swift:670`), so after the clear the 5-week slot is nil; `filteredPlayers` keeps only rows with a recent value when a sort metric exists (`TeamView.swift:220-221`, sort metric defaults from `.passing`, `:19,115-126`), and the `filteredPlayers.isEmpty` branch (`:638`) precedes the spinner branch (`:647`). The audit's repro uses window 3 on both sides, which is not the default, so its "hasRecentData true, dashes" path is a reachable variant (user picks 3 weeks) but not the common one. Proved twice: the audit repro (window 3) and `testSM1DefaultWindowsTeamRosterLosesData` (window 5, `XCTAssertNotNil failed - SM-1 on defaults`), with `testSM1TeamTaskRerunHeals` passing. |
| SM-2 | CONFIRMED-MINOR | Free, any iPhone. Follow a player (profile star or Follow sheet). A later live season in which he has no row at all (retired, unsigned, season-long IR). Stats, Following, Manage. | Free users with a followed player who has no live-season row. The ghost cannot be removed anywhere (no profile, search or board row to reach him), and the Following footer keeps counting him. Cosmetic: no cap on follows (`FavoritesStore.swift:38-45`), nothing else reads the id. The audit's "every followed player is affected between the September rollover and their first game" is true but costs nothing: before the opener the live season has no rows for anyone, so every board and the sheet's browse list are empty too, and it heals at kickoff. Pro users are unaffected because history is loaded. | Sheet promise and fallback, `FollowPlayersSheet.swift:35-42`. History loads only for Pro, `StatScoutApp.swift:130-133`; free loads fetch `season=eq.current` only, `StatcastAPI.swift:90`; `loadHistoricalIfNeeded` is the only path that adds past seasons, `DashboardViewModel.swift:1672-1702`. Footer, `FollowingStatsView.swift:15,69-73`. A playoff-phase miss is covered by the fallback (`playerHistories` holds the live regular-season row), so only a full-season absence triggers it. Proved at the model seam: `testSM2FreeUserCannotSeeFollowedPlayerWithoutLiveRow` fails with `("[1]") is not equal to ("[1, 2]")`; `testSM2ProUserFallbackWorks` passes. The test copies the sheet's private expression, so a UI test (seeded `favorites.playerIds`, open Manage) would be the final word on the view itself. |
| SM-3 (Trends) | CONFIRMED-MINOR | Pro (often a trial) on Trends with last season selected; subscription or trial expires while the process stays alive; foreground refresh flips `isPro` (`StatScoutApp.swift:76-78`). | What the lapsed user keeps is the locked board's single real leader row for last season instead of this season (`HotColdView.swift:377-459`; free users already get one real leader row by design, `:369-376`). Everything else is blurred and the pill shows the lock. No data or money lost. Population is larger than the audit's "tiny" (cancelled trials expire on a schedule), but the cost is near zero. | `applyProState` clamps only `selectedSeason`, `DashboardViewModel.swift:178-189`. Trends' season is view `@State` (`HotColdView.swift:28`) set only in `init` (`:40`), `selectSeason` (`:280-287`) and `onChange(of: recentFormSeason)` (`:196-198`); no `onChange(of: store.isPro)`. Code leaves no doubt; no test seam reaches view `@State`, and a launch cannot flip `isPro` mid-process. |
| SM-3 (Compare) | NOT A BUG | Same route on Compare. | None. | Compare keeps `seasonA/B` but blurs, disables and hides the cards from VoiceOver when not Pro, `CompareView.swift:200-205`, and its load task is keyed on `store.isPro`, `:233-234`. Nothing from the locked season renders. |
| SM-4 | CONFIRMED-MINOR | Any user, any iPhone. A process that is alive before 1 September and still alive after the opener (about ten days suspended without being killed). | Very rare. Between 1 September and the opener the stale process shows last season's data, which is arguably better than the empty new season a fresh launch shows. Only after the opener is it wrong: a free user sees the new season locked and last season as free; a Pro user's live fetch still asks for the old season (`StatcastAPI.swift:83,90`). Heals on relaunch. The audit's "all hit at the opener, when attention is highest" overstates it. The cited comment (`StoreService.swift:47-51`) is about shipped builds needing a release, which the calendar rule does fix; process lifetime is a separate, unaddressed case. | `static let current = season(on: .now)`, `StoreService.swift:55`; `static let free = current`, `:75`. Swift initialises a static stored property once per process, so the code leaves no doubt. No clock seam on `current`; a test would need `current` to read an injectable clock. |

## What survived, ranked

1. **SM-1** (CONFIRMED). Paid Team roster empties after a normal tab round trip, with a misleading "No players found". Pro only, reachable all year via the Playoffs phase pill.
2. **SM-2** (CONFIRMED-MINOR). Free users cannot remove a followed player who has no live-season row. Cosmetic, permanent until he plays again.
3. **SM-4** (CONFIRMED-MINOR). Season frozen per process across the September rollover. Wrong only for a process that outlives the opener.
4. **SM-3 Trends** (CONFIRMED-MINOR). A lapsed user keeps one real leader row of last season. Near-zero cost.

The audit ranked SM-3 above SM-4; SM-4 is rarer but does more damage when it hits (wrong free season, missing live data), so they swap. SM-1's ranking stands, but its "what the user sees" column should read "No players found", not dashes.

## Product calls

All seven agree with the code and are fairly classed as not counted:
- `apply(customerInfo:)` is last-write-wins (`StoreService.swift:891-901,995-999`). No out-of-order delivery was shown; agree.
- Cold-start drift leaves an empty board with no error; `DataFreshnessTests.testRevisionChangeDuringFetchDoesNotAdoptMixedData` asserts it on purpose. Agree.
- Device-local vs UTC season flip (`StoreService.swift:62`): nothing is played on 1 September. Agree.
- Conversion record skips Ask to Buy and restores (`StoreService.swift:798-806`): analytics only. Agree.
- `lastError` is global and shown under the status card for Pro (`SettingsView.swift:148-155`). Agree, minor.
- `purchaseInFlight` is written (`StoreService.swift:783-784`) and never read. Dead state, not a bug.
- `markShown()` before `requestReview()` (`RootTabView.swift:83-85`): matches the tracker's documented pacing. Agree.
- M9 offline onboarding Restore: Settings still offers Restore after Skip. Agree.

No serious state bug outside the audit was found within its scope. Team pages pushed from Stats (`StandardDestinations`, `RootTabView.swift:516-530`) share the same cache and task, so SM-1 also applies there; the Trends stack has no direct link to a team page, so the reverse case (Trends board blanked by a team page on its own stack) was not found.

## Method changes

- **Drive the repro from the view's real defaults.** The SM-1 repro used a 3-week roster window, but TeamView opens on 5 weeks. That hid the actual symptom (a "No players found" empty state, not dashes). Read every `@State` initial value on the route and use them in the test.
- **Trace the render branch, not just the data.** "What the user sees" should come from following the `if/else` chain that draws the screen (`TeamView.swift:630-660`), including filters applied before it.
- **Check the counterfactual when estimating impact.** SM-2's rollover window and SM-4's pre-opener window are periods where the correct behaviour is also empty or worse; impact there is nil.
- **Scope multi-screen claims per screen.** SM-3 bundled Compare, which blurs and disables; a one-line check per screen would have dropped it.
- **Quote the promise for the case claimed.** SM-4 cites a comment about shipped builds for a process-lifetime bug; say so rather than presenting it as the broken promise.
- **Use a unique `--id-file` path.** `/tmp` is shared by sessions on a fleet machine; a generic name was overwritten by another run during this review.
