# State machine audit, 2026-11-06

Scope: the iOS app (`StatScout/`), audited at committed `992e874` after `git stash -u` (nothing was stashed, the tree was clean). The app has no widgets, no watch target, no notifications and no App Intents (`.claude/rules/localization.md`), and no backend function that the entitlement depends on (RevenueCat is the entitlement authority, the Supabase pipeline only serves stats). Android is out of scope. No app code was changed.

**Summary: 1 bug proved by test (SM-1), 3 by code path only (SM-2 to SM-4), 7 product calls (not counted).**

Repro: `state-machines-2026-11-06-repro.swift` (4 tests in `StatScoutTests/StateMachineAuditRepro`). Run with `gh-sim test --scheme StatScout --only StatScoutTests/StateMachineAuditRepro` on Xcode 27.1, iPhone 17, iOS 27.2. Recorded result: `tests: Failed, 2 passed, 2 failed`. The two failures are the SM-1 claims, the two passes are the controls. In both failures the earlier precondition assertions passed, including `vm.recentFormByWindow[3] != nil` (the Team page's `hasRecentData`), so the failure is the last assertion only.

## Bugs, ranked by impact

| id | bug | what the user sees | promise | user route | impact | evidence | file:line |
|---|---|---|---|---|---|---|---|
| SM-1 | The recent-form cache holds one season/phase context. The Trends tab and the Team page Recent roster each load with a different season/phase source (Trends: its own `@State`; Team: the view model's `selectedSeason`/`selectedPhase`). The later load clears the other's data, and the screen that was cleared never reloads. | On the Teams tab, a team's Recent roster shows `-` in every row (no spinner, no error) after the user visited Trends on another season or on Playoffs. Stays until the next data revision or until they change a roster control. | `RootTabView.swift:96`: "Tabs live in a `ZStack` and toggle visibility rather than being swapped, so each one's navigation stack and scroll position survive switching away and back." Trends offers last season in its own pill (`HotColdView.swift:160`), Team page offers Recent on the live and previous season (`DashboardViewModel.supportsRecentForm`). | Pro user. Trends tab, season pill, pick last season (or phase pill, Playoffs). Teams tab, tap a team, Recent segment. Tap Trends tab (it reloads). Tap Teams tab. | Pro subscribers only, who use both Trends and Team Recent and move one off the live regular season. Playoff weeks and any Pro user who browsed last season on Trends. Cost: wrong-looking page (all dashes) until self-heal; self-heal is the next adopted data revision (`TeamView.swift:325` task id includes `freshnessRevision`), minutes on a game day, up to a week otherwise. | Proved. `testTeamRecentRosterSurvivesTrendsOnAnotherSeason` and `testTeamRecentRosterSurvivesTrendsOnPlayoffs` fail on `XCTAssertNotNil failed - Team Recent roster lost its data to the Trends tab` (resp. `...Trends playoffs board`). Controls `testControlSameContextKeepsBothScreensLoaded`, `testControlTeamReadWorksAfterItsOwnLoad` pass. | `DashboardViewModel.swift:662,789-795` (single `recentFormContext`, clears every window on mismatch); `HotColdView.swift:182-190` (task id has `isActive`, reloads on tab return); `TeamView.swift:325-328` (task id has no `isActive`, no reload), `:166` and `:191` (reads); `:647` (no spinner when the window slot is non-nil) |
| SM-2 | A followed player with no row in the loaded data is invisible in the Follow sheet, so cannot be unfollowed. | Stats, Following: "N followed players have no published stats for this season and phase yet. They stay on your list." Manage shows `FOLLOWING (0)` and no row to remove. | `FollowPlayersSheet.swift:36-38`: "A followed player with no line this season (injured, retired) falls back to his latest season, so he can still be unfollowed here." | Free user (history never loaded, `ContentView` loads it only for Pro). Follow a player from a profile star. Next season, or any season where that player has no row (injured, retired, before Week 1). Stats tab, Following segment, Manage. | Free users who follow a player who later has no live-season row. Fallback `viewModel.playerHistories[id]?.first` is nil because `playerHistories` holds only the live season for free users (`DashboardViewModel.swift:1672-1702`, `.task(id: store.isPro)` in `StatScoutApp.swift:117`). Every followed player is affected between the September rollover and their first game. Cost: stuck ghost entries, count in the footer never shrinks. | Code path. Not run: the claim needs the Follow sheet's private `followed` computed property, which no test seam reaches. Settle with a UI test (`-ScreenshotData`, seed `favorites.playerIds` with an id absent from the fixture, open Manage, expect a removable row) or by making `followed` a static function and unit testing it. | `FollowPlayersSheet.swift:35-42`, `FollowingStatsView.swift:15,69-70`, `FavoritesStore.swift:35-41` |
| SM-3 | Per-view season state is not re-clamped when Pro lapses mid-session. `DashboardViewModel.applyProState(false)` clamps only `selectedSeason`. `HotColdView.selectedSeason` (and Compare `seasonA/B`) keep the locked season. | A lapsed user stays on Trends for last season and sees the real board header and leader for a Pro-only season, while the pill shows it locked. | `HotColdView.swift:277-278`: "the free tier is pinned to the live season everywhere, Trends included". | Pro user on Trends, last season selected, app suspended past expiry, foreground (`StatScoutApp.swift:79` refresh flips `isPro`). | Tiny: needs a lapse inside one process lifetime. Cost: one free glimpse of a Pro surface, no data lost. | Code path. `selectSeason` is private to the view. Settle with a UI test using `STATSCOUT_FORCE_PRO` toggled, or by hoisting the clamp. | `DashboardViewModel.swift:178-189`, `HotColdView.swift:28,40,196-198` |
| SM-4 | `StatScoutSeason.current` and `.free` are `static let`, evaluated once per process. A process that survives 1 September keeps last season as the free season. | After the rollover the app still treats the old season as the free default until the process dies. | `StoreService.swift:47-51`: "A hard-coded year meant a shipped build stopped seeing new data the moment the next season started". | App backgrounded in August, resumed in September without a relaunch. | iOS usually kills suspended apps in days. Small population, but all hit at the opener, when attention is highest. | Code path. No clock seam on `current`; `season(on:calendar:)` is tested (`ClockBoundaryTests`) but `current` is not re-derived. Settle by making it a computed property. | `StoreService.swift:55,75`; readers `DashboardViewModel.swift:71,114`, `PlayerCache.swift:164,176,192` |

## Product calls (no citation to a broken promise, not counted)

- **`StoreService.apply(customerInfo:)` has no ordering.** Four writers (purchase result, restore result, `updateCustomerProductStatus`, delegate via `Task { @MainActor }`) set `isPro` last-write-wins (`StoreService.swift:797,870,891-901,995-999`). An older `CustomerInfo` landing after a newer one would flip a just-purchased user back to free. No promise cited and the SDK ordering was not shown, so it is not counted. Settle by comparing `CustomerInfo.requestDate` in `apply` with a fake that delivers two infos in reverse order.
- **Cold-start revision drift shows "No data available".** If the status revision moves between the two status reads of the very first load with an empty cache, `players` stays empty, `isLoading` false, no error (`DashboardViewModel.swift:1551-1565`). The test `DataFreshnessTests.testRevisionChangeDuringFetchDoesNotAdoptMixedData` asserts this on purpose. Recovery waits for the 300 s throttles (`:1345`, `:1362`), about 5 to 8 minutes. Population is new installs within seconds of a publish.
- **Season flips at device-local midnight, the pipeline at UTC midnight.** `StatScoutSeason.season(on:)` uses the device zone (`StoreService.swift:62`), `backend/ingest.py:50-51` uses UTC. Up to about 9 hours of disagreement on 1 September, with no games played, so nothing visible.
- **Conversion record misses Ask to Buy approvals and restores.** `ConversionDiagnostics.recordConversion` runs only when `purchase` returns `.purchased` (`StoreService.swift:800-805`). Analytics only, no user effect.
- **`StoreService.lastError` is global.** Settings shows it under the status card even for a Pro user (`SettingsView.swift:150-157`), and any success of an unrelated call clears it (`StoreService.swift:856`, `:787`). A stale "Couldn't load subscription options" can sit under "StatScout+ Unlocked" until the next foreground.
- **`purchaseInFlight` is written but read nowhere.** Three screens guard purchases with their own `@State` flag (`PaywallView`, `PlusDirectCTA`, onboarding), so the store has no single in-flight state. Apple's sheet is modal, so no double purchase was found.
- **Review prompt: `markShown()` runs before `requestReview()`** (`RootTabView.swift:83-85`), so a prompt the system declines still starts the 30 day cooldown. Documented in the tracker ("paces our calls").

## Related fixes, in order

1. SM-1: key the cache by (season, phase, window) instead of one `recentFormContext`, or give each consumer its own slot. Add `isActive` to the Team page task id as a stopgap.
2. SM-2: make the Follow sheet resolve unknown ids from the follow store itself (name and team snapshot), not from loaded data.
3. SM-3: re-clamp view-owned seasons on `isPro` change.
4. SM-4: derive the season at read time.

## State machines

Legend for invariants: I1 to In are listed per machine; "breaks" names the invariant a transition violates.

### M1. Entitlement (`StoreService.isPro`, `customerInfo`)

States: `unknown` (launch, `isPro=false`, `customerInfo=nil`), `free` (info present, not entitled), `pro` (`hasProEntitlement`), `lapsed` (free and a past-expiry entitlement, `isLapsed`), `pending` (Ask to Buy: no state of its own, the UI shows a message and `isPro` stays false).

Invariants: I1 `isPro == customerInfo.hasProEntitlement` for the newest `CustomerInfo`. I2 Every `isPro` change is mirrored into `DashboardViewModel.isPro` (`ContentView` `.onAppear` and `.onChange`). I3 A locked season is never the selected season for a non-Pro user. I4 A sheet that was opened to buy resumes its action once, only if access was granted by dismissal.

| from | event | to | note |
|---|---|---|---|
| unknown | `start()` `.fetchCurrent` ok / delegate cached info | free or pro | `StoreService.swift:724`, RevenueCat sends cached info on delegate set |
| unknown | offline | unknown, `lastError` set | I1 holds vacuously |
| free | purchase ok | pro | `apply` at `:797` |
| free | purchase throws cancelled/pending | free | mapped by `purchaseState(for:)` `:816` |
| pro | expiry seen on foreground refresh | lapsed | breaks I3 for view-owned seasons (SM-3) |
| any | older `CustomerInfo` applied after a newer one | wrong | would break I1 (product call) |
| any | `restorePurchases` | free or pro, `lastError` set | message persists (product call) |

### M2. Purchase flow (`PlusDirectCTA`, `PaywallView`, `OnboardingCards`)

States per surface: `idle`, `purchasing` (local `@State`), `message(pending|cancelled|failed)`, `plans` (PaywallView sheet). Invariants: I1 one purchase at a time (StoreKit sheet is modal); I2 the CTA label matches the offer actually transacted (`directCTALabel`, `planPickerCTALabel`, tested in `PriceDisclosureTests`, `StoreKitPurchaseTests`); I3 pending is never an error.

| from | event | to | note |
|---|---|---|---|
| idle | tap CTA, offering missing | `plans` | `needsPlanPicker` `:833-836` |
| idle | tap CTA | purchasing | |
| purchasing | `.purchased` | idle, sheet dismissed by `isPro` change | `TrialPitchSheet.onChange` |
| purchasing | `.pending` | message | I3 holds |
| purchasing | `.cancelled` | message "Tap again" | |
| purchasing | sheet dismissed by `isPro` while task runs | idle | harmless |

No invariant break found beyond the product calls above.

### M3. Pending unlock (`PendingUnlockAction`)

States: `empty`, `armed(action)`. `prepare` arms, `finish(isPro:)` always clears and runs only when Pro. Every `prepare` call site sits next to a sheet that calls `finish` in `onDismiss` (checked in StatsView, TeamsView, TeamView, CompareView, HotColdView, PlayerProfileView, PlayerComparisonView). Invariant I4 above holds. `PaywallFunnelTests` covers purchase, restore, approval-while-open, dismissal-then-approval.

### M4. Review prompt (`ReviewPromptTracker` + `RootTabView`)

State: `appLaunchCount`, `lastShownDate`, `outcome`, `hasPendingPositiveMoment`, session flag `reviewPromptShownThisSession`.

Invariants: I1 never in the first session, never before onboarding; I2 at most one per 30 days; I3 never after `openedWriteReview`; I4 not while a presentation is on screen.

| from | event | to |
|---|---|---|
| idle | `recordPositiveMoment` | pending |
| pending | 3 s later, screen clear, eligible | `markShown` then `requestReview`, cooldown |
| pending | 3 s later, screen not clear | pending (next moment asks) |
| any | Settings Rate row | `outcome=openedWriteReview`, ends asks |
| any | Settings Feedback row | `submittedFeedback`, no effect |

I1 to I4 hold; tested in `ReviewPromptTrackerTests`.

### M5. Data load and freshness (`DashboardViewModel`)

States: `cold` (`isLoading=true`, empty), `loaded`, `refreshing` (`loadTask`), `drifted` (revision moved during load, result discarded), `failed` (`lastFetchFailed`), `offline`. Plus `displayedDataRevision` vs `dataFreshness.revision` (stale banner).

Invariants: I1 rows on screen and `displayedDataRevision` come from one accepted snapshot; I2 an empty, non-loading board must have an error; I3 history loaded during a refresh survives it (fixed 2026-10-30, `HistoryRefreshRaceTests`).

| from | event | to | note |
|---|---|---|---|
| cold | load, drift between status reads | empty, not loading, no error | breaks I2 (product call 1) |
| refreshing | drift | loaded (old rows), stale banner | recovery 5 to 8 min |
| loaded | foreground, revision differs | refreshing | |
| any | history load during refresh | histories merged at ingest | I3 holds |

### M6. Recent-form cache (`recentFormContext`, `recentFormByWindow`, `recentFormTasks`)

State: one context string `"<season>-<phase>"`, per-window dictionaries and tasks. Invariants: I1 data in the slot matches the context; I2 every consumer's context equals the slot's whenever it renders from it; I3 an in-flight request for an old context cannot repopulate (cancel + guard, `:818`).

| from | event | to | breaks |
|---|---|---|---|
| ctx A loaded | Trends (A) then Team page (B) loads | ctx B, A cleared | I2 for Trends, healed by `isActive` reload |
| ctx B | Trends reload (A) | ctx A, B cleared | I2 for the Team page, never healed: SM-1 |
| any | revision adopted | all cleared | by design |

### M7. Season gating and history (`selectedSeason`, `hasLoadedHistorical`, view-owned seasons)

Invariant I1: a non-Pro user never renders a locked season; I2 history loads once, only for Pro or on a Pro-initiated pick. `clampSelectedSeason` enforces I1 for the shared season only. View-owned seasons: `HotColdView.selectedSeason`, `CompareView.seasonA/B`, `PlayerProfileView.selectedPercentileSeason`; Compare blurs when not Pro, Trends does not (SM-3). `loadHistoricalIfNeeded` leaves `hasLoadedHistorical=false` when the bundle decodes empty, so each later pick retries.

### M8. Follow store (`FavoritesStore`, Follow sheet, Following board)

State: ordered `playerIds` and one `team`, persisted on each toggle. Invariants: I1 every followed id can be listed and removed; I2 the footer count equals the ids hidden from the board. I2 holds (`missingCount`). I1 breaks when the id is not in loaded data (SM-2).

### M9. Onboarding (`hasCompletedOnboarding`)

States: `page 0..2`, `done`. Exits: Skip (pages 0 and 1), Get Started, `isPro` flips true (closes onboarding without the last page), purchase success. Invariant: the CTA that transacts shows a price and disclosure only when a monthly package exists (`monthlyOffer`, one optional drives both). Holds. The Restore row is replaced by a spinner while `dataReady` is false (offline first launch), so a returning subscriber offline cannot restore from onboarding until data loads; the Settings row is still available after Skip. Counted as a product call: no promise was cited.

### M10. Pitch caps (`PaywallGate`, profile open count)

`PaywallGate` is per process, two per trigger. Profile opens persist in defaults, so a free user sees the player pitch on the 2nd and 3rd open of every session. The review moment fires only on a visit without a pitch. Consistent with the comments at `PlayerProfileView.swift:298-312`.

### M11. Games load, caches

`loadGames` merges nothing it cannot replace (`if !loadedGames.isEmpty || games.isEmpty`), `TwoTierPlayerCache` discards unprovenanced snapshots once and rejects a current cache whose season is not the live one. No invalid transition found.
