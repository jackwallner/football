import XCTest

/// Newcomer persona walk (task drafts-football-newcomer): a first-time user who
/// does not know the app's vocabulary and wants one useful result straight away.
///
/// These are observation scripts, not product assertions. Every step records a
/// screenshot, a control-frame note or an accessibility tree so a reviewer can
/// judge the screen; a missing control becomes a note, not a hard failure, so
/// one script fault cannot hide the screens after it. Seeded launches use the
/// DEBUG `-ScreenshotData` provider (fictional players, no network, no
/// RevenueCat key) and never force Pro, so the free tier is what is captured.
/// The seeded provider always marks onboarding complete, so onboarding is only
/// reachable from an unseeded launch (offline provider, no data).
@MainActor
final class NewcomerDraftsUITests: XCTestCase {
    private var tag = "n"
    private var notes: [String] = []

    override func setUp() {
        super.setUp()
        continueAfterFailure = true
        notes = []
    }

    /// Attached from each test body (via `defer`), not from `tearDown`: the
    /// `tearDown` override is nonisolated and Swift 6 rejects calling a
    /// main-actor helper from it.
    private func flushNotes() {
        attachText(notes.joined(separator: "\n"), name: "notes_\(tag)")
    }

    // MARK: - Flows

    /// First run, offline provider: the three onboarding pages, the paid path,
    /// the free path, the empty board a no-data newcomer lands on, and whether
    /// onboarding stays gone on the next launch.
    func testN01FirstRunOnboarding() {
        tag = "n01"
        defer { flushNotes() }
        let app = XCUIApplication()
        app.launchArguments = ["-ResetUITestState"]
        app.launch()

        let cont = app.buttons["Continue"]
        guard cont.waitForExistence(timeout: 90) else {
            snap(app, "n01_launch_without_continue")
            note("Continue never appeared on a fresh launch")
            return
        }
        settle(1.0)
        snap(app, "n01_onboarding_page1")
        attachText(app.debugDescription, name: "tree_n01_onboarding_page1")
        noteFrame("Skip", app.buttons["Skip"])
        noteFrame("Continue", cont)
        noteStaticTexts(app, "page1 texts")

        tap(cont, "Continue (page 1)")
        settle(0.9)
        snap(app, "n02_onboarding_page2")
        noteStaticTexts(app, "page2 texts")

        tap(app.buttons["Continue"], "Continue (page 2)")
        settle(1.2)
        snap(app, "n03_onboarding_page3")
        attachText(app.debugDescription, name: "tree_n03_onboarding_page3")
        noteButtons(app, "page3 buttons")
        noteFrame("Get Started", app.buttons["Get Started"])
        noteFrame("Restore Purchases", app.buttons["Restore Purchases"])
        noteStaticTexts(app, "page3 texts")

        // The paid path: with no loaded offering this is the plan-picker fallback.
        let paid = app.buttons.matching(
            NSPredicate(format: "label CONTAINS[c] 'upgrade' OR label CONTAINS[c] 'trial' OR label CONTAINS[c] 'continue with'")
        ).allElementsBoundByIndex.first { $0.isHittable }
        if let paid {
            noteFrame("paid CTA '\(paid.label)'", paid)
            paid.tap()
            settle(2.0)
            snap(app, "n04_onboarding_paid_cta_result")
            noteButtons(app, "after paid CTA buttons")
            dismissSheet(app)
            settle(1.0)
            snap(app, "n05_onboarding_after_sheet_dismiss")
        } else {
            note("no hittable paid CTA found on page 3")
        }

        // The free path.
        tap(app.buttons["Get Started"], "Get Started")
        settle(4.0)
        snap(app, "n06_first_screen_offline_no_data")
        attachText(app.debugDescription, name: "tree_n06_first_screen_offline")

        // Return visit: onboarding should stay gone. Drop the reset flag first,
        // otherwise the DEBUG reset wipes the stored completion.
        app.terminate()
        app.launchArguments = []
        app.launch()
        settle(4.0)
        snap(app, "n07_relaunch_same_state")
        note("relaunch shows Continue button: \(app.buttons["Continue"].exists)")
    }

