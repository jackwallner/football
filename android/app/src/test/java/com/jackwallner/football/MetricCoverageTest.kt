package com.jackwallner.football

import com.jackwallner.football.model.MetricCategory
import com.jackwallner.football.model.MetricCoverage
import com.jackwallner.football.model.StatScoutSeason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Coverage notes pin to the same source start years the pipeline uses. */
class MetricCoverageTest {
    @Test
    fun currentSeasonHasNoCoverageCaveat() {
        assertNull(MetricCoverage.note(StatScoutSeason.current))
    }

    @Test
    fun preNextGenSeasonIsExplained() {
        val note = MetricCoverage.note(2012)
        assertNotNull(note)
        assertTrue(note!!.contains("2016"))
    }

    @Test
    fun preCpoeSeasonNamesTheOlderLimit() {
        val note = MetricCoverage.note(2001)
        assertNotNull(note)
        assertTrue(note!!.contains("2006"))
    }

    @Test
    fun missingTargetSeasonsAreCalledOut() {
        for (season in 2003..2008) {
            val note = MetricCoverage.note(season, MetricCategory.RECEIVING)
            assertNotNull("$season should carry a note", note)
            assertTrue("$season note should mention targets: $note", note!!.contains("target"))
        }
        // 2009 has targets again. It still predates Next Gen Stats, so it keeps a
        // note, just not one about targets.
        val note2009 = MetricCoverage.note(2009, MetricCategory.RECEIVING)
        assertFalse("2009 has targets: $note2009", note2009?.contains("target") == true)
        // And a modern season has no caveat at all.
        assertNull(MetricCoverage.note(2024, MetricCategory.RECEIVING))
    }

    @Test
    fun defenseNoteTracksPfrStart() {
        assertNotNull(MetricCoverage.note(2017, MetricCategory.DEFENSE))
        assertNull(MetricCoverage.note(2018, MetricCategory.DEFENSE))
    }

    @Test
    fun allTimeExplainsItSpansEras() {
        val note = MetricCoverage.note(StatScoutSeason.ALL_TIME)
        assertNotNull(note)
        assertTrue(note!!.contains("Career"))
    }

    @Test
    fun isTrackedMatchesSourceStartYears() {
        assertFalse(MetricCoverage.isTracked("Separation", 2015))
        assertTrue(MetricCoverage.isTracked("Separation", 2016))

        assertFalse(MetricCoverage.isTracked("RYOE", 2017))
        assertTrue(MetricCoverage.isTracked("RYOE", 2018))

        assertFalse(MetricCoverage.isTracked("CPOE", 2005))
        assertTrue(MetricCoverage.isTracked("CPOE", 2006))

        assertFalse(MetricCoverage.isTracked("Pressures", 2017))
        assertTrue(MetricCoverage.isTracked("Pressures", 2018))
    }

    @Test
    fun targetDerivedMetricsAreUntrackedInTheGapOnly() {
        assertTrue(MetricCoverage.isTracked("Target Share", 2002))
        assertFalse(MetricCoverage.isTracked("Target Share", 2005))
        assertTrue(MetricCoverage.isTracked("Target Share", 2009))
    }

    /** Stats with no source limit are tracked everywhere, including the career rollup. */
    @Test
    fun unboundedMetricsAreAlwaysTracked() {
        assertTrue(MetricCoverage.isTracked("EPA/Play", 2000))
        assertTrue(MetricCoverage.isTracked("Pass Yds", 2000))
        assertTrue(MetricCoverage.isTracked("Separation", StatScoutSeason.ALL_TIME))
    }
}
