# What Is EPA Per Play in Football? EPA and CPOE Explained

> EPA per play measures how much each play changes a team's expected points. How it works, what CPOE adds, and what good looks like for NFL quarterbacks.

Web page: https://jackwallner.com/ios/football/what-is-epa-per-play.html

## What is EPA per play in football? (And what CPOE adds)

By Jack Wallner. Last updated October 28, 2026.

EPA per play is the average number of points a play adds to its team's expected score on the drive. A positive number means the play left the offense better off than before the snap, and a negative number means worse. CPOE, completion percentage over expected, is the companion stat that judges a passer's accuracy against how hard each throw was.

## How does EPA work?

EPA is the change in the offense's expected points from before a play to after it. Before every snap, a model estimates how many points the offense will score on average from that situation, using down, distance, field position, time and similar inputs. That number is the play's expected points, or EP. After the play, the model scores the new situation. EPA is the difference: expected points after, minus expected points before. The nflverse data dictionary describes it as the expected points added by the team with the ball for the given play.

Two real plays from Week 1 of the 2025 season show the range. On 3rd and 12 from the Saints' 45, Arizona was worth 1.60 expected points. Kyler Murray completed a 25-yard pass to Trey McBride to the Saints' 20 and the play was worth +2.79 EPA. On the other end, Carolina's Bryce Young threw an interception on 2nd and 1 with 1.22 expected points on the board, and the play was worth -4.89 EPA.

Because a first down, a touchdown or a turnover is priced in points, EPA counts what yards alone miss. A 5-yard gain on 3rd and 3 can be worth more than a 12-yard gain on 3rd and 15.

## What is the difference between EPA per play, per dropback and per rush?

Each divides total EPA by a different kind of opportunity, because total EPA mostly rewards volume. The common versions are EPA per play for a team, EPA per dropback for a quarterback, and EPA per rush or per target for runners and receivers. StatScout computes a quarterback's EPA per play as passing EPA divided by pass attempts plus sacks, so a sack that loses points counts against him.

By construction the league average play is close to zero. Across all regular-season pass plays in 2025, the nflverse play-by-play data averages +0.02 EPA per play. Rushing plays average -0.01.

## What is CPOE?

CPOE, completion percentage over expected, is a stat that measures a passer's accuracy against how hard each throw was. Every pass gets a completion probability, called cp in the data, based on factors such as how far downfield the throw went. For a single pass, CPOE is 1 minus cp if the pass was completed, or 0 minus cp if it was not. Averaged over a season, it shows how far above or below expectation a passer's completion percentage was. Murray's 45-yard completion to Marvin Harrison Jr. in that same game had a 30 percent completion probability, so it scored +70 percentage points on that one throw. StatScout shows CPOE in percentage points.

## What is a good EPA per play for a quarterback?

About +0.05 per dropback was the 2025 median, and about +0.20 was the 90th percentile. I calculated the 2025 regular-season spread from the nflverse player stats file for the 36 quarterbacks with at least 200 pass attempts.

| Metric (2025) | Weak (10th pct.) | Typical (median) | Strong (90th pct.) | Best |
| --- | --- | --- | --- | --- |
| EPA per play | -0.12 | +0.05 | +0.20 | +0.31 (Drake Maye) |
| CPOE | -3.2 | +1.6 | +4.9 | +10.8 (Drake Maye) |

EPA per play is passing EPA divided by attempts plus sacks. Percentiles are interpolated from the downloaded rows.

The gap between a weak and a strong starter is roughly 0.3 points per play. Over 600 plays, that is a difference of about 190 expected points across a season.

## What is EPA + CPOE?

EPA + CPOE is a combined quarterback rating that blends EPA per play with CPOE, because the pair predicts next season's play better than either alone. Ben Baldwin of rbsdm.com built it, and nflfastR later shipped it as DAKOTA. It is a fitted model trained to predict next year's adjusted EPA per play, not a fixed 50/50 sum, so it reads on the EPA per play scale. The nflverse data no longer includes the column, so the practical way to read it today is to put both numbers side by side.

