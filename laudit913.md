# Week 1 fan audit

Date: 2026-09-13

Audience: A football fan installing the app during Week 1 who wants to see what happened in the games, which players stood out, and how their team is doing.

Build audited: 1.2 (45), the checked-out XcodeGen project.

Scope: Fresh-install onboarding, free tier, current 2026 regular-season data, and the main Stats, Trends, Teams, Compare, player, and team routes.

## Executive verdict

Football Next: StatScout currently feels like a well-made league-wide player percentile scout. It does not yet feel like a Week 1 football app.

The biggest missing piece is a game-centered layer. A new fan can see that Week 1 has 10 games of data, then immediately has to leave the app mentally to answer the most basic questions:

- Which games were played?
- Who played whom?
- Who won?
- What was the score?
- What happened in my team's game?
- Which player performance belonged to which game?
- Were the advanced numbers complete, or are some still arriving?

The proposed Games tab is the right product direction. It should be treated as a core navigation surface, not a Pro-only add-on. The schedule, matchup, status, score, and a basic box score should be free. Advanced interpretation, deeper percentile comparisons, and historical game analysis can be the paid layer.

The Week 1 experience has two especially serious dead ends:

1. There is no Games or scoreboard surface anywhere in the app.
2. Trends is empty in Week 1 because the app requires a prior window before it ranks movement. The user is told there is no movement precisely when they most need a Week 1 answer to “who played well?”

The app has a strong foundation: current player data loads, the freshness caption is useful, the visual hierarchy is clean, player search works, standard player lines are available, and no account is required. The problem is not presentation quality. The problem is that the information architecture starts at the analysis layer and skips the football context layer.

## Audit basis

### Runtime test

I used a fresh simulator install on a headless iPhone 17 Pro, completed onboarding as a non-Pro user, and ran the app against the live Supabase feed. No app or source files were changed during the audit.

The live build and run completed successfully. I visited the onboarding flow, Stats, Trends, Teams, an unplayed team, a played team, and a player profile. This was an audit-only task, so no application test suite was changed or run.

The live feed reported:

| Field | Observed value |
| --- | --- |
| Season and phase | 2026 regular season |
| Current week | Week 1 |
| Expected games | 10 |
| Observed games | 10 |
| Coverage | Complete |
| Last game date | 2026-09-13 |
| Player snapshot rows | 582 |
| Teams with current player rows | 20 |
| Player game-log rows | 593 |
| Unique game IDs in game logs | 10 |
| Next Gen Stats status | Ready |
| PFR status | Pending |
| Overall publisher status | Degraded, despite complete game coverage |

The ten game IDs present in the live game-log data were:

| Game date | Game ID |
| --- | --- |
| 2026-09-09 | 2026_01_NE_SEA |
| 2026-09-10 | 2026_01_SF_LA |
| 2026-09-13 | 2026_01_ATL_PIT |
| 2026-09-13 | 2026_01_BAL_IND |
| 2026-09-13 | 2026_01_BUF_HOU |
| 2026-09-13 | 2026_01_CHI_CAR |
| 2026-09-13 | 2026_01_CLE_JAX |
| 2026-09-13 | 2026_01_NO_DET |
| 2026-09-13 | 2026_01_NYJ_TEN |
| 2026-09-13 | 2026_01_TB_CIN |

This matters because the underlying feed already has enough identity to start a game-centered experience. The app currently consumes that information only indirectly.

### Relevant implementation evidence

