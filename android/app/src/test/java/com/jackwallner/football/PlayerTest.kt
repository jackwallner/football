package com.jackwallner.football

import com.jackwallner.football.data.DashboardViewModel
import com.jackwallner.football.model.FootballMetricRegistry
import com.jackwallner.football.model.MetricCategory
import com.jackwallner.football.model.MetricKind
import com.jackwallner.football.model.Player
import com.jackwallner.football.model.PlayerPositionGroup
import com.jackwallner.football.model.SeasonPhase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerTest {
    @Test
    fun overallPercentileDoubleAverage() {
        val metrics = listOf(
            metric("A", "1", 75), metric("B", "2", 76), metric("C", "3", 77),
        )
        assertEquals(76, testPlayer(metrics = metrics).overallPercentile)
    }

    @Test
    fun shareSummaryIncludesTopSignal() {
        val player = testPlayer(name = "Patrick Mahomes", metrics = listOf(metric("Pass Yds", "4,918", 100)))
        val summary = player.shareSummary
        assertTrue(summary.contains("Patrick Mahomes"))
        assertTrue(summary.contains("Pass Yds"))
        assertTrue(summary.contains("100th"))
    }

    @Test
    fun multiCategoryOverallUsesBestCategoryAverage() {
        // A rushing QB carries both Passing and Rushing metrics: the headline
        // number should reflect the best category, not a blended average.
        val metrics = listOf(
            metric("Pass Yds", "4,000", 95, MetricCategory.PASSING),
            metric("Rating", "105", 95, MetricCategory.PASSING),
            metric("Rush Yds", "500", 30, MetricCategory.RUSHING),
            metric("Rush TD", "5", 30, MetricCategory.RUSHING),
        )
        assertEquals(95, testPlayer(name = "Dual Threat", team = "BUF", type = "qb", metrics = metrics).overallPercentile)
    }

    @Test
    fun playerDecodesSeasonAndPlayerType() {
        val json = """
            {
                "id": 1, "name": "Test", "team": "KC", "position": "QB", "handedness": "",
                "image_url": null, "updated_at": "2026-04-28T12:00:00Z", "season": 2025,
                "player_type": "qb", "source": "nflreadpy",
                "metrics": [], "standard_stats": [], "games": []
            }
        """.trimIndent()
        val player = Player.fromJson(Json.parseToJsonElement(json) as JsonObject)
        assertEquals(2025, player.season)
        assertEquals("qb", player.playerType)
        assertEquals("nflreadpy", player.source)
        assertEquals(SeasonPhase.REGULAR, player.seasonPhase)
    }

    @Test
    fun playerComparisonDoesNotCrossSidesOfBall() {
        val quarterback = testPlayer(1, "Quarterback", "KC", "QB", type = "qb")
        val receiver = testPlayer(2, "Receiver", "KC", "WR", type = "wr")
        val defender = testPlayer(3, "Defender", "DEN", "LB", type = "def")

        assertTrue(quarterback.canCompareHeadToHead(receiver))
        assertTrue(defender.canCompareHeadToHead(defender))
        assertFalse(quarterback.canCompareHeadToHead(defender))
    }

    @Test
    fun initialsHandleSuffixes() {
        assertEquals("MP", testPlayer(name = "Michael Pittman Jr.", team = "IND", position = "WR").initials)
        assertEquals("OB", testPlayer(name = "Odell Beckham Jr.", team = "MIA", position = "WR").initials)
        assertEquals("RG", testPlayer(name = "Robert Griffin III", team = "WAS").initials)
    }

    @Test
    fun initialsStandardNames() {
        assertEquals("PM", testPlayer(name = "Patrick Mahomes").initials)
        assertEquals("JC", testPlayer(name = "Ja'Marr Chase", team = "CIN", position = "WR").initials)
        assertEquals("C", testPlayer(name = "Cher").initials)
    }

    @Test
    fun rawNumericStripsThousandsSeparators() {
        assertEquals(4918.0, DashboardViewModel.rawNumeric("4,918")!!, 0.001)
        assertEquals(68.3, DashboardViewModel.rawNumeric("68.3%")!!, 0.001)
    }

    @Test
    fun displayPositionFallsBackToPlayerType() {
        assertEquals("DEF", testPlayer(position = "TBD", type = "def").displayPosition)
    }
}

class FootballMetricRegistryTest {
    @Test
    fun advancedAndTraditionalClassification() {
        assertEquals(MetricKind.ADVANCED, FootballMetricRegistry.kind(metric("EPA/Play", "0.18", 90)))
        assertEquals(MetricKind.TRADITIONAL, FootballMetricRegistry.kind(metric("Pass Yds", "4,000", 85)))
    }

    /** PFR's advanced defensive table (2018+) is merged in, so defence has both kinds. */
    @Test
    fun defenseHasAdvancedDefinitions() {
        val defense = FootballMetricRegistry.definitions.filter { PlayerPositionGroup.DEFENSE in it.positions }
        assertTrue(defense.isNotEmpty())
        assertTrue(defense.any { it.kind == MetricKind.ADVANCED })
        assertTrue(defense.any { it.kind == MetricKind.TRADITIONAL })
    }

    /** Coverage metrics describe what a defender gave up, so every one of them ranks ascending. */
    @Test
    fun coverageMetricsAreLowerIsBetter() {
        for (label in listOf("Cmp% Allowed", "Yds/Tgt Allowed", "Rating Allowed", "Missed Tkl%")) {
            val definition = FootballMetricRegistry.definition(label, MetricCategory.DEFENSE)
            assertNotNull("missing $label", definition)
            assertFalse("$label should rank lower-is-better", definition!!.higherIsBetter)
        }
        // Pass-rush counting stats go the other way.
        for (label in listOf("Pressures", "Hurries", "QB KD")) {
            assertTrue("$label should rank higher-is-better", FootballMetricRegistry.definition(label, MetricCategory.DEFENSE)!!.higherIsBetter)
        }
    }

    @Test
    fun defenseAdvancedMetricsLeadDisplayOrder() {
        val order = MetricCategory.DEFENSE.metricPriorityOrder
        val firstTraditional = order.indexOf("Tackles")
        val lastAdvanced = order.indexOf("Missed Tkl%")
        assertTrue(firstTraditional >= 0 && lastAdvanced >= 0)
        assertTrue(lastAdvanced < firstTraditional)
    }

    @Test
    fun positionHeadlinePreferences() {
        assertEquals("EPA/Play", PlayerPositionGroup.QB.preferredAdvancedMetrics.first())
        assertEquals("EPA/Rush", PlayerPositionGroup.RB.preferredAdvancedMetrics.first())
        assertEquals("EPA/Tgt", PlayerPositionGroup.WR.preferredAdvancedMetrics.first())
    }

    @Test
    fun lowerIsBetterUsesRegistry() {
        assertTrue(DashboardViewModel.lowerIsBetter("INT%", MetricCategory.PASSING))
        assertTrue(DashboardViewModel.lowerIsBetter("Fumble%", MetricCategory.RUSHING))
        assertFalse(DashboardViewModel.lowerIsBetter("EPA/Play", MetricCategory.PASSING))
    }

    @Test
    fun unknownMetricIsPreserved() {
        val unknown = metric("New Metric", "1.0", 50)
        assertEquals(MetricKind.ADVANCED, FootballMetricRegistry.kind(unknown))
        assertTrue(FootballMetricRegistry.isSupported(unknown, PlayerPositionGroup.QB))
        assertEquals(listOf(unknown), FootballMetricRegistry.sorted(listOf(unknown)))
    }
}
