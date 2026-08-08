import XCTest
@testable import Gridiron_StatScout

final class UpgradeCTATests: XCTestCase {
    /// Never "Try Free", trial or not. The entry-point pill appears on every
    /// screen, so a trial promoted there is the loudest pricing claim in the
    /// app, and it carries no billed amount to be subordinate to (3.1.2(c)).
    func testEntryPointNeverPromotesTheTrial() {
        for trialAvailable in [true, false] {
            let label = StoreService.upgradeCTALabel(trialAvailable: trialAvailable)
            XCTAssertEqual(label, "Upgrade")
            XCTAssertFalse(label.lowercased().contains("free"))
        }
    }

    func testOneLabelFitsEveryEntryPoint() {
        for trialAvailable in [true, false] {
            let label = StoreService.upgradeCTALabel(trialAvailable: trialAvailable)
            XCTAssertFalse(label.isEmpty)
            XCTAssertLessThanOrEqual(label.count, 12)
        }
    }

    func testKnownProductsMapToPurchasePackages() {
        XCTAssertEqual(StatScoutProduct.packageType(for: StatScoutProduct.yearly), .annual)
        XCTAssertEqual(StatScoutProduct.packageType(for: StatScoutProduct.monthly), .monthly)
        XCTAssertEqual(StatScoutProduct.packageType(for: StatScoutProduct.lifetime), .lifetime)
        XCTAssertNil(StatScoutProduct.packageType(for: "unknown"))
    }
}
