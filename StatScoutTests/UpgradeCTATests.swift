import XCTest
@testable import Gridiron_StatScout

final class UpgradeCTATests: XCTestCase {
    func testUpgradeLabelReflectsTrialAvailability() {
        for (trialAvailable, expected) in [(true, "Try Free"), (false, "Upgrade")] {
            XCTAssertEqual(StoreService.upgradeCTALabel(trialAvailable: trialAvailable), expected)
        }
    }

    func testKnownProductsMapToPurchasePackages() {
        XCTAssertEqual(StatScoutProduct.packageType(for: StatScoutProduct.yearly), .annual)
        XCTAssertEqual(StatScoutProduct.packageType(for: StatScoutProduct.monthly), .monthly)
        XCTAssertEqual(StatScoutProduct.packageType(for: StatScoutProduct.lifetime), .lifetime)
        XCTAssertNil(StatScoutProduct.packageType(for: "unknown"))
    }
}
