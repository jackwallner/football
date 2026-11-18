package com.jackwallner.football

import com.jackwallner.football.model.MetricCategory
import com.jackwallner.football.ui.screens.TeamLeagueTable
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The store capture feed: debug only, so this lives in the debug test source set. */
class ScreenshotFixtureTest {
    /** The team frame shows the featured club's passing and rushing bars; tied clubs would all read 50. */
    @Test
    fun featuredClubRanksNearTheTopOnTheTeamPage() = runTest {
        val players = ScreenshotFixtureApi.fetchCurrentPlayers()
        val offense = listOf(MetricCategory.PASSING, MetricCategory.RUSHING, MetricCategory.RECEIVING)
        val table = TeamLeagueTable(players, offense)
        val roster = players.filter { it.team == "KC" }
        val ranks = LinkedHashMap<String, Int>()
        for (category in listOf(MetricCategory.PASSING, MetricCategory.RUSHING)) {
            for (label in category.metricPriorityOrder) {
                val value = TeamLeagueTable.teamValue(label, category, roster) ?: continue
                ranks[label] = table.percentile(value, label, category) ?: continue
            }
        }
        println("featured club ranks: $ranks")
        assertTrue("too few ranked metrics: $ranks", ranks.size >= 9)
        assertTrue("a bar ranks under 75: $ranks", ranks.values.all { it >= 75 })
        assertTrue("every bar is 99: $ranks", ranks.values.any { it < 99 })
    }

    /** The best passer keeps the base values the player frames show. */
    @Test
    fun bestPasserKeepsItsBaseValues() = runTest {
        val best = ScreenshotFixtureApi.fetchCurrentPlayers().first { it.playerId == 12001 }
        val values = best.metrics.associate { it.label to it.value }
        assertEquals("0.31", values["EPA/Play"])
        assertEquals("6.4%", values["CPOE"])
        assertEquals("2.63 s", values["Time to Throw"])
    }
}