| 2025 regular season | EPA per dropback | CPOE |
| --- | --- | --- |
| Drake Maye (NE) | +0.308 | +10.8 |
| Matthew Stafford (LA) | +0.243 | +1.5 |
| Jordan Love (GB) | +0.240 | +5.5 |
| Brock Purdy (SF) | +0.214 | +7.2 |
| Dak Prescott (DAL) | +0.181 | +2.2 |
| League (all dropbacks) | +0.043 | +0.5 |
| Cam Ward (TEN) | -0.183 | -2.9 |
| J.J. McCarthy (MIN) | -0.200 | -5.7 |
| Shedeur Sanders (CLE) | -0.252 | -5.0 |

Top five and bottom three of the 36 quarterbacks with at least 200 attempts, from the nflverse 2025 player stats and play-by-play files. EPA per dropback is passing EPA divided by attempts plus sacks.

The two numbers disagree more often than you would think. Stafford ranked second in EPA with a CPOE near average, which points to big plays and good protection more than pinpoint accuracy. Purdy and Love were strong on both, the profile the composite rewards most. Through week 7 of 2026, the EPA leaders are Jared Goff (+0.317), Caleb Williams (+0.307, with a +9.0 CPOE), Brock Purdy and Josh Allen (both +0.290), and the league average is up to +0.063 per dropback.

## What doesn't EPA tell you?

It does not separate the quarterback from the line, receivers and play call. EPA credits the quarterback for everything on a pass play, including the line, the receivers and the play call. It is a noisy measure over a few games. Treat it as a strong starting point, and pair it with CPOE and the game film or team context before drawing conclusions about one player.

## How does StatScout show EPA?

[Pro Football: EPA StatScout](https://jackwallner.com/ios/football/) is an independent iPhone app, not affiliated with the NFL, that ranks every player on advanced percentiles including EPA per play and CPOE, next to the familiar totals. The data comes from the open nflverse archive and updates through game day, so you can see where a quarterback stands among his peers without looking up the numbers.

## Frequently asked questions

### What is EPA per play in football?

EPA per play is the average expected points added on each play. Each play is scored by the change in the offense's expected points from before the snap to after it, then averaged. Positive is good for the offense.

### What is a good EPA per play for a quarterback?

In 2025, the median quarterback with at least 200 attempts was about +0.05 EPA per dropback. The 90th percentile was about +0.20, and the best was Drake Maye at +0.31.

### What is CPOE?

CPOE is completion percentage over expected. It compares a passer's completions to the completion probability of each throw, so a hard throw that is caught counts for more than an easy one. It is measured in percentage points.

### What is EPA + CPOE?

It is a combined quarterback rating from Ben Baldwin (rbsdm.com), later called DAKOTA in nflfastR, that blends EPA per play and CPOE to predict next season's EPA per play. In 2025, Drake Maye led both parts at +0.31 EPA per dropback and +10.8 CPOE.

### Is EPA better than passer rating?

EPA accounts for down, distance, field position and turnovers, which passer rating ignores, so most analysts prefer it for efficiency. It is still noisy over small samples.

### Where does EPA data come from?

The free nflverse project publishes play-by-play data with an EPA value on every play, built from the nflfastR models. StatScout uses that data.

Related: [What are expected goals (xG) in hockey? (And GSAx for goalies)](https://jackwallner.com/ios/hockey/what-is-xg-in-hockey.html) and [True shooting percentage explained (and usage rate)](https://jackwallner.com/ios/basketball/true-shooting-percentage-explained.html).

## Sources

1. [nflreadr data dictionary: play-by-play (EP, EPA, cp, cpoe)](https://nflreadr.nflverse.com/articles/dictionary_pbp.html)
2. [nflreadr data dictionary: player stats](https://nflreadr.nflverse.com/articles/dictionary_player_stats.html)
3. [nflverse-data releases (play-by-play and player stats files)](https://github.com/nflverse/nflverse-data)
4. [nflfastR on GitHub](https://github.com/nflverse/nflfastR)
5. [nflfastR release notes (dakota, the EPA + CPOE composite)](https://github.com/nflverse/nflfastR/blob/master/NEWS.md)
