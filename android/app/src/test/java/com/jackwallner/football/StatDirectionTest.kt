package com.jackwallner.football

import com.jackwallner.football.data.DashboardViewModel
import com.jackwallner.football.model.MetricCategory
import com.jackwallner.football.model.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatDirectionTest {
    private fun player(
        id: Int,
        name: String,
        value: String,
        percentile: Int,
        label: String = "EPA/Play",
        category: MetricCategory = MetricCategory.PASSING,
    ) = testPlayer(
        id = id, name = name, team = "BUF", type = "qb",
        metrics = listOf(metric(label, value, percentile, category, id = "m$id")),
    )

    private fun ranked(players: List<Player>, descending: Boolean = true) =
        players.sortedWith(DashboardViewModel.metricComparator("EPA/Play", MetricCategory.PASSING, descending))

    @Test
    fun footballLowerIsBetterMetrics() {
        assertTrue(DashboardViewModel.lowerIsBetter("INT%", MetricCategory.PASSING))
        assertTrue(DashboardViewModel.lowerIsBetter("Sack%", MetricCategory.PASSING))
        assertTrue(DashboardViewModel.lowerIsBetter("Fumble%", MetricCategory.RUSHING))
        assertFalse(DashboardViewModel.lowerIsBetter("EPA/Play", MetricCategory.PASSING))
        assertFalse(DashboardViewModel.lowerIsBetter("Tackles", MetricCategory.DEFENSE))
    }

    @Test
    fun blankValuesRankByPercentileInsteadOfLast() {
        val players = listOf(
            player(1, "Printable but poor", "0.01", 5),
            player(2, "Blank but elite", "", 99),
            player(3, "Blank and poor", "", 2),
        )
        assertEquals(listOf("Blank but elite", "Printable but poor", "Blank and poor"), ranked(players).map { it.name })
    }

    @Test
    fun equalPercentilesBreakTiesOnValue() {
        val players = listOf(player(1, "Lower value", "0.12", 80), player(2, "Higher value", "0.21", 80))
        assertEquals(listOf("Higher value", "Lower value"), ranked(players).map { it.name })
    }

    @Test
    fun missingMetricSortsLastInBothDirections() {
        val hasMetric = player(1, "Has it", "0.12", 50)
        val lacksMetric = player(2, "Lacks it", "62.0%", 40, label = "Cmp%")
        for (descending in listOf(true, false)) {
            assertEquals("Lacks it", ranked(listOf(lacksMetric, hasMetric), descending).last().name)
        }
    }
}
