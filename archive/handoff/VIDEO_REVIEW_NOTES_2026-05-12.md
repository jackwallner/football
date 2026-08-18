# Video Review: StatScout Screen Recording - May 12, 2026

**Source file:** RPReplay_Final1778621691.MP4
**Duration:** ~6:23
**Screen recording** of iPhone app "Baseball Savvy Stats Scout" with narration identifying bugs, UX issues, and desired changes.

---

## Screenshot Key

All screenshots are at `/tmp/video_screens/{TIMESTAMP}.jpg`. Timestamps below correspond to the video.

---

## Per-Timestamp Breakdown

### 00:00-00:19 - Intro and App Launch

**Transcription:**
This video will be talking through how I debug my app through this workflow of reviewing a screen recording and going through the app. So I'm going to be starting here by going to the baseball app. I have it here, baseball savvy stats scout. Go ahead and get that installed and we're going to go through the full onboarding flow.

**What's on screen:** App icon on home screen, tapping to launch, install animation.

---

### 00:19-00:51 - Onboarding Page 1 ("Your Pocket Scout")

**Screenshot:** /tmp/video_screens/00_25.jpg

**Transcription:**
Once we get it downloaded here, we've got our onboarding flow, got skip up in the top right, this looks fine but it's pretty bland. So I really would like if this was more stat baseball sabermetric-themed, statcast component.

**Vibe / What to do:**
- **DESIGN CHANGE:** The onboarding feels generic/bland. Redesign it to feel like Baseball Savant / StatCast aesthetic.
  - Use the color scheme (red/navy) more aggressively
  - Add bar/chart visual elements
  - Make it feel like what users are getting
- **NO CODE CHANGE:** Current layout and content (bullet points, icon, description) is fine, just needs visual polish.

**Files involved:**
- `StatScout/StatScoutApp.swift` - OnboardingCard struct, OnboardingPage definitions, BulletItem
- `StatScout/Views/SavantTokens.swift` - SavantPalette colors, SavantType fonts

**Current state:**
- Page 0 icon: "baseball.fill", title: "Your Pocket\nScout"
- Description: "Baseball percentile rankings built for a fast mobile view..."
- 3 bullet items with checkmark.circle.fill icons in red
- White background (SavantPalette.canvas)

---

### 00:41-01:25 - Onboarding Page 2 ("Find Insights Fast") - Signal Icons

**Screenshot:** /tmp/video_screens/00_41.jpg

**Transcription:**
This has the right information which is good. AI loves word signals. Let's remove all signals and make a rule to never use signals again because it looks silly.

**Vibe / What to do:**
- **DESIGN CHANGE:** Remove all "word signals" - the current bullet icons use "signal" icon or signal-like imagery that looks "silly"
- **RULE:** Never use signal/waveform icons anywhere in the app
- Replace with cleaner, more appropriate icons

**Current bullet icons in code (StatScoutApp.swift):**
- Page 0: checkmark.circle.fill
- Page 1: checkmark.circle.fill
- Page 2 (Go Pro): checkmark.circle.fill (free features) + crown.fill (pro features)
- Page 3 (Play Ball): checkmark.circle.fill

**Likely culprit:** Look for "signal" or "waveform" icons anywhere in the app - they might be in SavantModules or elsewhere.

---

### 01:15-01:46 - Onboarding Page 3 ("Go Pro") - Paywall/Copy

**Screenshot:** /tmp/video_screens/01_35.jpg

**Transcription:**
Free covers today, pro and lots of trends. Again, the kind of AI slop work for a bitch there. We really want to have something that offers the pro at this point, you know, some sort of soft paywall, not required, but some sort of pitch here of that being an option if this person is interested because they're most interested when they download the app there.

**Vibe / What to do:**
- **COPY CHANGE:** The "Go Pro" page text is "AI slop" - rewrite to be more compelling and human
- **UX CHANGE:** Add a "soft paywall" pitch on the pricing page - not a hard paywall but a friendly upsell
- The current page shows "Maybe Later" + "Unlock Pro" buttons which is the right pattern
- The Description text: "Current season is free. Pro unlocks the trends that tell the full story - past seasons, year-over-year changes, and head-to-head comparisons." - this could be better
- The go-pro pitch should acknowledge they just downloaded the app and this is the most interested they'll ever be

