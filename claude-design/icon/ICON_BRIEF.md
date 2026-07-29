# Gridiron StatScout — App Icon Brief

**Audience:** a Claude Design agent with no repository context
**Deliverable:** one iOS app icon, 1024×1024, plus small-size proofs
**Status of current icon:** shipping, directionally correct, visually amateur. Replace it, do not redraw it.

---

## 1. What the app is

Gridiron StatScout is an NFL advanced-statistics app for iPhone. It ranks players by percentile
across passing, rushing, receiving, and defense, shows full percentile profiles for a single
player, tracks who is heating up or cooling off, and compares players, teams, and seasons.

The audience is fantasy and dynasty players, analytics-aware fans, and writers. They already
know EPA, CPOE, YAC, target share. The product voice is **almanac, not broadcast**: a printed
statistical reference, tight and unhyped, not a neon sports-network graphics package.

The icon must read as "serious football data," not "football game" and not "generic chart app."

---

## 2. The concept (already decided — do not re-pitch)

**A bar chart with a football over it.** Keep this. The job is execution quality, not a new idea.

The current icon (`reference/current-icon-v1-1024.png`) has the right elements and the wrong craft:

| Problem in v1 | Why it hurts |
|---|---|
| Football is a flat brown blob with a lazy gradient and jagged, hand-cut point tips | Reads as clip art at 180px, mush at 40px |
| Football floats detached above the bars, overlapping nothing meaningfully | No composition, no relationship between the two elements |
| Bars are four unrelated colors (gold, dusty pink, green, green) with no logic | Pink is not in the product palette at all; the ramp implies nothing |
| Bars are pill-shaped with fully rounded tops and bottoms | Fights the app's flat, hairline, squared-off UI |
| Composition sits low-left, large dead zone across the top | Off-balance in the rounded-rect mask; looks like a mistake |
| Background is a near-black green-black with a faint invisible gradient | Muddy; loses all identity in a dark Home Screen |
| Football laces are thin white sticks at inconsistent angles | First thing that disintegrates at small sizes |

Fix all seven. Same two objects, professionally drawn.

---

## 3. Palette (from the shipping app — use these exact values)

The app UI is a warm cream "almanac" light theme with deep green and leather accents.

| Token | Hex | Role in the icon |
|---|---|---|
| `midnight` | `#091412` | Deepest background green-black |
| `turf` | `#145C33` | Primary green, bars and background field |
| `performanceHigh` | `#057533` | Brighter green, the top/best bar |
| `leather` | `#7A3B1A` | Football body |
| `gold` | `#D6A130` | Single accent — use sparingly |
| `canvas` | `#F0EDE3` | Warm off-white, laces and any light element |
| `surface` | `#FCFAF0` | Lightest cream |
| `hairline` | `#B8B5A8` | Rule lines only |
| `performanceLow` | `#B23314` | Low-percentile red — probably unused here |
| `linkBlue` | `#0D5273` | Do not use in the icon |

Rules:
- Never use pure white `#FFFFFF` or pure black `#000000`. Cream and green-black instead.
- Maximum three hues in the final icon plus the cream. Fewer is better.
- No team colors, no NFL red/blue/silver conventions.

See `reference/ui_leaders.png` and `reference/ui_player_profile.png` for the real in-app look:
cream cards, hairline rules, monospaced stat figures, green-to-red percentile ramp, no gloss.

---

## 4. Composition spec

Canvas: 1024×1024, square, full-bleed art. iOS applies the rounded-rect mask itself — do **not**
draw a rounded rectangle, do not add a border, do not leave transparent corners.

Safe area: keep all meaningful geometry inside the centered **880×880** region. Nothing important
within 72px of any edge.

Optical center: the combined mass of ball + bars should be centered, or a hair above center.
No dead half of the canvas.

### Bars
- **Three or four bars**, ascending left to right, evenly spaced.
- **Square or 2px-radius tops and bottoms.** Not pills. This matches the app's `radiusCard: 4`.
- Bars sit on a shared implied baseline. Do not draw the baseline as a visible axis unless it is
  a single cream hairline — that is allowed and probably good.
