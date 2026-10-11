package com.jackwallner.football

import com.jackwallner.football.model.Game
import com.jackwallner.football.model.GameBoxScore
import com.jackwallner.football.model.MetricCategory
import com.jackwallner.football.model.Player
import com.jackwallner.football.model.PlayerGameLog
import com.jackwallner.football.model.RecentForm
import com.jackwallner.football.model.RecentFormWindow
import com.jackwallner.football.model.SeasonPhase
import com.jackwallner.football.model.TrendMetric
import com.jackwallner.football.model.competitionRanks
import com.jackwallner.football.model.prominence
import com.jackwallner.football.ui.components.partialSlate
import com.jackwallner.football.ui.screens.TeamLeagueTable
import com.jackwallner.football.ui.screens.perGameText
import com.jackwallner.football.ui.screens.rankedByProminence
import com.jackwallner.football.ui.theme.GridironPalette
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Numbers a football fan checks against the box score: team pages rank clubs
 * against clubs, windows count games rather than rows, and streaks compare per
 * game when two windows hold different numbers of games. Mirrors iOS
 * `FanCorrectnessTests`.
 */
class FanCorrectnessTest {
    private fun player(id: Int, team: String, type: String = "qb", passYds: String? = null, standard: List<Pair<String, String>>): Player =
        testPlayer(
            id = id, name = "P$id", team = team, position = type.uppercase(), season = 2026, type = type,
            metrics = listOfNotNull(passYds?.let { metric("Pass Yds", it, 50, MetricCategory.PASSING, id = "py") }),
            standard = standard.map { (label, value) -> std(label, value) },
        )

    private var nextId = 1

    private fun log(date: String, team: String = "KC", metrics: String): PlayerGameLog {
        val json = """{"player_id":${nextId++},"season":2026,"game_date":"$date","player_type":"def","team":"$team","plays":10,"touches":0,"metrics":$metrics}"""
        return PlayerGameLog.fromJson(Json.parseToJsonElement(json) as JsonObject)
    }

    private fun form(metrics: String, prior: String, games: Int): RecentForm {
        val json = """{"player_id":1,"season":2026,"player_type":"wr","window_weeks":3,"games":$games,"metrics":$metrics,"prior_metrics":$prior,"delta":{}}"""
        return RecentForm.fromJson(Json.parseToJsonElement(json) as JsonObject)
    }

    // MARK: - Team pages

    /** A club's passing yards are its quarterbacks' sum per game, not their average, and the bar ranks it among clubs. */
    @Test
    fun teamValueSumsTheRosterPerGameAndRanksAgainstClubs() {
        val kc = listOf(
            player(1, "KC", passYds = "1,000", standard = listOf("G" to "5")),
            player(2, "KC", passYds = "50", standard = listOf("G" to "2")),
        )
        val buf = listOf(player(3, "BUF", passYds = "900", standard = listOf("G" to "5")))
        assertEquals(210.0, TeamLeagueTable.teamValue("Pass Yds", MetricCategory.PASSING, kc)!!, 1e-9)
        assertEquals(180.0, TeamLeagueTable.teamValue("Pass Yds", MetricCategory.PASSING, buf)!!, 1e-9)

        val table = TeamLeagueTable(kc + buf, listOf(MetricCategory.PASSING))
        assertEquals(99, table.percentile(210.0, "Pass Yds", MetricCategory.PASSING))
        assertEquals(1, table.percentile(180.0, "Pass Yds", MetricCategory.PASSING))
        assertEquals("Pass Yds/G", TeamLeagueTable.displayLabel("Pass Yds", MetricCategory.PASSING))
        assertEquals("Pass Yds", TeamLeagueTable.baseLabel("Pass Yds/G"))
        assertEquals("Cmp%", TeamLeagueTable.displayLabel("Cmp%", MetricCategory.PASSING))
    }

    /** One row per player per game: a window's game count is its dates. */
    @Test
    fun windowCountsGamesNotRows() {
        val logs = listOf(
            log("2026-10-04", metrics = """{"def_tackles_solo":5}"""),
            log("2026-10-04", metrics = """{"def_tackles_solo":3}"""),
            log("2026-09-27", metrics = """{"def_tackles_solo":4}"""),
        )
        assertEquals(2, RecentFormWindow.build("Last 2", 2, logs).games)
    }

    // MARK: - Trends

