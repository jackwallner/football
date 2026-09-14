import XCTest
@testable import Gridiron_StatScout

final class GamesTests: XCTestCase {
    private func date(_ iso: String) -> Date {
        ISO8601DateFormatter().date(from: iso)!
    }

    private func game(_ id: String, week: Int, kickoff: String, away: String = "BUF", home: String = "HOU",
                      awayScore: Int? = nil, homeScore: Int? = nil) -> Game {
        Game(id: id, season: 2026, week: week, kickoff: date(kickoff), awayTeam: away, homeTeam: home,
             awayScore: awayScore, homeScore: homeScore)
    }

    func testDecodesPublishedRow() throws {
        let json = """
        [{"game_id":"2026_01_NO_DET","season":2026,"season_type":"REG","game_type":"REG","week":1,
          "game_date":"2026-09-13","kickoff_at":"2026-09-13T17:00:00+00:00","away_team":"NO","home_team":"DET",
          "away_score":30,"home_score":31,"overtime":true,"stadium":"Ford Field","synced_at":"2026-09-14T03:37:56.93+00:00"}]
        """.data(using: .utf8)!
        let decoded = try JSONDecoder.statScout.decode([Game].self, from: json)
        let game = try XCTUnwrap(decoded.first)
        XCTAssertTrue(game.isFinal)
        XCTAssertEqual(game.resultLine(for: "DET"), "W 31-30 OT")
        XCTAssertEqual(game.resultLine(for: "NO"), "L 30-31 OT")
        XCTAssertEqual(game.matchupLabel(for: "NO"), "at DET")
        XCTAssertEqual(game.kickoff, date("2026-09-13T17:00:00Z"))
    }

    func testStatusWithoutLiveScores() {
        let upcoming = game("a", week: 1, kickoff: "2026-09-13T17:00:00Z")
        XCTAssertEqual(upcoming.status(now: date("2026-09-13T16:00:00Z")), .upcoming)
        XCTAssertEqual(upcoming.status(now: date("2026-09-13T19:00:00Z")), .inProgress)
        XCTAssertEqual(upcoming.status(now: date("2026-09-13T23:00:00Z")), .awaitingScore)
        let final = game("b", week: 1, kickoff: "2026-09-13T17:00:00Z", awayScore: 36, homeScore: 31)
        XCTAssertEqual(final.status(now: date("2026-09-13T19:00:00Z")), .final)
    }

    func testCurrentWeekHoldsUntilMidweek() {
        let games = [
            game("w1a", week: 1, kickoff: "2026-09-10T00:20:00Z"),
            game("w1b", week: 1, kickoff: "2026-09-15T00:15:00Z"),
            game("w2a", week: 2, kickoff: "2026-09-17T00:15:00Z"),
        ]
        XCTAssertEqual(GameWeek.current(in: games, now: date("2026-08-01T00:00:00Z"))?.week, 1)
        // Tuesday after Monday night: still week 1.
        XCTAssertEqual(GameWeek.current(in: games, now: date("2026-09-15T15:00:00Z"))?.week, 1)
        // Wednesday afternoon: week 2.
        XCTAssertEqual(GameWeek.current(in: games, now: date("2026-09-16T18:00:00Z"))?.week, 2)
        // After the season: the last week.
        XCTAssertEqual(GameWeek.current(in: games, now: date("2027-03-01T00:00:00Z"))?.week, 2)
    }

    @MainActor
    func testTeamRecordCountsRegularSeasonFinalsThroughAGame() async {
        let w1 = game("w1", week: 1, kickoff: "2026-09-13T17:00:00Z", away: "BUF", home: "HOU", awayScore: 36, homeScore: 31)
        let w2 = game("w2", week: 2, kickoff: "2026-09-20T17:00:00Z", away: "DET", home: "BUF", awayScore: 24, homeScore: 17)
        let w3 = game("w3", week: 3, kickoff: "2026-09-27T17:00:00Z", away: "BUF", home: "LAC")
        let model = DashboardViewModel(provider: GamesProvider(games: [w3, w1, w2]))
        await model.loadGames(force: true)
        XCTAssertEqual(model.record(forTeam: "BUF"), "1-1")
        XCTAssertEqual(model.record(forTeam: "BUF", through: w1), "1-0")
        XCTAssertEqual(model.record(forTeam: "HOU"), "0-1")
        XCTAssertNil(model.record(forTeam: "LAC"))
        XCTAssertEqual(model.schedule(forTeam: "BUF").map(\.id), ["w1", "w2", "w3"])
    }

