import XCTest
@testable import Gridiron_StatScout

/// State machine audit 2026-11-06, SM-2. A free user loads only the live
/// season, so a followed player with no live row (retired, unsigned, out all
/// year) had no row in the Follow sheet and could never be unfollowed.
@MainActor
final class FollowedPlayersTests: XCTestCase {
    private struct SplitProvider: StatcastProviding, @unchecked Sendable {
        let current: [Player]
        let history: [Player]

        func fetchPlayers() async throws -> [Player] { current + history }
        func fetchHistoricalPlayers() async throws -> [Player] { history }
        func fetchCurrentPlayers() async throws -> [Player] { current }
        func fetchGameLogs(playerId: Int, season: Int, seasonPhase: SeasonPhase) async throws -> [PlayerGameLog] { [] }
        func fetchTeamGameLogs(team: String, season: Int, seasonPhase: SeasonPhase, sinceDate: Date) async throws -> [PlayerGameLog] { [] }
        func fetchRecentForm(season: Int, seasonPhase: SeasonPhase, windowWeeks: Int) async throws -> [RecentForm] { [] }
        func fetchDataCoverage(season: Int) async throws -> DataCoverage? { nil }
    }

    private func player(_ id: Int, season: Int) -> Player {
        Player(
            playerId: id, name: "P\(id)", team: "KC", position: "QB", handedness: "",
            updatedAt: Date(), season: season, playerType: "qb",
            metrics: [Metric(id: "epa", label: "EPA/Play", value: "0.2", percentile: 80, category: .passing)],
            standardStats: [], games: []
        )
    }

    private func makeModel(isPro: Bool) -> DashboardViewModel {
        let live = StatScoutSeason.current
        let provider = SplitProvider(
            current: [player(1, season: live)],
            history: [player(2, season: live - 2), player(2, season: live - 1), player(3, season: live - 1)]
        )
        let vm = DashboardViewModel(provider: provider)
        vm.isPro = isPro
        return vm
    }

    func testFreeUserCanSeeFollowedPlayerWithoutLiveRow() async {
        let vm = makeModel(isPro: false)
        await vm.load()
        XCTAssertEqual(vm.players(forSeason: StatScoutSeason.current).map(\.playerId), [1], "precondition: live season loaded")
        XCTAssertNil(vm.playerHistories[2], "precondition: free users never load history")

        await vm.loadArchivedFollowedPlayers(ids: [1, 2])
        let followed = vm.followedPlayers(ids: [1, 2])
        XCTAssertEqual(followed.map(\.playerId), [1, 2], "a followed player with no live row has no row to unfollow")
        XCTAssertEqual(followed.last?.season, StatScoutSeason.current - 1, "falls back to his latest season")
        XCTAssertNil(vm.playerHistories[2], "the lookup must not load past seasons into the free data")
        XCTAssertNil(vm.playerHistories[3])
    }

    func testProUserFallbackStillWorks() async {
        let vm = makeModel(isPro: true)
        await vm.load()
        await vm.loadHistoricalIfNeeded()
        XCTAssertEqual(vm.followedPlayers(ids: [2, 1]).map(\.playerId), [2, 1])
    }

    func testUnknownIdStaysOutWithoutError() async {
        let vm = makeModel(isPro: false)
        await vm.load()
        await vm.loadArchivedFollowedPlayers(ids: [1, 99])
        XCTAssertEqual(vm.followedPlayers(ids: [1, 99]).map(\.playerId), [1])
    }
}
