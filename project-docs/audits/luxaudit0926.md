# Football Next: StatScout, independent UX audit, 2026-09-26

- Date: 2026-09-26 PT
- Scope: independent follow-up to `uxaudit0923.md`, focused on the live regular-season experience, data trust, and the first-run path through the five tabs.

## Verdict

The core concern from the Sep 23 audit remains: a percentile can look definitive when it is based on a tiny sample, and most defensive players still have no advanced defensive metrics. The current feed makes both issues more visible. The app already receives a per-metric `qualified` flag and the publisher's PFR state, but neither appears where a fan needs that context.

The visual system is coherent and the game detail concept is strong. The highest-value improvements are clearer sample context, a visible explanation when defensive enrichment is pending, and a more direct first-run route to the advanced stats. I would revise two claims in the prior audit: the Week 3 schedule selection is consistent with the current football week, and a midrank percentile for a very large tie is mathematically expected. Both still need clearer on-screen context.

## Current live state

Read-only snapshot from the app's Supabase project on Sep 26 at 18:10 UTC. The published player revision was last written at 06:08 UTC.

| Field | Live value |
|---|---|
| Season and phase | 2026 REG |
| Stats coverage | Through Week 3, 33 of 33 completed games included, latest game date Sep 24 |
| Week 3 schedule | 16 games, 1 final and 15 scheduled after the Sep 24 Atlanta at Green Bay game |
| Overall refresh state | `degraded` |
| Next Gen Stats | `ready` |
| PFR advanced defense | `pending` |
| Snapshot rows | 1,087 players: 713 DEF, 44 QB, 97 RB, 151 WR, 82 TE |
| Metric rows | 10,043 |

The current app source has no local changes. The visual screenshots in `docs/uxaudit0923/` are dated Sep 23, so they show the screen before the first Week 3 final. I checked the current schedule and metric payload separately. I did not rebuild or interact with a simulator for this audit.

The working tree contains backend work that is not in the published feed: `backend/ingest.py` has a pending change to prorate the qualification bar from median team games instead of the single most active player's games. New `backend/ingest_enrichment.py` and `backend/team_ratings.py` files add profile, snap, injury, rating, and projection data. The enrichment module says existing app builds do not read those tables. These changes do not yet change what the current app displays.

## Findings

### P0. Top rankings can be based on only a few opportunities

The backend flags 4,350 of 10,043 current metric rows as `qualified: false` (43.3%). The false share ranges from 25.7% of DEF metric rows to 73.4% of TE rows. `Metric.qualified` is used only when a user activates the Qualified filter; no row or metric presentation displays it. The live Stats filter defaults to `All Players`, and the `Qualified` option is behind the View menu (`DashboardViewModel.swift:754-786`, `StatsBoardControls.swift:216-229`).

This reaches the first rows of real boards. In the current feed, Ryan Miller is 100th percentile in EPA/Tgt on 1 target, Jakobi Meyers is 99th on 3, and Zay Flowers is 99th on 6. Connor Heyward is 99th percentile in EPA/Rush on 2 carries. On EPA/Play, Marcus Mariota is fifth at 91st percentile on 16 pass attempts, with `qualified: false`. The board shows the value and bar without the flag or denominator (`Components.swift:611-677`).

These values are mathematically ranked within the current cohort. The problem is the interface gives a tiny sample the same visual authority as a qualified line. A one-target 100th percentile reads as a reliable league-leading result.

**Recommended fix:** show the exposure beside every rate stat, mark unqualified rows visibly, and suppress or de-emphasize their percentile bar. Default live-season boards to Qualified, with an obvious All samples option. Keep the qualification status tied to the stat being shown where eligibility differs by metric. The pending median-team proration change addresses one pipeline cause of abrupt early-week thresholds, but the UI still needs to show the flags.

### P0. The defensive data gap is invisible on the boards that need it

DEF accounts for 713 of 1,087 snapshot rows (65.6%). With `pfr_status: pending`, none of those players has Pressures, Hurries, QB KD, Cmp% Allowed, Yds/Tgt Allowed, Rating Allowed, or Missed Tkl% in the current feed. The profile labels the remaining rows `PRODUCTION PERCENTILES`, which is accurate, but the board does not explain why the expected advanced set is missing. Selecting DEF while Advanced is active can fall back to Standard without explaining the change.

The publisher status is available to the app as `advancedDefenseStatus`, and `DataFreshness.isAdvancedPending` already computes whether PFR or NGS is late. The freshness view does not use that property. It maps a degraded state to a quiet ready caption when game coverage is complete (`DataFreshnessView.swift:25-30`, `DataFreshness.swift:106-117`). The current publisher reports complete game coverage and degraded overall status, so a fan sees a normal updated timestamp while the defensive metrics are absent.

**Recommended fix:** on DEF stats and player profiles, state that advanced defense data is pending and identify the available production stats. Keep the board usable, but avoid silently presenting the fallback as the full advanced set. The shared freshness caption can stay quiet for ordinary feed timing; the affected DEF surface needs the source-specific explanation.

### P1. Rate rows omit the amount of football behind the value

