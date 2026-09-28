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
- **`epa` is a homograph, so treat it as a combo token only.** Pop 44 looks great until you read
  the SERP: 12/12 results are Environmental Protection Agency and HVAC EPA-608 exam prep apps.
  Kept in the field at 4 characters purely for `football epa` (diff 15) and `epa per play`
  (diff 23), never as a head term.
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

**Never field-slot as heads:** `dfs`, `snap share`, `football app`, `american football`, `sports
analytics`, `pro football`, `nfl statistics` — homograph or authority walls despite popularity.

### Name variant not taken

`Football Next: NFL Stats` (24/30) would put the higher-traffic exact phrase in the name, which
is what carries Iron: Football Stats to #4. Rejected to keep the StatScout brand string in the
title. Revisit if `football stats` rank stalls past 200.

---

## 3. Astro state (2026-07-29)

**US:** 98 keywords tracked on app id `119`.

| Tag | Count | Keywords |
|---|---:|---|
| `deployed` | 13 | gen, epa, statistics, cpoe, yac, football trends, compare players, player comparison, qb stats, passing stats, rushing stats, defense rankings, percentile |
| `target` | 19 | football epa, epa per play, football statistics, football analytics, nfl analytics, advanced football stats, football percentiles, nfl percentiles, next gen stats, football stats app, football metrics, football research, football stat tracker, football compare, football gen stats, nfl stats, football stats, football efficiency, nfl percentile |
| `wall` | 23 | dfs, nfl statistics, stats, stat, stats app, player stats, sports statistics, snap share, football app, nfl team stats, football players, nfl players, running back, target share, fantasy football tools, dynasty fantasy football, nfl next gen, american football, sports analytics, pro football, nfl rankings, football tracker, nfl stats app |

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

## 5. Localization: when to translate, when to stay in English

### What the baseball 50-locale pass actually returned

Measured 2026-07-29 against the live Baseball Savvy StatScout listing:

| Store | Result |
|---|---|
| `de` | **#1** `statcast perzentile`, **#1** `mlb statcast perzentile`, #4 `statcast`, #99 `mlb statcast` — the localized German long-tails do index and rank |
| `jp` | **#1** `mlb statcast percentiles`, #2, #4, #5 on statcast phrases — and these are *English* tokens, because "Statcast" has no Japanese translation |
| `mx` | `béisbol` (pop **32**, the one term with real traffic) → **rank 1000**. Not won. |

The pattern: localization reliably wins pop-5 long-tails and reliably loses anything with actual
search volume, because volume terms are decided by authority, not by translation. It costs
almost nothing, so it is worth doing, but it should be budgeted as free optionality rather than
as a growth channel.

**Screenshots do not need localizing.** Baseball runs 50 live localizations with screenshots in
`en-US` only; ASC falls back to the primary language. The 50-locale pass is metadata-only.

### Tiering for an NFL app

NFL demand is far more concentrated than baseball's. Spend effort accordingly.

| Tier | Storefronts | Treatment |
|---|---|---|
| **1 — English, real NFL demand** | `en-US`, `en-GB`, `en-CA`, `en-AU` | English copy, but a **different keyword field per storefront**. Free extra surface. In GB/AU, "football" means soccer even harder than in the US, so `american football` (pop 9) and `nfl` carry the intent there while `football` is dead weight |
| **2 — Real NFL following, non-English** | `de-DE`, `es-MX`, `pt-BR`, `ja`, `es-ES` | Hand-write native keywords. Germany is the largest NFL market outside North America; Mexico, Brazil and Spain all host regular-season games |
| **3 — Everything else** | ~40 storefronts | Keep the **English** name/subtitle/description, localize the **keyword field only**. NFL terminology does not translate, and a machine-translated description converts worse than clean English for an audience already reading English football content |

### The es-MX lever

The **US storefront indexes English (U.S.) *and* Spanish (Mexico)**. `es-MX` is therefore not a
Mexico play, it is a second 100-char keyword field aimed at the US market. Give it a genuinely
different Spanish set (`estadisticas`, `futbol americano`, `mariscal de campo`, `temporada`)
rather than a translation of the English one. Same mechanic applies to `fr-CA` in the Canadian
storefront. Verify with rank tracking after launch rather than assuming.

