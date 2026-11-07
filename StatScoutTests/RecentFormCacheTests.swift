import XCTest
@testable import Gridiron_StatScout

/// State machine audit 2026-11-06, SM-1. The Trends tab and a team's Recent
/// roster stay mounted side by side (RootTabView's ZStack) and load recent
/// form for different seasons or phases. Only Trends reloads on a tab return,
/// so a load for one screen must never clear what the other is showing.
@MainActor
final class RecentFormCacheTests: XCTestCase {
    private let live = StatScoutSeason.current
    private var prior: Int { StatScoutSeason.current - 1 }

    /// Serves one qb row for whatever (season, phase, window) is asked for.
    private struct FormProvider: StatcastProviding, @unchecked Sendable {
        func fetchPlayers() async throws -> [Player] { [] }
        func fetchHistoricalPlayers() async throws -> [Player] { [] }
        func fetchCurrentPlayers() async throws -> [Player] { [] }
        func fetchGameLogs(playerId: Int, season: Int, seasonPhase: SeasonPhase) async throws -> [PlayerGameLog] { [] }
        func fetchTeamGameLogs(team: String, season: Int, seasonPhase: SeasonPhase, sinceDate: Date) async throws -> [PlayerGameLog] { [] }
        func fetchDataCoverage(season: Int) async throws -> DataCoverage? { nil }
        func fetchRecentForm(season: Int, seasonPhase: SeasonPhase, windowWeeks: Int) async throws -> [RecentForm] {
            let json = """
            [{"player_id":1,"season":\(season),"season_type":"\(seasonPhase.rawValue)","player_type":"qb",
              "window_weeks":\(windowWeeks),"team":"KC","games":\(windowWeeks),"plays":100,"touches":60}]
            """
            return try JSONDecoder.statScout.decode([RecentForm].self, from: Data(json.utf8))
        }
    }

    private func makeModel() -> DashboardViewModel {
        let vm = DashboardViewModel(provider: FormProvider())
        vm.isPro = true
        return vm
    }

    private func trendsRows(_ vm: DashboardViewModel, season: Int, phase: SeasonPhase) -> Int {
        vm.recentFormRows(window: .three, playerType: "qb", season: season, phase: phase).count
    }

    /// The shipping defaults: TeamView opens its roster on 5 weeks, Trends on 3.
    func testTeamRecentRosterSurvivesTrendsOnAnotherSeason() async {
        let vm = makeModel()
        XCTAssertEqual(vm.recentWindow, .three, "precondition: Trends default window")
        XCTAssertEqual(vm.selectedSeason, live, "precondition: Teams on the live season")
        XCTAssertEqual(vm.selectedPhase, .regular, "precondition: regular season")
        XCTAssertTrue(vm.supportsRecentForm(prior), "precondition: Trends offers last season")

        await vm.loadRecentFormIfNeeded(window: TrendWindow.three, season: prior, phase: .regular)
        XCTAssertEqual(trendsRows(vm, season: prior, phase: .regular), 1, "precondition: Trends board loaded")

        await vm.loadRecentFormIfNeeded(window: RecentWindow.five)
        XCTAssertNotNil(vm.recentForm(for: 1, window: RecentWindow.five), "precondition: Team Recent roster loaded")
        XCTAssertEqual(trendsRows(vm, season: prior, phase: .regular), 1, "Trends board lost its data to the Team page")

        // Back on Trends: its task reruns. TeamView's does not.
        await vm.loadRecentFormIfNeeded(window: TrendWindow.three, season: prior, phase: .regular)
        XCTAssertTrue(vm.hasRecentForm(window: .five), "Team Recent roster lost its data to the Trends tab")
        XCTAssertNotNil(vm.recentForm(for: 1, window: RecentWindow.five), "Team Recent roster lost its data to the Trends tab")
    }

    /// Same, with the Playoffs phase pill on Trends, offered all year.
    func testTeamRecentRosterSurvivesTrendsOnPlayoffs() async {
        let vm = makeModel()
        await vm.loadRecentFormIfNeeded(window: TrendWindow.three, season: live, phase: .playoffs)
        XCTAssertEqual(trendsRows(vm, season: live, phase: .playoffs), 1, "precondition: Trends playoffs board loaded")
        await vm.loadRecentFormIfNeeded(window: RecentWindow.three)
        XCTAssertNotNil(vm.recentForm(for: 1, window: RecentWindow.three), "precondition: Team Recent roster loaded")
        await vm.loadRecentFormIfNeeded(window: TrendWindow.three, season: live, phase: .playoffs)
        XCTAssertEqual(trendsRows(vm, season: live, phase: .playoffs), 1, "precondition: Trends reloaded")
        XCTAssertNotNil(vm.recentForm(for: 1, window: RecentWindow.three), "Team Recent roster lost its data to the Trends playoffs board")
    }

    /// Control: both screens on the same season and phase never fight.
    func testSameContextKeepsBothScreensLoaded() async {
        let vm = makeModel()
        await vm.loadRecentFormIfNeeded(window: TrendWindow.three, season: live, phase: .regular)
        await vm.loadRecentFormIfNeeded(window: RecentWindow.three)
        await vm.loadRecentFormIfNeeded(window: TrendWindow.three, season: live, phase: .regular)
        XCTAssertEqual(trendsRows(vm, season: live, phase: .regular), 1)
        XCTAssertNotNil(vm.recentForm(for: 1, window: RecentWindow.three))
    }

    /// A new data revision still clears every slot.
    func testInvalidationClearsEverySlot() async {
        let vm = makeModel()
        await vm.loadRecentFormIfNeeded(window: TrendWindow.three, season: prior, phase: .regular)
        await vm.loadRecentFormIfNeeded(window: RecentWindow.five)
        vm.invalidateRecentFormCache()
        XCTAssertEqual(trendsRows(vm, season: prior, phase: .regular), 0)
        XCTAssertFalse(vm.hasRecentForm(window: .five))
        XCTAssertFalse(vm.isRecentFormLoading(season: prior, phase: .regular))
    }
}
