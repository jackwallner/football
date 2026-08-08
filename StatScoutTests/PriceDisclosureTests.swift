import XCTest
@testable import Gridiron_StatScout

/// Guards the copy Apple rejected the first submission over.
///
/// The bug was not a missing string, it was two strings that could disagree:
/// the onboarding CTA hard-coded "Continue with StatScout+" while the
/// disclosure beside it came from the store and was nil until products loaded.
/// So a button that charges the card could render with no price anywhere on
/// screen. Everything here is a pure function precisely so this stays provable
/// without configuring RevenueCat in the simulator.
final class PriceDisclosureTests: XCTestCase {

    private let renewSentence = "Auto-renews unless cancelled at least 24 hours before the end of the current period."
    private let cancelSentence = "Manage or cancel in Settings › Apple ID › Subscriptions."

    // MARK: - CTA labels

    func testTrialLabelNamesTheTrial() {
        let label = StoreService.directCTALabel(price: "$14.99 / year", trial: "7-day free trial", isWinback: false)
        XCTAssertEqual(label, "Start 7-day free trial")
    }

    func testPaidLabelNamesThePrice() {
        let label = StoreService.directCTALabel(price: "$14.99 / year", trial: nil, isWinback: false)
        XCTAssertEqual(label, "Try StatScout+ for $14.99 / year")
    }

    /// A lapsed subscriber has already used the trial, so a win-back that
    /// offered it again would promise something the App Store will not grant.
    func testWinbackNeverOffersTheTrialAndStillNamesThePrice() {
        let label = StoreService.directCTALabel(price: "$14.99 / year", trial: "7-day free trial", isWinback: true)
        XCTAssertEqual(label, "Restart StatScout+ for $14.99 / year")
    }

    /// The regression itself: no branch of a transacting label may be generic.
    func testEveryCTALabelNamesEitherAPriceOrATrial() {
        let price = "$14.99 / year"
        for trial in [nil, "7-day free trial"] {
            for isWinback in [false, true] {
                let label = StoreService.directCTALabel(price: price, trial: trial, isWinback: isWinback)
                XCTAssertTrue(
                    label.contains(price) || label.contains("free trial"),
                    "CTA \"\(label)\" states neither the price nor the trial (trial: \(trial ?? "none"), winback: \(isWinback))"
                )
                XCTAssertFalse(
                    label == "Continue" || label == "Continue with StatScout+",
                    "A button that transacts must never read as a bare Continue"
                )
            }
        }
    }

    // MARK: - Disclosure

    func testSubscriptionWithTrialStatesTrialThenPriceThenTerms() {
        let text = StoreService.disclosureText(price: "$14.99 / year", isSubscription: true, trial: "7-day free trial")
        // `.capitalized` title-cases the trial phrase, which is what the
        // approved build ships and what App Review saw.
        XCTAssertEqual(
            text,
            "7-Day Free Trial, then $14.99 / year. \(renewSentence) \(cancelSentence)"
        )
    }

    func testSubscriptionWithoutTrialLeadsWithThePrice() {
        let text = StoreService.disclosureText(price: "$1.99 / month", isSubscription: true, trial: nil)
        XCTAssertTrue(text.hasPrefix("$1.99 / month."), text)
        XCTAssertTrue(text.contains(renewSentence), text)
        XCTAssertTrue(text.contains(cancelSentence), text)
    }

    /// A non-consumable must not claim to auto-renew.
    func testLifetimeIsNotDescribedAsASubscription() {
        let text = StoreService.disclosureText(price: "$29.99", isSubscription: false, trial: nil)
        XCTAssertEqual(text, "$29.99. One-time purchase. Lifetime access, no subscription.")
        XCTAssertFalse(text.contains("Auto-renews"), text)
    }

    /// Whatever the offer, the disclosure states the price, and for anything
    /// recurring it also states that it renews and how to stop it.
    func testEveryDisclosureStatesThePriceAndRecurringOnesStateTheTerms() {
        let cases: [(price: String, isSubscription: Bool, trial: String?)] = [
            ("$14.99 / year", true, "7-day free trial"),
            ("$14.99 / year", true, nil),
            ("$1.99 / month", true, "3-day free trial"),
            ("$1.99 / month", true, nil),
            ("$29.99", false, nil)
        ]
        for c in cases {
            let text = StoreService.disclosureText(price: c.price, isSubscription: c.isSubscription, trial: c.trial)
            XCTAssertTrue(text.contains(c.price), "\"\(text)\" omits the price \(c.price)")
            if c.isSubscription {
                XCTAssertTrue(text.contains(renewSentence), "\"\(text)\" omits the renewal terms")
                XCTAssertTrue(text.contains(cancelSentence), "\"\(text)\" omits how to cancel")
            }
            if let trial = c.trial {
                XCTAssertTrue(
                    text.lowercased().contains(trial.lowercased()),
                    "\"\(text)\" omits the trial length"
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
