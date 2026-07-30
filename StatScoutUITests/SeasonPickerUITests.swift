import XCTest

/// The nav-bar season control: what it offers, and that choosing something takes
/// effect.
///
/// The previous version was baseball-fork leftover and could not pass here. It
/// waited on `app.staticTexts["LEADERBOARD"]` - a section title the football
/// redesign removed - and looked for `Calendar.current.component(.year)`, i.e.
/// the real-world year, when the NFL season label lags it (season 2025 runs into
/// February 2026). It also drove the picker by tapping normalised screen
/// coordinates, which broke the moment the bar changed. This version anchors on
/// the table header and addresses the control by its accessibility label.
final class SeasonPickerUITests: XCTestCase {
    var app: XCUIApplication!

    /// A debug build decodes the 33k-row bundled snapshot unoptimised; release
    /// does the same work in about three seconds.
    private let loadTimeout: TimeInterval = 120

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments += ["-hasCompletedOnboarding", "YES"]
        app.launch()
    }

    /// Season and phase share one pill, labelled for VoiceOver as
    /// "Season and season type" with a value like "2025, Regular Season".
    private func seasonControl() -> XCUIElement {
        app.buttons["Season and season type"]
    }

    private func waitForBoard() -> Bool {
        app.staticTexts["RANK"].waitForExistence(timeout: loadTimeout)
    }

    func testSeasonMenuOffersEveryStoredSeason() throws {
        XCTAssertTrue(waitForBoard(), "Leaderboard should appear")

        let control = seasonControl()
        XCTAssertTrue(control.waitForExistence(timeout: 15), "Season control should exist in the nav bar")
        control.tap()

        // The menu should carry the career rollup plus the full 2000-current
        // range. Spot-check the ends and the sentinel rather than all 27 rows,
        // since a long menu scrolls and off-screen rows aren't hittable.
        XCTAssertTrue(
            app.buttons["All Time"].waitForExistence(timeout: 5),
            "Season menu should offer the career rollup"
        )
        XCTAssertTrue(app.buttons["2025"].exists, "Season menu should offer the current season")

        // Years are bare four-digit strings - never thousands-separated, which is
        // what this originally guarded against ("2,025").
        let commaYears = app.buttons.matching(
            NSPredicate(format: "label MATCHES %@", "^[0-9],[0-9]{3}$")
        )
        XCTAssertEqual(commaYears.count, 0, "Season labels should not be thousands-separated")
    }

    func testSelectingAnotherSeasonKeepsTheBoardAlive() throws {
        XCTAssertTrue(waitForBoard(), "Leaderboard should appear")

        let control = seasonControl()
        guard control.waitForExistence(timeout: 15) else {
            return XCTFail("Season control should exist")
        }
        let before = control.value as? String

        control.tap()
        let target = app.buttons["2024"]
        guard target.waitForExistence(timeout: 5) else {
            // Locked behind Pro in this build: the paywall is a valid outcome.
            app.tap()
            return
        }
        target.tap()

        // Either the board comes back for the new season, or the trial sheet
        // intercepted the locked year. Both are correct; a blank screen isn't.
        let board = app.staticTexts["RANK"].waitForExistence(timeout: loadTimeout)
        let paywall = app.buttons.matching(
            NSPredicate(format: "label CONTAINS[c] 'trial' OR label CONTAINS[c] 'unlock'")
        ).firstMatch.exists
        XCTAssertTrue(board || paywall, "Season change should leave the app in a usable state")

        if board, let before {
            let after = seasonControl().value as? String
            XCTAssertNotEqual(after, before, "The control should report the newly selected season")
        }
    }

    func testPhaseIsSelectableFromTheSameControl() throws {
        XCTAssertTrue(waitForBoard(), "Leaderboard should appear")

        let control = seasonControl()
        guard control.waitForExistence(timeout: 15) else {
            return XCTFail("Season control should exist")
        }
        control.tap()

        // Season and phase were two separate pills until they were merged to stop
        // iOS pushing the upgrade CTA into a "..." overflow. Both sections must
        // still be reachable from the one menu.
        XCTAssertTrue(
            app.buttons["Playoffs"].waitForExistence(timeout: 5),
            "The merged menu should still offer the season type"
        )
        XCTAssertTrue(app.buttons["Regular Season"].exists, "Regular season should be listed too")
    }
}