**Files:** StatScoutApp.swift, PaywallView.swift

---

### 01:35-01:52 - Onboarding Page 4 ("Play Ball") to Main App

**Screenshot:** /tmp/video_screens/01_45.jpg

**Transcription:**
We're going to click Play Ball and we can go ahead and see the data here which is awesome.

**Observations:** Works fine, transitions to RootTabView.

---

### 01:52-02:10 - Data Freshness / "Last Updated" Bug

**Screenshot:** /tmp/video_screens/01_55.jpg

**Transcription:**
It does seem like the app was last updated yesterday at 6:04 am so that is either that should be wrong. We either have something wrong with the data backup or that date hasn't refreshed because it should be every night gets refreshed here.

**What's on screen:** Leaders tab showing "Updated Yesterday at 6:04 AM" text and data.

**Vibe / What to do:**
- **POTENTIAL BUG:** The "last updated" timestamp is wrong or not refreshing properly
- The nightly refresh workflow (GitHub Actions) may not be running correctly, or the freshness display logic has a bug
- Check: DashboardViewModel.freshnessText - computed from players.map(\.updatedAt).max()
  - If updatedAt on the Player model is wrong (server timestamp format), freshness will be wrong
- Check the nightly StatCast refresh workflow: .github/workflows/nightly-statcast.yml
- An app update may need to be downloaded after the backend finishes its nightly refresh

**Files:**
- StatScout/ViewModels/DashboardViewModel.swift - freshnessText, lastUpdated
- StatScout/Models/Player.swift - updatedAt property
- Backend refresh: .github/workflows/nightly-statcast.yml

---

### 02:07-02:15 - "Past Seasons Required"

**Screenshot:** /tmp/video_screens/02_10.jpg

**Transcription:**
Great. We've got this here, past seasons required. Awesome.

**Observation:** The "Past seasons" button is working - it shows the lock icon and triggers paywall on tap. Functioning as designed.

---

### 02:15-02:48 - Paywall: Monthly/Yearly/Lifetime Numbers Wrong

**Screenshots:** /tmp/video_screens/02_15.jpg, /tmp/video_screens/02_30.jpg, /tmp/video_screens/02_40.jpg

**Transcription:**
The monthly, yearly and lifetime numbers are wrong there. If we click start free trial, we should get this brought up. Great. We can actually see the information that it should be. Similarly here, great. If I click this, doesn't move this selection there, which is weird. And similarly this lifetime, I guess just gave it to me. So something weird there to make sure that I was supposed to get that. I did purchase this or do a test purchase before.

**Vibe / What to do:**
- **BUG:** The RevenueCat paywall pricing display shows incorrect monthly/yearly/lifetime numbers
- **BUG:** Tapping on plan options doesn't visually move the selection indicator
- **BUG:** Potentially auto-purchased lifetime without user confirming
- The native RevenueCatUI PaywallView is being used, so pricing data comes from RevenueCat dashboard
- Verify: RevenueCat product configuration has correct prices
- Verify: The selection visual state in PaywallView - RevenueCatUI handles this
- **CRITICAL:** Check if StoreService's purchase() flow has any issues with completion handling
- Note: User mentions having done a "test purchase" before, so the lifetime grant might be expected

**Files:**
- StatScout/Services/StoreService.swift - purchase(), purchaseInFlight, customerInfo, isPro
- StatScout/Views/PaywallView.swift - RevenueCatUI wrapper
- RevenueCat dashboard products: Lifetime (com.jackwallner.baseball.pro), Yearly, Monthly

---

### 02:58-03:08 - Players List and Sort By

**Screenshot:** /tmp/video_screens/03_00.jpg

**Transcription:**
Here we can see all the players, which is awesome. Great. We can sort by there. I would like an option to change this sort by, which is great. I can see that here, but also go up or down. Right now it just has that sort, but awesome.

**Vibe / What to do:**
- **SORTING:** The user notes they can sort by metric, but it's not clear there's an ascending/descending toggle
- **WHERE IT IS:** DashboardView.swift - sortHeaderMenu shows a Menu with sort options + a toggle for viewModel.sortDescending
- The sort toggle IS there (high/low), but it might not be obvious. Minor UX concern.
- **NOT A BUG** - the sort toggle works but could be more discoverable