### Terms that must stay English in every locale

`NFL` · `EPA` · `CPOE` · `YAC` · `QB` · `Next Gen` · `StatScout`. Translating these loses the
query, and every serious NFL fan searches them in English regardless of storefront.

### What shipped (2026-07-29, all 50 on ASC draft 1.0)

Measured pop/diff per storefront before writing each field. `nfl` is the head term in every
foreign store (GB 58, CA 61, AU 59, MX 62, BR 51, DE 58, JP 40), while `american football` sits
at the pop-5 floor in GB — so NFL carries the intent abroad, not the disambiguator.

| Locale | Name | Subtitle |
|---|---|---|
| `en-US` | Football Next: StatScout | Advanced NFL Stats & Analytics |
| `en-GB` / `en-AU` | Football Next: StatScout | American Football Statistics |
| `en-CA` | Football Next: StatScout | Advanced NFL Stats & Analytics |
| `de-DE` | NFL Statistiken: StatScout | American Football Perzentile |
| `es-MX` / `es-ES` | NFL Estadísticas: StatScout | Fútbol Americano Avanzado |
| `pt-BR` | NFL Estatísticas: StatScout | Futebol Americano Avançado |
| `ja` | アメフト統計: StatScout | NFL選手のスタッツと分析 |
| other 41 | Football Next: StatScout | Advanced NFL Stats & Analytics |

`en-GB`/`en-AU` move `statistics` (pop 20, diff 17 GB / 13 AU) into the subtitle, which frees
their keyword fields to carry `nfl` and `gridiron` (diff 15 GB, diff 7 CA). The five Tier-2
locales put the exact searched phrase in the **name** — German users type "nfl statistiken" —
because in-name exact match is the lever this niche rewards.

**Dedupe traps found in the first draft of these fields:**

- Every Tier-3 field opened with `nfl,` while the subtitle already contains NFL. Apple indexes
  name + subtitle + keywords as one set, so that was 4 wasted characters in 40 locales.
- French, Italian and Dutch repeated `football` from the name inside "football américain",
  "football americano", "american football". Apple tokenizes on **spaces as well as commas**, so
  the phrase produced a duplicate token. Cost 9 characters each.

**Word-sense errors caught in review of the non-Latin fields** (worth re-checking on any future
locale pass, since a dictionary translation silently targets the wrong sport):

| Locale | Was | Problem | Now |
|---|---|---|---|
| `ta-IN` | அமெரிக்க கால்பந்து | கால்பந்து is **soccer** — read as "American soccer" | அமெரிக்க ஃபுட்பால் |
| `ml-IN` | സ്ഥിതിവിവരക്കണക്കുകൾ | census/bureaucratic register for "statistics", ~20 chars, never typed by a fan | സ്റ്റാറ്റ്സ് |
| `zh-Hans` / `zh-Hant` | 橄榄球 / 橄欖球 | ambiguous with rugby | 美式橄榄球 / 美式橄欖球 |
| `vi` | bóng bầu dục | reads as rugby in Vietnamese media | bóng bầu dục Mỹ |
| `hi` | रक्षा | national-security "defense", not the sports unit | डिफेंस |
| `ar-SA` | لاعبون | literary nominative plural; not the typed form | لاعبين |
| `ja` | 選手 | duplicated the subtitle; レシーブ is a volleyball term | キャッチ, ランキング |

---

## 6. Rollout

1. **Staged:** all 50 locales on ASC draft 1.0 (name, subtitle, keywords, promo, description),
   8 screenshots at 1320x2868 in `en-US` only, StatScout+ IAPs at $1.99 / $9.99 / $19.99.
2. **Pending:** Jack's approval, then attach a build and submit.
3. **go refine:** 7-14 days after the listing is live and rank data exists. Only then is
   `astro-optimize.sh` meaningful, since it tunes against real ranks.

The app UI ships **English only** (no `.lproj`, no `.xcstrings`). Localized store listings for an
English-only app are permitted and standard, and baseball runs the same way, but it is a
deliberate tradeoff rather than an oversight.
