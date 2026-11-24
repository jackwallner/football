import XCTest

/// Release gate journeys (see ~/ios/qa/README.md). They assert reachability
/// and the core loop's outcome; `QA.checkpoint` judges every screen.
///
/// Data is the DEBUG fixture slate (`-ScreenshotData`), so no network is needed
/// for players. `-MockProducts` prices the paywalls without configuring
/// RevenueCat, so no `appl_` key is ever used here.
@MainActor
final class ReleaseJourneysUITests: XCTestCase {
    private var app: XCUIApplication!
    private let loadTimeout: TimeInterval = 90

    /// Onboarding done, fixture players. `-FixtureKeepState` makes the follow
    /// list survive a relaunch; `-profileOpenCount 0` keeps the trial pitch off
    /// the profile (the argument domain wins over what the app writes back).
    private let returning = [
        "-ScreenshotData",
        "-MockProducts",
        "-FixtureKeepState",
        "-hasCompletedOnboarding", "YES",
        "-stats.board", "standard",
        "-stats.qualifier", "All Players",
        "-profileOpenCount", "0",
        "-StartTab", "stats",
    ]

    override func setUp() async throws {
        try await super.setUp()
        continueAfterFailure = false
        app = XCUIApplication(bundleIdentifier: "com.jackwallner.football")
    }

    // MARK: - Journeys

    /// Real first run: onboarding pages, the offer on the last page, then the
    /// free tier's first screen.
    func testFirstRunOnboardingToStats() {
        QA.launch(app, arguments: ["-ScreenshotData", "-MockProducts", "-FixtureFresh", "-StartTab", "stats"])
        let continueButton = QA.button(app, "Continue", "Weiter")
        XCTAssertTrue(continueButton.waitForExistence(timeout: 30), "Onboarding should open")
        QA.checkpoint(app, "onboarding-first-page")

        var page = 1
        while continueButton.exists, page < 8 {
            continueButton.tap()
            page += 1
            QA.checkpoint(app, "onboarding-page-\(page)")
        }

        let getStarted = QA.button(app, "Get Started", "Los geht's")
        XCTAssertTrue(getStarted.waitForExistence(timeout: 20), "The last onboarding page should offer Get Started")
        // The offer bills the yearly plan (the Aug 11 monthly button lost money).
        let yearlyPrice = app.staticTexts.containing(NSPredicate(format: "label CONTAINS %@ OR label CONTAINS %@", "/ year", "/ Jahr")).firstMatch
        XCTAssertTrue(yearlyPrice.waitForExistence(timeout: 20), "The onboarding offer should bill the yearly plan")
        QA.checkpoint(app, "onboarding-offer")
        getStarted.tap()

        XCTAssertTrue(boardLoaded(), "Stats should open after onboarding")
        QA.checkpoint(app, "stats-first-value")
    }

    func testOnboardingSkipReachesStats() {
        QA.launch(app, arguments: ["-ScreenshotData", "-MockProducts", "-FixtureFresh", "-StartTab", "stats"])
        let skip = QA.button(app, "Skip", "Überspringen")
        XCTAssertTrue(skip.waitForExistence(timeout: 30), "Onboarding should offer Skip")
        skip.tap()
        XCTAssertTrue(boardLoaded(), "Stats should open after Skip")
        QA.checkpoint(app, "stats-after-skip")
    }

    /// Upgrade entry: the toolbar crown opens the paywall and Close returns.
    func testPaywallShownAndDismissed() {
        QA.launch(app, arguments: returning)
        XCTAssertTrue(boardLoaded(), "Stats should open")
        let upgrade = app.buttons.matching(NSPredicate(
            format: "label ENDSWITH %@ OR label ENDSWITH %@",
            "unlock all features", "schaltet alle Funktionen frei")).firstMatch
        XCTAssertTrue(toolbarButtonExists(upgrade), "A free user should see the upgrade button")
        QA.checkpoint(app, "stats-free")
        upgrade.tap()

        let close = QA.button(app, "Close", "Schließen")
        XCTAssertTrue(close.waitForExistence(timeout: 20), "The paywall should open with a Close button")
        QA.checkpoint(app, "paywall")
        close.tap()

        XCTAssertTrue(boardLoaded(timeout: 10), "Closing the paywall should return to Stats")
    }

