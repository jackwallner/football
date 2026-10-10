package com.jackwallner.football

import com.jackwallner.football.model.PlayerGameLog
import com.jackwallner.football.model.RecentFormWindow
import com.jackwallner.football.model.SeasonPhase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regular-season and playoff games must never end up in the same window. Playoff
 * games are the newest rows a season has, so a phase-blind "last five games" for a
 * club that reached January was mostly playoff football under a Regular Season heading.
 */
class GameLogPhaseTest {
    private fun log(date: String, seasonType: String?, yards: Double): PlayerGameLog {
        val phase = seasonType?.let { ""","season_type":"$it"""" } ?: ""
        val json = """{"player_id":7,"season":2025,"game_date":"$date","player_type":"qb","plays":30,"touches":20,"metrics":{"passing_yards":$yards}$phase}"""
        return PlayerGameLog.fromJson(Json.parseToJsonElement(json) as JsonObject)
    }

    @Test
    fun seasonPhaseIsDecodedFromSeasonType() {
        assertEquals(SeasonPhase.REGULAR, log("2026-01-04", "REG", 200.0).seasonPhase)
        assertEquals(SeasonPhase.PLAYOFFS, log("2026-02-08", "POST", 300.0).seasonPhase)
    }

    /** A row with no phase is a regular-season row, so fixtures that predate the column keep decoding. */
    @Test
    fun missingSeasonTypeDefaultsToRegular() {
        assertEquals(SeasonPhase.REGULAR, log("2025-12-07", null, 250.0).seasonPhase)
    }

    @Test
    fun aPhaseBlindWindowWouldBeAllPlayoffs() {
        val logs = listOf(
            log("2026-02-08", "POST", 300.0),
            log("2026-01-25", "POST", 280.0),
            log("2026-01-18", "POST", 260.0),
            log("2026-01-04", "REG", 240.0),
            log("2025-12-25", "REG", 220.0),
        )

        val newestThree = logs.sortedByDescending { it.gameDate }.take(3)
        assertTrue(newestThree.all { it.seasonPhase == SeasonPhase.PLAYOFFS })

        val regularOnly = logs.filter { it.seasonPhase == SeasonPhase.REGULAR }.sortedByDescending { it.gameDate }
        assertEquals(2, regularOnly.size)
        val window = RecentFormWindow.build("Last 3", 3, regularOnly)
        assertEquals(460.0, window.metrics["passing_yards"]!!, 0.0)
    }
}