    /// Priced onboarding page and paywall surfaces, rendered from the DEBUG
    /// mock products (the harness is the only way to see a price on a simulator).
    func testN02PricedSurfaces() {
        tag = "n02"
        defer { flushNotes() }
        for mode in ["onboarding", "trial", "monthly"] {
            let app = XCUIApplication()
            app.launchArguments = ["-PaywallSnapshot", mode]
            app.launch()
            settle(5.0)
            snap(app, "n10_priced_\(mode)")
            noteButtons(app, "\(mode) buttons")
            noteStaticTexts(app, "\(mode) texts")
            if mode == "onboarding" {
                noteFrame("Get Started", app.buttons["Get Started"])
            }
            attachText(app.debugDescription, name: "tree_n10_priced_\(mode)")
            app.terminate()
        }
    }

    /// The first useful result and a first profile: board, tap a player, read
    /// the percentile card, ask what a percentile is, open the glossary.
    func testN03FirstResultAndProfile() {
        tag = "n03"
        defer { flushNotes() }
        let app = launchSeeded(tab: "stats")
        guard waitForRows(app) else { return }
        settle(1.0)
        snap(app, "n20_stats_first_screen")
        attachText(app.debugDescription, name: "tree_n20_stats_first_screen")
        noteButtons(app, "stats first-screen buttons")
        noteFrame("Settings", vis(app, "Settings"))
        noteFrame("Search players or teams", vis(app, "Search players or teams"))
        noteFrame("Sort direction", vis(app, "Sort direction"))
        noteFrame("View options", vis(app, "View options"))
        noteFrame("Stat", vis(app, "Stat"))
        noteStaticTexts(app, "stats first-screen texts")

        // Scroll the board to see its footer legend and where the upsell sits.
        app.swipeUp()
        settle(0.8)
        snap(app, "n21_stats_scrolled_once")
        app.swipeUp()
        settle(0.8)
        snap(app, "n22_stats_scrolled_twice")
        app.swipeDown()
        app.swipeDown()
        settle(0.8)

        let started = Date()
        guard openRow(app, index: 0) else { return }
        let open = Date().timeIntervalSince(started)
        note("profile open to 'Compare with another player' hittable: \(String(format: "%.2f", open)) s (CI runner, not a device frame measurement)")
        settle(1.0)
        snap(app, "n23_profile_top")
        attachText(app.debugDescription, name: "tree_n23_profile_top")
        noteButtons(app, "profile buttons")
        noteFrame("About Percentiles", vis(app, "About Percentiles"))
        noteFrame("Advanced tab", vis(app, "Advanced"))
        noteFrame("Standard tab", vis(app, "Standard"))
        noteFrame("Year Compare tab", vis(app, "Year Compare"))
        noteStaticTexts(app, "profile top texts")

        // Ask what a percentile is, the way a confused first-timer would.
        let info = vis(app, "About Percentiles")
        if info.waitForExistence(timeout: 5) {
            tap(info, "About Percentiles")
            settle(1.2)
            snap(app, "n24_percentile_info_sheet")
            noteButtons(app, "info sheet buttons (is there a visible close control?)")
            noteStaticTexts(app, "info sheet texts")
            dismissSheet(app)
            settle(1.0)
            snap(app, "n25_after_info_dismiss")
        } else {
            note("About Percentiles control not found")
        }

        for step in 1...3 {
            app.swipeUp()
            settle(0.8)
            snap(app, "n26_profile_scroll\(step)")
        }
        let glossary = app.buttons.matching(
            NSPredicate(format: "label CONTAINS[c] 'What do these stats mean'")
        ).firstMatch
        if glossary.waitForExistence(timeout: 3) {
            noteFrame("glossary link", glossary)
            tap(glossary, "glossary link")
            settle(1.2)
            snap(app, "n27_glossary")
            let field = visField(app)
            if field.waitForExistence(timeout: 3) {
                field.tap()
                field.typeText("EPA")
                settle(1.0)
                snap(app, "n28_glossary_search_epa")
            }
        } else {
            note("glossary link not reachable after 3 swipes")
            snap(app, "n27_glossary_missing")
        }
    }