- The root navigation has exactly four tabs, Stats, Trends, Teams, and Compare. [RootTabView.swift](StatScout/Views/RootTabView.swift#L140)
- The app API exposes player snapshots, player game logs, team game logs, Recent Form, and freshness. It has no fetch-games or fetch-game-detail contract. [StatcastAPI.swift](StatScout/Services/StatcastAPI.swift#L11)
- Player game logs carry team, opponent, date, week, raw counts, and metrics in the backend. The iOS model does not decode the backend game_id field. [PlayerGameLog.swift](StatScout/Models/PlayerGameLog.swift#L5)
- The event-aware migration already adds game_id to player_game_logs and indexes it. [20260912000000_event_aware_refresh.sql](supabase/migrations/20260912000000_event_aware_refresh.sql#L89)
- The backend currently uses the schedule to map game_id to a game date when ingesting logs. [ingest_game_logs.py](backend/ingest_game_logs.py#L135)
- The backend uses schedule home_score and away_score fields to determine whether a game is complete, but the app has no schedule/game model or score endpoint. [refresh.py](backend/refresh.py#L141)

## The new-user journey

### What the fan wants

A Week 1 fan usually has a simple sequence of questions:

1. Show me today's or this week's games.
2. Let me find my team.
3. Tell me the score and result.
4. Show me the important player and team numbers.
5. Let me go deeper into advanced analysis if I want it.

### What actually happens

1. The fan sees three onboarding cards.
2. The second card promises that four tabs cover “every angle of the game.”
3. The four tabs are Stats, Trends, Teams, and Compare. There is no game angle.
4. The final onboarding page makes the paid CTA primary. “Get Started” is only a de-emphasized text action.
5. After entering, the fan lands on a player leaderboard, initially QB and Pass Yds.
6. The screen says “Week 1 · 10 games · Updated ...” but does not name any game.
7. Teams shows all 32 clubs, not the 20 clubs with current Week 1 player rows, and does not mark played, upcoming, bye, or unplayed.
8. A team page shows team aggregates or “Not enough offense data to aggregate,” but never says what happened in the team’s game.
9. Trends says “No movement to rank yet” because Week 1 has no prior window.
10. A player profile shows season or Week 1 totals, but no opponent, score, result, or “view this game” path.
11. The most useful recent-form and comparison features are gated behind StatScout+.

The fan can browse stats. They cannot follow the football week.

### Question coverage scorecard

| Fan question | Current answer | Assessment |
| --- | --- | --- |
| What is covered? | Week 1, 10 games, freshness age | Good but incomplete |
| Which games happened? | No answer | Missing |
| Which teams played? | Only indirectly through player rows | Missing as a user surface |
| Who won? | No answer | Missing |
| What was the score? | No answer | Missing |
| Who led the league in a basic stat? | Yes, player leaderboard | Good |
| Who played well this week? | Partly through manual leaderboard browsing | Unclear |
| Who is heating up? | No movement in Week 1 | Dead end |
| How did my team do? | Team percentile/roster data only | Missing game context |
| How did a player do in that game? | Season/Week 1 line without matchup context | Incomplete |
| Are advanced numbers complete? | Mostly hidden behind a general freshness caption | Incomplete |
| Can I find my team? | Yes, through Teams or search | Good |
| Can I find a game? | No game search or list | Missing |

## Priority summary

### P0, blocks the Week 1 fan use case

- F-01: No Games tab, schedule, scoreboard, results, or game detail.
- F-02: Player and team stats are not connected back to a specific game.
- F-03: Trends has no meaningful Week 1 output.

### P1, high-impact friction or trust issue

- F-04: Teams presents all 32 clubs without current-game state.
- F-05: Freshness reports the data age and game count, but not the completeness of each data layer.
- F-06: The default leaderboard has no playing-time minimum, which is risky in a one-game sample.
- F-07: The first-run flow asks for a purchase before the user sees current Week 1 value.
- F-08: Recent form is both gated and represented by illustrative teaser numbers.
- F-09: Team advanced stats look like game/team analytics but are roster-derived aggregates.
- F-10: No standings, records, kickoff times, live state, or upcoming-game context.

### P2, clarity and retention improvements

- F-11: Several labels assume an analytics-literate football fan.
- F-12: Refresh behavior is not ideal for a live Sunday use case.
- F-13: Empty states do not always distinguish “this team did not play” from “the data is missing.”
- F-14: App naming is not fully consistent across the App Store, home screen, and in-app language.

## Detailed findings

## F-01, P0: There is no game-centered surface

### Observation

The app opens to a player leaderboard and has four navigation destinations:

- Stats
- Trends
- Teams
- Compare

There is no Games, Schedule, Scores, Results, Week, or Scoreboard destination. The onboarding copy explicitly describes these four tabs as covering “every angle of the game,” which sets an expectation the current navigation does not meet. [StatScoutApp.swift](StatScout/StatScoutApp.swift#L456)

The current freshness line tells the fan that ten games are represented, but a count is not a game list. It cannot answer even the basic question of which games those ten are.

### Fan impact

This is the largest mismatch between the download intent and the product experience. A fan who opens the app after watching Sunday football expects to scan the slate first. Instead, the first useful screen is a sorted list of quarterbacks by one statistic.

The absence is also contagious:

- There is no entry point for game details.
- There is no place to show scores.
- There is no way to browse upcoming games.
- There is no “my team played” shortcut.
- There is no reliable place to expose partial or delayed game data.

### Recommendation

Add a Games tab with the current week as the default. Keep the game list and basic box score free. A game row should show:

- Away and home teams.
- Team marks or colors.
- Game status, such as Upcoming, Live, Final, or Advanced stats arriving.
- Score when available.
- Local kickoff time or final date.
- Week and season.
- A visible current-team/following affordance.

The list should be organized by status first, then time:

1. Live
2. Final
3. Upcoming

After Week 1 is complete, the default should still make it easy to move through weeks.

### Acceptance criteria

- A fresh Week 1 user can name every game represented by the “10 games” caption without leaving the app.
- Every current game has a stable matchup identity, not just a date.
- A fan can reach a specific game detail in one tap from the Games tab.
- The screen distinguishes a game that has not started, a game in progress, a final game, and a final game whose advanced enrichment is still arriving.

## F-02, P0: Existing stats are not connected to a game

### Observation

The standard Stats board shows player, team abbreviation, rank, selected stat, and percentile bar. The visible rows do not show:

- Opponent.
- Game date.
- Home or away.
- Game result.
- Score.
- A link to a game detail.

The player profile shows Advanced, Standard, and Year Compare. Its Standard view can show useful values such as completions/attempts, games, yards, touchdowns, and interceptions, but it still does not identify the game that produced the Week 1 line.

The player game-log API is currently used to build Recent Form for one player, and team game logs are used for team recent aggregates. Those are analysis inputs, not user-visible game records. [StatcastAPI.swift](StatScout/Services/StatcastAPI.swift#L80)

### Fan impact

The fan has to mentally join a player to a matchup. This is especially bad for:

- Players who have a bye.
- Players traded during a season.
- Players sharing a team abbreviation.
- Teams with multiple games on adjacent days.
- Any stat that looks surprising without opponent or game context.

The app can tell the fan that a quarterback threw for 410 yards. It cannot tell the fan which opponent that performance came against, whether it was a win, or where it sits in the game story.

### Recommendation

Use game_id as the join key across schedule, player game logs, and game detail. Every game-level player row should be able to navigate to the same Game Detail route.

At minimum, add a compact “Week 1” or “Last game” context to player rows and profiles:

- Opponent.
- Date.
- Result.
- Score.
- One or two key stats.
- “View game” action.

Do not join by date alone. The backend already carries game_id on the live game-log rows, and the migration adds that field to the table. The iOS model currently omits it from CodingKeys, so it is not available to the UI. [PlayerGameLog.swift](StatScout/Models/PlayerGameLog.swift#L28)

### Acceptance criteria

- Tapping a player from the Week 1 board can expose the player's Week 1 matchup.
- Tapping a player from a game detail returns to the same game detail context.
- Every player game line has a stable game identity.
- A player with no game in the selected week is clearly described as inactive, on a bye, or not yet played, rather than simply absent.

## F-03, P0: Trends is empty when Week 1 fans need it most

### Observation

The Trends screen offers:

- Position selector.
- Metric selector, initially EPA/Play.
- Heating up or Cooling off.
- Three, five, or eight weeks.

The screen also explains that it compares the selected number of league weeks with the same span before them.

The current Week 1 data has only one game in the recent-form window. The model marks any form with fewer than two games as a small sample. The ranking filters those forms out. [RecentForm.swift](StatScout/Models/RecentForm.swift#L125)

The runtime result is:

> No movement to rank yet

The description says:

> EPA/Play doesn't have enough of a prior window to compare against. Try another stat or a longer window.

This is technically honest, but it does not solve the Week 1 user problem. A longer window still cannot create a prior comparison before the season has enough games.

The same empty result applies to a Pro user. The paid upgrade does not fix the lack of a prior window.

### Fan impact

The onboarding explicitly sells Trends as “heating up, cooling off,” and the Pro page promises the league ranked by form. In the first and most important acquisition week, that promise leads to an empty board.

A fan looking for “who was great in Week 1?” gets no answer. A fan looking for “who should I watch next week?” gets no answer. The product gives the user the controls for a trend board without providing a first-week substitute.

### Recommendation

Give Week 1 its own mode. Possible names:

- Week 1 Standouts.
- Debut Leaders.
- This Week.
- Single-Game Impact.

The Week 1 board should rank current-week performance, not change from a prior window. It can use:

- Standard production.
- EPA or EPA/Play where available.
- Position-adjusted percentile.
- Volume and sample indicators.

Once a prior window exists, switch to the existing Heating Up and Cooling Off logic.

If a comparative trend cannot be shown, the screen should explain the limitation and offer a useful next action:

- View Week 1 standouts.
- View Week 1 games.
- View all current player stats.

The controls should not imply that changing from three to eight weeks will solve a missing prior period.

### Acceptance criteria

- In Week 1, Trends always gives the user either a meaningful Week 1 board or a direct route to a meaningful substitute.
- The screen does not show comparative trend controls as if they are usable when every current sample is ineligible.
- The copy distinguishes “not enough historical comparison” from “data failed to load.”
- Pro users and free users receive the same truthful Week 1 explanation, with the paid layer applied only to the intended depth.

## F-04, P1: Teams presents all 32 clubs without game state

### Observation

The Teams screen intentionally renders all 32 NFL teams once any current data exists. The live Week 1 feed has current player rows for 20 teams, but the UI says:

> ALL TEAMS
>
> 32 teams

The grid is alphabetical by team name. It does not distinguish:

- Teams that played.
- Teams that have a game scheduled later.
- Teams that are live.
- Teams on a bye.
- Teams whose player data is delayed.
- Teams with no current data.

The team tiles are compact colored abbreviations. They are visually clean and useful for a football-literate user, but they are not game status indicators. [TeamsView.swift](StatScout/Views/TeamsView.swift#L264)

### Runtime example

I opened an unplayed Week 1 team. The page showed:

- Week 1, 10 games, and freshness.
- Advanced, Standard, and Roster tabs.
- “TEAM ADVANCED STATS.”
- “Not enough offense data to aggregate.”

It did not say:

- The team had not played.
- The team was scheduled for a future game.
- The team had a bye.
- The team’s game was delayed.

### Fan impact

“Not enough data” sounds like a pipeline problem. For an unplayed team, the correct explanation is a football state. The user should not have to know the schedule to understand whether the app is broken.

The same ambiguity appears for a team that has current player rows but a particular side of the ball has insufficient qualifying data. The screen does not give enough context to tell the difference between a legitimate small sample and missing ingestion.

### Recommendation

Add a status treatment to each team tile or to the team page header:

- Final, with score.
- Live, with score and clock if available.
- Upcoming, with kickoff.
- Bye.
- Data arriving.

For an all-teams view, group or filter by:

- My team.
- Playing this week.
- Final.
- Upcoming.
- All teams.

Keep the alphabetical league grid as a browse mode, but do not make it the only Week 1 orientation.

### Acceptance criteria

- A team page can explain why there are no Week 1 player rows.
- The Teams screen shows which clubs are in the current slate.
- A fan can go from a team tile to its current game in one tap.
- The selected team’s score and result are visible before synthetic team aggregates.

## F-05, P1: Freshness says how old the data is, but not what is complete

### Observation

The current freshness treatment is much better than having no status. The app shows a compact caption such as:

> Week 1 · 10 games · Updated 14m ago

It is tappable and can trigger a refresh. The caption correctly focuses on the time the stats changed, not merely the last status check. [DataFreshnessView.swift](StatScout/Views/DataFreshnessView.swift#L3)

However, the live publisher status at audit time was:

- Overall status: degraded.
- Coverage: complete, 10 of 10 games.
- Next Gen Stats: ready.
- PFR: pending.

The app maps the degraded state to a normal ready presentation when all expected games are present. That makes sense if PFR is optional, but it hides the fact that a class of advanced data is still arriving.

There is also no per-game or per-section readiness. A fan cannot tell whether:

- The score is final.
- Standard player totals are complete.
- Advanced player metrics are complete.
- Defensive coverage metrics are pending.
- A game has no applicable metric versus a metric that has not arrived.

The detailed caveat that advanced metrics may arrive later than game totals exists in the About Percentiles content, not in the context of a specific game or player. [PlayerProfileView.swift](StatScout/Views/PlayerProfileView.swift#L1351)

### Fan impact

“Updated” can be interpreted as “everything is final.” That is risky for an app whose main differentiation is advanced analytics.

The user needs to know whether a blank or missing advanced metric means:

- The player did not qualify.
- The metric does not apply to the position.
- The source has not published it.
- The game is still in progress.
- The request failed.

### Recommendation

Keep the quiet one-line caption for the normal case, but make the detail discoverable:

- “Week 1, 10 games, standard stats through Sunday, advanced defense still arriving.”
- “All 10 game scores final, 9 of 10 advanced stat packages ready.”
- “Game totals are final. Some Next Gen or PFR metrics may update later.”

On a Game Detail screen, show a small section-level label:

- Box score ready.
- Advanced stats ready.
- Advanced stats arriving.
- Data unavailable.

Do not surface internal provider names unless they help the user. “Advanced defense still arriving” is more useful than “PFR pending.”

### Acceptance criteria

- The fan can distinguish coverage completeness from enrichment completeness.
- A completed score is not presented as if every advanced field is final.
- A missing metric has a reason that is meaningful to a fan.
- The normal caption remains compact when all relevant data is ready.

## F-06, P1: The default Week 1 leaderboard exposes small-sample noise

### Observation

The Stats board starts with:

- League leaders.
- QB selected.
- Standard stats.
- Pass Yds selected.

The default qualifier is All Players, described as “No playing-time minimum.” [DashboardViewModel.swift](StatScout/ViewModels/DashboardViewModel.swift#L644)

That is defensible for a discovery product that wants every player from Week 1, but it is not self-explanatory on a league leaders screen. One game is an inherently noisy sample. A player can rank highly on a small number of attempts, targets, or snaps.

The Qualified option is available through the View control, but a new fan can miss it. The table itself does not visibly state whether the list is all players or qualified players.

### Fan impact

The user may read “leaders” as “best or most meaningful performance,” when the default list is actually “published players sorted by this stat, with no playing-time minimum.”

This can create two opposite problems:

- A casual fan distrusts the leaderboard because the ordering looks strange.
- An experienced fan has to hunt for the sample filter.

### Recommendation

Keep All Players available, but make the sample rule visible where the list is consumed:

- “All players, no minimum.”
- “Qualified players.”
- “Week 1 sample, use volume to judge.”

Consider making Qualified the default for leaderboards and retaining All Players as an explicit browse mode. Alternatively, use a Week 1-specific layout with volume displayed beside the headline stat.

For a player row, show enough denominator context to interpret the number:

- Pass yards with attempts.
- Rush yards with carries.
- Receiving yards with targets and receptions.
- Defensive totals with snaps or games when available.

### Acceptance criteria

- A fan can tell what qualifies a player for the visible list without opening a secondary menu.
- Week 1 leaderboards expose relevant volume alongside the selected headline number.
- The app does not present a one-play or low-volume result with the same visual authority as a full-game result.

## F-07, P1: Onboarding asks for a purchase before showing Week 1 value

### Observation

The first two onboarding pages are clear:

- “Your Pocket Scout.”
- “Find Insights Fast.”

The second page lists Stats, Trends, Teams, and Compare, but not games. The final page sells StatScout+ and makes the purchase CTA the primary bottom button. “Get Started” is a lower-contrast text action above it. [StatScoutApp.swift](StatScout/StatScoutApp.swift#L252)

The fan has not yet seen:

- A live Week 1 leaderboard.
- A game list.
- Their team.
- A current player profile.
- A real recent-form result.

There is no favorite-team selection during onboarding, so the app does not immediately personalize the first screen.

### Fan impact

The purchase ask arrives before the user knows whether the app answers their basic question. A fan who downloaded for Week 1 scores may interpret the app as selling an advanced subscription before delivering a scoreboard.

The user can still choose Get Started, so this is not a hard lock. It is a sequencing and emphasis problem.

### Recommendation

Make the first useful action prominent:

- “See Week 1.”
- “Get Started.”

Keep StatScout+ visible, but present it after the user has seen a real screen or as a secondary action. A lightweight onboarding personalization step could ask for a favorite team after the user enters, not require an account.

The second onboarding page should mention the actual Week 1 value:

- Games and scores.
- Player leaders.
- Team context.
- Advanced analysis.

The product claim should not say four tabs cover every angle if a core game tab is added later without updating the copy.

### Acceptance criteria

- A new fan sees current Week 1 data before being asked to buy.
- Get Started is easy to find on the final onboarding page.
- The first session can be personalized to a team without requiring sign-up.
- Onboarding accurately describes the navigation surfaces.

## F-08, P1: Recent form is hidden at exactly the time it would be most useful

### Observation

Recent Form is a central paid feature. The player profile advertises:

> See last 3 / 5 / 8 game form for any player

The free experience shows a blurred static teaser. The teaser numbers are explicitly illustrative and are not fetched from the live game logs. [RecentFormCard.swift](StatScout/Views/RecentFormCard.swift#L147)

The same approach is used for recent team bars. The free team teaser contains static sample totals such as 3,980 pass yards and 1,720 rush yards, not the selected team's real Week 1 values. [TeamFormCard.swift](StatScout/Views/TeamFormCard.swift#L335)

In Week 1, the user has only one game in hand, so three, five, and eight game form cannot yet be filled. That makes the game's most important paid selling point feel distant twice:

1. It is gated for free users.
2. It has limited practical depth in the first week.

### Fan impact

The fan can see that a feature exists but cannot use it to answer “how did this player look this week?” The static numbers also create a trust risk if the user reads them as the selected player's actual recent output through the blur.

### Recommendation

Use a real, clearly labeled Week 1 sample in the free tier:

- One-game line.
- “Week 1 only.”
- Volume.
- A small-sample label.
- Link to the game detail.

Reserve for Pro:

- Multi-game windows after enough games exist.
- Full league ranking by form.
- Recent percentile overlays.
- Historical windows and comparisons.

If a teaser is illustrative, label it as an example in accessible text, not only through blur.

### Acceptance criteria

- Free users can see at least one real current-week player line.
- A one-game result is not labeled as a three-game or five-game trend.
- Static teaser values cannot be mistaken for the selected player's actual stats.
- The paid feature has a meaningful Week 1 preview even before multi-game windows exist.

## F-09, P1: Team advanced stats can be mistaken for true team game analytics

### Observation

The team screen labels the main card:

> TEAM ADVANCED STATS

The team percentile card is built by aggregating player values across the roster. The UI says:

> Averaged across the offense roster

The standard team card adds current roster season lines, with a note that a traded player brings his whole year with him. The implementation is useful as a team roster profile, but it is not the same thing as a game box score or an official team total.

The dashboard also has a legacy teamScores cache that averages player overall percentiles. That value is not an NFL score and must not be reused for a scoreboard. [DashboardViewModel.swift](StatScout/ViewModels/DashboardViewModel.swift#L1237)

### Fan impact

A casual user sees “team advanced stats” and reasonably expects:

- What the team did in the game.
- How many drives or plays it had.
- EPA per play for that game.
- Success rate.
- Passing and rushing output.
- Defensive impact.

Roster averages answer a different question: how strong the current player group looks against the league. Without very explicit framing, a fan can mistake a roster percentile for a game performance or team score.

### Recommendation

Separate the concepts visually and verbally:

- Team Profile: roster-based season percentile and roster totals.
- Game Detail: game-specific box score and game-specific advanced analytics.
- Recent Team Form: a multi-game aggregate with an explicit window.

Use a strong label for the existing card:

> Roster profile, season average

Use team game totals only when the data source and aggregation rules are exact. Do not sum or average player metrics indiscriminately:

- Counting stats can often be summed with position-specific rules.
- Rates must use their true denominators.
- Defensive metrics may double count or omit team-level events.
- Player EPA is not automatically the same as team EPA.

### Acceptance criteria

- A fan can tell whether a number describes a roster, season, recent window, or one game.
- Game Detail never uses the roster percentile cache as a game score or game rating.
- Every team advanced metric has a documented aggregation identity.
- Traded-player and roster attribution rules are visible where they affect the number.

## F-10, P1: Core football context is missing

The app currently does not expose the context a fan expects around a Week 1 slate:

- Win/loss result.
- Team record.
- Opponent.
- Home/away.
- Kickoff time.
- Local date.
- Live status.
- Final status.
- Score by team.
- Upcoming games.
- Bye weeks.
- Standings.
- Division or conference position.
- Game-to-game navigation.
- Quarter or period scoring.
- Game leaders.

Not every item needs to ship in the first Games tab. The first release should prioritize matchup, status, score, date/time, and basic player/team lines. But the absence should be treated as a product gap, not merely a missing visual component.

### Recommended priority for context

#### Minimum

- Away team and home team.
- Score.
- Final, Live, or Upcoming state.
- Week and date.
- Opponent and result on team/player routes.

#### Strong next step

- Team record entering the game.
- Score by quarter.
- Game leaders.
- Following-team filter.
- Last updated for the specific game.

#### Advanced companion features

- Drives.
- Possessions.
- Field position.
- Success rate.
- Explosive plays.
- Early-down pass rate.
- Pressure and coverage.
- Win probability or leverage.

Only show the advanced companion features when their data provenance and aggregation are reliable.

## F-11, P2: The product vocabulary assumes a more advanced fan

The app opens with terms such as:

- EPA.
- CPOE.
- YAC.
- RYOE.
- Percentile.
- Next Gen qualifier.
- PFR.

This is a strong identity for an advanced analytics audience, but the download intent in this audit is simply “I want to see some stats.” The app does not give the user a traditional football orientation before introducing the analysis vocabulary.

The metric information button and glossary are good. They are discoverable after the user already encounters the metric. The first screen would be easier to understand if it paired the advanced stat with a familiar football line:

- Pass yards, then EPA/Play.
- Rush yards, then RYOE.
- Completion percentage, then CPOE.
- Receiving yards, then YAC.
- Tackles and sacks, then defensive percentile.

### Recommendation

Use a two-layer presentation:

1. Familiar game or player box score.
2. Advanced interpretation beneath it.

For example:

> 205 pass yards, 3 TD, 1 INT
>
> EPA/Play 0.31, 82nd percentile

This gives a new fan an immediate anchor while preserving the app's analytical identity.

## F-12, P2: Refresh behavior is not ideal for a live Sunday

The freshness caption re-renders its age every minute, which is good. It does not itself fetch new data every minute.

Foreground status checks are throttled to five minutes normally and two minutes when the status is pending. The current screen can also be refreshed by tapping the caption or pulling to refresh. [DashboardViewModel.swift](StatScout/ViewModels/DashboardViewModel.swift#L863)

For a fan watching a Sunday game, the likely expectation is that a final score or newly published stat appears without requiring them to understand the refresh model.

### Recommendation

For the Games tab:

- Poll only while the screen is active and only for Live or recently Final games.
- Use a visible “Updated X ago” per game or section.
- Stop polling when all games are final and advanced data is ready.
- Preserve a manual refresh action.
- Avoid frequent polling on player-only tabs.

The app should not promise real-time data unless the source supports that cadence. “Updated after final stats are validated” is better than a vague live promise.

## F-13, P2: Empty states do not always speak football

The app has honest empty-state copy, but some copy describes the data shape rather than the football situation:

- “Not enough offense data to aggregate.”
- “No movement to rank yet.”
- “No games in the last N games.”

These are understandable to an analyst and ambiguous to a fan.

### Better state vocabulary

| Technical state | Fan-facing copy |
| --- | --- |
| Team not in current slate | This team has not played in Week 1 |
| Team has future game | Next game: date and opponent |
| Team bye | Bye week |
| Game still processing | Final score is in, advanced stats are still arriving |
| No prior trend window | Week 1 performance is available; comparative trends start after more games |
| Metric not applicable | This metric is not tracked for this position |
| Source failure | We could not update this game; showing saved data |

The fan should not have to infer a schedule state from a nil aggregate.

## F-14, P2: Product naming is not fully consistent

The App Store name is Football Next: StatScout, while the in-app product and project use Gridiron StatScout and StatScout. StatScout+ is the paid tier.

This is not the most important Week 1 problem, and the runtime screens mostly use short labels such as Stats and Teams. It can still create mild uncertainty when the user moves between:

- App Store listing.
- Home-screen name.
- Onboarding.
- Paywall.
- Settings.

The product should choose one primary consumer-facing name and use the other name as a subtitle or descriptor consistently.

## Proposed Games tab

## Product position

The Games tab should be the place where a fan starts with football reality and then moves into the app's differentiated analysis.

The hierarchy should be:

1. What game is this?
2. What is the score and state?
3. What happened in the basic box score?
4. Which players and units drove the result?
5. What does the advanced analysis say?

Do not lead with an abstract team percentile before showing the score. Do not make a user reconstruct the matchup from player rows.

## Games list

### Default state

Open to the current season and current week. On Week 1 Sunday, show the current slate. After the week is complete, keep Week 1 selected until the user changes it, or show the most recent week with a clear selector.

### Suggested header

- 2026 Regular Season.
- Week 1 selector.
- Optional date selector for the current week.
- Following team toggle.
- Search by team or game.

### Suggested sections

- Live now.
- Final.
- Upcoming.

If there are no live or upcoming games, do not hide those states. A concise empty section can confirm that the list is complete.

### Suggested game row

Each row should contain:

- Away team abbreviation and name.
- Home team abbreviation and name.
- Team colors or marks.
- Score for each side when available.
- Status.
- Local kickoff or final time.
- Small data state, such as Advanced stats arriving.
- A chevron or clear tap target.

Example:

> HOU 24
>
> BUF 31
>
> Final, advanced stats ready

For an upcoming game:

> HOU at BUF
>
> Sunday, 1:05 PM PT

The exact time zone should be explicit or use the device locale consistently.

## Game detail

### Header

- Away and home teams.
- Logos or strong team marks.
- Score.
- Result.
- Status.
- Date and kickoff.
- Week.
- Data freshness.

### Basic box score

This should be available to every user:

- Passing.
- Rushing.
- Receiving.
- Team scoring.
- Turnovers.
- Sacks.
- Defensive headline totals.
- Team total plays and yards where the source supports them.

The implementation should show denominators where useful:

- Completions and attempts.
- Carries.
- Targets and receptions.
- Tackles and sacks.

### Advanced game scorecard

The advanced card should compare the two teams and explain the game rather than simply list isolated percentiles.

Recommended first metrics, only where the current source can calculate them exactly:

- Team EPA or EPA per play.
- Offensive success rate.
- Pass and rush EPA.
- Early-down pass rate.
- Explosive pass and rush plays.
- CPOE or completion efficiency.
- Yards after catch.
- Rushing yards over expected.
- Pressure, sacks, interceptions, and pass defense metrics.

Every metric should have:

- A short definition.
- A value for each team.
- A direction or winner indicator.
- A sample/denominator when relevant.
- An unavailable or arriving state when the source has not published it.

### Player leaders

Show a small set of game-specific leaders:

- Passing leader.
- Rushing leader.
- Receiving leader.
- Defensive impact leader.
- Highest game EPA or comparable metric where supported.

Each player row should open the player profile and preserve the game context.

### Game story

This is optional for the first implementation but valuable later:

- Scoring by quarter.
- Turnover margin.
- Explosive-play margin.
- Pressure/sack margin.
- Which unit explains the result.

Avoid writing a natural-language summary until the underlying data is stable enough to support it.

## Free and paid boundary

### Free

- Game schedule.
- Scores.
- Final/live/upcoming status.
- Basic box score.
- At least a small set of real Week 1 player leaders.
- One or two advanced metrics per game, with clear definitions.
- Link to player and team profiles.

### StatScout+

- Full advanced game scorecard.
- All advanced metric categories.
- Multi-game game-form views.
- Historical game comparisons.
- Full player and team recent-form rankings.
- Head-to-head and year-over-year analysis.

The user should never have to pay to find out whether their team won or who it played.

## Data and implementation contract for the next agent

The current backend and app have useful pieces but need a first-class game object.

### Game summary fields

At minimum:

- game_id.
- season.
- season_type.
- week.
- game_date.
- kickoff timestamp if available.
- away_team.
- home_team.
- away_score.
- home_score.
- status.
- completed.
- updated_at.

Useful later:

- quarter or period.
- clock.
- venue.
- broadcast.
- team records entering the game.

### Game availability fields

Keep game result state separate from advanced enrichment:

- score_status.
- box_score_status.
- advanced_status.
- source_published_at.
- published_at.
- last_checked_at.
- error or message.

Suggested values:

- Upcoming.
- Live.
- Final, box score ready.
- Final, advanced stats arriving.
- Final, advanced stats ready.
- Unavailable.

### Game detail fields

Use separate objects for:

- Team game totals.
- Player game lines.
- Advanced team metrics.
- Advanced player metrics.
- Quarter or drive data.

Do not make a roster average masquerade as a team game total.

### Existing join opportunity

The live player_game_logs table already contains game_id through the event-aware migration. The Swift PlayerGameLog model currently decodes:

- player_id.
- season.
- season_type.
- game_date.
- player_type.
- team.
- opponent.
- plays.
- touches.
- metrics.

It does not decode game_id. Add the field to the model when the game feature is implemented, and use it consistently for navigation and aggregation.

The current backend schedule helper maps game_id only to gameday. Extend the published schedule data to include matchup and score fields. The refresh code already reads home_score and away_score when deciding whether a scheduled game is complete, so the source appears to contain the essential score information upstream.

### API shape

The app needs a separate contract rather than overloading player snapshots:

- fetchGames(season, phase, week).
- fetchGame(gameID).
- optionally fetchGameAvailability(gameID).

The Games list should not fetch all player logs for every game. Load summaries first, then fetch detail on demand. Cache completed game summaries and details using game_id.

### Aggregation rules

Document each game metric before exposing it:

- Counting stats can be summed only from the correct player types.
- Passing, rushing, and receiving rows must not double count team totals.
- Rates must use the correct denominator.
- A player percentile is not a team score.
- A roster mean is not a game result.
- A missing advanced field is not zero.
- Optional provider enrichment must not make a completed basic box score look incomplete.

## Surface-by-surface recommendations

### Stats

Keep the player leaderboard as the app's analytical home, but add football context:

- Current Week 1 indicator.
- Opponent or last-game context on the row.
- Volume beside headline stats.
- “View games” action near the top.
- Visible All Players or Qualified label.
- A Week 1 standouts route when Trends has no prior window.

### Trends

Keep the current comparative board after enough games exist. Add a Week 1 mode that ranks current performance without requiring a prior period.

### Teams

Add current-week status to every team and a one-tap route to the team game. Keep the 32-team browse grid, but make played/upcoming/bye state explicit.

### Team profile

Add a Games or Results section above synthetic roster aggregates. Put score and result first, then show the existing Team Profile cards under a clearly labeled roster or season heading.

### Player profile

Add a current-week or last-game card:

- Opponent.
- Date.
- Result.
- Score.
- Standard line.
- Key advanced line.
- View game.

Keep Recent Form for multi-game analysis, but let a free user see one real current-week result.

### Compare

The current Compare tab is a useful enthusiast feature. It should not be the place users discover game context. Later, add game-to-game or player-in-game comparisons only after the primary Games flow exists.

### Freshness

Keep the current compact caption. Add more detail inside Game Detail and behind a tap:

- Games covered.
- Basic box score readiness.
- Advanced readiness.
- Last published time.

### Onboarding

Update the four-tab promise when Games exists. Show a Week 1 example. Make the first useful action prominent. Explain that scores and basic box scores are free, while StatScout+ adds deeper analysis.

## Suggested minimum release scope

The smallest change set that would make the app feel useful to a Week 1 fan is:

1. Add Games to the root navigation.
2. Publish a Week 1 game summary list with matchup, status, score, and date.
3. Add Game Detail with a real basic box score.
4. Link player game rows and player profiles to Game Detail.
5. Add team game status and score to the team route.
6. Add a Week 1 standouts state to Trends.
7. Label standard versus advanced readiness.
8. Make the free entry path show real current data before the purchase prompt.

Do not block this release on drives, a full play-by-play view, standings, or every advanced metric. The important first step is giving the fan a trustworthy game identity and a useful bridge from score to analytics.

## Future-agent acceptance checklist

### Fresh install

- [ ] Onboarding describes the actual navigation.
- [ ] The user can enter the free experience without a purchase-first impression.
- [ ] The first useful screen includes a clear Week 1 path.

### Games

- [ ] Games tab exists and is visible in the root navigation.
- [ ] Week selector works.
- [ ] Live, final, and upcoming states are distinct.
- [ ] The 10 live Week 1 game IDs resolve to ten visible matchups.
- [ ] Scores are shown when available.
- [ ] Game rows are accessible with team names, not only abbreviations.
- [ ] Game Detail opens in one tap.
- [ ] Basic box score is free.
- [ ] Advanced metrics have clear ready, arriving, and unavailable states.

### Stats and Trends

- [ ] Stats can link a player to the relevant game.
- [ ] Week 1 volume is visible with headline statistics.
- [ ] All Players versus Qualified is visible.
- [ ] Trends has a useful Week 1 fallback.
- [ ] No prior-window empty state suggests that selecting a longer window will create data that cannot exist yet.

### Teams and profiles

- [ ] Team tiles show played, upcoming, bye, or data-arriving state.
- [ ] An unplayed team says that it has not played, rather than only saying data is insufficient.
- [ ] Team pages show the current game score before roster-derived aggregates.
- [ ] Player profiles show opponent, result, and game link for the current week.
- [ ] A one-game sample is labeled as a one-game sample.

### Trust and data quality

- [ ] game_id is used as the stable join key.
- [ ] Basic team totals are not derived from the legacy percentile average.
- [ ] Rates use documented denominators.
- [ ] Missing advanced fields are not silently converted to zero.
- [ ] Score and basic box-score readiness are independently represented from advanced enrichment.
- [ ] The user can see when a game or metric was last updated.

## Final assessment

The current app gives a fan good answers to “which players rank highly on this metric?” It gives weak or no answers to “what happened in football this week?”

The Games tab should become the missing front door:

> Game first, box score second, advanced explanation third.

That structure would make the existing player percentiles, Recent Form, team cards, and comparisons easier to understand and more valuable. Without it, the app asks a Week 1 fan to start with the deepest layer of the product before supplying the basic football context that makes the numbers meaningful.

No source or app files were changed for this audit.
