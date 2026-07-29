# StatScout — App Store Preview Frames

> **Audience:** Claude Design
> **Deliverable:** 6–8 marketing preview frames sized for the App Store iPhone 6.7" slot (1290 × 2796), each combining (a) one of the supplied raw simulator screenshots, (b) a headline + sub-copy band, and (c) brand-consistent background art.
> **Source screenshots:** `/Users/jackwallner/baseball/Screenshots/marketing/raw_*.png` (iPhone 17 Pro, 1206 × 2622, light-mode UI).

---

## 1. Product One-Liner

**StatScout** is a fast, mobile-first percentile-rankings app for baseball fans — Statcast-style sliders, daily refresh, no account required. Built for the curious fan who'd otherwise be pinching-and-zooming a desktop site on their phone during a game.

Audience: hardcore baseball viewers, fantasy/dynasty owners, content creators, beat-writers needing a quick-glance metric on a 25-year-old call-up. They already know what xwOBA and Barrel% mean. Marketing copy should be confident, terse, and never explain what a percentile is.

Tone: **declarative, sportsbook-confident, fast.** Not playful, not cute. Short. No emojis. Numbers welcome.

---

## 2. Brand Visual System ("Savant Style")

The app is a deliberate reinterpretation of Baseball Savant's player-page aesthetic — that should carry through into the marketing frames. Treat the previews as **an extension of the in-app design system**, not a separate marketing universe.

### 2.1 Color tokens (use exactly)

| Token | Hex | Usage in frames |
|---|---|---|
| `savantNavy` | `#0E2A50` | Header bar in-app; primary background band for headline copy |
| `savantRed` | `#D71920` | Active highlights, "hot" percentile fill, key word emphasis |
| `pctlCold` | `#1A5BC5` | "Cold" percentile fill, secondary accent only |
| `canvas` | `#F2F4F7` | Light grey page background; safe neutral behind the device frame |
| `surface` | `#FFFFFF` | Card / sheet white |
| `ink` | `#0B1220` | Primary text on light surfaces |
| `inkOnDark` | `#FFFFFF` | Primary text on navy |
| `inkSecondary` | `#5B6470` | Sub-copy, metadata |

**Do not introduce gradients, drop shadows beyond a soft device shadow, or any color outside this set.** Savant is graphic and flat. Marketing frames must feel the same.

### 2.2 Type

- **Headline:** SF Pro Rounded, Heavy, 64–80pt at 1290px width. Letterspacing -0.5. Two lines max, hard line breaks deliberate.
- **Sub-copy:** SF Pro, Semibold, 30–34pt. One line where possible.
- **Numerals in any callouts:** SF Mono / monospaced digits. Savant uses monospace for stat values.
- Headlines may include one **red emphasis word** but never two. Example: `Every player. Every metric. **Always fresh.**`

### 2.3 Layout grid

Two stacked bands per frame:

```
┌─────────────────────────┐
│  NAVY BAND (top ~22%)   │  ← headline + sub-copy, white text
│  Headline goes here     │
│  Optional sub-copy line │
├─────────────────────────┤
│                         │
│   DEVICE FRAME          │  ← screenshot inside a clean
│   (centered, ~76% h)    │     iPhone 15/17 Pro shell,
│                         │     soft 8px shadow, no glare
│                         │
└─────────────────────────┘
```

Alternative layout for the "ranks" / data-density frames: navy band at the **bottom** so the dense table content reads first. Mix both styles across the set to avoid monotony.

Phone device frame: black titanium iPhone (Pro), no Dynamic Island callouts, no MLB stadium reflections, no hand mockups. The status bar in the screenshots already shows 9:41 / 100% battery — do not overpaint it.

---

## 3. Legal / Trademark Constraints (read before designing)

- The app **does not** display MLB logos, team logos, or player headshots. The screenshots reflect this.
- Team identification uses **city only**, with `(AL)` / `(NL)` disambiguation for LA, NY, and Chicago. Do **not** add the team nickname back in any callout or annotation. ("Yordan Alvarez · Houston" — never "Houston Astros".)
- **Do not** add MLB or any team's wordmark, color block matching a specific team palette, or stadium imagery. The `savantNavy` + `savantRed` palette is intentionally generic — keep it that way.
- Avoid the phrase "Major League Baseball" anywhere on the frame. Use "MLB players" only if needed for clarity; "every qualified hitter and pitcher" is preferred.
- The disclaimer baked into the app reads *"Not affiliated with… MLB… team names and abbreviations are used for identification only."* The marketing frames inherit the same posture.

---

## 4. Frame-by-Frame Brief

Each frame uses one raw screenshot, listed in priority order. Frames 1–3 are mandatory for the App Store slot; 4–6 are recommended; 7–8 are stretch if pacing allows.

