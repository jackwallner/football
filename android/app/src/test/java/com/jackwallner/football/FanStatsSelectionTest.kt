package com.jackwallner.football

import com.jackwallner.football.model.FanStatsSelection
import com.jackwallner.football.model.SeasonPhase
import com.jackwallner.football.model.StandardStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FanStatsSelectionTest {
    private fun player(id: Int, season: Int = 2026, phase: SeasonPhase = SeasonPhase.REGULAR, stats: List<StandardStat> = emptyList()) =
        testPlayer(id = id, name = "Player $id", team = "SEA", season = season, phase = phase, type = "qb", standard = stats)

    @Test
    fun followingDoesNotSubstitutePriorSeasonOrPostseasonStats() {
        val players = listOf(player(1, season = 2025), player(2), player(1, phase = SeasonPhase.PLAYOFFS))
        val selected = FanStatsSelection.players(listOf(1, 2), players, 2026, SeasonPhase.REGULAR)
        assertEquals(listOf(2), selected.map { it.playerId })
    }

    @Test
    fun followingPreservesPersonalOrderAndDeduplicates() {
        val players = listOf(player(1), player(2), player(2), player(3))
        val selected = FanStatsSelection.players(listOf(3, 2, 3, 99, 1), players, 2026, SeasonPhase.REGULAR)
        assertEquals(listOf(3, 2, 1), selected.map { it.playerId })
    }

    @Test
    fun missingSummaryStatsAreNotShownAsZero() {
        val selected = player(1, stats = listOf(StandardStat("Pass Yds", "280", "yards")))
        assertEquals(listOf("Pass Yds"), FanStatsSelection.summary(selected).map { it.label })
        assertTrue(FanStatsSelection.summary(player(2)).isEmpty())
    }
}