    /** 150 yards in two games after 180 in three is a rise, not a fall. */
    @Test
    fun countingTrendsCompareWindowsPerGame() {
        val recYards = TrendMetric.receivingStandard.first { it.key == "rec_yards" }
        val values = recYards.values(form("""{"rec_yards":150,"games":2}""", """{"rec_yards":180,"games":3}""", 2))
        assertEquals(75.0, values.now!!, 1e-9)
        assertEquals(60.0, values.then!!, 1e-9)
        assertEquals(15.0, values.delta!!, 1e-9)
        assertEquals("75.0/g", recYards.display(75.0))
        assertTrue(TrendMetric.rbAdvanced.first { it.key == "rush_yoe" }.isCount)
    }

    /** A rollup without window game counts has no honest per-game prior, so no change to rank. */
    @Test
    fun countingTrendWithoutPriorGamesHasNoDelta() {
        val recYards = TrendMetric.receivingStandard.first { it.key == "rec_yards" }
        val values = recYards.values(form("""{"rec_yards":150}""", """{"rec_yards":180}""", 2))
        assertEquals(75.0, values.now!!, 1e-9)
        assertNull(values.then)
        assertNull(values.delta)
    }

    /** Rates are already per play; they pass through untouched. */
    @Test
    fun rateTrendsUseThePublishedDelta() {
        val catchPct = TrendMetric.receivingAdvanced.first { it.key == "catch_pct" }
        val values = catchPct.values(form("""{"catch_pct":70}""", """{"catch_pct":60}""", 2))
        assertNull(values.delta)
        assertEquals(70.0, values.now!!, 1e-9)
    }

    // MARK: - Profile recent windows

    @Test
    fun perGameFiguresKeepADecimalOnlyWhenSmall() {
        assertEquals("317/g", perGameText(317.0))
        assertEquals("0.7/g", perGameText(0.7))
        assertEquals("2/g", perGameText(2.0))
        assertEquals("12/g", perGameText(12.4))
    }

    // MARK: - Box score and lists

    @Test
    fun defenseBoxScoreListsTacklesFirst() {
        val logs = listOf(
            log("2026-10-08", metrics = """{"def_tackles_solo":5,"def_interceptions":1}"""),
            log("2026-10-08", metrics = """{"def_tackles_solo":9}"""),
        )
        assertEquals(listOf(9, 5), GameBoxScore(logs).defenders("KC").map { it.tackles.toInt() })
    }

    @Test
    fun pickerOrdersStartersAboveBackups() {
        val starter = player(1, "KC", standard = listOf("Pass Yds" to "1,381", "Pass TD" to "9"))
        val backup = player(2, "KC", standard = listOf("Pass Yds" to "40", "Pass TD" to "0"))
        assertTrue(starter.prominence > backup.prominence)
        assertEquals(listOf(1, 2), rankedByProminence(listOf(backup, starter)).map { it.playerId })
    }

    @Test
    fun tiesShareALeaderboardRank() {
        assertEquals(listOf(1, 2, 2, 4), competitionRanks(listOf(500.0, 479.0, 479.0, 300.0)))
        assertEquals(listOf(1, 2), competitionRanks(listOf(null, null)))
    }

    @Test
    fun tiesAreNeitherWinNorLoss() {
        assertEquals(GridironPalette.inkSecondary, GridironPalette.resultColor("T"))
        assertEquals(GridironPalette.performanceHigh, GridironPalette.resultColor("W"))
        assertEquals(GridironPalette.performanceLow, GridironPalette.resultColor("L"))
    }

    // MARK: - Freshness

    /** Thursday's game is in and Sunday's are not: say how much of the week is here. */
    @Test
    fun partlyPlayedWeekCountsItsGames() {
        fun game(id: String, week: Int) = Game(
            id = id, season = 2026, seasonPhase = SeasonPhase.REGULAR, week = week,
            kickoff = Instant.parse("2026-10-08T00:15:00Z"), awayTeam = "KC", homeTeam = "BUF",
        )
        val games = listOf(game("a", 5), game("b", 5), game("c", 5), game("d", 4))
        assertEquals(1 to 3, partialSlate(games, 2026, 5) { it.id == "a" })
        assertNull(partialSlate(games, 2026, 5) { true })
        assertNull(partialSlate(games, 2026, 5) { false })
    }
}