    func testReturnVisitAcrossTabs() {
        QA.launch(app, arguments: returning)
        XCTAssertTrue(boardLoaded(), "Stats should open")

        relaunch()
        XCTAssertTrue(boardLoaded(), "Stats should reopen without onboarding")
        QA.checkpoint(app, "stats-after-relaunch")

        QA.openTab(app, "Games", "Spiele")
        QA.checkpoint(app, "games")
        QA.openTab(app, "Trends")
        QA.checkpoint(app, "trends")
        QA.openTab(app, "Teams")
        QA.checkpoint(app, "teams")
        QA.openTab(app, "Compare", "Vergleichen")
        XCTAssertTrue(yourPlayersHeader.waitForExistence(timeout: 20), "Compare should open")
        QA.checkpoint(app, "compare")
    }

    func testSettingsOpensWithAttribution() {
        QA.launch(app, arguments: returning)
        XCTAssertTrue(boardLoaded(), "Stats should open")
        let gear = QA.button(app, "Settings", "Einstellungen")
        XCTAssertTrue(toolbarButtonExists(gear), "Stats should offer Settings")
        gear.tap()

        let reference = app.staticTexts.matching(NSPredicate(format: "label IN %@", ["REFERENCE", "REFERENZ"])).firstMatch
        XCTAssertTrue(reference.waitForExistence(timeout: 30), "Settings should open")

        let attribution = app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "nflverse")).firstMatch
        XCTAssertTrue(QA.reveal(attribution, in: app), "Settings should credit nflverse for the data")
        QA.checkpoint(app, "settings-attribution")
    }

    func testInterruptionOnPlayerProfile() {
        QA.launch(app, arguments: returning)
        XCTAssertTrue(boardLoaded(), "Stats should open")
        openProfile("Reed", as: player)

        QA.interrupt(app)
        XCTAssertEqual(app.state, .runningForeground, "The app should come back to the foreground")
        XCTAssertTrue(followButton(player).waitForExistence(timeout: 20), "The profile should survive the interruption")
        QA.checkpoint(app, "profile-after-interrupt")
    }

    /// Core loop: find a player, follow them, see them under YOUR PLAYERS on
    /// Compare, and still be following after the app is killed and reopened.
    func testCoreLoopFollowPlayerSurvivesRelaunch() {
        QA.launch(app, arguments: returning)
        XCTAssertTrue(boardLoaded(), "Stats should open")

        openProfile("Reed", as: player, checkpoint: true)
        // Earlier runs share this install; start from not following.
        if unfollow(player).exists {
            unfollow(player).tap()
        }
        let followControl = follow(player)
        XCTAssertTrue(followControl.waitForExistence(timeout: 10), "An unfollowed player should offer Follow")

        followControl.tap()
        XCTAssertTrue(unfollow(player).waitForExistence(timeout: 10), "Following should flip the button to Unfollow")

        QA.openTab(app, "Compare", "Vergleichen")
        XCTAssertTrue(followedRow(player).waitForExistence(timeout: 20), "\(player) should be listed under YOUR PLAYERS")
        QA.checkpoint(app, "compare-your-players")

        relaunch()
        XCTAssertTrue(boardLoaded(), "Stats should reopen after the relaunch")
        QA.openTab(app, "Compare", "Vergleichen")
        XCTAssertTrue(followedRow(player).waitForExistence(timeout: 20), "\(player) should still be followed after a relaunch")
        QA.checkpoint(app, "compare-your-players-after-relaunch")

        QA.openTab(app, "Stats")
        openProfile("Reed", as: player)
        XCTAssertTrue(unfollow(player).waitForExistence(timeout: 10), "The profile should still show Unfollow after a relaunch")

        unfollow(player).tap()
        XCTAssertTrue(follow(player).waitForExistence(timeout: 10), "Unfollow should flip the button back")
    }

    // MARK: - Helpers

    /// A fixture quarterback the fixture does not follow.
    private let player = "Mason Reed"

    private var yourPlayersHeader: XCUIElement {
        app.staticTexts.matching(NSPredicate(format: "label IN %@", ["YOUR PLAYERS", "DEINE SPIELER"])).firstMatch
    }

    /// At accessibility text sizes the navigation bar folds its trailing
    /// buttons into a "More" menu; open it when the button is not on the bar.
    private func toolbarButtonExists(_ button: XCUIElement) -> Bool {
        if button.waitForExistence(timeout: 20) { return true }
        // The overflow control is labelled "More" (or its German "Mehr"); fall
        // back to the last button on the bar, which is where iOS draws it.
        let named = app.buttons.matching(NSPredicate(format: "label IN %@", ["More", "Mehr"])).firstMatch
        let barButtons = app.navigationBars.buttons
        let more = named.exists ? named : barButtons.element(boundBy: max(barButtons.count - 1, 0))
        guard more.exists else { return false }
        more.tap()
        return button.waitForExistence(timeout: 10)
    }

    /// Kill and reopen. If the system already took the app down (a loaded
    /// simulator can), `terminate()` throws for want of a process, so launch
    /// the fresh copy directly; the state under test must survive either way.
    private func relaunch(journey: String = #function) {
        if app.state == .notRunning {
            QA.system("relaunch", journey: journey) { app.launch() }
        } else {
            QA.relaunch(app, journey: journey)
        }
    }

    /// The leaderboard header: "RANK" text, or the header button whose label
    /// starts with it, or the sort direction control.
    private func boardLoaded(timeout: TimeInterval? = nil) -> Bool {
        let rank = app.staticTexts.matching(NSPredicate(format: "label IN {'RANK','RANG'}")).firstMatch
        let header = app.buttons.matching(NSPredicate(
            format: "label BEGINSWITH 'RANK' OR label BEGINSWITH 'RANG' OR label IN {'Highest first','Lowest first','Höchster zuerst','Niedrigster zuerst'}")).firstMatch
        let deadline = Date().addingTimeInterval(timeout ?? loadTimeout)
        while Date() < deadline {
            if rank.exists || header.exists { return true }
            Thread.sleep(forTimeInterval: 0.5)
        }
        return rank.exists || header.exists
    }

    /// Searches Stats for `query`, opens the matching player and clears the
    /// trial pitch if one covers the profile.
    private func openProfile(_ query: String, as name: String, checkpoint: Bool = false, journey: String = #function) {
        let chip = QA.button(app, "Search players or teams", "Spieler oder Teams suchen")
        QA.tap(chip, in: app, "Search")
        // Every tab stays mounted; type into the field that is on screen.
        let fields = app.textFields
        var field: XCUIElement?
        let fieldDeadline = Date().addingTimeInterval(10)
        while field == nil, Date() < fieldDeadline {
            field = fields.allElementsBoundByIndex.first { $0.exists && $0.isHittable }
            if field == nil { Thread.sleep(forTimeInterval: 0.3) }
        }
        guard let field else {
            XCTFail("The search field should open")
            return
        }
        QA.type(query, into: field, in: app)

        // The name also labels hidden copies of the row, so tap the first match
        // that is on screen.
        let rows = app.buttons.matching(NSPredicate(format: "label MATCHES %@", "^([0-9]+, )?\(name),.*"))
        var row: XCUIElement?
        let deadline = Date().addingTimeInterval(20)
        while row == nil, Date() < deadline {
            row = rows.allElementsBoundByIndex.first { $0.exists && $0.isHittable }
            if row == nil { Thread.sleep(forTimeInterval: 0.5) }
        }
        XCTAssertNotNil(row, "Search should find \(name)")
        row?.tap()

        let maybeLater = QA.button(app, "Maybe later", "Vielleicht später")
        if maybeLater.waitForExistence(timeout: 4) {
            QA.checkpoint(app, "trial-pitch", journey: journey)
            maybeLater.tap()
            XCTAssertTrue(maybeLater.waitForNonExistence(timeout: 10), "The trial pitch should dismiss")
        }
        XCTAssertTrue(followButton(name).waitForExistence(timeout: 30), "\(name)'s profile should open")
        if checkpoint { QA.checkpoint(app, "profile", journey: journey) }
    }

    private func follow(_ name: String) -> XCUIElement {
        QA.button(app, "Follow \(name)", "\(name) folgen")
    }

    private func unfollow(_ name: String) -> XCUIElement {
        QA.button(app, "Unfollow \(name)", "\(name) nicht mehr folgen")
    }

    private func followButton(_ name: String) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label IN %@", [
            "Follow \(name)", "Unfollow \(name)", "\(name) folgen", "\(name) nicht mehr folgen",
        ])).firstMatch
    }

    private func followedRow(_ name: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", name)).firstMatch
    }
}
