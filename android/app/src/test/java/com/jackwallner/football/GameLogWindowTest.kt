package com.jackwallner.football

import com.jackwallner.football.model.StatScoutSeason
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a team card's rolling game-log window starts. It used to be measured back
 * from today, which finds nothing from March until the next kickoff in September.
 */
class GameLogWindowTest {
    private val zone: ZoneId = ZoneId.systemDefault()

    private fun date(year: Int, month: Int, day: Int): Instant = LocalDate.of(year, month, day).atStartOfDay(zone).toInstant()

    private fun minusDays(instant: Instant, days: Long): Instant = instant.atZone(zone).minusDays(days).toInstant()

    @Test
    fun duringTheSeasonTheWindowIsAnchoredToToday() {
        val now = date(2025, 12, 1)
        val start = StatScoutSeason.gameLogWindowStart(2025, 120, now)
        assertEquals(minusDays(now, 120), start)
    }

    /** August 2026 is five months past the 2025 season's last game; anchored to the season's end it reaches December. */
    @Test
    fun afterTheSeasonTheWindowIsAnchoredToTheSeasonsEnd() {
        val now = date(2026, 8, 17)
        val start = StatScoutSeason.gameLogWindowStart(2025, 120, now)
        assertTrue(start < now)
        assertEquals(minusDays(date(2026, 3, 1), 120), start)
        // It has to reach the last game actually played, in the first week of January 2026.
        assertTrue(start < date(2026, 1, 4))
    }

    @Test
    fun aPastSeasonNeverDependsOnTheCurrentDate() {
        val early = StatScoutSeason.gameLogWindowStart(2022, now = date(2026, 8, 17))
        val late = StatScoutSeason.gameLogWindowStart(2022, now = date(2030, 1, 1))
        assertEquals(early, late)
        assertTrue(early < date(2023, 1, 8))
    }

    /** Wide enough to slice the longest window (8 games) out of, byes included. */
    @Test
    fun windowSpansEnoughWeeksForTheLongestSlice() {
        val start = StatScoutSeason.gameLogWindowStart(2025, now = date(2026, 8, 17))
        val weeks = ChronoUnit.DAYS.between(start.atZone(zone), date(2026, 3, 1).atZone(zone)) / 7
        assertTrue(weeks >= 12)
    }
}
