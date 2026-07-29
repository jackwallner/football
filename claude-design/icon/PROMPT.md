# Paste this into Claude Design

Attach these four files, then paste the prompt below.

- `claude-design/icon/ICON_BRIEF.md`
- `claude-design/icon/reference/current-icon-v1-1024.png`
- `claude-design/icon/reference/ui_leaders.png`
- `claude-design/icon/reference/ui_player_profile.png`

---

Design a new iOS app icon for Gridiron StatScout, an NFL advanced-statistics app.

Read `ICON_BRIEF.md` first and follow it exactly — palette hex values, composition spec, hard
constraints, and deliverables are all specified there. The other three attachments are the
current shipping icon and two real screenshots of the app's UI.

The concept is fixed: **a bar chart with a football over it.** Do not propose a different
concept. The current icon has the right idea and amateur execution — detached floating ball,
jagged point tips, four arbitrary bar colors including a pink that isn't in the product palette,
pill-shaped bars that fight the app's flat squared-off UI, and a muddy invisible background
gradient. Your job is to draw that same idea properly.

The app's visual identity is a warm cream "almanac" — a printed statistical reference, flat,
hairline rules, monospaced figures, deep green and leather. Not a broadcast sports graphic. The
icon should look like it came from the same hand as the screenshots.

Non-negotiables: 1024×1024 sRGB, no alpha, full-bleed square with no rounded corners drawn in
(iOS masks it). No text or letterforms. No NFL/team/NFLPA marks or team color schemes. No gloss,
bevels, drop shadows, or photographic texture. Nothing thinner than 12px stroke weight. It must
survive 40×40.

Produce the three concepts and the six-size proof sheets described in §6 of the brief, plus
`NOTES.md` with your recommendation. Write everything to `output/`.
