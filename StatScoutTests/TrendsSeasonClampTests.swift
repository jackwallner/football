import XCTest
@testable import Gridiron_StatScout

/// State machine audit 2026-11-06, SM-3. Trends keeps its own season; a Pro
/// lapse mid-session left it on last season, so the locked board's leader row
/// showed a Pro-only season.
@MainActor
final class TrendsSeasonClampTests: XCTestCase {
    func testLapsedUserIsMovedOffTheLockedTrendsSeason() {
        let vm = DashboardViewModel(provider: PreviewStatcastAPI())
        let prior = vm.recentFormSeason - 1
        vm.applyProState(true)
        XCTAssertEqual(vm.trendsSeason(clamping: prior), prior, "Pro keeps last season")
        vm.applyProState(false)
        XCTAssertEqual(vm.trendsSeason(clamping: prior), vm.recentFormSeason)
        XCTAssertEqual(vm.trendsSeason(clamping: vm.recentFormSeason), vm.recentFormSeason)
    }
}
