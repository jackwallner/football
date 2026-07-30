import XCTest

/// Drives the live app (Pro forced on) to a player's profile and screenshots the
/// Recent Form card so its bar layout can be eyeballed against the season card.
///
/// Was written for the baseball build ("a hitter's profile", "any hitting list").
/// The mechanics carry over unchanged - the football leaderboard labels its rows
/// the same way - but the waits had to grow: this runs against a debug build that
/// decodes the 33k-row bundled snapshot unoptimised, and the old 40s row timeout
/// expired before the board existed, which surfaced as the unhelpful
/// "application is not running".
@MainActor
final class RecentFormScreenshotUITests: XCTestCase {
    func testCaptureRecentForm() throws {
        let app = XCUIApplication()
        app.launchEnvironment["FORCE_PRO"] = "1"
        // Skip onboarding by setting the flag rather than driving the pager.
        // The old version swiped through it in a loop while the app was still
        // decoding the bundled snapshot, and the combination of long implicit
        // waits and synthesised swipes is what produced "Failed to get background
        // assertion for target app" - a flake in the harness, not the app.
        app.launchArguments += ["-hasCompletedOnboarding", "YES"]
        app.launch()

        // Player rows are buttons labelled "<rank>, <name>, <pos>, <team>, <stat>"
        // on both the leaderboard and metric-ranking lists. Tap the first one.
        // The top of any position board is a qualified player with game logs.
        let playerRow = app.buttons.matching(
            NSPredicate(format: "label MATCHES %@", "^[0-9]+,.*")
        ).firstMatch
        XCTAssertTrue(playerRow.waitForExistence(timeout: 150), "A player row should load with live data")
        playerRow.tap()

        // Recent Form sits below the percentile card; it exists in the a11y tree
        // even off-screen, so scroll until it's actually hittable.
        let recentForm = app.staticTexts["RECENT FORM"]
        XCTAssertTrue(recentForm.waitForExistence(timeout: 30), "Recent Form card should exist on the profile")
        var tries = 0
        while !recentForm.isHittable && tries < 10 {
            app.swipeUp()
            tries += 1
        }
        XCTAssertTrue(recentForm.isHittable, "Recent Form card should be scrolled into view")

        // Let game logs load and the bars render.
        sleep(5)

        let shot = XCTAttachment(screenshot: app.screenshot())
        shot.name = "RecentForm"
        shot.lifetime = .keepAlways
        add(shot)
    }
}
