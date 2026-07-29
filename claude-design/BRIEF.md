# Gridiron StatScout App Store strategy

**Audience:** A new Claude Design agent with no repository context
**Primary deliverable:** Eight App Store screenshot frames based on the supplied real UI captures
**Secondary deliverable:** A coherent metadata and optional video strategy

## 1. Product and audience

Gridiron StatScout is a fast NFL advanced-statistics app for people who want to rank players, understand percentiles, find recent movement, and compare players or seasons without using a desktop analytics site.

The primary audience is:

- Fantasy and dynasty players evaluating performance beyond totals
- NFL fans comparing quarterbacks and skill players
- Writers and creators who need a fast reference
- Analytics-aware users who already recognize EPA, CPOE, YAC, target share, and percentile language

The marketing voice is direct, informed, and compact. Lead with proof. Avoid hype, betting outcomes, projections, and claims that the app can predict games.

The conversion story is:

1. Rank the league beyond box-score totals.
2. Understand one player immediately.
3. See who is changing right now.
4. Compare players, teams, and seasons.

The first three frames carry most of that story and must work at App Store search-result thumbnail size.

## 2. Metadata strategy

### Recommended searchable fields

| Field | Recommended copy | Count |
|---|---|---:|
| Name | `Gridiron StatScout: NFL Stats` | 29/30 |
| Subtitle | `Advanced Football Analytics` | 27/30 |
| Keywords | `fantasy,quarterback,player,rankings,percentile,EPA,CPOE,passing,rushing,receiving,defense,compare` | 97/100 |

This fixes the current brand mismatch in `Football Next Play: StatScout`, avoids repeating name and subtitle terms in the keyword field, and puts the highest-intent category phrase in the name.

Use `NFL` descriptively and keep the non-affiliation language in the description. Do not use NFL, team, or NFLPA logos anywhere.

### Recommended promotional text for July and preseason

`Training camp is here. Rank every quarterback, back, receiver, tight end, and defender with nightly advanced stats and live comparison tools.`

Count: 141/170.

Replace the seasonal opening after Week 1. Promotional text can change without a new binary, so use it for the current moment rather than permanent positioning.

### Description structure

The opening should mirror the first three screenshot frames:

> See who leads beyond the box score.
> Rank NFL players by advanced metrics, read full percentile profiles, and track who is heating up or cooling off.
>
> Gridiron StatScout puts EPA per play, CPOE, rushing efficiency, receiving performance, defensive production, and familiar box-score totals in one fast iPhone app.

Then use these short sections:

1. **Advanced leaderboards:** QB, RB, WR, TE, and defense, sortable by metric.
2. **Player profiles:** Advanced percentiles, standard totals, and recent game logs.
3. **Trends:** League-wide recent movement over 3, 5, or 8 weeks.
4. **Compare:** Any two players or any two available seasons side by side.
5. **Teams:** Advanced stats, standard totals, and rosters for all 32 teams.
6. **Nightly updates:** Current-season data refreshed through the latest completed games.
7. **Privacy:** No account, ads, or third-party tracking.
8. **StatScout+:** Trends, comparisons, team scouting, and historical analysis.
9. **Subscription disclosure, privacy URL, terms URL, and non-affiliation disclaimer.**

Before submission, correct these current metadata problems:

- Replace `Gridiron Pro` with the in-app product name `StatScout+`.
- Remove `Next Gen-style`. It creates unnecessary brand confusion and says less than naming the actual metrics.
- Remove the claim about network requests for player images. The app does not use player photos.
- Replace `back to 2015` with `historical seasons from 2020` unless the shipping dataset is expanded and verified before submission.
- Confirm live App Store product prices and trial eligibility before hardcoding any amount in the description. The simulator products are not the source of truth.

Keep this disclaimer verbatim at the end:

> Gridiron StatScout is not affiliated with, authorized, endorsed by, or sponsored by the National Football League (NFL), any NFL team, or the NFLPA. Player names, team names, and statistics are used for identification and informational purposes only.

## 3. Brand system

The frames must feel like the app, not generic football advertising.

### Core colors

