# Football Next: StatScout: product audit and in-season plan, 2026 Week 3

Date: 2026-09-26 PT (Saturday, Week 3 in progress: TNF final, 15 games Sunday/Monday)
Scope: the shipped app (1.2.1 live, 1.2.2 build 50 on TestFlight, source `d281dd0`) read
against the live 2026 feed, a second pass on `uxaudit0923.md`, and outside research on
what analytically minded fans use and which free data sources the pipeline can add.
Author: Claude (Opus 5.5). The 0923 audit stands; this document confirms, corrects and
extends it, then sets the 1.2.2 scope.

## Live data state

| Field | Value |
|---|---|
| Season / phase | 2026 REG, `max_week` 3 (TNF only) |
| Coverage | 33 of 33 games `complete` |
| NGS / PFR advanced defense | `ready` / **`pending`** |
| Player rows | 1,087 (def 713, wr 151, rb 97, te 82, qb 44) |
| Games played per player | 1 game: 270, 2 games: 768, 3 games: 49 |

## Verdict

Nothing in the 0923 audit has been fixed yet (the only commit since is the audit
itself), and two of its P0s have got worse in the three days since, for a reason the
0923 audit did not catch (finding N1). The app's core promise is still "every player,
ranked by percentile", and at Week 3 that promise is false on most rows it draws.

The larger point from this pass: the app answers "how good is he?" but never "compared
with what he costs, or where he was drafted?", and never "how good is this team?". Both
are the questions analytics-minded fans ask most in season (research below), both have
free, licence-clean data, and one of them is exactly what Hawk Blogger has been building.

---

# Part 1. Confirmed from 0923 (still open)

| 0923 finding | Status on 9/26 |
|---|---|
| P0-1 zero-count defender percentiles (0 INT = 47th) | Open. 674 of 713 defenders at 0 INT, all painted 47th. 585 at 0 sacks, all 41st. |
| P0-2 `qualified` flag never rendered | Open, and now worse (N1). |
| P0-3 Advanced board hides volume | Open. |
| Games opens on the wrong week Tue-Wed | Open (rule in `GameWeek.current`, 36h after last kickoff). |
| No records / standings | Open. `record(forTeam:)` exists and is unused on Games and Teams. |
| Trends CTA sells movement it says does not exist | Open until Week 6 with the 5-week default. |
| Teams grid says "Sun" 28 times | Open. |
| No game log, no bio, glossary unreachable | Open. |

# Part 2. New findings

## N1 (P0). One Thursday game re-qualified the whole league

`qualification_scale` (backend `ingest.py`) prorates the full-season bar by **the most
games any player has appeared in**. After Thursday night, 49 players (two teams) have 3
games and 1,038 have 1 or 2. So every player in the league is now held to a 3-game bar
while 30 of 32 teams have played 2.

Measured on the live feed, share of metric rows flagged unqualified:

| Group | 9/23 (after MNF) | 9/26 (after TNF) |
|---|---|---|
| QB | 41% | 50% |
| WR | 40% | 56% |
| RB | 50% | 65% |
| TE | 57% | 73% |
| DEF | 0% | 26% |

This repeats every week from Thursday to Sunday, and it gets worse at bye weeks (Week 5
on), when the max is always a team that has not had its bye. Fix: prorate by the median
number of games teams have played, not the max over players. One line in the backend,
and it corrects the flag for the 1.2.1 app already in users' hands.

## N2 (P0 for defense). Defenders are qualified on games, not snaps

The defensive bar is `games >= 1` (prorated). Snap counts exist for 2026 (3,086 rows,
99.7% of app players mapped through `load_players().pfr_id`), so a special-teams gunner
and an every-down linebacker are the same kind of row today. Snap share is the only fair
gate for defense until PFR's advanced table publishes, and it is also a fact fans want
on its own ("playing 94% of snaps").

## N3. Nothing tells you what a team is

Teams has no records, no standings, no strength measure. The Games tab's upcoming slate
is a list of kickoff times with nothing to say about either side. The data to fix both
is already in the database: `games` has every final, and `game_details` rebuilds team
EPA per play from play-by-play for every game. A team rating in points per game
(Part 4) turns both screens from calendars into previews.

## N4. Nothing tells you what a player costs

There is no contract, cap hit, draft slot, age, size, college or jersey number anywhere.
`load_contracts` (OverTheCap via nflverse) has an active deal for **100% of the offensive
players the app ships and 712 of 713 defenders** (checked by gsis id), with APY and APY
as a share of the cap. "Paid like the 90th-percentile receiver, producing like the
40th" is the single most-argued sentence in NFL fan discourse, and the app has both
halves of it and prints neither.

## N5. Injuries are invisible

`load_injuries` publishes the weekly report daily (733 rows through Week 3). A player
who is Out shows up on every board and profile with nothing to explain a missing week.

## N6. `handedness` is always empty

`PlayerIdentityStrip` prints `position · handedness`; the backend writes `""` for every
NFL row. The strip is the natural home for the bio line in N4.

## N7. Smaller

- The Standard board's "Week 3 · 33 games" reads as 33 games in Week 3.
- The profile's defender "Advanced" tab is titled PRODUCTION PERCENTILES and is the same
  content as Standard while PFR is pending (0923 said this; still true).
- The 0923 audit's "sort unqualified below qualified" was not strictly needed once the
  default qualifier and per-metric flag are right, but it is the cheapest guard against
  a one-target receiver leading EPA/Tgt, so keep it.

---

# Part 3. What analytics-minded fans actually want

Sources: the 2026 state-of-public-NFL-analytics survey (The Analytics Say), PFF,
FTN, SumerSports, rbsdm.com / nfeloapp conventions, Hawk Blogger, and nflverse's own
data-schedule docs. Distilled:

