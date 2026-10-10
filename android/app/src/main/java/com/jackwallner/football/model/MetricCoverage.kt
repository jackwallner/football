package com.jackwallner.football.model

import java.text.NumberFormat
import java.util.Locale

/**
 * What the public record can and cannot say about a season. Naming a source's
 * start year is what keeps a missing metric from reading as a bug. Mirrors the
 * constants in `backend/ingest.py`.
 */
object MetricCoverage {
    const val NEXT_GEN_FIRST_SEASON = 2016
    const val RUSHING_OVER_EXPECTED_FIRST_SEASON = 2018
    const val CPOE_FIRST_SEASON = 2006
    const val ADVANCED_DEFENSE_FIRST_SEASON = 2018
    val MISSING_TARGET_SEASONS = 2003..2008

    fun note(season: Int, category: MetricCategory? = null): String? {
        if (StatScoutSeason.isAllTime(season)) {
            return "Career totals span 2000 onward. Advanced metrics cover only the seasons their source tracked, so rate metrics are averaged over those years."
        }
        if (category == MetricCategory.DEFENSE) {
            return if (season < ADVANCED_DEFENSE_FIRST_SEASON)
                "Advanced defensive stats (pressures, coverage allowed, missed tackles) start in $ADVANCED_DEFENSE_FIRST_SEASON."
            else null
        }
        val first = MISSING_TARGET_SEASONS.first
        val last = MISSING_TARGET_SEASONS.last
        if (category == MetricCategory.RECEIVING && season in MISSING_TARGET_SEASONS) {
            return "The play-by-play record has no target data for $first-$last, so target-based metrics are unavailable and receivers are ranked by receptions."
        }
        if (season in MISSING_TARGET_SEASONS) {
            return "Next Gen Stats start in $NEXT_GEN_FIRST_SEASON. Target data is also missing league-wide for $first-$last."
        }
        if (season < CPOE_FIRST_SEASON) {
            return "Only EPA and traditional stats reach $season. CPOE starts in $CPOE_FIRST_SEASON and Next Gen Stats in $NEXT_GEN_FIRST_SEASON."
        }
        if (season < NEXT_GEN_FIRST_SEASON) {
            return "Next Gen Stats (Time to Throw, Separation, YAC+) start in $NEXT_GEN_FIRST_SEASON."
        }
        if (season < RUSHING_OVER_EXPECTED_FIRST_SEASON) {
            return "Rushing Yards Over Expected starts in $RUSHING_OVER_EXPECTED_FIRST_SEASON."
        }
        return null
    }

    /** The live season's own gap: a source that exists for this year but has not published yet. */
    fun pendingNote(category: MetricCategory, advancedDefenseStatus: String?, nextGenStatus: String?): String? {
        fun pending(status: String?): Boolean {
            val s = status?.lowercase() ?: return false
            return s != "ready" && s != "not_applicable" && s != "unavailable"
        }
        if (category == MetricCategory.DEFENSE && pending(advancedDefenseStatus)) {
            return "Advanced defensive stats (pressures, coverage allowed, missed tackles) publish once Pro-Football-Reference posts them for this season, usually within the first month. Until then defenders are ranked on production."
        }
        if (category != MetricCategory.DEFENSE && pending(nextGenStatus)) {
            return "Next Gen Stats (CPOE, separation, RYOE) for the latest games are still arriving."
        }
        return null
    }

    fun isTracked(label: String, season: Int): Boolean {
        if (StatScoutSeason.isAllTime(season)) return true
        return when (label) {
            "Time to Throw", "Aggressiveness", "Intended Air Yds", "Separation", "YAC+" -> season >= NEXT_GEN_FIRST_SEASON
            "RYOE" -> season >= RUSHING_OVER_EXPECTED_FIRST_SEASON
            "CPOE" -> season >= CPOE_FIRST_SEASON
            "Pressures", "Hurries", "QB KD", "Cmp% Allowed", "Yds/Tgt Allowed", "Rating Allowed", "Missed Tkl%" ->
                season >= ADVANCED_DEFENSE_FIRST_SEASON
            "Target Share", "WOPR", "RACR", "Catch%", "EPA/Tgt" -> season !in MISSING_TARGET_SEASONS
            else -> true
        }
    }
}

/** A metric the Trends board ranks by, keyed to the rollup column. */
data class TrendMetric(val key: String, val label: String, val unit: String, val decimals: Int, val lowerIsBetter: Boolean) {
    val id: String get() = key