- Color logic must be *readable as data*: one continuous ramp (dark turf → performanceHigh), or
  all turf with only the tallest bar in gold. Never four arbitrary colors.
- Bar width to gap ratio roughly 2:1. Confident and chunky, not thin.

### Football
- Proper prolate-spheroid silhouette: symmetric, smooth, with **clean sharp points** where the
  two arcs meet. No jagged or hand-cut tips. Tilted 20–35° clockwise from horizontal.
- Sized to occupy roughly 45–55% of the canvas width. It is the hero.
- **It must interact with the bars,** not hover above them. Choose one:
  - the ball crosses in front of the bars, and the bars continue visibly on both sides, or
  - the ball is knocked out of the bars (a cream/background-colored gap traces its outline), or
  - the tallest bar passes *behind* the ball and emerges above it.
  Overlap creates depth. Detachment is what makes v1 look amateur.
- Surface: flat `leather` with at most one very subtle darker edge shade for form. No photographic
  gradient, no specular highlight, no drop shadow.
- **Laces:** one straight cream spine plus 3–4 short perpendicular cross-stitches, all at
  consistent angle and spacing, all the same weight. Stroke weight no thinner than 14px at 1024.
  This is the single most likely element to fail at small sizes — if it can't survive 40px, drop
  the cross-stitches and keep only the spine.
- The two side seams (the thin arcs near each point) are optional. Include them only if they hold
  up at 120px; otherwise omit.

### Background
- Solid or a very subtle vertical gradient, `midnight` → a slightly lighter green-black.
- The gradient must be perceptible on an OLED phone. v1's is not.
- Alternative worth trying: a **cream** (`canvas`) background with dark green bars and leather ball.
  This would stand out sharply against the dark-icon-heavy iOS Home Screen and matches the app's
  actual light UI. Produce this as one of the concepts (see §6).

---

## 5. Hard constraints

- 1024×1024 PNG, sRGB, **no alpha channel**, full-bleed square.
- No text, no letterforms, no numbers, no wordmark. Not "SS", not "GS", not "NFL".
- No NFL, team, NFLPA, or college logos, marks, or team color schemes. Zero trade dress.
- No photographic textures, no bevels, no glass/glossy shine, no outer drop shadow, no long shadow.
- No thin hairline strokes below 12px at 1024 scale anywhere in the art.
- Must remain legible and identifiable at **40×40** (Spotlight/Settings) and **60×60** (Home Screen @1x).
- Must not resemble the baseball sibling app (navy background, diamond, white ball with red stitching).

---

## 6. Deliverables

Write everything to `claude-design/icon/output/`.

1. **Three concepts**, each as a 1024×1024 PNG, named:
   - `concept_a_dark.png` — dark `midnight` background, the safe evolution of v1
   - `concept_b_cream.png` — `canvas` cream background, dark green bars
   - `concept_c_knockout.png` — ball knocked out of the bars, most graphic/reductive of the three
2. For **each** concept, a proof sheet PNG showing it rendered with the iOS rounded-rect mask at
   **1024, 180, 120, 80, 60, and 40 px** on both a light and a dark neutral backdrop, named
   `proof_<concept>.png`. This is how the winner gets chosen — do not skip it.
3. `NOTES.md`: one short paragraph per concept covering what you changed relative to v1, the color
   logic you used for the bars, and which concept you recommend and why.

Do not touch `StatScout/Assets.xcassets/`. Installation is a separate, later step.

---

## 7. How the winner gets installed (for reference, not your job)

The single chosen 1024 PNG is flattened to remove alpha and copied to
`StatScout/Assets.xcassets/AppIcon.appiconset/AppIcon.png`. The asset catalog uses the modern
single-size format, so no other sizes are needed. `scripts/install_icon.sh` in this folder's
parent repo does this.

---

## 8. Success criterion

Held at arm's length at 60px on a crowded Home Screen, it reads instantly as **football + data**,
in that order, and looks like it was made by the same person who designed the app's cream almanac
UI. If it reads as "sports app" generically, or as "chart app" generically, it failed.
