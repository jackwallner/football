package com.jackwallner.football

import com.jackwallner.football.model.DataFreshness
import com.jackwallner.football.model.Game
import com.jackwallner.football.model.GameBoxScore
import com.jackwallner.football.model.GameDetail
import com.jackwallner.football.model.GameStatus
import com.jackwallner.football.model.GameWeek
import com.jackwallner.football.model.PlayerGameLog
import com.jackwallner.football.model.RecentForm
import com.jackwallner.football.ui.screens.cleanDescription
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GamesTest {
    private fun date(iso: String): Instant = Instant.parse(iso)

    private fun game(
        id: String, week: Int, kickoff: String, away: String = "BUF", home: String = "HOU",
        awayScore: Int? = null, homeScore: Int? = null,
    ) = Game(
        id = id, season = 2026, week = week, kickoff = date(kickoff), awayTeam = away, homeTeam = home,
        awayScore = awayScore, homeScore = homeScore,
    )

    private fun objects(json: String) = (Json.parseToJsonElement(json) as JsonArray).map { it as JsonObject }

    @Test
    fun decodesPublishedRow() {
        val json = """
            [{"game_id":"2026_01_NO_DET","season":2026,"season_type":"REG","game_type":"REG","week":1,
              "game_date":"2026-09-13","kickoff_at":"2026-09-13T17:00:00+00:00","away_team":"NO","home_team":"DET",
              "away_score":30,"home_score":31,"overtime":true,"stadium":"Ford Field","synced_at":"2026-09-14T03:37:56.93+00:00"}]
        """.trimIndent()
        val game = Game.fromJson(objects(json).first())
        assertTrue(game.isFinal)
        assertEquals("W 31-30 OT", game.resultLine("DET"))
        assertEquals("L 30-31 OT", game.resultLine("NO"))
        assertEquals("at DET", game.matchupLabel("NO"))
        assertEquals(date("2026-09-13T17:00:00Z"), game.kickoff)
    }

    @Test
    fun statusWithoutLiveScores() {
        val upcoming = game("a", 1, "2026-09-13T17:00:00Z")
        assertEquals(GameStatus.UPCOMING, upcoming.status(date("2026-09-13T16:00:00Z")))
        assertEquals(GameStatus.IN_PROGRESS, upcoming.status(date("2026-09-13T19:00:00Z")))
        assertEquals(GameStatus.AWAITING_SCORE, upcoming.status(date("2026-09-13T23:00:00Z")))
        val final = game("b", 1, "2026-09-13T17:00:00Z", awayScore = 36, homeScore = 31)
        assertEquals(GameStatus.FINAL, final.status(date("2026-09-13T19:00:00Z")))
    }

    @Test
    fun currentWeekHoldsUntilMidweek() {
        val games = listOf(
            game("w1a", 1, "2026-09-10T00:20:00Z"),
            game("w1b", 1, "2026-09-15T00:15:00Z"),
            game("w2a", 2, "2026-09-17T00:15:00Z"),
        )
        assertEquals(1, GameWeek.current(games, date("2026-08-01T00:00:00Z"))?.week)
        // Tuesday after Monday night: still week 1.
        assertEquals(1, GameWeek.current(games, date("2026-09-15T15:00:00Z"))?.week)
        // Wednesday afternoon: week 2.
        assertEquals(2, GameWeek.current(games, date("2026-09-16T18:00:00Z"))?.week)
        // After the season: the last week.
        assertEquals(2, GameWeek.current(games, date("2027-03-01T00:00:00Z"))?.week)
    }

    @Test
    fun completedWeekStaysUpUntilTheEveOfThursdayNight() {
        val games = listOf(
            game("w2a", 2, "2026-09-18T00:15:00Z"),
            game("w2z", 2, "2026-09-22T00:15:00Z", awayScore = 20, homeScore = 17),
            game("w3a", 3, "2026-09-25T00:15:00Z"),
            game("w3b", 3, "2026-09-27T17:00:00Z"),
        )
        // Wednesday morning Eastern, the day the old rule rolled over.
        assertEquals(2, GameWeek.current(games, date("2026-09-23T13:00:00Z"))?.week)
        // Wednesday night, a day before Thursday's kickoff: the new slate.
        assertEquals(3, GameWeek.current(games, date("2026-09-24T01:00:00Z"))?.week)
        // Saturday between Thursday and Sunday: still week 3.
        assertEquals(3, GameWeek.current(games, date("2026-09-26T18:00:00Z"))?.week)
    }

    @Test
    fun teamRecordCountsRegularSeasonFinalsThroughAGame() = runTest {
        val w1 = game("w1", 1, "2026-09-13T17:00:00Z", "BUF", "HOU", 36, 31)
        val w2 = game("w2", 2, "2026-09-20T17:00:00Z", "DET", "BUF", 24, 17)
        val w3 = game("w3", 3, "2026-09-27T17:00:00Z", "BUF", "LAC")
        val provider = object : MockProvider() {
            override suspend fun fetchGames(season: Int) = listOf(w3, w1, w2)
        }
        val model = dashboard(provider)
        model.loadGames(force = true)
        assertEquals("1-1", model.record("BUF"))
        assertEquals("1-0", model.record("BUF", w1))
        assertEquals("0-1", model.record("HOU"))
        assertNull(model.record("LAC"))
        assertEquals(listOf("w1", "w2", "w3"), model.schedule("BUF").map { it.id })
    }

    @Test
    fun slateOrderPutsLiveFirstThenFinalsThenUpcoming() {
        val now = date("2026-09-13T19:00:00Z")
        val slate = Game.slateOrder(
            listOf(
                game("late", 1, "2026-09-14T00:20:00Z"),
                game("final", 1, "2026-09-13T17:00:00Z", awayScore = 1, homeScore = 2),
                game("live", 1, "2026-09-13T17:00:00Z"),
            ),
            now,
        )
        assertEquals(listOf("live", "final", "late"), slate.map { it.id })
    }

    @Test
    fun boxScoreTotalsDoNotDoubleCountReceiving() {
        val json = """
            [{"player_id":1,"season":2026,"season_type":"REG","game_id":"g","game_date":"2026-09-13","player_type":"qb","team":"BUF",
              "metrics":{"attempts":30,"completions":20,"passing_yards":300,"sack_yards_lost":10,"sacks_suffered":2,
                         "interceptions":1,"passing_first_downs":12,"passing_epa":6.0,"carries":4,"rushing_yards":20,"rushing_epa":1.0}},
             {"player_id":2,"season":2026,"season_type":"REG","game_id":"g","game_date":"2026-09-13","player_type":"wr","team":"BUF",
              "metrics":{"targets":9,"receptions":7,"receiving_yards":150,"receiving_first_downs":6,"receiving_epa":5.0}},
             {"player_id":3,"season":2026,"season_type":"REG","game_id":"g","game_date":"2026-09-13","player_type":"rb","team":"BUF",
              "metrics":{"carries":16,"rushing_yards":80,"rushing_first_downs":4,"rushing_fumbles_lost":1,"rushing_epa":-1.0}}]
        """.trimIndent()
        val logs = objects(json).map { PlayerGameLog.fromJson(it) }
        assertEquals("g", logs.first().gameId)
        val box = GameBoxScore(logs)
        val totals = box.totals("BUF")
        assertEquals(390.0, totals.totalYards, 0.0)
        assertEquals(16.0, totals.firstDowns, 0.0)
        assertEquals(2.0, totals.turnovers, 0.0)
        assertEquals(6.0 / 52, totals.epaPerPlay!!, 0.0001)
        assertEquals(listOf("Passing", "Rushing", "Receiving"), box.leaders.map { it.title })
        assertEquals(3, box.rushers("BUF").first().playerId)
    }

    @Test
    fun gameDetailDecodesRatedStatsAndCounts() {
        val json = """
            [{"game_id":"g","away_team":"BUF","home_team":"HOU",
              "team_stats":{"away":{"plays":52,"epa_per_play":{"value":0.229,"pct":85},"red_zone_td_rate":null},
                            "home":{"plays":73,"epa_per_play":{"value":0.069,"pct":62}}},
              "players":[{"role":"passer","player_id":34857,"name":"J.Allen","team":"BUF","dropbacks":32,"epa":14.54,
                          "epa_per_dropback":{"value":0.454,"pct":88},"success_rate":{"value":0.438,"pct":null}},
                         {"role":"kicker","player_id":1,"team":"BUF"}],
              "win_probability":[[0,0.567],[3600,0.0]],
              "big_plays":[{"qtr":4,"clock":"01:41","team":"BUF","description":"(1:41) (Shotgun) 17-J.Allen pass deep middle to 5-J.Palmer for 34 yards, TOUCHDOWN.","epa":4.59,"home_wpa":-0.324}]}]
        """.trimIndent()
        val detail = GameDetail.fromJson(objects(json).first())
        assertEquals(52.0, detail.stats("BUF")["plays"]?.value)
        assertEquals(62, detail.stats("HOU")["epa_per_play"]?.percentile)
        assertNull(detail.away["red_zone_td_rate"])
        assertEquals(1, detail.players.size)
        assertNull(detail.players[0].successRate?.percentile)
        assertEquals(3600.0, detail.winProbability.last().elapsed, 0.0)
        assertEquals("J.Allen pass deep middle to J.Palmer for 34 yards, TOUCHDOWN.", cleanDescription(detail.bigPlays[0].description))
    }

    @Test
    fun weekRangeLabelNeverStartsBeforeWeekOne() {
        val json = """
            {"player_id":1,"season":2026,"player_type":"qb","window_weeks":3,"start_week":-1,"end_week":1,"games":1,
             "metrics":{},"prior_metrics":{},"delta":{}}
        """.trimIndent()
        val form = RecentForm.fromJson(Json.parseToJsonElement(json) as JsonObject)
        assertEquals("Week 1", form.weekRangeLabel)
        assertTrue(form.isSmallSample)
        assertTrue(form.isSmallSample(1))
    }

    @Test
    fun freshnessReportsAdvancedPending() {
        val json = """
            {"status":"degraded","refresh_id":"r","max_game_date":"2026-09-13","max_week":1,
             "observed_games":10,"expected_games":10,"ngs_status":"ready","pfr_status":"pending"}
        """.trimIndent()
        val freshness = DataFreshness.fromJson(Json.parseToJsonElement(json) as JsonObject)
        assertTrue(freshness.isAdvancedPending)
        val roundTrip = DataFreshness.fromJson(freshness.copy(isCached = true).toJson())
        assertEquals("pending", roundTrip.advancedDefenseStatus)
    }
}