    /// Tabs a newcomer will try next, on the free tier.
    func testN04OtherTabs() {
        tag = "n04"
        defer { flushNotes() }
        let app = launchSeeded(tab: "stats")
        guard waitForRows(app) else { return }
        for name in ["Games", "Trends", "Teams", "Compare"] {
            guard let tab = tabButton(app, name) else {
                note("tab bar button \(name) not found")
                continue
            }
            noteFrame("tab \(name)", tab)
            tab.tap()
            settle(2.0)
            snap(app, "n30_tab_\(name.lowercased())")
            noteStaticTexts(app, "\(name) texts")
            if name == "Trends" || name == "Compare" {
                attachText(app.debugDescription, name: "tree_n30_tab_\(name.lowercased())")
            }
            if name == "Compare" {
                app.swipeUp()
                settle(0.8)
                snap(app, "n31_compare_scrolled")
            }
        }
    }

    /// Settings, the toolbar upgrade pill and what the free tier shows there.
    func testN05SettingsAndUpgrade() {
        tag = "n05"
        defer { flushNotes() }
        let app = launchSeeded(tab: "stats")
        guard waitForRows(app) else { return }
        let upgrade = app.buttons.matching(
            NSPredicate(format: "label CONTAINS[c] 'unlock all features'")
        ).allElementsBoundByIndex.first { $0.isHittable } ?? vis(app, "Upgrade")
        if upgrade.exists {
            noteFrame("toolbar upgrade pill '\(upgrade.label)'", upgrade)
            tap(upgrade, "toolbar upgrade pill")
            settle(2.5)
            snap(app, "n40_upgrade_pill_result")
            noteButtons(app, "upgrade sheet buttons")
            noteStaticTexts(app, "upgrade sheet texts")
            dismissSheet(app)
            settle(1.0)
        } else {
            note("toolbar upgrade pill not found")
        }

        let gear = vis(app, "Settings")
        if gear.waitForExistence(timeout: 5) {
            tap(gear, "Settings gear")
            settle(1.5)
            snap(app, "n41_settings_top")
            noteStaticTexts(app, "settings texts")
            noteFrame("Restore Purchases", app.buttons["Restore Purchases"])
            app.swipeUp()
            settle(0.8)
            snap(app, "n42_settings_scrolled")
        } else {
            note("Settings gear not found")
        }
    }

    /// A newcomer's first instinct: type a name or a team.
    func testN06Search() {
        tag = "n06"
        defer { flushNotes() }
        let app = launchSeeded(tab: "stats")
        guard waitForRows(app) else { return }
        let chip = vis(app, "Search players or teams")
        guard chip.waitForExistence(timeout: 10) else {
            note("search chip missing")
            return
        }
        tap(chip, "search chip")
        settle(1.0)
        let field = visField(app)
        guard field.waitForExistence(timeout: 5) else {
            snap(app, "n50_search_no_field")
            note("search field never appeared")
            return
        }
        snap(app, "n50_search_open")
        // The simulator's first-run "Speed up your typing" sheet covers the
        // keyboard; its Continue button is the only Continue on a seeded launch.
        let intro = app.buttons["Continue"]
        if intro.waitForExistence(timeout: 2), intro.isHittable {
            note("dismissed the keyboard intro sheet")
            intro.tap()
            settle(0.8)
        }
        field.typeText("Chiefs")
        settle(1.2)
        snap(app, "n51_search_chiefs")
        noteStaticTexts(app, "search chiefs texts")
        replaceText(in: field, with: "Jalen")
        settle(1.2)
        snap(app, "n52_search_name_on_other_position_tab")
        noteStaticTexts(app, "search Jalen texts")
        replaceText(in: field, with: "Mahomes")
        settle(1.2)
        snap(app, "n53_search_famous_name_not_in_data")
        noteStaticTexts(app, "search Mahomes texts")
    }