### Frame 1 — Hero / Leaderboard
- **Asset:** `raw_01_dashboard.png` (Leaders tab, Hitting, sorted by xwOBA)
- **Headline:** `Every qualified hitter.` / `Ranked.`
- **Sub-copy:** `Statcast percentiles, refreshed daily.`
- **Emphasis:** "Ranked." in `savantRed`.
- **Layout:** Navy band top.

### Frame 2 — Player Profile
- **Asset:** `raw_05_profile.png` (Yordan Alvarez, all-100 row near top)
- **Headline:** `Read a player` / `at a glance.`
- **Sub-copy:** `One screen. Every metric that matters.`
- **Emphasis:** "at a glance." in `savantRed`.
- **Layout:** Navy band top. Consider an inline callout circle pointing to the "100 xwOBA" percentile bar with the label `top 1%` in a small red pill — only if it doesn't crowd the screenshot.

### Frame 3 — StatScout Best/Worst
- **Asset:** `raw_03_metrics.png`
- **Headline:** `Who's hottest.` / `Who's not.`
- **Sub-copy:** `Best and worst at every Statcast metric.`
- **Emphasis:** "hottest." in `savantRed`; the second `Who's not.` line stays white for contrast.
- **Layout:** Navy band **bottom** so the two-column data leads.

### Frame 4 — Pitching Leaders
- **Asset:** `raw_07_pitching.png`
- **Headline:** `Pitchers too.`
- **Sub-copy:** `xwOBA against, K%, Whiff% — sortable.`
- **Layout:** Navy band top. Short headline; this frame is for variety, not depth.

### Frame 5 — Box Score / Standard Stats
- **Asset:** `raw_04_boxscore.png`
- **Headline:** `Old-school numbers, new-school speed.`
- **Sub-copy:** `AVG, HR, RBI, OBP, SLG, OPS — all there.`
- **Layout:** Navy band top. Sub-copy can wrap to two lines.

### Frame 6 — Team Roster
- **Asset:** `raw_08_team_detail.png` (New York (AL) roster)
- **Headline:** `Your team.` / `Sorted.`
- **Sub-copy:** `Every roster, hitting through running.`
- **Emphasis:** "Sorted." in `savantRed`.
- **Layout:** Navy band top.

### Frame 7 — Teams Index (optional)
- **Asset:** `raw_02_teams.png`
- **Headline:** `30 clubs.` / `One tap.`
- **Sub-copy:** `Sort by team xwOBA in or out of the box.`
- **Layout:** Navy band top.

### Frame 8 — Pro Upgrade (optional)
- **Asset:** `raw_06_paywall.png`
- **Headline:** `Unlock every season` / `back to 2000.`
- **Sub-copy:** `Compare any two players, side-by-side.`
- **Emphasis:** "every season" in `savantRed`.
- **Layout:** This screenshot already contains its own marketing copy. **Do not add a second headline band.** Instead, render the screenshot inside the device frame on a clean `canvas` background with a small top-aligned StatScout wordmark in `savantNavy` — that's it.

---

## 5. Output Specifications

- **Dimensions:** 1290 × 2796 px, PNG, sRGB.
- **Device shell:** iPhone 15/16/17 Pro (black titanium). Single soft shadow, max 12px blur, 30% opacity, no glow.
- **Filename convention:** `appstore_preview_<NN>_<slug>.png` (e.g., `appstore_preview_01_leaderboard.png`).
- **Safe zone:** Keep all headline text ≥ 64px from any edge. App Store crops 24px on some surfaces.
- **Export location:** `/Users/jackwallner/baseball/Screenshots/appstore/` (overwrite existing files; current set is stale).
- **Provide both:** (a) the 8 individual PNGs, (b) one composite contact-sheet PNG showing all frames at 25% scale for quick review.

---

## 6. What NOT to do

- ❌ No MLB / team logos, wordmarks, or stadium imagery
- ❌ No player headshots, signatures, or jersey photography
- ❌ No emojis, sparkles, motion blur, "BOOM" effects, or sportsbook-promo styling
- ❌ No multiple emphasis colors per frame — one red word, not two
- ❌ No gradients beyond the navy header (which is a solid color)
- ❌ No fake/marketing UI — every pixel inside the device frame must come from the supplied screenshot, unchanged. Crop only; do not retouch text, numbers, or layout inside the screen.
- ❌ No "Available on the App Store" badge inside the frame — the App Store places that itself
- ❌ Don't rewrite headlines; if a line reads awkwardly, flag it and propose an alternative rather than guessing

---

## 7. Reference Files

| File | What it is |
|---|---|
| `handoff/STATSCOUT_SAVANT_HANDOFF.md` | In-app design system spec — palette, type, module definitions |
| `handoff/SAVANT_PLAYER_PAGE_REFERENCE.html` | High-fidelity HTML mock of the player-page aesthetic |
| `design-brief-for-claude-design.md` | Broader product/design context |
| `Screenshots/marketing/raw_*.png` | Source screenshots for the 8 frames |
