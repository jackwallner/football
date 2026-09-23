# Football Next: StatScout: product / UX audit, 2026 Week 3

Date: 2026-09-23 PT
Scope: the whole shipped app (1.2.2 / build 50 source, `d7f43ad`) judged against the
live 2026 dataset, not against a fixture. Not a release-readiness pass; `laudit915.md`
and `caudit914.md` already cover that ground.

Method: read every view, view model, model and the backend metric pipeline; queried the
live Supabase feed the app actually reads (1,084 player rows, 10,089 metric rows,
`data_refresh_status`, `player_recent_form`, `game_details`); built Debug against
production config and drove it headless on `agent-sim-4` for the five tab screenshots
referenced below (`docs/uxaudit0923/`). Nothing in the app, backend or config was changed.

## Live data state at the time of the audit

| Field | Value |
|---|---|
| Season / phase | 2026 REG |
| `max_week` | 2 |
| Coverage | 32 of 32 games, `complete` |
| NGS | `ready` |
| PFR advanced defense | **`pending`** |
| Overall status | `degraded` |
| Published | 2026-09-23 08:23 UTC |
| Player rows | 1,084 (def 712, wr 150, rb 97, te 82, qb 43) |

## Verdict

The skeleton is right. Games, game detail, the metric registry, the freshness
publisher and the season/phase plumbing are all genuinely good, and the game page is
the best screen in the app. The baseball format does carry over.

What does not carry over is the **percentile**, and the percentile is the entire
product. In football, at Week 2, with two thirds of the roster being defenders, the
percentile is currently either meaningless or actively wrong on most of the rows the
app draws. Everything else in this document is smaller than that.

Three things break the core promise right now. Then a tab-by-tab pass. Then the data
we already have a licence-free feed for and don't use. Then what the app could be.

---

# Part 1. The three things that break the core promise

## P0-1. Two thirds of the league has no advanced stat, and its percentile scale is fake

`pfr_status` is `pending`, so Pro-Football-Reference's advanced defensive table has not
published for 2026. Verified against the live feed:

- **0 of 712 defenders** carry a single advanced metric. Not one Pressure, Hurry, QB
  KD, Cmp% Allowed, Yds/Tgt Allowed, Rating Allowed or Missed Tkl% exists in the 2026
  snapshot.
- Defenders are **66% of the league rows the app ships**.

