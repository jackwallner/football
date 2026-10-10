package com.jackwallner.football

import com.jackwallner.football.model.FootballMetricRegistry
import com.jackwallner.football.model.MetricAggregation
import com.jackwallner.football.model.MetricCategory
import com.jackwallner.football.model.MetricValueFormat
import com.jackwallner.football.model.MetricWeight
import com.jackwallner.football.model.metricNumericValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Roster-pooling rules the team comparison relies on. A rate is weighted by the
 * volume it was measured over, so a three-carry cameo cannot outrank a season.
 */
class MetricAggregationTest {
    private fun aggregation(label: String, category: MetricCategory) = FootballMetricRegistry.aggregation(label, category)

    @Test
    fun passingRatesWeightByAttempts() {
        assertEquals(MetricAggregation.Weighted(MetricWeight.ATTEMPTS), aggregation("EPA/Play", MetricCategory.PASSING))
        assertEquals(MetricAggregation.Weighted(MetricWeight.ATTEMPTS), aggregation("Sack%", MetricCategory.PASSING))
        // Volume stats still add up.
        assertEquals(MetricAggregation.Sum, aggregation("Pass Yds", MetricCategory.PASSING))
    }

    @Test
    fun rushingTotalsSumAndRatesWeightByCarries() {
        assertEquals(MetricAggregation.Sum, aggregation("RYOE", MetricCategory.RUSHING))
        assertEquals(MetricAggregation.Sum, aggregation("Rush EPA", MetricCategory.RUSHING))
        assertEquals(MetricAggregation.Weighted(MetricWeight.CARRIES), aggregation("EPA/Rush", MetricCategory.RUSHING))
    }

    @Test
    fun targetSharesSumWhileTargetRatesWeight() {
        // A roster's target shares are shares of one offence, so they add.
        assertEquals(MetricAggregation.Sum, aggregation("Target Share", MetricCategory.RECEIVING))
        assertEquals(MetricAggregation.Weighted(MetricWeight.TARGETS), aggregation("EPA/Tgt", MetricCategory.RECEIVING))
    }

    @Test
    fun attemptWeightReadsDenominatorOfCmpAtt() {
        assertEquals(584.0, MetricWeight.ATTEMPTS.value(testPlayer(standard = listOf(std("Cmp/Att", "401/584")))))
    }

    @Test
    fun targetWeightReadsDenominatorOfRecTgt() {
        assertEquals(141.0, MetricWeight.TARGETS.value(testPlayer(standard = listOf(std("Rec/Tgt", "98/141")))))
    }

    @Test
    fun carryWeightReadsPlainColumn() {
        assertEquals(272.0, MetricWeight.CARRIES.value(testPlayer(standard = listOf(std("Car", "272")))))
    }

    /** A player with no volume must drop out of the weighted mean rather than enter it at weight 1. */
    @Test
    fun missingWeightIsNullNotZero() {
        val p = testPlayer(standard = listOf(std("G", "17")))
        assertNull(MetricWeight.ATTEMPTS.value(p))
        assertNull(MetricWeight.TARGETS.value(p))
        assertNull(MetricWeight.CARRIES.value(p))
    }

    @Test
    fun zeroVolumeIsTreatedAsMissing() {
        assertNull(MetricWeight.CARRIES.value(testPlayer(standard = listOf(std("Car", "0")))))
    }

    @Test
    fun percentFormatIsPreserved() {
        val format = MetricValueFormat.inferred(listOf("6.2%", "4.8%"))
        assertTrue(format.isPercent)
        assertEquals(1, format.decimals)
        assertEquals("5.5%", format.string(5.5))
    }

    @Test
    fun signedFormatKeepsLeadingPlus() {
        val format = MetricValueFormat.inferred(listOf("+2.3", "-1.1"))
        assertTrue(format.isSigned)
        assertEquals("+1.4", format.string(1.4))
        assertEquals("-1.4", format.string(-1.4))
    }

    @Test
    fun groupedIntegerFormat() {
        val format = MetricValueFormat.inferred(listOf("3,322", "1,004"))
        assertTrue(format.hasGrouping)
        assertEquals(0, format.decimals)
        assertEquals("4,918", format.string(4918.0))
    }

    /** Mixed precision in one column renders at the finer of the two. */
    @Test
    fun decimalsTakeTheMaximumSeen() {
        val format = MetricValueFormat.inferred(listOf("0.1", "0.12"))
        assertEquals(2, format.decimals)
        assertEquals("0.15", format.string(0.155))
    }

    @Test
    fun twoDecimalRateFormat() {
        val format = MetricValueFormat.inferred(listOf("0.05", "0.18"))
        assertFalse(format.isPercent)
        assertEquals("0.12", format.string(0.115))
    }

    @Test
    fun numericParsingHandlesFeedShapes() {
        assertEquals(3322.0, metricNumericValue("3,322")!!, 0.0)
        assertEquals(6.2, metricNumericValue("6.2%")!!, 0.0)
        assertEquals(2.3, metricNumericValue("+2.3")!!, 0.0)
        assertEquals(-1.4, metricNumericValue("-1.4")!!, 0.0)
        assertEquals(0.5, metricNumericValue(".5")!!, 0.0)
        assertNull(metricNumericValue("-"))
    }
}