1. **Efficiency first, counts second.** EPA per play / dropback / target, success rate,
   CPOE, RYOE. The app already leads with these; the problem is sample honesty, not
   the metric set.
2. **Context on every number: volume, snaps, and the denominator.** Every serious site
   prints "on 16 att" beside a rate. The app prints it on the Standard board only.
3. **Team strength and matchup previews.** Power ratings in points vs an average team
   (nfelo, HB Power Rankings, Neil Paine) are the most-shared football-analytics
   artifact of the week, and "this offense vs that defense" is how people read an
   upcoming slate.
4. **Contract and draft value.** PFF's WAR-per-dollar, OTC's cap data and Hawk Blogger's
   draft Value Over Expected all answer "is he worth it?". No free app does it on a
   phone for the live season.
5. **Game logs and "this week".** Week-by-week lines and Monday-morning leaders are the
   highest-frequency lookups in the category.
6. **Gaps nobody fills well:** special teams, defensive charting, real-time context.
   (The survey's own conclusion.)

## Hawk Blogger's "value" work, and what to take from it

Brian Nemhauser (hawkblogger.com) runs two value-style tools:

- **HB Power Rankings** (relaunched 2026-09-15, stats.hawkblogger.com): rating = "three
  things you do, minus the same three things you allow, adjusted for who you played".
  Rush EPA, adjusted net yards per attempt (replacing passer rating so sacks count), and
  points scored/allowed. The schedule adjustment starts at zero and reaches full weight
  by Week 10; about a third of the early rating is anchored to last season, fading out
  by Week 9. Output is **points per game against an average team on a neutral field**,
  so +7 vs -3 reads as a 10-point favourite (home field about 2). He reports roughly 70%
  of Week 3 top-ten teams made the playoffs.
- **Hot Draft Time Machine / Value Over Expected**: what a player (or a draft class)
  produced relative to what his draft slot predicted, blending league percentile,
  class percentile, snaps, awards and positional value, with OTC's AAV data behind it.

Both translate cleanly into StatScout's percentile idiom:

- **Team Power Rating** for Teams and Games: HB's structure on data the pipeline
  already builds (team EPA from play-by-play plus posted scores), in points per game,
  with his schedule and prior-season ramps. It gives standings a strength column and
  gives every upcoming game a projected margin.
- **Player Value**: production percentile minus pay percentile within the position
  group, from OTC cap share. "Paid like the 88th percentile, producing like the 97th:
  +9". It is Value Over Expected where the expectation is the contract rather than the
  draft slot, which is the version that moves every week of the season.

# Part 4. Free data sources, verified 2026-09-26

| Source (`nflreadpy`) | Use | Verified |
|---|---|---|
| `load_contracts` (OverTheCap) | APY, cap %, years, draft slot, bio | 2,469 active deals, 1,086 / 1,087 app players |
| `load_snap_counts` | Snap share, defensive qualification | 3,086 rows, 99.7% mapped |
| `load_injuries` | Weekly status and injury | 733 rows, through Week 3 |
| `load_players` | Age, height, weight, college, jersey, experience | 24,830 rows |
| `load_pbp` (already used) | Team EPA for power rating | in pipeline |
| `games` table (already live) | Records, standings, point differential | in pipeline |
| `load_ftn_charting` | Play action, blitz, motion, drops (48h after games) | available, not used yet |
| `load_team_stats` | 138 weekly team columns incl. defense allowed | available, not used yet |
| `load_ff_opportunity` | Expected fantasy points | available, not used yet |

None needs a key or a paid licence. Contract figures are OverTheCap's, redistributed by
nflverse; attribute them in the glossary.

# Part 5. 1.2.2 scope (this release)

Ordered by value to the in-season user. Everything else from 0923 Part 4 stays on the
list for later releases.

**Percentile honesty (fixes P0-1, P0-2, P0-3, N1, N2)**
1. Backend: prorate qualification by the median team's games played (N1).
2. Zero-count counting stats are *unranked*: the value shows, the bar and number do
   not, and they drop out of the overall average.
3. Render `qualified`: small-sample rows dim their value and bar, sort below qualified
   rows, and carry a "small sample" note on the profile.
4. The live season defaults to Qualified, qualification is per metric, and the choice
   persists.
5. Defenders qualify on snap share (at least 25% of defensive snaps).
6. Advanced board subtitles carry the denominator: `QB · 16 att`, `WR · 23 tgt`,
   `LB · 142 snaps`.
7. "Advanced defensive stats publish once Pro-Football-Reference posts them" on
   defensive boards while `pfr_status` is pending.

**Value (Hawk Blogger inspired)**
8. Backend: `player_profiles` (bio, contract, snaps, injury) and `team_ratings` (power
   rating), each written by one additive script. New tables only, so the 1.2.1 app is
   untouched.
9. Profile: bio line (age, size, college, draft, jersey), injury badge, and a Value card
   (cap hit, pay percentile, production percentile, verdict). Free.
10. Stats: "Contract Value" board ranking bargains and overpays. StatScout+.
11. Teams: standings by division with record, point differential and Power Rating, and
    a league-wide Power ranking. Records replace "Sun" under each club.
12. Games: records beside each team, projected margin on upcoming games, and the
    completed week stays up until the day before the next slate.

**Smaller**
13. Trends: 3-week default window (movement from Week 4), and early-season CTA copy that
    sells what is actually behind the blur.
14. "Through Week 3" copy on the Standard board.
15. Profile game log (every game this season, free).

Out of scope for 1.2.2: live scores, notifications and widgets, fantasy points, FTN
charting, share cards, special teams, iPad layout.