On the advanced Stats board, `LeaderboardTableRow` shows only the display position under a player's name. Its value column contains the raw metric and an unlabeled percentile bar. No attempt, target, carry, play, or game count appears (`Components.swift:635-677`). The current EPA/Play and EPA/Tgt examples above show why the missing denominator matters.

**Recommended fix:** add the denominator used by each metric to the row subtitle, such as dropbacks for EPA/Play, targets for EPA/Tgt, and carries for EPA/Rush. The denominator should match the calculation, rather than using a generic games-played label. A compact percentile number or one-line legend would also explain what the colored bar encodes.

### P1. A fresh install opens on the least distinctive Stats board

`StatsView` initializes `stats.board` to Standard (`StatsView.swift:9`). A first-run user sees a traditional stat such as Pass Yds. Advanced metrics live in a dropdown, and the control is not identified as the app's main differentiator. This is a weak first impression for an app whose central promise is advanced player comparison.

**Recommended fix:** make Advanced the first-run live-season board and keep Standard one tap away, or put the two board types in a visible segmented control. Keep the choice persistent after the user's first selection.

### P1. The early-season Trends pitch promises movement that is not available yet

At Week 3, the default five-week window has no prior window to compare. The screen says it is too early for movement, then the locked CTA still says the paid board ranks players by how far they have moved (`HotColdView.swift:64-75, 238-240, 401-405`). The preview row is a current season leader, which is useful, but the paid promise below it conflicts with the explanation above it.

**Recommended fix:** use the three-week window by default so movement starts sooner, and change the early-season CTA to describe the full board currently behind the gate. Keep the explicit early-season explanation.

### P1. Compare's locked state hides an unconfigured comparison

The initial Compare state has no selected players or teams. Free users can follow players, while the comparison cards are blurred and disabled (`CompareView.swift:54-73, 185-211`). The Sep 23 screenshot shows the blur covering blank pickers, so the user is asked to unlock a result they have not seen.

**Recommended fix:** show a real named sample comparison behind the lock, or let free users complete one comparison before the paywall. Make the first visible comparison communicate the benefit before asking for payment.

### P2. Coverage wording makes a cumulative count sound week-specific

The freshness caption currently formats the coverage as `Week 3 · 33 games · Updated ...` (`DataFreshnessView.swift:110-127`). The count is cumulative through the latest completed game, not a count of games played in Week 3. At the time of this audit, Week 3 has one final and fifteen future games.

**Recommended fix:** say `Through Week 3 · 33 games` or `Week 3 through Sep 24 · 33 games`. Add the selected week's date range to the Games header so the schedule and stats coverage are easy to distinguish.

### P2. Teams is a useful schedule glance, but it still does not answer standings

The Sep 23 Teams screenshot showed upcoming kickoff days under most clubs. The current code also renders a final result when a game is complete (`TeamsView.swift:385-405`), so as of Sep 26 Atlanta and Green Bay show their Thursday result while the other clubs show their upcoming day. The team grid still has no season W-L record or standings, and its eight division rows do not fit above the floating tab bar on the captured phone size.

**Recommended fix:** put each club's record in the tile and provide a standings sort or division view. Keep the current-week game status as secondary context. Treat the full one-screen grid comment as a layout bug: allow a clean scroll with enough inset to clear the tab bar.

### P2. The floating tab bar reduces row legibility

The translucent tab bar overlays leaderboard, schedule, and team content. In the captured Stats, Games, and Teams screenshots, text remains visible through the material and collides visually with the tab labels (`docs/uxaudit0923/02-stats-advanced.png`, `03-games.png`, `05-teams.png`). The scroll spacer lets a user move the last rows clear, but rows in the resting viewport still compete with the bar.

**Recommended fix:** give the bar a more opaque surface or reserve its height in the scroll content inset. Preserve the floating shape and current tab emphasis.

## Reassessment of the Sep 23 audit

- **Defensive zero percentiles:** `rank_percentiles` uses average rank for ties (`backend/ingest.py:317-326`). A zero INT value shared by 674 of 713 defenders receives a midrank around the 47th percentile. That result follows the chosen midrank convention. The user-facing problem is that the bar suggests useful separation when nearly the whole group is tied. Show the tie or exposure context, or remove the visual bar for a zero-heavy metric. Pinning every zero to the bottom would change the ranking convention and should be a deliberate product decision.
- **Games opening on Week 3:** `GameWeek.current` selects the schedule week whose last kickoff is still within the current week window (`Game.swift:215-230`). On Sep 26, Week 3 is the active schedule week and contains one final plus fifteen future games. The selection is coherent. The screen still needs a date range and a clearer distinction from Stats coverage through the completed games.
- **Teams showing kickoff days:** that was accurate for the Sep 23 capture, before Week 3's first final. Current code shows final scores and upcoming days. The continuing gap is the lack of season records and standings.
- **Live scores:** the game model and screen say scores arrive at final (`Game.swift:3-6`, `GamesView.swift:149-151, 290-295`). Keep that limitation explicit wherever the Games tab is introduced or promoted.

## Audit limits

This is a source and live-data follow-up, not a new simulator capture. Visual observations use the screenshots from Sep 23 and the current UI source, which has not changed. The live data snapshot is current to Sep 26, 18:10 UTC; it does not include the pending local backend changes described above. I did not verify purchase flows, iPad layouts, or interactions behind Pro gates.
