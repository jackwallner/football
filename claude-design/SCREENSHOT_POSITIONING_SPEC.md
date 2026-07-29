# Gridiron StatScout screenshot positioning spec

This is the implementation contract for Claude Design. It adapts the current July baseball system to football while preserving its conversion-focused geometry.

## 1. Deliverables

Create:

- Eight primary PNGs at 1320×2868
- Eight secondary PNGs at 1290×2796, derived from the complete primary compositions without stretching
- One contact sheet at 25% scale

Use these filenames:

```text
appstore_preview_01_qb_leaders.png
appstore_preview_02_player_profile.png
appstore_preview_03_trends.png
appstore_preview_04_player_compare.png
appstore_preview_05_team_profile.png
appstore_preview_06_year_compare.png
appstore_preview_07_standard_passing.png
appstore_preview_08_teams_index.png
```

Place unapproved work in:

```text
claude-design/output/1320x2868/
claude-design/output/1290x2796/
claude-design/output/contact-sheet.png
```

Apple accepts 1320×2868 for the 6.9-inch iPhone slot and can scale the highest-resolution set for smaller displays. Keep the 1290 derivatives for controlled Media Manager placement and review parity with the baseball package. To create a derivative, scale the 1320 master to 1290×2803, then center-crop seven vertical pixels to 1290×2796.

## 2. Primary canvas geometry

All values below are pixels on the 1320×2868 canvas.

| Element | Geometry |
|---|---|
| Canvas | x 0, y 0, width 1320, height 2868 |
| Horizontal safe margin | 96 |
| Marketing band | y 0 through 393 |
| Device outer frame | x 96, y 393, width 1128, height 2415 |
| Device inner screenshot | x 112, y 409, width 1096, height 2381 |
| Device bezel | 16 |
| Device corner radius | 60 |
| Bottom margin | 60 |

The supplied raw screenshot is 1320×2868. Scale it uniformly to 1096×2381, approximately 0.8303, then place it at x 112, y 409.

Use a single center crop only if rounding leaves a one-pixel mismatch. Never stretch.

The product begins at y 393. Do not introduce a gap between the marketing band and device. The complete device remains visible, including the floating tab bar.

## 3. Marketing band

Use this shared grid:

| Element | Position |
|---|---|
| Overline top | y 44 |
| Overline left | x 96 |
| Headline top | y 92 |
| Headline left | x 96 |
| Headline maximum width | 1128 |
| Subcopy baseline band | y 323 through 372 |

Typography:

- Overline: SF Pro Semibold, 26 px, tracking +1.4 px
- Headline: SF Pro Display Heavy, 78 px, 104 px line height, two lines maximum
- Subcopy: SF Pro Semibold, 34 px, 42 px line height, one line preferred

If a headline does not fit, reduce the headline to 74 px. Do not reduce below 74 px, change the line break, or rewrite the copy without flagging it.

For Frame 1, use the 1024×1024 app icon at 48×48 with an 11 px corner radius, followed by `GRIDIRON STATSCOUT` at x 160. Other overlines are text only.

For `STATSCOUT+`, use a small outlined capsule:

- Height 44
- Horizontal padding 18
- Stroke 2
- Corner radius 22
- Gold stroke and text on dark frames
- Turf stroke and text on light frames

## 4. Alternating frame treatment

| Frames | Background | Primary type | Emphasis |
|---|---|---|---|
| 01, 03, 05, 07 | `#091412` | `#FCFAF0` | `#D6A130` |
| 02, 04, 06, 08 | `#F0EDE3` | `#121714` | `#145C33` |

Use one emphasis phrase per headline. Everything else remains the primary type color.

The device shell is near-black `#050A08`. On dark frames, add a 1 px `#38403B` outline so the shell remains visible.

Device shadow:

- x 0
- y 8
- blur 12
- black at 30%

No glow, perspective, rotation, hand mockup, reflection, or background illustration.

## 5. Source placement rules

- Use each raw with the same frame number.
- Preserve the raw's full width and height within the device.
- Keep the raw status bar and Dynamic Island.
- Keep the floating tab bar visible.
- Do not cover, blur, rewrite, or retouch any UI.
- Do not add team logos, headshots, league marks, or visual callouts inside the device.
- Do not substitute stale files from `claude-design/screenshots/`.

## 6. Thumbnail check

Before export, render the eight-frame contact sheet at 25%.

Reject any frame if:

- The headline needs zooming to read.
- The app does not visibly begin near the top quarter.
- The screenshot's main proof is hidden below the fold.
- A `STATSCOUT+` capability appears without its overline.
- The device location shifts between frames.
- A headline or device edge sits closer than 60 px to the canvas edge.

Frame 1, Frame 2, and Frame 3 must remain distinct and understandable when viewed as three adjacent thumbnails.

## 7. Export checks

For every output:

- PNG
- sRGB
- Opaque RGB, no alpha channel
- Exact required pixel dimensions
- No embedded design-tool guides
- No extra filename suffixes

Do not copy outputs into `fastlane/screenshots/` or upload them to App Store Connect until the contact sheet has been approved.
