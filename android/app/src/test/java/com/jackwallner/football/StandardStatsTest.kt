package com.jackwallner.football

import com.jackwallner.football.model.Metric
import com.jackwallner.football.model.MetricCategory
import com.jackwallner.football.model.PlayerPositionGroup
import com.jackwallner.football.model.RecentFormWindow
import com.jackwallner.football.model.StandardStatSemantics
import com.jackwallner.football.ui.screens.LeaguePercentileCurve
import com.jackwallner.football.ui.screens.StandardStatCatalog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StandardStatsTest {
    @Test
    fun positionCatalogUsesNflStatLines() {
        assertEquals("Pass Yds", StandardStatCatalog.defaultStat(PlayerPositionGroup.QB))
        assertEquals("Rush Yds", StandardStatCatalog.defaultStat(PlayerPositionGroup.RB))
        assertEquals("Rec Yds", StandardStatCatalog.defaultStat(PlayerPositionGroup.WR))
        assertEquals("Rec Yds", StandardStatCatalog.defaultStat(PlayerPositionGroup.TE))
        assertEquals("Tackles", StandardStatCatalog.defaultStat(PlayerPositionGroup.DEFENSE))
    }

    @Test
    fun walkingThePositionTabsRanksEachPositionByItsOwnDefault() {
        var stat = StandardStatCatalog.defaultStat(PlayerPositionGroup.QB)
        var position = PlayerPositionGroup.QB
        for (next in listOf(PlayerPositionGroup.RB, PlayerPositionGroup.WR, PlayerPositionGroup.TE, PlayerPositionGroup.DEFENSE, PlayerPositionGroup.QB)) {
            stat = StandardStatCatalog.stat(stat, position, next)
            position = next
            assertEquals(StandardStatCatalog.defaultStat(next), stat)
        }
    }

    @Test
    fun deliberatelyChosenStatFollowsToPositionsThatOfferIt() {
        assertEquals("Rush TD", StandardStatCatalog.stat("Rush TD", PlayerPositionGroup.QB, PlayerPositionGroup.RB))
        assertEquals("Rush TD", StandardStatCatalog.stat("Rush TD", PlayerPositionGroup.RB, PlayerPositionGroup.WR))
        assertEquals("Tackles", StandardStatCatalog.stat("Rush TD", PlayerPositionGroup.WR, PlayerPositionGroup.DEFENSE))
    }

    @Test
    fun quarterbackInterceptionsDefaultLowestFirst() {
        assertFalse(StandardStatCatalog.defaultDescending("INT", PlayerPositionGroup.QB))
        assertTrue(StandardStatCatalog.defaultDescending("Def INT", PlayerPositionGroup.DEFENSE))
    }

    @Test
    fun metricCategoryDecodesCaseInsensitively() {
        for (raw in listOf("passing", "PASSING", "Passing")) {
            val json = """{"id":"m","label":"EPA/Play","value":"0.12","percentile":88,"category":"$raw"}"""
            val metric = Metric.fromJson(Json.parseToJsonElement(json) as JsonObject)
            assertEquals(MetricCategory.PASSING, metric.category)
        }
    }

    @Test
    fun compositeStandardStatsUseRatesInsteadOfLeadingCounts() {
        assertEquals(72.727, StandardStatSemantics.numericValue("Rec/Tgt", "8/11")!!, 0.001)
        assertEquals(60.0, StandardStatSemantics.numericValue("Cmp/Att", "15/25")!!, 0.001)
    }

    @Test
    fun standardComparisonRespectsDirectionAndRates() {
        assertEquals(StandardStatSemantics.Winner.LEFT, StandardStatSemantics.winner("Rec/Tgt", "8/11", "5/9"))
        assertEquals(StandardStatSemantics.Winner.LEFT, StandardStatSemantics.winner("INT", "1", "3"))
        assertNull(StandardStatSemantics.winner("G", "1", "1"))
    }

    @Test
    fun everyExistingStandardStatGetsAPercentile() {
        assertEquals(50, StandardStatSemantics.percentile("Rec/Tgt", "8/11", listOf("8/11", "5/9", "10/10")))
        assertEquals(50, StandardStatSemantics.percentile("G", "1", emptyList()))
    }

    @Test
    fun percentileRanksAgainstWhicheverPeersHaveTheStat() {
        // Week one: two peers, and a value that beats one of them.
        assertEquals(75, StandardStatSemantics.percentile("Rush Yds", "80", listOf("80", "20")))
        // A cohort of one is the middle of its own distribution, never 0.
        assertEquals(50, StandardStatSemantics.percentile("Rush Yds", "80", listOf("80")))
        // Direction still applies with a thin pool: fewer picks is better.
        val peers = listOf("0", "3")
        assertTrue(StandardStatSemantics.percentile("INT", "0", peers) > StandardStatSemantics.percentile("INT", "3", peers))
    }

    @Test
    fun percentileIsNeverZeroForAnExistingValue() {
        // A stat that exists always lands on a drawable 1-100 bar.
        for (value in listOf("0", "1", "250", "0.0%")) {
            val pct = StandardStatSemantics.percentile("Rec Yds", value, listOf("0", "1", "250", "999"))
            assertTrue("$value produced $pct", pct in 1..100)
        }
    }

    @Test
    fun leagueCurveInterpolatesFromTwoPoints() {
        val curve = LeaguePercentileCurve.of(listOf(100.0 to 20.0, 300.0 to 80.0))
        assertNotNull(curve)
        assertEquals(50, curve!!.percentile(200.0))
        assertEquals(20, curve.percentile(50.0))
        assertEquals(80, curve.percentile(400.0))
        assertNull(LeaguePercentileCurve.of(listOf(100.0 to 20.0)))
    }

    @Test
    fun recentWindowCaptionNamesTheGamesInHand() {
        assertEquals("1 game", RecentFormWindow.caption(1, 5))
        assertEquals("3 games", RecentFormWindow.caption(3, 5))
        assertEquals("5 games", RecentFormWindow.caption(5, 5))
        // A window can never hold more than it asked for.
        assertEquals("8 games", RecentFormWindow.caption(9, 8))
    }
}
