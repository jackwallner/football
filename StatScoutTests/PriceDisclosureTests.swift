import XCTest
@testable import Gridiron_StatScout

/// Guards the copy App Review rejected 1.0 (19) over, Guideline 3.1.2(c):
///
/// > The auto-renewable subscription promotes the free trial, introductory
/// > pricing, or introductory period for the subscription more clearly and
/// > conspicuously than the billed amount.
///
/// The price was never missing. It was outranked: the CTA read "Start 7-day
/// free trial" in the largest, highest-contrast type on the screen, while
/// "$9.99 / year" sat in grey micro text beneath it, and on the plan card the
/// billed amount was rendered smaller and greyer than the calculated per-month
/// figure below it. The rule these tests encode is that the amount charged
/// leads every string, and the trial is only ever mentioned after it.
///
/// Type sizes and colours are asserted by eye against the screenshots the
/// `-MockProducts` harness produces; what is checkable here is the ordering
/// and the presence of the billed amount, which is where the regression lived.
final class PriceDisclosureTests: XCTestCase {

    private let renewSentence = "Auto-renews unless cancelled at least 24 hours before the end of the current period."
    private let cancelSentence = "Manage or cancel in Settings › Apple ID › Subscriptions."

    // MARK: - CTA labels

    /// The button carries the biggest text in the flow, so it must name the
    /// billed amount, and only the billed amount.
    func testCTANamesTheBilledAmountAndNotTheTrial() {
        let label = StoreService.directCTALabel(price: "$9.99 / year", isWinback: false)
        XCTAssertEqual(label, "Subscribe for $9.99 / year")
        XCTAssertFalse(label.lowercased().contains("trial"), "The CTA must not promote the trial: \(label)")
        XCTAssertFalse(label.lowercased().contains("free"), "The CTA must not promote the trial: \(label)")
    }

    func testWinbackAlsoLeadsWithTheBilledAmount() {
        let label = StoreService.directCTALabel(price: "$9.99 / year", isWinback: true)
        XCTAssertEqual(label, "Restart for $9.99 / year")
    }

    /// The regression, stated as a rule: no CTA branch may omit the price or
    /// advertise the trial, whatever the offer behind it.
    func testNoCTABranchPromotesTheTrialOverThePrice() {
        for price in ["$9.99 / year", "$1.99 / month", "$19.99"] {
            for isWinback in [false, true] {
                let label = StoreService.directCTALabel(price: price, isWinback: isWinback)
                XCTAssertTrue(label.contains(price), "\"\(label)\" omits the billed amount")
                XCTAssertFalse(
                    label.lowercased().contains("free") || label.lowercased().contains("trial"),
                    "\"\(label)\" promotes the trial in the most prominent element on screen"
                )
            }
        }
    }

    // MARK: - Disclosure

    /// The price comes first in the sentence, the trial second.
    func testDisclosureLeadsWithTheBilledAmountNotTheTrial() {
        let text = StoreService.disclosureText(price: "$9.99 / year", isSubscription: true, trial: "7-day free trial")
        XCTAssertEqual(
            text,
            "$9.99 / year, billed after a 7-day free trial. \(renewSentence) \(cancelSentence)"
        )
        XCTAssertTrue(text.hasPrefix("$9.99 / year"), "The billed amount must lead: \(text)")

        let priceIndex = text.range(of: "$9.99 / year")!.lowerBound
        let trialIndex = text.range(of: "7-day free trial")!.lowerBound
        XCTAssertLessThan(priceIndex, trialIndex, "The trial must be subordinate in position")
    }

    func testSubscriptionWithoutTrialLeadsWithThePrice() {
        let text = StoreService.disclosureText(price: "$1.99 / month", isSubscription: true, trial: nil)
        XCTAssertTrue(text.hasPrefix("$1.99 / month."), text)
        XCTAssertTrue(text.contains(renewSentence), text)
        XCTAssertTrue(text.contains(cancelSentence), text)
    }

    /// A non-consumable must not claim to auto-renew.
    func testLifetimeIsNotDescribedAsASubscription() {
        let text = StoreService.disclosureText(price: "$19.99", isSubscription: false, trial: nil)
        XCTAssertEqual(text, "$19.99. One-time purchase. Lifetime access, no subscription.")
        XCTAssertFalse(text.contains("Auto-renews"), text)
    }

    /// Whatever the offer, the disclosure opens on the price, states the
    /// renewal terms for anything recurring, and never puts the trial first.
    func testEveryDisclosureOpensOnTheBilledAmount() {
        let cases: [(price: String, isSubscription: Bool, trial: String?)] = [
            ("$9.99 / year", true, "7-day free trial"),
            ("$9.99 / year", true, nil),
            ("$1.99 / month", true, "3-day free trial"),
            ("$1.99 / month", true, nil),
            ("$19.99", false, nil)
        ]
        for c in cases {
            let text = StoreService.disclosureText(price: c.price, isSubscription: c.isSubscription, trial: c.trial)
            XCTAssertTrue(text.hasPrefix(c.price), "\"\(text)\" does not open on the billed amount")
            if c.isSubscription {
                XCTAssertTrue(text.contains(renewSentence), "\"\(text)\" omits the renewal terms")
                XCTAssertTrue(text.contains(cancelSentence), "\"\(text)\" omits how to cancel")
            }
            if let trial = c.trial {
                XCTAssertTrue(text.lowercased().contains(trial.lowercased()), "\"\(text)\" omits the trial length")
                XCTAssertLessThan(
                    text.range(of: c.price)!.lowerBound,
                    text.range(of: trial)!.lowerBound,
                    "\"\(text)\" puts the trial ahead of the price"
                )
            }
        }
    }

    // MARK: - Legal links

    /// Apple requires a functional EULA link at the purchase point. Both of
    /// these are rendered next to every CTA that transacts.
    func testLegalLinksResolve() {
        XCTAssertEqual(
            StatScoutLegal.termsURL.absoluteString,
            "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/"
        )
        XCTAssertEqual(StatScoutLegal.privacyURL.scheme, "https")
        XCTAssertFalse(StatScoutLegal.privacyURL.host?.isEmpty ?? true)
    }
}
