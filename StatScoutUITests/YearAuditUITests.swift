import XCTest

/// Smoke test for the season selector: every season the menu offers should draw
/// a populated leaderboard, and switching seasons should actually change it.
///
/// This file used to assert that 2026 and 2025 contained Aaron Judge, Shohei
/// Ohtani, Paul Skenes and Juan Soto - baseball players, hardcoded, in the NFL
/// app. It was left behind by the fork and could never pass: the football build
/// has no 2026 season (current is 2025) and obviously no MLB rosters. It now
/// checks the invariant that was presumably meant all along - that the picker
/// works and each season has its own data - without naming a single player, so
/// it can't rot again the next time a roster changes.
final class YearAuditUITests: XCTestCase {
    var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments += ["-hasCompletedOnboarding", "YES"]
        app.launch()
    }

    /// The leaderboard's first data row, used as the fingerprint of a season.
    private func topPlayerName() -> String? {
        // Row cells expose the player's name as their first static text; the
        // header row ("RANK"/"PLAYER") is excluded by skipping known labels.
        let ignored: Set<String> = ["RANK", "PLAYER", "TEAM"]
        for index in 0..<min(app.staticTexts.count, 40) {
            let text = app.staticTexts.element(boundBy: index)
            guard text.exists else { continue }
            let label = text.label.trimmingCharacters(in: .whitespaces)
            guard !label.isEmpty,
                  !ignored.contains(label.uppercased()),
                  label.contains(" ")           // "Drake Maye", not "QB" or "0.31"
            else { continue }
            return label
        }
        return nil
    }

    private func openSeasonMenu() -> Bool {
        // The one nav-bar pill carries season and phase, e.g. "2025 · Regular".
        let pill = app.buttons.containing(
            NSPredicate(format: "label CONTAINS '·' OR label MATCHES '.*[0-9]{4}.*'")
        ).firstMatch
        guard pill.waitForExistence(timeout: 10) else { return false }
        pill.tap()
        return true
    }

    func testSeasonPickerLoadsEachSeason() throws {
        XCTAssertTrue(
            app.staticTexts["RANK"].waitForExistence(timeout: 60),
            "Leaderboard should appear once the bundled data has decoded"
        )

        // Three recent seasons - all free-or-Pro gated the same way in a debug
        // build, and recent enough to have the full metric set.
        let seasons = ["2025", "2024", "2023"]
        var fingerprints: [String: String] = [:]

        for season in seasons {
            guard openSeasonMenu() else {
                return XCTFail("Season pill not found")
            }

            let option = app.buttons[season]
            guard option.waitForExistence(timeout: 5) else {
                // A locked season in a non-Pro build opens the paywall instead;
                // that is a legitimate state, not a failure of the picker.
                app.tap()
                continue
            }
            option.tap()

            XCTAssertTrue(
                app.staticTexts["RANK"].waitForExistence(timeout: 30),
                "\(season) should still render a leaderboard"
            )
            // Give the board a moment to repopulate for the new season.
            let expectation = expectation(description: "settle \(season)")
            DispatchQueue.main.asyncAfter(deadline: .now() + 3) { expectation.fulfill() }
            wait(for: [expectation], timeout: 10)

            if let top = topPlayerName() {
                fingerprints[season] = top
            }
        }

        // At least two seasons must have produced a populated board, and they
        // must not be identical - that is what proves the selection took effect
        // rather than the same cached rows being redrawn.
        XCTAssertGreaterThanOrEqual(
            fingerprints.count, 2,
            "Expected at least two seasons to load players, got \(fingerprints)"
        )
        XCTAssertGreaterThan(
            Set(fingerprints.values).count, 1,
            "Switching season did not change the board: \(fingerprints)"
        )
    }

    /// All Time is a stored season like any other, so it should render a board
    /// too. Skipped rather than failed when it is paywalled in this build.
    func testAllTimeRendersOrIsPaywalled() throws {
        XCTAssertTrue(
            app.staticTexts["RANK"].waitForExistence(timeout: 60),
            "Leaderboard should appear"
        )
        guard openSeasonMenu() else {
            return XCTFail("Season pill not found")
        }

        let allTime = app.buttons["All Time"]
        guard allTime.waitForExistence(timeout: 5) else {
            return XCTFail("All Time should be offered in the season menu")
        }
        allTime.tap()

        // Either the career board draws, or the trial sheet intercepts. Both are
        // correct; a blank screen is not.
        let board = app.staticTexts["RANK"]
        let paywall = app.buttons.matching(
            NSPredicate(format: "label CONTAINS[c] 'trial' OR label CONTAINS[c] 'unlock'")
        ).firstMatch
        let appeared = board.waitForExistence(timeout: 30) || paywall.waitForExistence(timeout: 5)
        XCTAssertTrue(appeared, "All Time should show a board or the upgrade sheet")
    }
}
