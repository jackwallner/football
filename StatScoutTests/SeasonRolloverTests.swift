import XCTest
@testable import Gridiron_StatScout

/// State machine audit 2026-11-06, SM-4. `StatScoutSeason.current` was a
/// `static let`, read once per process, so a process suspended across
/// 1 September kept last season as the free season and as the live fetch.
@MainActor
final class SeasonRolloverTests: XCTestCase {
    private final class TestClock: @unchecked Sendable {
        private let lock = NSLock()
        private var value: Date
        init(_ date: Date) { value = date }
        var date: Date {
            get { lock.withLock { value } }
            set { lock.withLock { value = newValue } }
        }
    }

    private final class SeasonLog: @unchecked Sendable {
        private let lock = NSLock()
        private var seasons: [Int] = []
        func record(_ season: Int) { lock.withLock { seasons.append(season) } }
        var last: Int? { lock.withLock { seasons.last } }
    }

    /// Serves one live row for whatever season the app currently calls live,
    /// the way the real API filters on `StatScoutSeason.current`.
    private struct LiveProvider: StatcastProviding, @unchecked Sendable {
        let log: SeasonLog

        func fetchPlayers() async throws -> [Player] { try await fetchCurrentPlayers() }
        func fetchHistoricalPlayers() async throws -> [Player] { [] }
        func fetchCurrentPlayers() async throws -> [Player] {
            let season = StatScoutSeason.current
            log.record(season)
            return [Player(
                playerId: 1, name: "P1", team: "KC", position: "QB", handedness: "",
                updatedAt: Date(), season: season, playerType: "qb",
                metrics: [Metric(id: "epa", label: "EPA/Play", value: "0.2", percentile: 80, category: .passing)],
                standardStats: [], games: []
            )]
        }
        func fetchGameLogs(playerId: Int, season: Int, seasonPhase: SeasonPhase) async throws -> [PlayerGameLog] { [] }
        func fetchTeamGameLogs(team: String, season: Int, seasonPhase: SeasonPhase, sinceDate: Date) async throws -> [PlayerGameLog] { [] }
        func fetchRecentForm(season: Int, seasonPhase: SeasonPhase, windowWeeks: Int) async throws -> [RecentForm] { [] }
        func fetchDataCoverage(season: Int) async throws -> DataCoverage? { nil }
    }

    private func noon(_ year: Int, _ month: Int, _ day: Int) -> Date {
        Calendar.current.date(from: DateComponents(year: year, month: month, day: day, hour: 12))!
    }

    private func useClock(_ clock: TestClock) {
        StatScoutSeason.liveSeasonClock.setNow { clock.date }
        addTeardownBlock { StatScoutSeason.liveSeasonClock.setNow { Date() } }
    }

    func testLiveSeasonFollowsTheClockWithinOneProcess() {
        let clock = TestClock(noon(2031, 8, 20))
        useClock(clock)
        XCTAssertEqual(StatScoutSeason.current, 2030)
        clock.date = noon(2031, 9, 10)
        XCTAssertEqual(StatScoutSeason.current, 2031, "the live season stayed frozen at process start")
        XCTAssertEqual(StatScoutSeason.free, 2031)
    }

    func testSuspendedProcessMovesToTheNewSeasonOnForeground() async {
        let clock = TestClock(noon(2031, 8, 20))
        useClock(clock)
        let log = SeasonLog()
        let vm = DashboardViewModel(provider: LiveProvider(log: log))
        await vm.load()
        XCTAssertEqual(vm.selectedSeason, 2030, "precondition: opened in August on last season")
        XCTAssertEqual(log.last, 2030, "precondition: fetched last season as live")

        clock.date = noon(2031, 9, 12)
        await vm.refreshOnForeground(now: clock.date)

        XCTAssertEqual(vm.freeSeason, 2031)
        XCTAssertEqual(vm.selectedSeason, 2031, "a free user stayed on a season that is now locked")
        XCTAssertFalse(vm.isSeasonLocked(2031))
        XCTAssertTrue(vm.isSeasonLocked(2030))
        XCTAssertEqual(log.last, 2031, "the live fetch still asked for last season")
        XCTAssertEqual(vm.players(forSeason: 2031).map(\.playerId), [1])
    }

    func testProUserOnAnOlderSeasonKeepsIt() async {
        let clock = TestClock(noon(2031, 8, 20))
        useClock(clock)
        let vm = DashboardViewModel(provider: LiveProvider(log: SeasonLog()))
        vm.isPro = true
        await vm.load()
        vm.selectedSeason = 2027

        clock.date = noon(2031, 9, 12)
        await vm.refreshOnForeground(now: clock.date)
        XCTAssertEqual(vm.selectedSeason, 2027)
    }
}
