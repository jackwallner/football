package com.jackwallner.football.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

object StatScoutSeason {
    /**
     * The season the pipeline is writing: named for the year it kicks off, so
     * September on is this year and earlier is last year. Floored at 2025.
     */
    val current: Int = resolve(ZonedDateTime.now())

    fun resolve(now: ZonedDateTime): Int = maxOf(2025, if (now.monthValue >= 9) now.year else now.year - 1)

    /** The only season without StatScout+, and the one every screen opens on. */
    val free: Int get() = current

    /** Oldest season with per-game history, the floor for Recent Form. */
    const val EARLIEST_RECENT_FORM = 2025

    const val EARLIEST = 2000

    /** Sentinel for the career rollup; zero sorts below every real year. */
    const val ALL_TIME = 0

    fun isAllTime(season: Int): Boolean = season == ALL_TIME

    /**
     * Start of a rolling game-log window on [season], anchored to the season's
     * end (1 March of the following year) rather than to now, so the offseason
     * and past seasons still find their games.
     */
    fun gameLogWindowStart(season: Int, days: Long = 120, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): Instant {
        val seasonEnd = LocalDate.of(season + 1, 3, 1).atStartOfDay(zone).toInstant()
        val anchor = if (now < seasonEnd) now else seasonEnd
        return anchor.atZone(zone).minusDays(days).toInstant()
    }
}

/** How a season reads in the UI; the sentinel is "All since 2000", never "0". */
object SeasonLabel {
    fun text(season: Int): String =
        if (StatScoutSeason.isAllTime(season)) "All since ${StatScoutSeason.EARLIEST}" else season.toString()

    fun text(season: Int, phase: SeasonPhase): String =
        if (StatScoutSeason.isAllTime(season)) text(season) + " · " + phase.label else "$season ${phase.label}"
}