**Files:**
- StatScout/Views/DashboardView.swift - sortHeaderMenu + LeaderboardTableHeader with sort indicator
- StatScout/ViewModels/DashboardViewModel.swift - sortDescending, setUserSortMetric()

---

### 03:08-03:26 - Header Bar Animation Bug

**Screenshot:** /tmp/video_screens/03_10.jpg

**Transcription:**
It's kind of weird how the header bar like changes there and it's kind of odd. It doesn't need to do that.

**Vibe / What to do:**
- **BUG:** The unified control bar (season menu + search + category filter) has an odd animation or layout shift when the view appears or data loads
- Likely caused by the loadingStatusBar appearing/disappearing, which shifts the control bar down
- Or the freshnessText appearing/disappearing
- Fix: Either remove the animation or make the layout stable so it doesn't shift things around

**Files:**
- StatScout/Views/DashboardView.swift - unifiedControlBar, loadingStatusBar, freshnessText display

---

### 03:21-03:31 - Player Search

**Screenshot:** /tmp/video_screens/03_25.jpg

**Transcription:**
I can try searching for a player. Great. I can click in that player, see their data.

**Observation:** Search functionality works. Users can search, tap a player, and navigate to their profile.

---

### 03:31-04:05 - Year Compare Screen (2026 to 2025) - Looks Silly

**Screenshots:** /tmp/video_screens/03_35.jpg, /tmp/video_screens/03_50.jpg, /tmp/video_screens/04_00.jpg

**Transcription:**
Let me go to 2025, see the difference there. Year compare, 2026 to 2025. This screen could use some work. It looks a bit silly. There's some good information here, but it's a little hard to read. So some sort of cool like overlay or showing the delta difference. The two stats bars there and the difference overall change.

**Vibe / What to do:**
- **DESIGN CHANGE:** Year Compare screen (YearComparisonView) is hard to read/ugly
- Add visual comparison elements:
  - Cool overlay to show delta/difference
  - Two stat bars side-by-side showing percentile change
  - Visual indicator of overall change
- **BUG:** "We can't just say all these stats are the same" - the app is showing "all stats the same" which is obviously wrong; this might be a data issue where year-over-year data isn't loaded correctly
- The current YearComparisonView shows a text grid with metric label, two year values, percentile, and delta arrow. Needs a visual redesign.

**Files:**
- StatScout/Views/YearComparisonView.swift - Full redesign needed
- StatScout/ViewModels/DashboardViewModel.swift - playerHistories, loadHistoricalIfNeeded(), availableSeasons
- StatScout/Models/Player.swift - season property, metrics

**Gating:** Year Compare is Pro-only (locked behind store.isPro)

---

### 04:00-04:14 - Compare With Someone Doesn't Work

**Screenshot:** /tmp/video_screens/04_10.jpg

**Transcription:**
This I should really compare with someone. Doesn't really work. I'm guessing because the years are different, but the right idea there that should just actually work.

**Vibe / What to do:**
- **BUG:** "Compare with" (player comparison) doesn't actually navigate/work when two players are selected
- The issue is likely in the PlayerPickerSheet to ComparisonRoute nav flow
- In PlayerProfileView.swift, the sheet sets comparisonPlayer, then there's a .background modifier that puts a hidden NavigationLink to ComparisonRoute
- The NavigationLink inside .background with .hidden() is fragile - it might not trigger navigation properly
- **FIX:** Use a proper navigation state approach instead of the hidden NavigationLink hack

**Files:**
- StatScout/Views/PlayerProfileView.swift - showingPlayerPicker, comparisonPlayer, background hidden NavigationLink
- StatScout/Views/RootTabView.swift - ComparisonRoute navigation destination

---

### 04:09-04:14 - Player Profile Loading

**Transcription:**
Perfect. That looks great.

**Observation:** The player detail/percentile page loads and looks good.

---

### 04:14-04:31 - Teams Tab: First Load Bugs Out

**Screenshot:** /tmp/video_screens/04_20.jpg

**Transcription:**
If I go to teams, there's going to be some bug when I first go to teams that it initializes and bugs out here. So need to make sure that we're having some sort of loading screens so we're not just bugging out.

