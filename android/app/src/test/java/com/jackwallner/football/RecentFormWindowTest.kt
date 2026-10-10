package com.jackwallner.football

import com.jackwallner.football.model.PlayerGameLog
import com.jackwallner.football.model.RecentFormWindow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What "the last N games" adds up to on a player page. */
class RecentFormWindowTest {
    private fun log(date: String, plays: Int = 30, touches: Int = 20, metrics: String): PlayerGameLog {
        val json = """{"player_id":1,"season":2025,"game_date":"$date","player_type":"def","plays":$plays,"touches":$touches,"metrics":$metrics}"""
        return PlayerGameLog.fromJson(Json.parseToJsonElement(json) as JsonObject)
    }

    /** The feed has solo tackles and assists as separate columns; the window derives the total. */
    @Test
    fun combinedTacklesAreDerivedFromSoloAndAssists() {
        val logs = listOf(
            log("2025-11-02", metrics = """{"def_tackles_solo":5,"def_tackle_assists":2}"""),
            log("2025-11-09", metrics = """{"def_tackles_solo":3,"def_tackle_assists":1}"""),
        )
        assertEquals(11.0, RecentFormWindow.build("Last 2", 2, logs).metrics["tackles"]!!, 0.0)
    }

    /** Assists alone still total; neither column means no key, so the row falls back to the season value. */
    @Test
    fun tacklesKeyIsAbsentWhenNeitherColumnHasData() {
        val logs = listOf(log("2025-11-02", metrics = """{"passing_yards":300}"""))
        assertNull(RecentFormWindow.build("Last 1", 1, logs).metrics["tackles"])

        val assistsOnly = listOf(log("2025-11-02", metrics = """{"def_tackle_assists":4}"""))
        assertEquals(4.0, RecentFormWindow.build("Last 1", 1, assistsOnly).metrics["tackles"]!!, 0.0)
    }

    /** The window's game count is what was supplied, not what was asked for. */
    @Test
    fun windowSumsCountingStatsOverTheGamesSupplied() {
        val logs = listOf(
            log("2025-11-02", 30, 20, """{"rushing_yards":88,"rushing_tds":1}"""),
            log("2025-11-09", 18, 14, """{"rushing_yards":42,"rushing_tds":0}"""),
        )
        val window = RecentFormWindow.build("Last 3", 3, logs)
        assertEquals(2, window.games)
        assertEquals(3, window.span)
        assertEquals(48, window.plays)
        assertEquals(34, window.touches)
        assertEquals(130.0, window.metrics["rushing_yards"]!!, 0.0)
        assertEquals(1.0, window.metrics["rushing_tds"]!!, 0.0)
    }
}