So for two thirds of the app's players, the "Advanced" tab of the player profile
renders seven counting stats under the heading `PRODUCTION PERCENTILES`, the Trends tab
drops its Advanced/Standard control entirely, and the Stats tab silently flips the
board from Advanced to Standard when you tap DEF. None of those screens says why.
`MetricCoverage.note` has a purpose-built sentence for exactly this ("Advanced
defensive stats start in 2018") and it never fires, because the guard is
`season < 2018` and the season is 2026. The one place in the codebase designed to
explain this gap is structurally unable to explain the live one.

Worse than the absence is what fills it. Percentiles on counting stats, ranked with a
midpoint-of-ties convention, at Week 2:

| Metric | Defenders at zero | Percentile they are all given |
|---|---|---|
| INT | 675 / 712 (95%) | **47th** |
| FF | 660 / 712 (93%) | **46th** |
| Sacks | 584 / 712 (82%) | **41st** |
| PD | 546 / 712 (77%) | **38th** |
| TFL | 507 / 712 (71%) | **36th** |
| QB Hits | 493 / 712 (69%) | **35th** |

A defender who has done literally nothing (one tackle, zero of everything else) is
presented to the user as a **35th-percentile NFL player**. He is not. He is the bottom
of the league. Six hundred and seventy-five players share one identical "47th
percentile" interception ranking. The whole defensive overall-percentile scale
compresses into **35 to 88, median 48**: nobody is ever bad, nobody is ever elite, and
the colour ramp that is supposed to be the app's visual signature does nothing but
paint 700 players the same muddy mid-green.

This is not a cosmetic issue. It is the app telling a user something false about a
player, in the exact idiom ("percentile") the app sells itself on, on two thirds of its
rows, for the whole first half of the season.

**What to do, in order of value:**

1. **Ingest snap counts** (`nflreadpy.load_snap_counts`, verified live for 2026: 2,994
   rows, `defense_snaps` / `defense_pct`). This is the single highest-leverage change
   available. It gives:
   - a real qualification gate for defenders (snap share, not games played), replacing
     the current `games >= 1` bar that admits every special-teamer;
   - per-snap rate metrics (tackles per snap, pressure rate, TFL rate) that are
     rankable at Week 2 in a way counting stats never are;
   - playing time, which is a headline fact fans want on its own.
2. **Suppress percentiles on zero-value counting stats.** A metric where the modal
   value is zero should render the count and no bar, or a bar pinned to the bottom of
   the distribution, never the midpoint of a 600-player tie. The honest rank for
   "0 INT among 712 defenders" is "not ranked", not 47th.
3. **Say the advanced set is pending.** Extend `MetricCoverage` to read
   `data_refresh_status.pfr_status` and emit "Advanced defensive stats publish later in
   the season" on defensive boards while it is `pending`. The plumbing already carries
   the field to the client; nothing reads it.
4. **Rank defense against its own position group.** A cornerback and a nose tackle are
   currently in one 712-player pool on Tackles. `def` is a single `player_type` in the
   snapshot; splitting it into DL / LB / DB (the `position` field is already there) is
   a backend-only change that makes every defensive bar mean something.

## P0-2. A quarter of the live season's numbers are flagged untrustworthy and the app never says so

`Metric.qualified` exists on the model, is decoded, is cached, is round-tripped through
the encoder. It is rendered in exactly zero places. Grep confirms: no view reads it.

In the live feed **2,357 of 10,089 metric rows (23%)** are `qualified: false`. On
offense it is worse, because the defensive gate passes everybody:

| Group | Metric rows flagged unqualified | Players carrying at least one |
|---|---|---|
| RB | 756 / 1,504 (50%) | 68 / 97 |
| TE | 529 / 925 (57%) | 52 / 82 |
| WR | 746 / 1,874 (40%) | 75 / 150 |
| QB | 326 / 802 (41%) | 33 / 43 |
| DEF | 0 / 4,396 | 0 / 712 |

The qualification bar prorates by season progress (`qualification_scale`), so at Week 2
it is 18 pass attempts, 10 carries, 5 targets, 1 game. That is a defensible backend
choice, because you cannot have an empty board in September. It is not a defensible *display*
choice. What it produces today, on the live WR advanced board:

```
EPA/Tgt, 2026, Week 2
  1. Ryan Miller      MIA   7.01   1 target    qualified: false
  2. Omar Cooper Jr.  NYJ   3.58   1 target    qualified: false
  3. Jakobi Meyers    JAX   2.63   1 reception qualified: false
  4. Zay Flowers      BAL   2.18   6 targets   qualified: true
```

and on Catch%, eight players tied at 100.0% on one to three targets, every one of them
painted 85th percentile.

Meanwhile `qualifierLevel` defaults to `.all` ("No playing-time minimum"), and the
board header spends the PLAYER column on the words `ALL PLAYERS · NO MINIMUM`, which is
a filter state dressed as a column title and which no user reads as "these numbers are
noise".

**What to do:**

1. **Render the flag.** A dimmed row, a small dot, or the value in secondary ink with
   the bar suppressed. One glyph closes the entire gap between what the feed knows and
   what the user is told. This is the cheapest high-impact fix in the document.
2. **Default the live season to `.qualified`** for the first six weeks, with the "All
   players" escape hatch one tap away and a caption saying the minimum is prorated.
   Showing a credible top ten beats showing a complete one.
3. **Sort unqualified rows below qualified ones** rather than interleaving them, so the
   top of a board is never a one-target receiver.
4. `hasQualifyingMetric` currently returns true if *any* metric qualifies, not the one
   being ranked. A receiver who clears the target bar on Catch% is admitted to the
   Separation board he does not qualify for. Make it per-metric.

## P0-3. The Advanced board, the flagship screen, hides the one number that makes it readable

Screenshot `docs/uxaudit0923/02-stats-advanced.png`. The Standard board draws `QB · 90 att` under every name. The
Advanced board draws `QB`. Volume context is present on the board where it barely
matters and absent on the board where it is the whole story.

The consequence, live, today: Marcus Mariota sits **fifth in the NFL in EPA/Play** on
16 attempts, between Drew Lock and Dak Prescott, with nothing on the row to distinguish
him from a starter with 60. `LeaderboardTableRow` builds that subtitle from
`player.displayPosition` alone (`Components.swift:644`).

Fix: put the denominator in the subtitle, chosen from the ranked metric's own weight
(`MetricWeight` already knows it is attempts for passing, carries for rushing, targets
for receiving). `QB · 16 att` next to `QB · 60 att` does more for credibility than any
copy change in the app.

---

# Part 2. Tab by tab

## Games

The data behind this tab is excellent and the tab throws most of it away.

**It opens on the wrong week.** Screenshot `docs/uxaudit0923/03-games.png`: on Tuesday 23 September the
Games tab opens on **Week 3, a list of ten future kickoffs with no scores in it**,
while Stats, Trends and Teams all correctly say Week 2. `GameWeek.current`
(`Game.swift:221`) advances as soon as the last kickoff is 36 hours old, which lands
Wednesday-morning-equivalent after Monday night. NFL conversation about a week runs
Monday to Saturday. The front door of a football app is blank for most of that window,
and the app contradicts itself between tabs.

Rule it should use: stay on the completed week until the next week's *first* kickoff,
and once that week starts, lead with whatever is live or final and keep "last week" one
tap away as a labelled section rather than a pill the user must know to hunt for.

**No records anywhere.** "Atlanta Falcons / Green Bay Packers, Thu 5:15 PM" and nothing
else. `DashboardViewModel.record(forTeam:through:)` already computes W-L-T from posted
finals and is used nowhere on this screen. Adding `(1-1)` beside each club is a
one-line change against data already in memory.

**No live scores.** `statusDetail` says "Score at final" during a game. That is honest
about the nflverse schedule feed, but it means a tab called Games is inert on Sunday
afternoon, which is the single highest-intent moment of the week for this audience.
Either acquire a live score source or rename the promise. The current state invites a
one-star review that says "doesn't even show scores".

**Smaller:** no week date range in the header ("Week 3 · Sep 25–29"); no bye line on an
upcoming slate (byes are computed but only rendered under the finals); the floating tab
bar covers the last two rows of every slate.

## Stats

**The advanced half of the app is hidden behind a menu.** `stats.board` defaults to
`.standard`, so a first-run user lands on a passing-yards leaderboard that every free
app on the store already has. The differentiator (EPA, CPOE, RYOE, WOPR, the thing the
onboarding just promised on page one) requires finding a "View" menu. Default the live
season to Advanced, or show both boards as a two-segment control at the top rather than
burying one inside an overflow.

**No week filter.** Everything is season-to-date. "Who went off on Sunday" is the
highest-frequency NFL stat question there is, `player_game_logs` has the answer per
game, and the only surface that touches it is a Pro-gated rolling window. A free
"This week" segment on the Stats board would be the most-used control in the app.

**"Week 2 · 32 games"** reads as thirty-two games in Week 2. It means cumulative. Say
"Through Week 2 · 32 games" or drop the count.

**The bar has no legend.** The percentile is the product and the board never prints a
percentile number. `LeaderboardTableRow` deliberately suppresses it
(`Components.swift:611`). Defensible for density, but combined with an unlabelled bar
and a colour ramp, a new user has no way to learn that green means 90th percentile
rather than "near the top of this visible list". One caption under the header, or the
percentile as a superscript on the value, fixes it.

**Tab bar over content.** Rows 12 and 13 are illegible behind a 0.8-opacity ultraThin
capsule on every screenshot taken. The 88pt bottom spacer means nothing is *trapped*,
but the app spends two rows of its densest screen on permanent visual noise. Either
make the bar opaque, or add a scroll-aware hide, or inset the list.

**Defense's standard catalog is thinner than its advanced one.**
`StandardStatCatalog.stats(for: .defense)` offers Tackles, Sacks, Def INT, G, while the
metric feed carries PD, TFL, FF and QB Hits for the same players. Four rankable stats
exist and are unreachable from the Standard board. Same gap in `standard_stats` from
the backend (four entries for a defender versus eight for a QB).

## Trends

This is the tab StatScout+ is sold on, and right now it is selling something it tells
you does not exist.

Screenshot `docs/uxaudit0923/04-trends.png`. The caption reads *"Too early for movement: a 5-week
comparison starts in Week 6. Until then, the best of the season so far."* Eight hundred
points below it, the unlock CTA reads *"See the full board: every position ranked by
how far they've moved."* A free user in Weeks 1 through 5 is being asked to pay for
movement, on a screen that just told them there is no movement yet.

And the fallback board (`earlyRanked`, the season-to-date leaders) is **the same
ranking the Stats tab gives away for free**, with a blur on it. So for the first five
weeks of every season, the paid tab is the free tab plus a blur plus a promise the app
contradicts.

That is September. September is when the installs happen.

**What to do:**

1. Default `recentWindow` to `.three`, not `.five`. Movement then starts Week 4 instead
   of Week 6, which halves the dead zone at no cost.
2. Change the CTA copy while `isEarlySeason` is true. Sell what is actually behind the
   blur this week (every position, every metric, the full board), not movement.
3. Give the early-season board something the free tab does not have. Per-game or
   per-snap rates, or week-over-week deltas (Week 2 vs Week 1 exists from game one and
   is genuinely "movement" in the only sense available), or the defensive side that the
   Stats tab cannot rank at all.
4. Longer term: the whole "too early" problem disappears if the window is measured in
   *games played by that player* rather than league weeks, which also fixes bye weeks
   and mid-season injuries. `RecentFormCard` already does exactly this per player; the
   league board does not.

## Teams

Screenshot `docs/uxaudit0923/05-teams.png`. Thirty-two coloured circles, and under twenty-eight of them,
the word **"Sun"**. That is the entire information content of the screen.

`weekStatus` shows the *upcoming* fixture day, so on any day between Tuesday and
Saturday every tile in the league says the same three letters. The eighth division row
sits under the tab bar despite the code comment claiming the league fits one screen.
There are no records, no point differentials, no rankings, no indication that any of
these teams has played football.

**There are no standings anywhere in the app.** The `games` table has every posted
final; `record(forTeam:)` already computes W-L-T. A division grid with `2-0`, `1-1`,
`0-2` under each club would turn the weakest screen in the app into one of the reasons
to open it, using data that is already in memory, with no backend work at all.

**Team defense is measured backwards.** `TeamRankingsCard`'s Defense side aggregates the
team's own defenders' counting stats: tackles, sacks, interceptions. A bad defense that
is constantly on the field records *more* tackles and therefore ranks *higher*. There is
no opponent-allowed number anywhere in the app. `nflreadpy.load_team_stats` publishes
138 columns of real weekly team data for 2026 including full defensive splits; it is not
ingested.

**Dead code:** `TeamGridTile` is defined and never instantiated.

## Compare

Screenshot `docs/uxaudit0923/06-compare.png`. For a free user, four fifths of the screen is a grey smudge
and the smudge is over **two empty pickers**. There is nothing behind the blur. The
teaser teases nothing, and the unlock button sits underneath the tab bar.

Fix: put a real, filled, named comparison behind the blur (Mahomes vs Allen, this
season) so the blur is hiding something a user wants. Better still, give away one full
comparison per week. Head-to-head is the most screenshot-and-share-able artifact the app
can produce, and sharing is free marketing that the current design forecloses.

The follow list above it is good and correctly free.

## Player profile

Strong bones (the family-grouped percentile card, the phase-aware season menu, the
route-carried season/phase) with three sizeable holes.

**No game log.** This is the biggest missing screen in the app. `player_game_logs` has a
row per player per game, and the only thing the app ever does with it is collapse it
into a Last 3/5/8 aggregate. For a 17-game sport, the week-by-week line (Wk 1 vs KC
24/35, 312, 3 TD) is the single most-requested view a football stats app can have, it
fits on one screen, and it is the natural free counterpart to the Pro rolling windows.
It also has no bye-week or injury context to invent: the games either exist or they
don't.

**No player.** There is no age, height, weight, college, draft round, years of
experience or jersey number anywhere. `nflreadpy.load_players` carries every one of
those for 24,830 players and is not ingested. An app that calls itself a scout shows a
name, a team abbreviation and a two-letter position.

**Advanced and Standard are the same screen for a defender.** With PFR pending, the
Advanced tab is seven counting stats with bars and the Standard tab is four of the same
counting stats without them. Two tabs, one content set, no explanation.

**The glossary is unreachable from where it is needed.** `MetricDefinition.description`
is a genuinely good, written-out sentence for all 44 metrics, and it is only surfaced
in a Settings screen. A user looking at a WOPR row has no way to ask what WOPR is
without leaving the player. Make the metric row itself tappable to a definition sheet.

**`Player.games` (`GameTrend`) is dead.** The field is decoded, encoded, and empty in
every live row; `latestGame`, `latestPercentileDelta` and `weeklyDelta` are unreachable.
Either populate it or delete it, but `shareSummary` should stop depending on it.

## Game detail

The best screen in the app and it needs almost nothing. Win probability chart, team
efficiency with percentile bars against all team games, "Plays that swung it" with WPA,
per-player efficiency tables that link back to profiles. This is the screen that proves
the concept.

Two notes. The Pro gate sits on the per-player efficiency tables while the far more
impressive team efficiency card is free, which is the right way round for acquisition,
but it means the paid unlock here is the least visually striking thing on the page.
And `big_plays` descriptions are cleaned with a regex that strips jersey numbers; worth
a glance that it never mangles a name.

## Onboarding, settings, freshness

Onboarding is three clean cards and the copy is accurate, with one exception: page one
promises "Every player ranked from week one", and at Week 2 the ranking on two thirds of
players is the fiction described in P0-1.

The freshness caption is a model of restraint and the publisher behind it
(`event_aware_refresh`, content hashing, `mark_data_refresh_unchanged`) is better
engineering than most shipping apps have. Keep it.

---

# Part 3. Data we already have and do not use

Every one of these is in `nflreadpy`, free, no key, already reachable from the existing
pipeline, and verified live for 2026 during this audit.

| Source | What it unlocks | Verified 2026 |
|---|---|---|
| `load_snap_counts` | Defensive qualification, per-snap rates, playing time, snap-share trends | 2,994 rows |
| `load_injuries` | Weekly status (Out / Doubtful / Questionable), practice participation | 455 rows |
| `load_team_stats` | Real team offense **and defense-allowed**, 138 columns, weekly | 64 rows |
| `load_players` | Age, height, weight, college, draft round/pick, experience, jersey | 24,830 rows |
| `load_depth_charts` | Starter vs backup, role changes | 554,114 rows |
| `fantasy_points`, `fantasy_points_ppr` | Already columns on `load_player_stats`; currently discarded | in feed |
| `load_ff_opportunity` | Expected fantasy points, over/under-performance | available |
| `fg_made`, `fg_pct`, `fg_long`, `gwfg_*`, return stats | Kickers, punters, returners. **No special teams exist in the app at all** | in feed |

Two of these deserve to be called out as strategic rather than incremental:

**Fantasy points.** The app has no fantasy surface whatsoever, and fantasy is the single
largest cohort of NFL stat consumers by an order of magnitude. `fantasy_points_ppr` is
already a column on the weekly feed the pipeline reads and is thrown away on ingest. A
PPR / standard toggle on the Stats board, fantasy points on the game log, and
points-above-expected from `load_ff_opportunity` would open a market the app currently
does not address, and "fantasy football stats" is an ASO term with a volume the app's
current keyword set cannot reach.

**Injuries.** Nothing explains *why* a player's recent form fell off a cliff. A
Questionable / Out badge on a profile, a game log row, and a Trends row is a small
amount of data that makes every other number on the screen interpretable.

---

# Part 4. Aspirational: what would make this the NFL app

Roughly in order of value per unit of work.

1. **Standings.** Free, no backend work, turns the Teams tab from decoration into a
   destination. Division grid with records, streak, point differential; conference view
   with playoff seeding once the season is far enough along.
2. **The week-by-week game log.** One screen, data already in the table, the most
   requested view in the category, and the natural free tier for a Pro rolling-window
   product.
3. **Snap counts.** Fixes the defensive percentile problem at the root, adds playing
   time as a first-class fact, and makes a defensive Trends board possible for the first
   time.
4. **A "This Week" board.** Week-scoped leaders on Stats, free. Pairs with fixing the
   Games week rollover. Makes the app a Monday-morning habit rather than a
   look-something-up utility.
5. **Fantasy layer.** PPR toggle, fantasy points on every board and game log, expected
   points. New audience, new keywords, near-zero new pipeline.
6. **Real team stats and defense-allowed.** `load_team_stats` replaces an aggregation
   that is currently analytically inverted, and enables team-vs-team matchup pages,
   "this offense against this defense", which is the natural preview product for the
   Games tab's upcoming slate.
7. **Player identity.** Age, size, draft, college, experience. Cheap, and it is the
   difference between a table and a scouting report.
8. **Notifications and a widget.** Following a player currently has almost no payoff: a
   star on a row and a slot in a picker. "Your followed players, Sunday's results" as a
   push, and a home-screen widget with your team's next game and your followed players'
   last line, are the two things that convert a good app into a daily one. There is no
   widget extension and no notification capability in `project.yml` today.
9. **Share cards.** A rendered percentile card or head-to-head image is how this app
   spreads in group chats. `shareSummary` currently produces plain text and depends on a
   dead field.
10. **Special teams.** Kickers and returners are players fans look up. The feed has
    them. The app has five position groups and none of them is K.

---

# Part 5. Smaller defects and cleanup

- `MetricCoverage` cannot express "this source has not published *yet* for the live
  season", only "this source did not exist in that year". Needs a live-status branch.
- `player_recent_form` rows carry `start_week: -5` for the 8-week window at Week 2;
  harmless if `weekRangeLabel` clamps, worth confirming it does before Week 3 copy
  renders "Weeks -5-2".
- `StandardStatCatalog` for QB omits Cmp%, Rating and Cmp/Att, all of which exist in
  `standard_stats`.
- `TeamGridTile` is dead. `Player.games` / `GameTrend` is dead. `fetchHistoricalPlayers`
  is unreachable (already noted in the backend rules).
- `qualifierLevel` is a view-model property with no persistence, so the choice resets
  every launch.
- The Compare tab's unlock CTA renders under the floating tab bar.
- Division grid claims to fit one screen in a comment; it does not on a 6.9" phone.
- iPad is a supported device family with a 900pt max width and no iPad-specific layout;
  it was not exercised in this audit.

# Part 6. Not verified

- iPad and small-phone layouts.
- Any flow requiring a tap: the shared simulator pool has no input driver available to
  this session (`simctl` has no tap verb, `idb` is not installed), so player profile,
  team page, game detail, paywall and the Following board were audited from source and
  live data rather than from the running app. The five tab screenshots were taken via
  the `-StartTab` launch argument.
- Purchase and restore flows.
- Anything behind the Pro gate at runtime.