    /// Second profile open: the free-tier pitch sheet, and how to leave it.
    func testN07SecondProfile() {
        tag = "n07"
        defer { flushNotes() }
        let app = launchSeeded(tab: "stats")
        guard waitForRows(app) else { return }
        guard openRow(app, index: 0) else { return }
        settle(1.0)
        goBack(app)
        settle(1.0)
        guard waitForRows(app) else { return }
        guard openRow(app, index: 1) else {
            snap(app, "n60_second_profile_not_opened")
            return
        }
        settle(2.5)
        snap(app, "n60_second_profile")
        attachText(app.debugDescription, name: "tree_n60_second_profile")
        noteButtons(app, "second profile buttons (pitch sheet controls)")
        noteStaticTexts(app, "second profile texts")
    }

    /// Return visit with the seeded provider: the tab and filter you left.
    func testN08ReturnVisitSeeded() {
        tag = "n08"
        defer { flushNotes() }
        let app = launchSeeded(tab: "stats")
        guard waitForRows(app) else { return }
        let wr = vis(app, "WR")
        if wr.waitForExistence(timeout: 5) {
            tap(wr, "WR position tab")
            settle(1.5)
            snap(app, "n70_before_relaunch_wr")
        } else {
            note("WR position tab not found")
        }
        app.terminate()
        // Keep the seeded provider, drop the reset and the forced start tab.
        app.launchArguments = ["-ScreenshotData"]
        app.launch()
        _ = waitForRows(app)
        settle(1.5)
        snap(app, "n71_after_relaunch")
        noteStaticTexts(app, "after relaunch texts")
    }

    /// What a real newcomer has: nobody followed yet. The seeded provider
    /// pre-follows three players, so the other tests show a state no first-time
    /// user is ever in; `-favorites.playerIds "()"` overrides it with an empty list.
    func testN09EmptyFollowState() {
        tag = "n09"
        defer { flushNotes() }
        let app = launchSeeded(tab: "stats", extra: ["-favorites.playerIds", "()"])
        guard waitForRows(app) else { return }
        tap(vis(app, "Following"), "Following segment")
        settle(1.5)
        snap(app, "n80_following_empty")
        noteButtons(app, "following empty buttons")
        noteStaticTexts(app, "following empty texts")

        let choose = vis(app, "Choose players")
        if choose.waitForExistence(timeout: 5) {
            noteFrame("Choose players", choose)
            tap(choose, "Choose players")
            settle(1.5)
            snap(app, "n81_follow_sheet_empty")
            noteButtons(app, "follow sheet buttons")
            let firstFollow = app.buttons.matching(
                NSPredicate(format: "label BEGINSWITH 'Follow '")
            ).firstMatch
            if firstFollow.waitForExistence(timeout: 5) {
                note("first follow row: '\(firstFollow.label)' \(frameText(firstFollow))")
                tap(firstFollow, "first Follow row")
                settle(1.0)
                snap(app, "n82_follow_sheet_one_followed")
            }
            tap(vis(app, "Done"), "Done")
            settle(1.5)
            snap(app, "n83_following_board_one_player")
            noteStaticTexts(app, "following board texts")
        } else {
            note("Choose players button not found")
        }

        if let tab = tabButton(app, "Compare") {
            tab.tap()
            settle(2.0)
            snap(app, "n84_compare_one_follow")
            noteStaticTexts(app, "compare texts")
        }
    }

