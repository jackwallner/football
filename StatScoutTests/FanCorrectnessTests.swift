import XCTest
@testable import Gridiron_StatScout

/// Numbers a football fan checks against the box score: team pages rank clubs
/// against clubs, windows count games rather than rows, and streaks compare
/// per game when two windows hold different numbers of games.
final class FanCorrectnessTests: XCTestCase {
    private func player(
        id: Int,
        team: String,
        type: String = "qb",
        metrics: [Metric] = [],
        standard: [StandardStat]
    ) -> Player {
        Player(
            playerId: id, name: "P\(id)", team: team, position: type.uppercased(),
            handedness: "", updatedAt: Date(), season: 2026, playerType: type,
            metrics: metrics, standardStats: standard, games: []
        )
    }

    private func std(_ label: String, _ value: String) -> StandardStat {
        StandardStat(id: "std-\(label)", label: label, value: value)
    }

    private func passYds(_ value: String) -> Metric {
        Metric(id: "py", label: "Pass Yds", value: value, percentile: 50, category: .passing)
    }

    private func log(date: String, team: String = "KC", metrics: [String: Double]) throws -> PlayerGameLog {
        let payload: [String: Any] = [
            "player_id": Int.random(in: 1...100_000), "season": 2026, "game_date": date,
            "player_type": "def", "team": team, "plays": 10, "touches": 0, "metrics": metrics,
        ]
        return try JSONDecoder().decode(PlayerGameLog.self, from: JSONSerialization.data(withJSONObject: payload))
    }

    // MARK: - Team pages

    /// A club's passing yards are its quarterbacks' sum per game, not their
    /// average, and the bar ranks it among clubs.
    func testTeamValueSumsTheRosterPerGameAndRanksAgainstClubs() {
        let kc = [
            player(id: 1, team: "KC", metrics: [passYds("1,000")], standard: [std("G", "5")]),
            player(id: 2, team: "KC", metrics: [passYds("50")], standard: [std("G", "2")]),
        ]
        let buf = [player(id: 3, team: "BUF", metrics: [passYds("900")], standard: [std("G", "5")])]
        XCTAssertEqual(TeamLeagueTable.teamValue(label: "Pass Yds", category: .passing, roster: kc), 210)
        XCTAssertEqual(TeamLeagueTable.teamValue(label: "Pass Yds", category: .passing, roster: buf), 180)

        let table = TeamLeagueTable(players: kc + buf, categories: [.passing])
        XCTAssertEqual(table.percentile(for: 210, label: "Pass Yds", category: .passing), 99)
        XCTAssertEqual(table.percentile(for: 180, label: "Pass Yds", category: .passing), 1)
        XCTAssertEqual(TeamLeagueTable.displayLabel("Pass Yds", category: .passing), "Pass Yds/G")
        XCTAssertEqual(TeamLeagueTable.baseLabel("Pass Yds/G"), "Pass Yds")
    }

    /// One row per player per game: a window's game count is its dates.
    func testWindowCountsGamesNotRows() throws {
        let logs = [
            try log(date: "2026-10-04", metrics: ["def_tackles_solo": 5]),
            try log(date: "2026-10-04", metrics: ["def_tackles_solo": 3]),
            try log(date: "2026-09-27", metrics: ["def_tackles_solo": 4]),
        ]
        XCTAssertEqual(RecentFormWindow.build(label: "Last 2", span: 2, logs: logs).games, 2)
    }

    // MARK: - Trends

    private func form(metrics: [String: Double], prior: [String: Double], games: Int) throws -> RecentForm {
        let payload: [String: Any] = [
            "player_id": 1, "season": 2026, "player_type": "wr", "window_weeks": 3,
            "games": games, "metrics": metrics, "prior_metrics": prior, "delta": [:] as [String: Double],
        ]
        return try JSONDecoder().decode(RecentForm.self, from: JSONSerialization.data(withJSONObject: payload))
    }

    /// 150 yards in two games after 180 in three is a rise, not a fall.
    func testCountingTrendsCompareWindowsPerGame() throws {
        let recYards = TrendMetric.receivingStandard.first { $0.key == "rec_yards" }!
        let row = try form(
            metrics: ["rec_yards": 150, "games": 2],
            prior: ["rec_yards": 180, "games": 3],
            games: 2
        )
        let values = recYards.values(in: row)
        XCTAssertEqual(values.now, 75)
        XCTAssertEqual(values.then, 60)
        XCTAssertEqual(values.delta, 15)
        XCTAssertEqual(recYards.display(75), "75/g")
    }

    /// Rates are already per play; they pass through untouched.
    func testRateTrendsUseThePublishedDelta() throws {
        let catchPct = TrendMetric.receivingAdvanced.first { $0.key == "catch_pct" }!
        let row = try form(metrics: ["catch_pct": 70], prior: ["catch_pct": 60], games: 2)
        XCTAssertNil(catchPct.values(in: row).delta)
        XCTAssertEqual(catchPct.values(in: row).now, 70)
    }

    // MARK: - Box score and lists

    func testDefenseBoxScoreListsTacklesFirst() throws {
        let logs = [
            try log(date: "2026-10-08", metrics: ["def_tackles_solo": 5, "def_interceptions": 1]),
            try log(date: "2026-10-08", metrics: ["def_tackles_solo": 9]),
        ]
        let defenders = GameBoxScore(logs: logs).defenders(for: "KC")
        XCTAssertEqual(defenders.map { Int($0.tackles) }, [9, 5])
    }

    func testPickerOrdersStartersAboveBackups() {
        let starter = player(id: 1, team: "KC", standard: [std("Pass Yds", "1,381"), std("Pass TD", "9")])
        let backup = player(id: 2, team: "KC", standard: [std("Pass Yds", "40"), std("Pass TD", "0")])
        XCTAssertGreaterThan(starter.prominence, backup.prominence)
    }

    /// The Season Totals column compares counts, and games played is context.
    func testTotalsTrophyComparesCountsNotRates() {
        XCTAssertEqual(StandardStatSemantics.winner(label: "Rec/Tgt", left: "27/34", right: "1/1"), .left)
        XCTAssertNil(StandardStatSemantics.winner(label: "G", left: "5", right: "4"))
    }

    func testSurnameSkipsGenerationalSuffix() {
        let player = Player(
            playerId: 9, name: "Marvin Harrison Jr.", team: "ARI", position: "WR",
            handedness: "", updatedAt: Date(), metrics: [], standardStats: nil, games: []
        )
        XCTAssertEqual(player.surname, "Harrison")
    }

    func testTiesAreNeitherWinNorLoss() {
        XCTAssertEqual(GridironPalette.resultColor("T"), GridironPalette.inkSecondary)
        XCTAssertEqual(GridironPalette.resultColor("W"), GridironPalette.performanceHigh)
    }
}
