import XCTest
@testable import Gridiron_StatScout

final class UpgradeCTATests: XCTestCase {
    func testSaysTryFreeWhenATrialIsAvailable() {
        XCTAssertEqual(StoreService.upgradeCTALabel(trialAvailable: true), "Try Free")
    }

    func testFallsBackToUpgradeWithoutATrial() {
        XCTAssertEqual(StoreService.upgradeCTALabel(trialAvailable: false), "Upgrade")
    }

    func testOneLabelFitsEveryEntryPoint() {
        for trialAvailable in [true, false] {
            let label = StoreService.upgradeCTALabel(trialAvailable: trialAvailable)
            XCTAssertFalse(label.isEmpty)
            XCTAssertLessThanOrEqual(label.count, 12)
        }
    }
}
