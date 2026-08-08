import XCTest
@testable import Gridiron_StatScout

/// Guards the copy App Review rejected 1.0 (19) over, Guideline 3.1.2(c):
///
/// > The auto-renewable subscription promotes the free trial, introductory
/// > pricing, or introductory period for the subscription more clearly and
/// > conspicuously than the billed amount.
///
/// The price was never missing. It was outranked: the cited sheet's button read
/// "Start 7-day free trial" in the largest, highest-contrast type on the
/// screen, while "$9.99 / year" sat in grey micro text beneath it, and the
/// disclosure led with the trial as well.
///
/// The fix is scoped to that sheet. `PriceEmphasis.trialFirst` is what every
/// other surface here and in the approved baseball build ships, and it stays
/// that way deliberately: you fix what was cited and leave what shipped alone.
/// Both orderings are pinned below so neither can drift into the other.
final class PriceDisclosureTests: XCTestCase {

    private let renewSentence = "Auto-renews unless cancelled at least 24 hours before the end of the current period."
    private let cancelSentence = "Manage or cancel in Settings › Apple ID › Subscriptions."

    // MARK: - The cited sheet: billed amount first

    func testCitedSheetCTANamesTheBilledAmountAndNotTheTrial() {
        let label = StoreService.directCTALabel(
            price: "$9.99 / year", trial: "7-day free trial",
            isWinback: false, emphasis: .billedAmountFirst
        )
        XCTAssertEqual(label, "Subscribe for $9.99 / year")
        XCTAssertFalse(label.lowercased().contains("trial"), "The cited CTA must not promote the trial: \(label)")
        XCTAssertFalse(label.lowercased().contains("free"), "The cited CTA must not promote the trial: \(label)")
    }

    func testCitedSheetDisclosureLeadsWithTheBilledAmount() {
        let text = StoreService.disclosureText(
            price: "$9.99 / year", isSubscription: true,
            trial: "7-day free trial", emphasis: .billedAmountFirst
        )
        XCTAssertEqual(
            text,
            "$9.99 / year, billed after a 7-day free trial. \(renewSentence) \(cancelSentence)"
        )
        XCTAssertLessThan(
            text.range(of: "$9.99 / year")!.lowerBound,
            text.range(of: "7-day free trial")!.lowerBound,
            "The trial must be subordinate in position"
        )
    }

    /// No branch of the cited emphasis may omit the price or advertise a trial.
    func testBilledAmountFirstNeverPromotesTheTrial() {
        for price in ["$9.99 / year", "$1.99 / month"] {
            for trial in [nil, "7-day free trial"] {
                for isWinback in [false, true] {
                    let label = StoreService.directCTALabel(
                        price: price, trial: trial, isWinback: isWinback, emphasis: .billedAmountFirst
                    )
                    XCTAssertTrue(label.contains(price), "\"\(label)\" omits the billed amount")
                    XCTAssertFalse(
                        label.lowercased().contains("free") || label.lowercased().contains("trial"),
                        "\"\(label)\" promotes the trial in the most prominent element on screen"
                    )
                }
            }
        }
    }

    // MARK: - Everywhere else: unchanged from the approved build

    func testUncitedSurfacesKeepTheApprovedCopy() {
        XCTAssertEqual(
            StoreService.directCTALabel(price: "$9.99 / year", trial: "7-day free trial", isWinback: false),
            "Start 7-day free trial"
        )
        XCTAssertEqual(
            StoreService.directCTALabel(price: "$9.99 / year", trial: nil, isWinback: false),
            "Try StatScout+ for $9.99 / year"
        )
        XCTAssertEqual(
            StoreService.disclosureText(price: "$9.99 / year", isSubscription: true, trial: "7-day free trial"),
            "7-Day Free Trial, then $9.99 / year. \(renewSentence) \(cancelSentence)"
        )
    }

    /// A lapsed subscriber has already used the trial, so a win-back never
    /// offers it again whatever the emphasis.
    func testWinbackNeverOffersTheTrial() {
        for emphasis in [PriceEmphasis.trialFirst, .billedAmountFirst] {
            let label = StoreService.directCTALabel(
                price: "$9.99 / year", trial: "7-day free trial", isWinback: true, emphasis: emphasis
            )
            XCTAssertTrue(label.contains("$9.99 / year"), label)
            XCTAssertFalse(label.lowercased().contains("free trial"), label)
        }
    }

    // MARK: - True of both orderings

    func testSubscriptionWithoutATrialAlwaysLeadsWithThePrice() {
        for emphasis in [PriceEmphasis.trialFirst, .billedAmountFirst] {
            let text = StoreService.disclosureText(
                price: "$1.99 / month", isSubscription: true, trial: nil, emphasis: emphasis
            )
            XCTAssertTrue(text.hasPrefix("$1.99 / month."), text)
            XCTAssertTrue(text.contains(renewSentence), text)
            XCTAssertTrue(text.contains(cancelSentence), text)
        }
    }

    /// A non-consumable must not claim to auto-renew, either way.
    func testLifetimeIsNeverDescribedAsASubscription() {
        for emphasis in [PriceEmphasis.trialFirst, .billedAmountFirst] {
            let text = StoreService.disclosureText(
                price: "$19.99", isSubscription: false, trial: nil, emphasis: emphasis
            )
            XCTAssertEqual(text, "$19.99. One-time purchase. Lifetime access, no subscription.")
            XCTAssertFalse(text.contains("Auto-renews"), text)
        }
    }

    /// Whatever the emphasis, the price and the renewal terms are both present.
    func testEveryDisclosureStatesThePriceAndTheTerms() {
        let cases: [(price: String, isSubscription: Bool, trial: String?)] = [
            ("$9.99 / year", true, "7-day free trial"),
            ("$9.99 / year", true, nil),
            ("$1.99 / month", true, "3-day free trial"),
            ("$19.99", false, nil)
        ]
        for c in cases {
            for emphasis in [PriceEmphasis.trialFirst, .billedAmountFirst] {
                let text = StoreService.disclosureText(
                    price: c.price, isSubscription: c.isSubscription, trial: c.trial, emphasis: emphasis
                )
                XCTAssertTrue(text.contains(c.price), "\"\(text)\" omits the price")
                if c.isSubscription {
                    XCTAssertTrue(text.contains(renewSentence), "\"\(text)\" omits the renewal terms")
                    XCTAssertTrue(text.contains(cancelSentence), "\"\(text)\" omits how to cancel")
                }
                if let trial = c.trial {
                    XCTAssertTrue(text.lowercased().contains(trial.lowercased()), "\"\(text)\" omits the trial")
                }
            }
        }
    }

    // MARK: - Legal links

    func testLegalLinksResolve() {
        XCTAssertEqual(
            StatScoutLegal.termsURL.absoluteString,
            "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/"
        )
        XCTAssertEqual(StatScoutLegal.privacyURL.scheme, "https")
        XCTAssertFalse(StatScoutLegal.privacyURL.host?.isEmpty ?? true)
    }
}