**Vibe / What to do:**
- **BUG:** Teams tab crashes/freezes/bugs out on first load
- **Root cause guess:** Loading ALL teams at once - "it's loading all these teams at once, but we really only need to load the team that we actually want to see"
- The TeamsView renders all teams in filteredTeams on first appear
- If viewModel.teamsWithData is empty during initial load, it shows "No teams available"
- But when data loads, it renders all teams at once - this might cause a lag
- **FIX:** Add loading state for Teams tab, lazy load content, ensure smooth initial render

**Files:**
- StatScout/Views/TeamsView.swift - filteredTeams, TeamsViewModel, setFavorite()
- StatScout/ViewModels/DashboardViewModel.swift - teamsWithData, teamScores, recomputeTeamCache()

---

### 04:31-04:44 - Mariners Team Page

**Screenshot:** /tmp/video_screens/04_30.jpg

**Transcription:**
So we'll go to the Mariners there. Perfect. You can click around. Awesome. Have a great year. You can see the standard stats. Excellent. That looks great.

**Observation:** Team detail view (TeamView) works well once navigated to.

---

### 04:44-04:57 - Starring a Team FREEZES / CRASHES

**Screenshots:** /tmp/video_screens/04_45.jpg, /tmp/video_screens/05_00.jpg

**Transcription:**
And I can star a team that I want to see. And looks like when I do that, it also freezes. So starring a team freezes. So need to fix that. And looks like I might actually crash the app to star a team, which is kind of crazy.

**Vibe / What to do:**
- **CRITICAL BUG:** Starring (favoriting) a team causes the app to freeze or crash
- In TeamsView.swift, the setFavorite() call does:
  1. Sets favoriteTeam which writes to UserDefaults
  2. Calls UIImpactFeedbackGenerator(style: .light).impactOccurred()
- The freeze might be caused by:
  - UserDefaults write blocking the main thread
  - The filteredTeams re-computation causing a massive re-render loop
  - The favoriteTeam section appearing/disappearing triggers a SwiftUI structural identity change
  - The buttonStyle(.borderless) + NavigationLink inside HStack might cause tap target conflicts

**FIX:**
- Investigate TeamsViewModel.setFavorite() for main thread issues
- Check if UserDefaults.didSet observer causes recompute cascade
- Add safeguards against rendering thrash when favorite changes
- Consider using Task { @MainActor in } for the haptic feedback

**Files:**
- StatScout/Views/TeamsView.swift - TeamsViewModel, setFavorite(), removeFavorite(), filteredTeams
- Star button is inside allTeamsSection ForEach

---

### 04:57-05:07 - App Relaunch After Crash

**Transcription:**
We'll go to there, come back. Good test to see what loading the app. The second time works here.

**Observation:** On second launch, the app loads fine. Confirms the star-crash is a first-time/state initialization issue.

---

### 05:07-05:20 - Share Option

**Screenshot:** /tmp/video_screens/05_10.jpg

**Transcription:**
Here's an option to share. This is a recording. I guess I can share it. That's fine. I'm not really caring about this because it'll get it.

**Observation:** Share functionality exists but the user doesn't care about it. Low priority.

---

### 05:20-05:46 - Metrics Tab: "Stats and Metrics Seem Silly"

**Screenshots:** /tmp/video_screens/05_20.jpg, /tmp/video_screens/05_35.jpg, /tmp/video_screens/05_45.jpg

**Transcription:**
And here are stats here as well. Stats and metrics kind of seem silly. So we probably should reword those to be a little different.

**Vibe / What to do:**
- **UX/DESIGN CHANGE:** The "Metrics" tab and "Stats" tab are confusingly similar
  - "One way to see standard stats and stats out. Stats, stats there."
  - Users don't understand the difference between Metrics (percentile-based) and Stats (traditional)
- **RENAME:** Rename tabs to be more descriptive
  - "Metrics" to "Percentile Leaders" or "Statcast Leaders"
  - "Stats" to "Standard Stats" or "Traditional Stats"
- Or merge them into one tab
- The current tab labels: "Metrics" (MetricLeadersView) and "Stats" (StandardStatsLeadersView)

**Files:**
- StatScout/Views/RootTabView.swift - Tab labels, icons, views
- StatScout/Views/MetricLeadersView.swift
- StatScout/Views/StandardStatsLeadersView.swift