| Token | Hex | Frame use |
|---|---|---|
| `midnight` | `#091412` | Dark frame background |
| `canvas` | `#F0EDE3` | Light frame background |
| `surface` | `#FCFAF0` | Device and small light surfaces |
| `ink` | `#121714` | Headline on light frames |
| `inkOnDark` | `#FCFAF0` | Headline on dark frames |
| `turf` | `#145C33` | Emphasis on light frames |
| `gold` | `#D6A130` | Emphasis on dark frames |
| `leather` | `#7A3B1A` | Secondary accent only |
| `hairline` | `#B8B5A8` | Fine outlines only |

Performance colors already exist inside the screenshots:

- High: `#057533`
- Mid: `#66614F`
- Low: `#B33314`

Do not recolor them.

### Typography

- SF Pro Display or SF Pro, Bold or Heavy, for headlines
- SF Pro, Semibold, for supporting copy
- SF Mono only for any added numeric annotation
- Tight but natural tracking
- Two headline lines maximum
- One emphasis phrase maximum per headline

Do not use varsity, slab-serif, stencil, jersey-number, or sports-broadcast fonts. The visual identity is editorial analytics, not tailgate merchandise.

### Visual treatment

- Alternate solid `midnight` and `canvas` backgrounds.
- Use `gold` emphasis on `midnight`.
- Use `turf` emphasis on `canvas`.
- Use a black or graphite device shell with one restrained shadow.
- Use the supplied app icon only on Frame 1.
- No gradients, field textures, chalk marks, yard lines, stadium lights, player photography, jerseys, helmets, or football clip art.
- No annotation arrows, magnifiers, or fake metric callouts. The real UI is already the evidence.

The app icon is at:

`../StatScout/Assets.xcassets/AppIcon.appiconset/AppIcon.png`

## 4. Frame order and copy

Use this exact order. The `StatScout+` overline is important because those frames show paid capabilities.

### Frame 1: Advanced quarterback leaderboard

- Raw: `raw_01_qb_leaders.png`
- Background: `midnight`
- Overline: app icon + `GRIDIRON STATSCOUT`
- Headline: `Quarterbacks.` / `Ranked beyond the box score.`
- Emphasis: `Beyond the box score.` in `gold`
- Subcopy: `EPA, CPOE, and more, refreshed nightly.`
- Purpose: Immediate category clarity and dense proof

### Frame 2: Player profile

- Raw: `raw_02_player_profile.png`
- Background: `canvas`
- Overline: `PLAYER PROFILE`
- Headline: `Read a player` / `at a glance.`
- Emphasis: `at a glance.` in `turf`
- Subcopy: `Every percentile that matters, on one screen.`
- Purpose: Explain the visual percentile payoff

### Frame 3: Trends

- Raw: `raw_03_trends.png`
- Background: `midnight`
- Overline: `STATSCOUT+`
- Headline: `Who's heating up` / `right now.`
- Emphasis: `right now.` in `gold`
- Subcopy: `League-wide change across recent weeks.`
- Purpose: Show freshness and a paid differentiator

### Frame 4: Player comparison

- Raw: `raw_04_player_compare.png`
- Background: `canvas`
- Overline: `STATSCOUT+`
- Headline: `Settle the debate.` / `Side by side.`
- Emphasis: `Side by side.` in `turf`
- Subcopy: `Any two players, any two seasons.`
- Purpose: High-intent fantasy and fan use case

### Frame 5: Team profile

- Raw: `raw_05_team_profile.png`
- Background: `midnight`
- Overline: `STATSCOUT+`
- Headline: `Your team.` / `Every angle.`
- Emphasis: `Every angle.` in `gold`
- Subcopy: `Advanced stats, standard totals, and roster.`
- Purpose: Expand the product beyond individual players

### Frame 6: Year comparison

- Raw: `raw_06_year_compare.png`
- Background: `canvas`
- Overline: `STATSCOUT+`
- Headline: `Compare seasons.` / `Year by year.`
- Emphasis: `Year by year.` in `turf`
- Subcopy: `Put any two available seasons side by side.`
- Purpose: Demonstrate historical value without overstating coverage

