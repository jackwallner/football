---
paths:
  - "StatScout/Views/RootTabView.swift"
  - "StatScout/Views/DetailPaneLink.swift"
  - "StatScout/Utilities/UnfoldedSplit.swift"
  - "StatScout/Utilities/PaywallScreenshotMode.swift"
---

# Gridiron StatScout: iPhone Duo (2026-10-21, revised 2026-10-23)

- Unfolded (regular width, at least 720 pt), `RootTabView` puts the five tabs
  and the tab bar left of the fold and the player, team or game they open in a
  pane on the right. Pane edges come from `GeometryProxy.verticalFold()`
  (iOS 27.1 `reservedRegions(.division)`), else an even split (`UnfoldedSplit`,
  unit tested). Portrait unfolded (669 pt) and every compact width keep one column.
- Player, team and game links are `DetailPaneLink`: a `NavigationLink` on a
  phone, a button that fills the right pane while split (`openInDetailPane`).
  Use it for any new player, team or game row; board rows pass
  `marksSelection: true`. Metric, stat, schedule and comparison links stay
  `NavigationLink` and push in their own pane.
- Until a row is opened, the right pane shows the Stats board leader.
- Folding only moves frames: the tabs and the detail pane stay mounted. Never
  swap layouts with an `if` on size class.
- Onboarding uses `UnfoldedPrimaryPane` so its column sits left of the fold.
- While split, the left pane's toolbar stays horizontal (`FoldSideToolbar`, in
  `GridironNavBar` and `GridironNavBarPublic`): iOS 27.1 otherwise gave that
  pane a vertical bar against the fold and narrowed every board by ~95 pt.
- White toolbar glyphs carry `toolbarGlyphDisc()`: the Duo vertical bar moves
  them off the midnight nav bar onto the light canvas.
- While split, the tab bar docks in a surface strip at the foot of the left
  pane (`UnfoldedSplit.dockedTabBarHeight`, unit tested) and the tabs end above
  it, so no row sits under the bar. On a phone it floats over the 88 pt
  scroll-under spacers as before. The 2026-10-23 review rejected the floating
  pill over the left pane's last rows.
- The detail pane's player page hides its inline title (`PaneTitleFromHeader`):
  beside share, follow, compare and the status bar it truncated, and the
  header names the player right below.
- Capture with `--screen`, never `--url` (iOS 27.1 alerts over every openurl):
  `gh-sim all --device 'iPhone Duo' --env FORCE_PRO=1 --env SCREENSHOT_MODE=1
  --args "-ScreenshotData -ResetUITestState -hasCompletedOnboarding YES
  -stats.board advanced"` plus one `--screen "-DuoScreen <route>"` per route
  (stats, receivers, trends, teams, team, compare; `RootTabView.openDuoRoute`).
  `-DuoTour -DuoRoutes ... -DuoAt ...` still walks routes on a clock for video.
  The fixture has no games, so the Games pane is not in the capture set.
