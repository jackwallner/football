import XCTest
@testable import Gridiron_StatScout

/// State machine audit 2026-11-06. Repro for SM-1: the recent-form cache is one
/// slot (one season/phase context) shared by the Trends tab and the Team page's
/// Recent roster, which are mounted at the same time in RootTabView's ZStack.
///
/// Each test asserts its own preconditions first, so a failure on the final
/// assertion is the claim and not a broken setup.
@MainActor
final class StateMachineAuditRepro: XCTestCase {
    private let live = StatScoutSeason.current
    private var prior: Int { StatScoutSeason.current - 1 }

    // MARK: fixtures

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

    /// The Trends tab (HotColdView) reads this; its `.task` loads with its own
    /// season/phase @State.
    private func trendsRows(_ vm: DashboardViewModel, season: Int, phase: SeasonPhase) -> Int {
        vm.recentFormRows(window: .three, playerType: "qb", season: season, phase: phase).count
    }

    /// The Team page's Recent roster (TeamView) reads this, with the view
    /// model's own selectedSeason/selectedPhase.
    private func teamRecent(_ vm: DashboardViewModel) -> RecentForm? {
        vm.recentForm(for: 1, window: RecentWindow.three)
    }

    // MARK: SM-1

    /// Pro user opens Trends on last season, then a team's Recent roster on the
    /// live season, then returns to Trends (which reloads because `isActive`
    /// flips), then returns to the Teams tab (which does not reload, its task id
    /// did not change).
    func testTeamRecentRosterSurvivesTrendsOnAnotherSeason() async {
        let vm = makeModel()
        XCTAssertEqual(vm.selectedSeason, live, "precondition: Teams/Stats are on the live season")
        XCTAssertEqual(vm.selectedPhase, .regular, "precondition: regular season")
        XCTAssertTrue(vm.supportsRecentForm(prior), "precondition: Trends offers last season")
        XCTAssertTrue(vm.supportsRecentForm(live), "precondition: Team page offers Recent on the live season")

        // Trends tab, season pill set to last season.
        await vm.loadRecentFormIfNeeded(window: TrendWindow.three, season: prior, phase: .regular)
        XCTAssertEqual(trendsRows(vm, season: prior, phase: .regular), 1, "precondition: Trends board loaded")

        // Teams tab: open a team, Recent roster (VM season).
        await vm.loadRecentFormIfNeeded(window: RecentWindow.three)
        XCTAssertNotNil(teamRecent(vm), "precondition: Team Recent roster loaded")

        // Back on Trends: HotColdView's task id contains isActive, so it reruns.
        await vm.loadRecentFormIfNeeded(window: TrendWindow.three, season: prior, phase: .regular)
        XCTAssertEqual(trendsRows(vm, season: prior, phase: .regular), 1, "precondition: Trends board reloaded")

        // Back on Teams: TeamView does not reload. It reads the cache.
        XCTAssertNotNil(vm.recentFormByWindow[RecentWindow.three.rawValue], "TeamView's hasRecentData is true, so no spinner")
        XCTAssertNotNil(teamRecent(vm), "Team Recent roster lost its data to the Trends tab")
    }

    /// Same, with the playoffs pill on Trends (a free phase pill, no lock).
    func testTeamRecentRosterSurvivesTrendsOnPlayoffs() async {
        let vm = makeModel()
        XCTAssertEqual(vm.selectedPhase, .regular, "precondition: Teams/Stats on regular season")
        await vm.loadRecentFormIfNeeded(window: TrendWindow.three, season: live, phase: .playoffs)
        XCTAssertEqual(trendsRows(vm, season: live, phase: .playoffs), 1, "precondition: Trends playoffs board loaded")
        await vm.loadRecentFormIfNeeded(window: RecentWindow.three)
        XCTAssertNotNil(teamRecent(vm), "precondition: Team Recent roster loaded")
        await vm.loadRecentFormIfNeeded(window: TrendWindow.three, season: live, phase: .playoffs)
        XCTAssertEqual(trendsRows(vm, season: live, phase: .playoffs), 1, "precondition: Trends reloaded")
        XCTAssertNotNil(teamRecent(vm), "Team Recent roster lost its data to the Trends playoffs board")
    }

    /// Control: both screens on the same season and phase never fight.
    func testControlSameContextKeepsBothScreensLoaded() async {
        let vm = makeModel()
        await vm.loadRecentFormIfNeeded(window: TrendWindow.three, season: live, phase: .regular)
        await vm.loadRecentFormIfNeeded(window: RecentWindow.three)
        await vm.loadRecentFormIfNeeded(window: TrendWindow.three, season: live, phase: .regular)
        XCTAssertEqual(trendsRows(vm, season: live, phase: .regular), 1)
        XCTAssertNotNil(teamRecent(vm))
    }

    /// Control: the team read does return data right after its own load.
    func testControlTeamReadWorksAfterItsOwnLoad() async {
        let vm = makeModel()
        await vm.loadRecentFormIfNeeded(window: RecentWindow.three)
        XCTAssertNotNil(teamRecent(vm))
    }
}
