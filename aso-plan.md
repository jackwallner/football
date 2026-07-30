# aso-plan.md — Football Next: StatScout ASO Plan

> Written 2026-07-29. App: **Football Next: StatScout** (ASC ID `6792930447`, bundle
> `com.jackwallner.football`, repo `~/football`). Astro tracking app id `119` (temporary;
> Astro has not indexed the real listing yet). Methodology ported from `~/baseball/aso-plan.md`
> and `~/ios/archive/aso/2026-05/astro-global-aso-go-2026.md`.

---

## 0. TL;DR

- **Positioning:** NFL advanced-stat percentile ranks for QB/RB/WR/TE/DEF, plus seasons back to
  2000 and All-Time career percentiles. NOT fantasy projections, NOT live scores, NOT betting.
- **Niche is at the pop-5 floor** almost everywhere, same shape as baseball. Real traffic exists
  only at `football` 71, `nfl` 70, `fantasy` 62, `fantasy football` 57, `nfl stats` 28,
  `football stats` 24, `quarterback` 22, `gridiron` 18.
- **`epa` is a homograph wall, not a metric term.** Pop 44 looks great until you read the SERP:
  12/12 results are Environmental Protection Agency and HVAC EPA-608 exam prep apps. Removed
  from the keyword field.
- **US `football` intent is majority soccer.** NFL intent has to be captured through `nfl`
  tokens; `football analytics` is winnable precisely because only 0-rating soccer AI-betting
  apps hold it.
- **Exact phrase match in the app name is the real lever here**, not popularity. "Iron: Football
  Stats" ranks #4 on `football stats` (pop 24, diff 78) on the strength of its name and a single
  rating.

---

## 1. Competitor tiers (US)

| Tier | Apps |
|---|---|
| **WALL** | FotMob (168k★), OneFootball (211k★), Sofascore (76k★), LiveScore (48k★) — all soccer, all sitting on `football stats` |
| **NFL WALL** | NFL app, ESPN, Yahoo Fantasy, Sleeper, Superfan Sports (8.5k★) |
| **WINNABLE PEERS** | Iron: Football Stats (1★), Next Play Stats (0★), SportsQuant (0★), Gridiron Oracle (1★), BlueChip Fantasy (12★) |
| **ADJACENT** | StatMuse (72★), HOF Sports Stats (10k★), Props.cash (7.6k★) — multi-sport research/props |

The entire `football analytics` SERP is 0-1 rating apps. That is the crack in the wall.

---

## 2. US metadata (staged on ASC draft 1.0, 2026-07-29)

| Field | Value | Count |
|---|---|---:|
| **Name** | `Football Next: StatScout` | 24/30 |
| **Subtitle** | `Advanced NFL Stats & Analytics` | 30/30 |
| **Keywords** | `gen,epa,cpoe,yac,statistics,compare,player,passing,rushing,receiving,defense,qb,fantasy,teams,trends` | 100/100 |

### What changed and why

| OUT | IN | Why |
|---|---|---|
| `Football Next Play: StatScout` | `Football Next: StatScout` | "Play" carried no search weight; frees 5 chars and keeps the Next Gen Stats association |
| `nfl`, `football` | — | Already indexed via name + subtitle; Apple indexes name + subtitle + keywords together, so duplicating them wasted ~11 chars |
| `nextgen` | `gen` | Apple tokenizes on spaces. `nextgen` is a distinct token that never matches the query "next gen stats"; `gen` + name's "Next" + subtitle's "Stats" does |
| `percentile` | `statistics` | `percentile` is pop 5 (floor) — essentially nobody searches it. `statistics` is pop **20**, diff 21, and Apple does not stem `stats` (subtitle) into `statistics` |
| — | `compare`, `player`, `teams`, `trends` | `player comparison` diff 5, `compare players` diff 17, `football team stats` diff 5, `football trends` diff 40; all shipped features |

### On `epa` (kept, deliberately)

Standalone `epa` is worthless: pop 44, SERP 12/12 Environmental Protection Agency and HVAC
EPA-608 exam prep. But Apple builds combinations across fields, so the token costs 4 characters
and buys `football epa` (**diff 15**) and `epa per play` (diff 23) against the name's "Football".
Those are low-volume, low-competition, and exactly the analytics-literate user this app wants.
**Budget it as a long-tail combo token, not as a pop-44 head.** Do not expect standalone `epa`
traffic and do not spend more characters defending it.

### Cross-field combinations this buys

`football stats` · `football analytics` · `nfl stats` · `nfl analytics` · `advanced football
stats` · `next gen stats` · `nfl percentile` · `football rankings` · `qb stats` · `passing
stats` · `rushing stats` · `defense rankings` · `player comparison` · `football team stats` ·
`fantasy football stats`

**Never field-slot:** `epa`, `dfs`, `snap share`, `football app`, `american football`, `sports
analytics`, `pro football` — homograph or authority walls despite popularity.

### Name variant not taken

`Football Next: NFL Stats` (24/30) would put the higher-traffic exact phrase in the name, which
is what carries Iron: Football Stats to #4. Rejected to keep the StatScout brand string in the
title. Revisit if `football stats` rank stalls past 200.

---

## 3. Astro state (2026-07-29)

**US:** 84 keywords tracked on app id `119`.

| Tag | Count | Keywords |
|---|---:|---|
| `deployed` | 10 | percentile, gen, cpoe, yac, compare players, player comparison, qb stats, passing stats, rushing stats, defense rankings |
| `target` | 16 | football analytics, nfl analytics, advanced football stats, football percentiles, nfl percentiles, next gen stats, football stats app, football metrics, football research, football stat tracker, football compare, football gen stats, nfl stats, football stats, football efficiency, nfl percentile |
| `wall` | 18 | epa, dfs, snap share, football app, nfl team stats, football players, nfl players, running back, target share, fantasy football tools, dynasty fantasy football, nfl next gen, american football, sports analytics, pro football, nfl rankings, football tracker, nfl stats app |

Astro cannot yet resolve real App Store ID `6792930447` (the listing has never been released).
Re-point `scripts/.astro-app.json` and migrate tracking once it indexes.

---

## 4. Growth lever

Every app holding `football analytics` and the lower slots of `football stats` has 0-1 ratings.
Authority, not keyword churn, decides this niche. Prioritize the in-app review funnel
(`StatScout/Services/ReviewPromptTracker.swift`) over further keyword edits.

The differentiator no competitor advertises: **seasons back to 2000 plus All-Time career
percentiles**. It is currently underclaimed in the listing copy.

---

## 5. Rollout

1. **Staged now:** en-US on ASC draft 1.0 (name, subtitle, keywords, promo, description,
   8 screenshots at 1320x2868).
2. **On approval:** 50-locale pass using the baseball methodology (native keywords, name/subtitle
   dedupe, `aso-apply-locale-optimizations.py`, `astro-sync-all-stores.sh`,
   `asc-finish-missed.sh`). See `docs/localization-aso.md`.
3. **go refine:** 7-14 days after the listing is live and rank data exists.
