import XCTest

/// Persona audit: a busy user interrupted mid-task. Each test leaves the app
/// (Home, then relaunch from a terminated process) and screenshots what is
/// waiting when they return. These record behaviour for the audit report; the
/// only hard assertions are that the app comes back and stays usable.
@MainActor
final class InterruptedUserUITests: XCTestCase {
    private let fixturePlayer = "Caleb Mercer"
    private let comparisonPlayer = "Mason Reed"

    override func setUp() {
        super.setUp()
        continueAfterFailure = false
    }

    // MARK: - Onboarding interrupted

    func testOnboardingInterruptedMidCarousel() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-ResetUITestState"]
        app.launch()
        XCTAssertTrue(app.buttons["Skip"].waitForExistence(timeout: 90), "Onboarding should show")
        capture(app, "a1_onboarding_page1")

        app.swipeLeft()
        sleep(1)
        capture(app, "a2_onboarding_page2")

        // Interruption 1: Home, then back (process alive).
        XCUIDevice.shared.press(.home)
        sleep(2)
        app.activate()
        sleep(2)
        capture(app, "a3_onboarding_after_background")

        // Interruption 2: process killed, relaunched without a reset.
        app.terminate()
        let again = XCUIApplication()
        again.launch()
        sleep(6)
        capture(again, "a4_onboarding_after_kill")
    }

    // MARK: - Stats: position + profile + scroll

    func testStatsContextAfterBackgroundAndKill() throws {
        let app = launchFixture(tab: "stats", reset: true)
        waitForText("League leaders", in: app)
        waitForText(fixturePlayer, in: app)

        let wr = app.buttons["WR"].firstMatch
        if wr.waitForExistence(timeout: 20) { wr.tap() }
        sleep(1)
        capture(app, "b1_stats_wr_selected")

        app.swipeUp()
        sleep(1)
        capture(app, "b2_stats_scrolled")

        // Open a profile from the (possibly scrolled) board.
        let row = app.buttons.matching(NSPredicate(format: "label MATCHES %@", "^[0-9]+, .*")).firstMatch
        if row.waitForExistence(timeout: 20) { row.tap() }
        sleep(2)
        capture(app, "b3_profile_open")

        XCUIDevice.shared.press(.home)
        sleep(2)
        app.activate()
        sleep(2)
        capture(app, "b4_profile_after_background")

        app.terminate()
        let again = launchFixture(tab: nil, reset: false)
        sleep(8)
        capture(again, "b5_stats_after_kill")
    }

    // MARK: - Compare: half-built comparison

    func testCompareAfterBackgroundAndKill() throws {
        let app = launchFixture(tab: "stats", reset: true)
        let row = app.buttons.matching(
            NSPredicate(format: "label MATCHES %@", "^[0-9]+, \(fixturePlayer),.*")
        ).firstMatch
        XCTAssertTrue(row.waitForExistence(timeout: 90))
        row.tap()
        let compare = app.buttons["Compare with another player"]
        XCTAssertTrue(compare.waitForExistence(timeout: 30))
        compare.tap()
        let other = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", comparisonPlayer)).firstMatch
        XCTAssertTrue(other.waitForExistence(timeout: 30))
        capture(app, "c1_comparison_picker")
        other.tap()
        waitForText("Player Comparison", in: app)
        capture(app, "c2_comparison_open")

        XCUIDevice.shared.press(.home)
        sleep(2)
        app.activate()
        sleep(2)
        capture(app, "c3_comparison_after_background")

        app.terminate()
        let again = launchFixture(tab: "compare", reset: false)
        sleep(8)
        capture(again, "c4_compare_tab_after_kill")
    }

    // MARK: - Teams: favourite team re-entry

    func testTeamsTabAfterKill() throws {
        let app = launchFixture(tab: "teams", reset: true)
        sleep(8)
        capture(app, "d1_teams_first_open")
        app.terminate()
        let again = launchFixture(tab: "teams", reset: false)
        sleep(8)
        capture(again, "d2_teams_after_kill")
    }

    // MARK: - Deep stack: getting back to the top

    func testReselectingTabDoesNotPopToRoot() throws {
        let app = launchFixture(tab: "stats", reset: true)
        let row = app.buttons.matching(
            NSPredicate(format: "label MATCHES %@", "^[0-9]+, \(fixturePlayer),.*")
        ).firstMatch
        XCTAssertTrue(row.waitForExistence(timeout: 90))
        row.tap()
        waitForText("ADVANCED PERCENTILES", in: app)
        capture(app, "e1_profile_before_tab_retap")

        // A returning user who is lost tries the iOS habit: tap the current tab.
        let statsTab = app.buttons["Stats"].firstMatch
        XCTAssertTrue(statsTab.waitForExistence(timeout: 10))
        statsTab.tap()
        sleep(2)
        capture(app, "e2_after_tab_retap")
        let stillOnProfile = app.staticTexts["ADVANCED PERCENTILES"].exists
        XCTContext.runActivity(named: "still on profile after re-tapping Stats: \(stillOnProfile)") { _ in }
    }

    // MARK: - Helpers

    private func launchFixture(tab: String?, reset: Bool) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchEnvironment = [
            "FORCE_PRO": "1",
            "STATSCOUT_FORCE_PRO": "1",
            "SCREENSHOT_MODE": "1",
        ]
        var args = [
            "-ScreenshotData",
            "-hasCompletedOnboarding", "YES",
            "-stats.board", "advanced",
            "-stats.qualifier", "All Players",
        ]
        if reset { args.append("-ResetUITestState") }
        if let tab { args += ["-StartTab", tab] }
        app.launchArguments = args
        app.launch()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 30))
        return app
    }

    private func waitForText(_ text: String, in app: XCUIApplication, timeout: TimeInterval = 90) {
        let element = app.descendants(matching: .any).matching(
            NSPredicate(format: "label == %@ OR value == %@", text, text)
        ).firstMatch
        XCTAssertTrue(element.waitForExistence(timeout: timeout), "Expected \(text) to appear")
    }

    private func capture(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