    fun format(value: Double): String {
        if (decimals == 0) return NumberFormat.getIntegerInstance(Locale.US).format(swiftRoundDouble(value).toLong()) + unit
        return String.format(Locale.US, "%.${decimals}f", value) + unit
    }

    companion object {
        private fun m(key: String, label: String, unit: String, decimals: Int, lower: Boolean = false) = TrendMetric(key, label, unit, decimals, lower)

        val qbAdvanced = listOf(
            m("passing_epa", "EPA/Play", "", 2),
            m("cpoe", "CPOE", "", 1),
            m("ypa", "Y/A", "", 1),
            m("cmp_pct", "Cmp%", "%", 1),
            m("passer_rating", "Rating", "", 1),
            m("int_rate", "INT%", "%", 1, true),
            m("sack_rate", "Sack%", "%", 1, true),
            m("avg_time_to_throw", "Time to Throw", " s", 2),
        )
        val rbAdvanced = listOf(
            m("ypc", "Y/C", "", 1),
            m("rush_yoe", "RYOE", "", 1),
            m("rushing_epa", "Rush EPA", "", 1),
            m("catch_pct", "Catch%", "%", 1),
            m("fumble_rate", "Fumble%", "%", 1, true),
        )
        val receivingAdvanced = listOf(
            m("receiving_epa", "Rec EPA", "", 1),
            m("catch_pct", "Catch%", "%", 1),
            m("avg_separation", "Separation", "", 1),
            m("avg_yac_above_expectation", "YAC+", "", 1),
            m("racr", "RACR", "", 2),
        )
        val qbStandard = listOf(
            m("pass_yards", "Pass Yds", "", 0),
            m("pass_tds", "Pass TD", "", 0),
            m("completions", "Cmp", "", 0),
            m("attempts", "Att", "", 0),
            m("interceptions", "INT", "", 0, true),
            m("rush_yards", "Rush Yds", "", 0),
        )
        val rbStandard = listOf(
            m("rush_yards", "Rush Yds", "", 0),
            m("rush_tds", "Rush TD", "", 0),
            m("carries", "Car", "", 0),
            m("rush_first_downs", "Rush 1D", "", 0),
            m("receptions", "Rec", "", 0),
            m("rec_yards", "Rec Yds", "", 0),
        )
        val receivingStandard = listOf(
            m("rec_yards", "Rec Yds", "", 0),
            m("receptions", "Rec", "", 0),
            m("rec_tds", "Rec TD", "", 0),
            m("targets", "Tgt", "", 0),
            m("yac", "YAC", "", 0),
        )
        /** Defence has no advanced list; the board drops the Advanced/Standard control. */
        val defStandard = listOf(
            m("tackles", "Tackles", "", 0),
            m("sacks", "Sacks", "", 1),
            m("def_ints", "INT", "", 0),
            m("passes_defended", "PD", "", 0),
            m("tfl", "TFL", "", 0),
            m("qb_hits", "QB Hits", "", 0),
            m("forced_fumbles", "FF", "", 0),
        )

        fun advanced(side: TrendSide): List<TrendMetric> = when (side) {
            TrendSide.QB -> qbAdvanced
            TrendSide.RB -> rbAdvanced
            TrendSide.WR, TrendSide.TE -> receivingAdvanced
            TrendSide.DEF -> emptyList()
        }

        fun standard(side: TrendSide): List<TrendMetric> = when (side) {
            TrendSide.QB -> qbStandard
            TrendSide.RB -> rbStandard
            TrendSide.WR, TrendSide.TE -> receivingStandard
            TrendSide.DEF -> defStandard
        }

        fun list(side: TrendSide, mode: TrendStatMode): List<TrendMetric> {
            val picked = if (mode == TrendStatMode.ADVANCED) advanced(side) else standard(side)
            return picked.ifEmpty { standard(side) }
        }
    }
}

enum class TrendStatMode(val label: String) { ADVANCED("Advanced"), STANDARD("Standard") }

/** Which position group the Trends board ranks; groups never mix. */
enum class TrendSide(val label: String, val shortLabel: String) {
    QB("Quarterbacks", "QB"),
    RB("Running Backs", "RB"),
    WR("Receivers", "WR"),
    TE("Tight Ends", "TE"),
    DEF("Defense", "DEF");

    /** Matches `player_recent_form.player_type`. */
    val playerType: String get() = name.lowercase()

    val hasAdvancedMetrics: Boolean get() = TrendMetric.advanced(this).isNotEmpty()
}