    /// Return visit after two profile opens: does the sales sheet come back on
    /// the very first profile of the next cold launch?
    func testN10ReturnVisitPitch() {
        tag = "n10"
        defer { flushNotes() }
        let app = launchSeeded(tab: "stats")
        guard waitForRows(app) else { return }
        for index in 0..<3 {
            guard openRow(app, index: index) else { return }
            settle(1.5)
            if vis(app, "Maybe later").exists {
                note("pitch sheet on profile open #\(index + 1)")
                snap(app, "n90_pitch_on_open_\(index + 1)")
                dismissSheet(app)
                settle(1.0)
            } else {
                note("no pitch sheet on profile open #\(index + 1)")
            }
            goBack(app)
            settle(1.0)
        }
        app.terminate()
        app.launchArguments = ["-ScreenshotData"]
        app.launch()
        guard waitForRows(app) else { return }
        guard openRow(app, index: 0) else { return }
        settle(2.0)
        snap(app, "n91_first_profile_of_next_launch")
        note("pitch sheet on first profile of the next launch: \(vis(app, "Maybe later").exists)")
    }

    /// The two menus on the first screen, read the way a newcomer would: what
    /// does "View" offer, and what is the stat chip for.
    func testN11BoardMenus() {
        tag = "n11"
        defer { flushNotes() }
        let app = launchSeeded(tab: "stats")
        guard waitForRows(app) else { return }
        let view = vis(app, "View options")
        if view.waitForExistence(timeout: 10) {
            tap(view, "View options")
            settle(1.2)
            snap(app, "n95_view_menu")
            noteButtons(app, "view menu buttons")
            noteStaticTexts(app, "view menu texts")
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.12)).tap()
            settle(1.0)
        } else {
            note("View options not found")
        }
        let stat = vis(app, "Stat")
        if stat.waitForExistence(timeout: 10) {
            tap(stat, "Stat menu")
            settle(1.2)
            snap(app, "n96_stat_menu")
            noteButtons(app, "stat menu buttons")
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.12)).tap()
            settle(1.0)
        } else {
            note("Stat chip not found")
        }
        for position in ["RB", "WR", "TE", "DEF"] {
            let tab = vis(app, position)
            if tab.exists {
                noteFrame("position tab \(position)", tab)
            }
        }
        tap(vis(app, "DEF"), "DEF position tab")
        settle(1.5)
        snap(app, "n97_defense_board")
        noteStaticTexts(app, "defense board texts")
    }

    /// Late layout on a freshly opened profile: the share button is added to the
    /// toolbar once the card image has rendered, which can move its neighbours.
    /// Records the toolbar controls and takes a screenshot at several delays
    /// after the profile becomes tappable. Not a frame-rate measurement.
    func testN12ProfileLateLayout() {
        tag = "n12"
        defer { flushNotes() }
        let app = launchSeeded(tab: "stats")
        guard waitForRows(app) else { return }
        guard openRow(app, index: 0) else { return }
        let started = Date()
        for (step, delay) in [0.0, 0.4, 1.0, 2.5].enumerated() {
            let wait = delay - Date().timeIntervalSince(started)
            if wait > 0 { settle(wait) }
            snap(app, "n98_profile_t\(step)")
            let elapsed = String(format: "%.2f", Date().timeIntervalSince(started))
            note("t=\(elapsed) s after profile became tappable")
            for prefix in ["Follow ", "Unfollow ", "Compare with", "Share "] {
                let match = app.buttons.matching(
                    NSPredicate(format: "label BEGINSWITH %@", prefix)
                ).allElementsBoundByIndex.first { $0.isHittable }
                if let match {
                    note("   '\(match.label)' \(frameText(match))")
                }
            }
            let strip = app.staticTexts.matching(
                NSPredicate(format: "label CONTAINS 'LAST GAME' OR label CONTAINS 'Last game'")
            ).firstMatch
            note("   last-game card present: \(strip.exists)")
            // The tab strip sits under the late-arriving last-game card, so its
            // y position moves if that card is inserted after first paint.
            note("   Advanced tab \(frameText(vis(app, "Advanced")))")
        }
    }

    /// A percentile row is a link to that stat's league ranking but draws no
    /// chevron. Taps the first one and records what a newcomer lands on.
    func testN13MetricRowTap() {
        tag = "n13"
        defer { flushNotes() }
        let app = launchSeeded(tab: "stats")
        guard waitForRows(app) else { return }
        guard openRow(app, index: 0) else { return }
        settle(1.2)
        let rows = app.buttons.matching(NSPredicate(format: "label CONTAINS 'percentile'"))
        note("-- percentile row buttons")
        for row in rows.allElementsBoundByIndex.prefix(8) {
            note("   '\(row.label)' \(frameText(row))")
        }
        guard let first = rows.allElementsBoundByIndex.first(where: { $0.isHittable }) else {
            note("no hittable percentile row")
            snap(app, "n99_no_percentile_row")
            return
        }
        snap(app, "n99_before_row_tap")
        first.tap()
        settle(1.8)
        snap(app, "n99_metric_row_ranking")
        noteStaticTexts(app, "metric ranking texts")
        noteButtons(app, "metric ranking buttons")
    }

    /// The other two profile tabs on the free tier: the plain-numbers tab and
    /// the locked year-over-year preview.
    func testN14ProfileTabsFree() {
        tag = "n14"
        defer { flushNotes() }
        let app = launchSeeded(tab: "stats")
        guard waitForRows(app) else { return }
        guard openRow(app, index: 0) else { return }
        settle(1.0)
        tap(vis(app, "Standard"), "Standard tab")
        settle(1.5)
        snap(app, "n100_profile_standard_tab")
        noteStaticTexts(app, "standard tab texts")
        app.swipeUp()
        settle(0.8)
        snap(app, "n101_profile_standard_scrolled")
        app.swipeDown()
        settle(0.5)
        tap(vis(app, "Year Compare"), "Year Compare tab")
        settle(1.8)
        snap(app, "n102_profile_year_compare_free")
        noteStaticTexts(app, "year compare texts")
        noteButtons(app, "year compare buttons")
    }

    // MARK: - Helpers

    private func launchSeeded(tab: String, extra: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-ScreenshotData", "-ResetUITestState", "-StartTab", tab] + extra
        app.launch()
        if !app.wait(for: .runningForeground, timeout: 60) {
            note("app did not reach the foreground")
        }
        return app
    }

    /// The hittable button with this label. All five tabs stay mounted in a
    /// ZStack, so a bare `firstMatch` can resolve to a control in a tab that is
    /// not showing (an earlier audit lost a Stat-menu step to exactly that).
    private func vis(_ app: XCUIApplication, _ label: String) -> XCUIElement {
        let matches = app.buttons.matching(NSPredicate(format: "label == %@", label)).allElementsBoundByIndex
        return matches.first { $0.isHittable } ?? app.buttons[label].firstMatch
    }

    private func visField(_ app: XCUIApplication) -> XCUIElement {
        app.textFields.allElementsBoundByIndex.first { $0.isHittable } ?? app.textFields.firstMatch
    }

    /// Board rows are buttons labelled "<rank>, <name>, ...".
    private func rowQuery(_ app: XCUIApplication) -> XCUIElementQuery {
        app.buttons.matching(NSPredicate(format: "label MATCHES %@", "^[0-9]+,.*"))
    }

    private func waitForRows(_ app: XCUIApplication, timeout: TimeInterval = 60) -> Bool {
        let first = rowQuery(app).firstMatch
        if first.waitForExistence(timeout: timeout) { return true }
        snap(app, "rows_never_appeared")
        attachText(app.debugDescription, name: "tree_rows_never_appeared")
        note("no leaderboard rows within \(Int(timeout)) s")
        return false
    }

    private func openRow(_ app: XCUIApplication, index: Int) -> Bool {
        let rows = rowQuery(app).allElementsBoundByIndex
        guard rows.count > index else {
            note("row \(index) missing, only \(rows.count) rows")
            return false
        }
        rows[index].tap()
        let compare = vis(app, "Compare with another player")
        let deadline = Date().addingTimeInterval(30)
        while Date() < deadline {
            if compare.exists, compare.isHittable { return true }
            // A free user's second profile opens under the pitch sheet, which
            // can cover the toolbar; its "Maybe later" button proves the push.
            if app.buttons["Maybe later"].exists { return true }
            usleep(300_000)
        }
        note("profile did not become ready after tapping row \(index)")
        snap(app, "profile_not_ready_row\(index)")
        return false
    }

    private func tabButton(_ app: XCUIApplication, _ name: String) -> XCUIElement? {
        let candidates = app.buttons.matching(NSPredicate(format: "label == %@", name)).allElementsBoundByIndex
        return candidates.first { $0.frame.minY > app.frame.height * 0.8 }
    }

    private func goBack(_ app: XCUIApplication) {
        let backs = app.navigationBars.buttons.allElementsBoundByIndex.filter {
            $0.isHittable && $0.frame.minX < 90
        }
        if let back = backs.first {
            back.tap()
            return
        }
        note("no nav back button, using edge swipe")
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.0, dy: 0.5)).withOffset(CGVector(dx: 2, dy: 0))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.8, dy: 0.5))
        start.press(forDuration: 0.05, thenDragTo: end)
    }

    /// Closes the topmost sheet: an explicit close control if there is one,
    /// otherwise the swipe-down every iOS user is assumed to know.
    private func dismissSheet(_ app: XCUIApplication) {
        for label in ["Close", "Done", "Cancel", "Maybe later", "Not now"] {
            let button = vis(app, label)
            if button.exists, button.isHittable {
                note("dismissed sheet with '\(label)'")
                button.tap()
                return
            }
        }
        note("no visible close control, dismissing with a swipe down")
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.18))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.95))
        start.press(forDuration: 0.05, thenDragTo: end)
    }

    private func replaceText(in field: XCUIElement, with text: String) {
        field.tap()
        if let current = field.value as? String, !current.isEmpty {
            field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: current.count))
        }
        field.typeText(text)
    }

    @discardableResult
    private func tap(_ element: XCUIElement, _ what: String, timeout: TimeInterval = 15) -> Bool {
        guard element.waitForExistence(timeout: timeout) else {
            note("MISSING \(what)")
            return false
        }
        let deadline = Date().addingTimeInterval(5)
        while !element.isHittable, Date() < deadline { usleep(200_000) }
        if element.isHittable {
            element.tap()
        } else {
            note("NOT HITTABLE \(what) \(frameText(element)), tapping its centre")
            element.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        }
        return true
    }

    private func settle(_ seconds: TimeInterval) {
        Thread.sleep(forTimeInterval: max(0.3, seconds * 0.7))
    }

    private func note(_ text: String) {
        notes.append(text)
    }

    private func frameText(_ element: XCUIElement) -> String {
        guard element.exists else { return "missing" }
        let f = element.frame
        return "frame=(\(Int(f.minX)),\(Int(f.minY)),\(Int(f.width))x\(Int(f.height))) hittable=\(element.isHittable ? "yes" : "no")"
    }

    private func noteFrame(_ what: String, _ element: XCUIElement) {
        note("\(what): \(frameText(element))")
    }

    private func noteButtons(_ app: XCUIApplication, _ heading: String) {
        noteTreeLines(app, heading, kinds: ["Button"])
    }

    private func noteStaticTexts(_ app: XCUIApplication, _ heading: String) {
        noteTreeLines(app, heading, kinds: ["StaticText"])
    }

    /// One accessibility snapshot filtered by element type. Reading each
    /// element's label and frame on its own costs several snapshots apiece on a
    /// CI runner, which made the first version of this script take an hour.
    private func noteTreeLines(_ app: XCUIApplication, _ heading: String, kinds: [String]) {
        note("-- \(heading)")
        var kept = 0
        for line in app.debugDescription.split(separator: "\n") {
            let text = line.trimmingCharacters(in: .whitespaces)
            guard kinds.contains(where: { text.hasPrefix($0) }) else { continue }
            note("   \(text)")
            kept += 1
            if kept >= 400 { break }
        }
    }

    private func snap(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    private func attachText(_ text: String, name: String) {
        let attachment = XCTAttachment(string: text)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
