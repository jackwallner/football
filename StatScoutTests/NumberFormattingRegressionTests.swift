import Foundation
import XCTest
@testable import Gridiron_StatScout

/// Display formatting that the localization pass broke between 1.2.7 and 1.2.8.
final class NumberFormattingRegressionTests: XCTestCase {
    private let us = Locale(identifier: "en_US")
    private let de = Locale(identifier: "de_DE")

    private func englishBundle() -> Bundle {
        let hosts = [Bundle.main] + Bundle.allBundles
        for host in hosts {
            if let path = host.path(forResource: "en", ofType: "lproj"),
               let bundle = Bundle(path: path) {
                return bundle
            }
        }
        return .main
    }

    // Game Detail rates arrive as 0-1 fractions ("success_rate": 0.438).
    func testGameDetailPercentRateIsNotZero() {
        let text = GameDetailView.RateStyle.percent.format(0.438)
        XCTAssertEqual(text, LocalizedNumber.fixed(43.8, fractionDigits: 0, percent: true))
        XCTAssertEqual(LocalizedNumber.fixed(43.8, fractionDigits: 0, percent: true, locale: us), "44%")
        XCTAssertEqual(LocalizedNumber.fixed(43.8, fractionDigits: 0, percent: true, locale: de), "44\u{00A0}%")
    }

    func testEnglishTabAndColumnLabelsDropTheDisambiguator() {
        let english = englishBundle()
        XCTAssertEqual(String(localized: "Games tab", bundle: english), "Games")
        XCTAssertEqual(String(localized: "Stats tab", bundle: english), "Stats")
        XCTAssertEqual(String(localized: "TEAM column", bundle: english), "TEAM")
    }

    func testSignedFeedValuesKeepThePlusSign() {
        XCTAssertEqual(LocalizedNumber.text("+4.1", locale: us), "+4.1")
        XCTAssertEqual(LocalizedNumber.text("+4.1", locale: de), "+4,1")
        XCTAssertEqual(LocalizedNumber.text("+4.5%", locale: us), "+4.5%")
        XCTAssertEqual(LocalizedNumber.text("+4.5%", locale: de), "+4,5\u{00A0}%")
        XCTAssertEqual(LocalizedNumber.text("-0.8", locale: us), "-0.8")
        XCTAssertEqual(LocalizedNumber.text("-0.8", locale: de), "-0,8")
    }

    func testRecentPerGameValuesKeepTheSuffixOnEveryBranch() {
        // "/g" follows the UI language ("/Spiel" in German).
        let perGame = String(localized: "\("")/g")
        XCTAssertEqual(LocalizedNumber.text(FeedNumber.perGame(0.7), locale: us), "0.7" + perGame)
        XCTAssertEqual(LocalizedNumber.text(FeedNumber.perGame(5.3), locale: us), "5.3" + perGame)
        XCTAssertEqual(LocalizedNumber.text(FeedNumber.perGame(12), locale: us), "12" + perGame)
        XCTAssertEqual(LocalizedNumber.text(FeedNumber.perGame(0.7), locale: de), "0,7" + perGame)
        XCTAssertEqual(LocalizedNumber.text(FeedNumber.perGame(87.4), locale: de), "87" + perGame)
    }

    // Recent rows go through `Metric.displayValue`, so the stored value must be
    // feed shaped: a value already formatted for a decimal-comma locale ("4,1")
    // is re-read as 41.
    func testRecentValuesAreLocalizedOnce() {
        XCTAssertEqual(FeedNumber.fixed(4.1, format: "%.1f"), "4.1")
        XCTAssertEqual(FeedNumber.fixed(62.5, format: "%.1f%%"), "62.5%")
        XCTAssertEqual(FeedNumber.perGame(0.7), "0.7/g")
        XCTAssertEqual(LocalizedNumber.text(FeedNumber.fixed(4.1, format: "%.1f"), locale: de), "4,1")
        XCTAssertEqual(LocalizedNumber.text(FeedNumber.fixed(62.5, format: "%.1f%%"), locale: de), "62,5\u{00A0}%")
        XCTAssertEqual(LocalizedNumber.text(FeedNumber.fixed(4.1, format: "%.1f"), locale: us), "4.1")
        XCTAssertEqual(LocalizedNumber.text(FeedNumber.fixed(62.5, format: "%.1f%%"), locale: us), "62.5%")

        XCTAssertEqual(FeedNumber.integer(3322), "3,322")
        XCTAssertEqual(LocalizedNumber.text(FeedNumber.integer(3322), locale: de), "3.322")
        XCTAssertEqual(LocalizedNumber.text(FeedNumber.integer(3322), locale: us), "3,322")

        let metric = Metric(id: "recent", label: "Cmp%", value: FeedNumber.fixed(62.5, format: "%.1f%%"),
                            percentile: 50, category: .passing)
        XCTAssertEqual(metric.displayValue, LocalizedNumber.fixed(62.5, fractionDigits: 1, percent: true))
    }
}