---

### 05:43-05:53 - Click Into Metric to See Highest/Lowest

**Transcription:**
And click in here. I want to make it a little more obvious that if I click into one of these things, I can see the highest or lowest.

**Vibe / What to do:**
- **UX CHANGE:** In the Metrics tab (MetricLeadersView), the metric name is tappable to navigate to MetricRankingView, but this isn't obvious
- Add visual indicator (chevron, highlight, or different styling) that metric labels are tappable
- Navigation happens via NavigationLink(value: MetricRoute(...)) on the metric label text

**Files:**
- StatScout/Views/MetricLeadersView.swift - NavigationLink on metric label
- StatScout/Views/MetricRankingView.swift - Full ranking for a single metric

---

### 05:46-06:00 - Teams Loads Poorly (Performance)

**Transcription:**
See the players that are worse, but still teams loads really poorly on that there.

**Observation:** Teams performance issue, already noted above in 04:14-04:31.

---

### 06:00-06:10 - Minimum Qualification Component

**Transcription:**
And then it would also be nice to be able to set a on the metrics, some sort of minimum qualification component. And that way I'm not getting overblown with people that haven't played before.

**Vibe / What to do:**
- **NEW FEATURE:** Add a minimum qualification filter for metrics
  - e.g., minimum plate appearances (PA) for hitters, minimum innings pitched (IP) for pitchers
  - Currently the leaderboard shows ALL players who have a metric, including those with tiny sample sizes
  - This inflates rankings with players who have 1-2 good games
- Would affect DashboardViewModel.leaderboard (the main sort/filter pipeline)
- Could be a toggle or slider in the filter/sort area
- The backend data may or may not include PA/IP counts

**Files:**
- StatScout/ViewModels/DashboardViewModel.swift - filteredPlayers, leaderboard, selectedCategory
- StatScout/Views/DashboardView.swift - Add UI control for minimum qualification
- StatScout/Models/Player.swift - Check if Player or StandardStat has PA/IP data

---

### 06:10-06:22 - Overall Assessment

**Transcription:**
Overall, just some bug fixes, the main core functionality is there. And it looks like the pro features are doing well, which is great.

**Vibe:** The user is generally positive about the core app. The fixes are mostly around polish, crash bugs, and UI improvements rather than fundamental rewrites.

---

## Consolidated Bug/Fix Priority List

### Critical (crashes/freezes)
1. **Starring a team freezes/crashes the app** - TeamsView.swift, TeamsViewModel.setFavorite()
2. **Teams tab bugs out on first load** - TeamsView.swift, data loading race condition

### High (broken functionality)
3. **"Compare with" player comparison doesn't navigate** - PlayerProfileView.swift hidden NavigationLink bug
4. **Paywall pricing numbers wrong / selection doesn't move** - RevenueCat config or StoreService.swift
5. **Year Compare "all stats are the same" / looks silly** - YearComparisonView.swift data or display bug

### Medium (UX/design)
6. **Onboarding is bland - needs StatCast theming** - OnboardingCards in StatScoutApp.swift
7. **"Metrics" and "Stats" tab names are confusing** - RootTabView.swift tab labels
8. **Year Compare screen hard to read** - YearComparisonView.swift visual redesign
9. **Header bar animation shift** - DashboardView.swift control bar layout instability
10. **Metric tappability not obvious** - MetricLeadersView.swift add chevron/hint

### Low (nice-to-have)
11. **Minimum qualification filter for metrics** - New feature: PA/IP minimums
12. **Onboarding copy is "AI slop"** - Rewrite to be more human/compelling
13. **Data freshness date wrong** - Investigate nightly refresh workflow or updatedAt field

### Cosmetic / Already Decided
14. Remove all "word signals" / signal icons from the app - rule should be applied project-wide
15. Onboarding paywall pitch should acknowledge user just downloaded the app

---

## Global Search Instructions

When fixing, search the ENTIRE codebase for the following and clean up:
- **Signal icons:** grep for "signal", "waveform", or any signal/wave-related SF Symbols
- **"Last updated" display logic:** Verify DashboardViewModel.freshnessText is correct
- **NavigationLink inside .background() pattern:** Find all hidden NavigationLink patterns and replace with proper navigationDestination(for:) where possible
