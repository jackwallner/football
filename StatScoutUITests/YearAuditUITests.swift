import XCTest

final class YearAuditUITests: XCTestCase {
    var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launch()
    }

    func testAllYearsSelectable() throws {
        // Wait for initial load - use the leaderboard table header as the anchor since
        // the redesigned top bar no longer surfaces a "LEADERBOARD" section title.
        let header = app.staticTexts["RANK"]
        XCTAssertTrue(header.waitForExistence(timeout: 15), "Leaderboard should appear")

        // Test key years
        let yearsToTest = [2026, 2025, 2024]
        var yearDataFound: [Int: [String]] = [:]

        for year in yearsToTest {
            print("Testing year: \(year)")

            // Open year picker
            let yearPicker = app.buttons.containing(NSPredicate(format: "label MATCHES '^[0-9]{4}$'")).firstMatch
            if yearPicker.exists {
                yearPicker.tap()
            } else {
                app.coordinate(withNormalizedOffset: CGVector(dx: 0.75, dy: 0.15)).tap()
            }
            sleep(2)

            // Tap the year
            let yearString = String(year)
            let yearButton = app.buttons[yearString]

            if yearButton.exists {
                if yearButton.isHittable {
                    yearButton.tap()
                } else {
                    // Estimate position
                    let offset = Double(2026 - year)
                    let yPos = 0.25 + (offset * 0.045)
                    app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: min(0.7, yPos))).tap()
                }
            } else {
                print("Year \(year) not found in picker")
                app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.8)).tap()
                sleep(1)
                continue
            }

            sleep(5) // Wait for data load

            // Take screenshot
            let screenshot = XCUIScreen.main.screenshot()
            let attachment = XCTAttachment(screenshot: screenshot)
            attachment.name = "Year_\(year)_Leaderboard"
            attachment.lifetime = .keepAlways
            add(attachment)

            // Check for known player names based on year
            var foundPlayers: [String] = []
            let expectedPlayers: [String]
            if year == 2026 {
                expectedPlayers = ["Aaron Judge", "Shohei Ohtani", "Bobby Witt Jr.", "Paul Skenes"]
            } else if year == 2025 {
                expectedPlayers = ["Juan Soto", "Vladimir Guerrero Jr."]
            } else {
                expectedPlayers = [] // No data for other years in sample
            }

            for player in expectedPlayers {
                if app.staticTexts[player].exists {
                    foundPlayers.append(player)
                }
            }

            yearDataFound[year] = foundPlayers
            print("Year \(year): Found \(foundPlayers.count) players: \(foundPlayers)")

            sleep(2)
        }

        // Verify year switching works by checking different years have different players
        print("\n=== YEAR AUDIT SUMMARY ===")
        for year in yearDataFound.keys.sorted() {
            let players = yearDataFound[year] ?? []
            print("Year \(year): \(players.count) players - \(players)")
        }

        // Verify 2026 and 2025 have different data
        let players2026 = yearDataFound[2026] ?? []
        let players2025 = yearDataFound[2025] ?? []

        XCTAssertGreaterThan(players2026.count, 0, "2026 should have players")
        XCTAssertGreaterThan(players2025.count, 0, "2025 should have players")

        // Verify the player sets are different
        let set2026 = Set(players2026)
        let set2025 = Set(players2025)
        let intersection = set2026.intersection(set2025)

        print("Common players between 2026 and 2025: \(intersection)")
        XCTAssertNotEqual(set2026, set2025, "2026 and 2025 should have different player sets")
    }
}