    func testSlateOrderPutsLiveFirstThenFinalsThenUpcoming() {
        let now = date("2026-09-13T19:00:00Z")
        let slate = Game.slateOrder([
            game("late", week: 1, kickoff: "2026-09-14T00:20:00Z"),
            game("final", week: 1, kickoff: "2026-09-13T17:00:00Z", awayScore: 1, homeScore: 2),
            game("live", week: 1, kickoff: "2026-09-13T17:00:00Z"),
        ], now: now)
        XCTAssertEqual(slate.map(\.id), ["live", "final", "late"])
    }

    func testBoxScoreTotalsDoNotDoubleCountReceiving() throws {
        let json = """
        [{"player_id":1,"season":2026,"season_type":"REG","game_id":"g","game_date":"2026-09-13","player_type":"qb","team":"BUF",
          "metrics":{"attempts":30,"completions":20,"passing_yards":300,"sack_yards_lost":10,"sacks_suffered":2,
                     "interceptions":1,"passing_first_downs":12,"passing_epa":6.0,"carries":4,"rushing_yards":20,"rushing_epa":1.0}},
         {"player_id":2,"season":2026,"season_type":"REG","game_id":"g","game_date":"2026-09-13","player_type":"wr","team":"BUF",
          "metrics":{"targets":9,"receptions":7,"receiving_yards":150,"receiving_first_downs":6,"receiving_epa":5.0}},
         {"player_id":3,"season":2026,"season_type":"REG","game_id":"g","game_date":"2026-09-13","player_type":"rb","team":"BUF",
          "metrics":{"carries":16,"rushing_yards":80,"rushing_first_downs":4,"rushing_fumbles_lost":1,"rushing_epa":-1.0}}]
        """.data(using: .utf8)!
        let logs = try JSONDecoder.statScout.decode([PlayerGameLog].self, from: json)
        XCTAssertEqual(logs.first?.gameId, "g")
        let box = GameBoxScore(logs: logs)
        let totals = box.totals(for: "BUF")
        XCTAssertEqual(totals.totalYards, 390)
        XCTAssertEqual(totals.firstDowns, 16)
        XCTAssertEqual(totals.turnovers, 2)
        XCTAssertEqual(try XCTUnwrap(totals.epaPerPlay), 6.0 / 52, accuracy: 0.0001)
        XCTAssertEqual(box.leaders.map(\.title), ["Passing", "Rushing", "Receiving"])
        XCTAssertEqual(box.rushers(for: "BUF").first?.playerId, 3)
    }

    func testWeekRangeLabelNeverStartsBeforeWeekOne() throws {
        let json = """
        {"player_id":1,"season":2026,"player_type":"qb","window_weeks":3,"start_week":-1,"end_week":1,"games":1,
         "metrics":{},"prior_metrics":{},"delta":{}}
        """.data(using: .utf8)!
        let form = try JSONDecoder.statScout.decode(RecentForm.self, from: json)
        XCTAssertEqual(form.weekRangeLabel, "Week 1")
        XCTAssertTrue(form.isSmallSample)
        XCTAssertTrue(form.isSmallSample(minimumGames: 1))
    }

    func testFreshnessReportsAdvancedPending() throws {
        let json = """
        {"status":"degraded","refresh_id":"r","max_game_date":"2026-09-13","max_week":1,
         "observed_games":10,"expected_games":10,"ngs_status":"ready","pfr_status":"pending"}
        """.data(using: .utf8)!
        let freshness = try JSONDecoder.statScout.decode(DataFreshness.self, from: json)
        XCTAssertTrue(freshness.isAdvancedPending)
        let roundTrip = try JSONDecoder.statScout.decode(
            DataFreshness.self,
            from: JSONEncoder.statScout.encode(freshness.replacing(isCached: true))
        )
        XCTAssertEqual(roundTrip.advancedDefenseStatus, "pending")
    }
}

private struct GamesProvider: StatcastProviding {
    let games: [Game]
    func fetchGames(season: Int) async throws -> [Game] { games }
    func fetchPlayers() async throws -> [Player] { [] }
    func fetchHistoricalPlayers() async throws -> [Player] { [] }
    func fetchCurrentPlayers() async throws -> [Player] { [] }
    func fetchGameLogs(playerId: Int, season: Int, seasonPhase: SeasonPhase) async throws -> [PlayerGameLog] { [] }
    func fetchTeamGameLogs(team: String, season: Int, seasonPhase: SeasonPhase, sinceDate: Date) async throws -> [PlayerGameLog] { [] }
    func fetchRecentForm(season: Int, seasonPhase: SeasonPhase, windowWeeks: Int) async throws -> [RecentForm] { [] }
    func fetchDataCoverage(season: Int) async throws -> DataCoverage? { nil }
}
