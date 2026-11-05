---
paths:
  - "fastlane/**/*"
  - "scripts/asc-*.py"
---

# Gridiron StatScout: the duplicate screenshots fixed in 1.1.0

Moved verbatim from AGENTS.md. Loads when a matching file is read; update it here.

**Duplicate screenshots, fixed in 1.1.0** (2026-08-17): live 1.0 shipped the
en-US `APP_IPHONE_67` set with 10 shots (`01_qb_leaders.png` and
`02_player_profile.png` twice) and `APP_IPAD_PRO_3GEN_129` with 2 (the same file
twice), from a partial re-upload — `fastlane/screenshots/en-US` was always clean
at 9 files. ASC refuses `DELETE /appScreenshots/{id}` on a submitted version
(409 `STATE_ERROR`), so it could only be fixed on a draft; the 1.1.0 draft
inherited the dupes and they were deleted there. **Verify after any screenshot
upload** — walk `appScreenshotSets` and compare `sourceFileChecksum`, because a
partial `deliver` run appends rather than replaces and nothing warns you.

## Localized store sets

The kit is `artifacts/asc-screenshots/l10n-20261105`. Create its venv and
install Pillow and NumPy, then run `tools/build.sh` and
`python tools/fetch_live.py` from the kit. Run `tools/capture.sh` for all target
locales, or pass store locales to restrict capture. It runs one GitHub
simulator job per device and includes an English reference. Run
`tools/compose.sh`, review every image in `sheets/`, and copy the verified PNGs
from `out/<locale>/<set>/` to `fastlane/screenshots/<locale>/`.

The iPhone scenes use `LocalizedStoreScreenshotUITests` and the DEBUG fixture.
The store fixture fixes the coverage at week 6, suppresses game logs so the
profile resembles the live English set, and avoids live sports data. The iPad
fixture adds fictional quarterbacks to fill the leaderboard. The iPad
English reference is a headerless older leaderboard. The compositor treats it
as a full-screen capture. GitHub results and raw captures are not committed.

The 2026-11-05 iPad recapture saved all attachments but failed terminating the
app after the final screenshot. The kit contains a proposed test fix; rerun
`SCREENSHOT_SET=ipad tools/capture.sh` and review both sheets before installing
that frame. The two iPhone sets are complete.