### Frame 7: Standard passing leaders

- Raw: `raw_07_standard_passing.png`
- Background: `midnight`
- Overline: `STANDARD STATS`
- Headline: `Box-score stats.` / `Sorted.`
- Emphasis: `Sorted.` in `gold`
- Subcopy: `Yards, touchdowns, picks, sacks, and more.`
- Purpose: Reassure users that familiar totals are included

### Frame 8: Teams index

- Raw: `raw_08_teams_index.png`
- Background: `canvas`
- Overline: `STATSCOUT+`
- Headline: `32 teams.` / `One tap.`
- Emphasis: `One tap.` in `turf`
- Subcopy: `Jump from the league to any team.`
- Purpose: Close with breadth and visual color

## 5. Raw capture context

The raw images are deliberate marketing states:

| Raw | State shown |
|---|---|
| `raw_01_qb_leaders.png` | 2025 Regular, qualified QBs, EPA/Play descending |
| `raw_02_player_profile.png` | Drake Maye, high-value percentile bars visible |
| `raw_03_trends.png` | 2025 QB EPA/Play, Heating Up, five-week window |
| `raw_04_player_compare.png` | Patrick Mahomes vs Josh Allen |
| `raw_05_team_profile.png` | Kansas City Chiefs advanced offense percentiles |
| `raw_06_year_compare.png` | Patrick Mahomes, 2025 vs 2024 |
| `raw_07_standard_passing.png` | 2025 passing yards descending |
| `raw_08_teams_index.png` | All 32 teams grouped by conference and division |

Do not replace names or numbers with fabricated values. Do not remove the status bar, floating tab bar, team names, or team abbreviations.

## 6. App Preview video recommendation

Do not launch with an App Preview video by default. Apple places a video before screenshots, so a merely adequate video would displace the strongest conversion frame.

Phase 1 should use the eight still frames. Add one video only after the still set is approved and if the motion version is clearly stronger.

If produced, use:

- One portrait video at 886×1920
- 18 to 22 seconds within Apple's 15 to 30 second limit
- H.264, 10 to 12 Mbps, progressive, no more than 30 fps
- Actual in-app interaction only
- A strong poster frame that visually matches Frame 1
- No narration required, no unlicensed music, no simulated push notifications

Suggested sequence:

1. 0:00 to 0:03, leaderboard sorted by EPA/Play
2. 0:03 to 0:07, tap Drake Maye and reveal percentile profile
3. 0:07 to 0:11, switch to Trends
4. 0:11 to 0:16, show Mahomes vs Allen comparison
5. 0:16 to 0:20, show the 32-team index and open one team profile

Apple's current references:

- Screenshot specifications: `https://developer.apple.com/help/app-store-connect/reference/app-information/screenshot-specifications/`
- App Preview specifications: `https://developer.apple.com/help/app-store-connect/reference/app-information/app-preview-specifications`

## 7. Legal and accuracy constraints

- Do not add the NFL shield, team logos, team wordmarks, NFLPA marks, player photos, jerseys, or stadium imagery.
- Team names and abbreviations may remain where they appear in the actual UI.
- Do not call the product official, authorized, exclusive, predictive, or betting-grade.
- Do not imply live play-by-play. The data refresh is nightly.
- Do not advertise a historical start year earlier than the verified shipping dataset.
- Do not hide the paid nature of Trends, Compare, Teams, or historical analysis. Use the `STATSCOUT+` overline.
- Do not alter the real screenshots, including awkward or imperfect UI details.

## 8. Acceptance criteria

The package is complete when:

- All eight 1320×2868 frames use the supplied raws and fixed geometry.
- First-frame text and product remain legible in a 25% contact sheet.
- Paid capabilities carry the `STATSCOUT+` overline.
- Every output is sRGB PNG with no alpha channel.
- No football league, team, or player imagery has been introduced.
- Final filenames and order match `SCREENSHOT_POSITIONING_SPEC.md`.
- The output remains under `claude-design/output/` until explicitly approved for App Store Connect.
