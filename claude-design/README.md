# Gridiron StatScout marketing package

This folder is the current handoff for App Store metadata, screenshots, and an optional App Preview video.

## Authoritative files

1. Read [`BRIEF.md`](./BRIEF.md) for positioning, metadata, frame order, copy, and legal constraints.
2. Read [`SCREENSHOT_POSITIONING_SPEC.md`](./SCREENSHOT_POSITIONING_SPEC.md) before building the frames.
3. Use only the real captures in [`screenshots-2026-07/`](./screenshots-2026-07/).

The older `screenshots/` and `reference/` folders are pre-mid-July baseball carryovers. They are retained only as historical material. Do not use their copy, colors, geometry, legal guidance, screenshots, or output paths.

## Source asset map

| Frame | Raw source |
|---|---|
| 01 | `screenshots-2026-07/raw_01_qb_leaders.png` |
| 02 | `screenshots-2026-07/raw_02_player_profile.png` |
| 03 | `screenshots-2026-07/raw_03_trends.png` |
| 04 | `screenshots-2026-07/raw_04_player_compare.png` |
| 05 | `screenshots-2026-07/raw_05_team_profile.png` |
| 06 | `screenshots-2026-07/raw_06_year_compare.png` |
| 07 | `screenshots-2026-07/raw_07_standard_passing.png` |
| 08 | `screenshots-2026-07/raw_08_teams_index.png` |

All raws are real iPhone 17 Pro Max simulator captures at 1320×2868 in light mode. They use live 2025 football data, a local simulator-only Pro entitlement, 9:41, and 100% battery. No production RevenueCat key was used.

## Expected Claude Design output

- Eight sRGB PNG masters at 1320×2868, without alpha channels
- Eight 1290×2796 derivatives for optional manual Media Manager placement
- One 25% contact sheet
- Output under `claude-design/output/`, not `fastlane/screenshots/`, until approved

Never alter data or UI pixels inside the supplied screenshots. Scaling and a single full-screen crop are allowed.
