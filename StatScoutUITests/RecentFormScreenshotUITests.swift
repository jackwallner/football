import XCTest

/// Drives the live app (Pro forced on) to a hitter's profile and screenshots the
/// Recent Form card so its bar layout can be eyeballed against the season card.
@MainActor
final class RecentFormScreenshotUITests: XCTestCase {
    func testCaptureRecentForm() throws {
        let app = XCUIApplication()
        app.launchEnvironment["FORCE_PRO"] = "1"
        app.launch()

        // Dismiss onboarding (Skip on early cards, Get Started on the last).
        let skip = app.buttons["Skip"]
        let getStarted = app.buttons["Get Started"]
        _ = skip.waitForExistence(timeout: 10) || getStarted.waitForExistence(timeout: 1)
        for _ in 0..<4 {
            if skip.exists { skip.tap(); break }
            if getStarted.exists { getStarted.tap(); break }
            app.swipeLeft()
        }

        // Player rows are buttons labelled "<rank>, <name>, <pos>, <team>, <stat>"
        // on both the leaderboard and metric-ranking lists. Tap the first one.
        // The top of any hitting list is a qualified hitter with game logs.
        let playerRow = app.buttons.matching(
            NSPredicate(format: "label MATCHES %@", "^[0-9]+,.*")
        ).firstMatch
        XCTAssertTrue(playerRow.waitForExistence(timeout: 40), "A player row should load with live data")
        playerRow.tap()

        // Recent Form sits below the percentile card; it exists in the a11y tree
        // even off-screen, so scroll until it's actually hittable.
        let recentForm = app.staticTexts["RECENT FORM"]
        XCTAssertTrue(recentForm.waitForExistence(timeout: 10), "Recent Form card should exist on the profile")
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
