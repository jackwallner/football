package com.jackwallner.football.model

import java.text.NumberFormat
import java.time.Instant
import java.util.Locale
import kotlinx.serialization.json.JsonObject

/** One player's contribution in one game (one row per player_type). */
data class PlayerGameLog(
    val playerId: Int,
    val season: Int,
    /** Every "last N games" window filters on this; playoffs are the newest rows. */
    val seasonPhase: SeasonPhase,
    /** nflverse game id, the join key to [Game]. */
    val gameId: String? = null,
    val gameDate: Instant,
    val playerType: String,
    val team: String?,
    val opponent: String?,
    /** Pass attempts + carries + targets. */
    val plays: Int,
    /** Completions + carries + receptions. */
    val touches: Int,
    /** JSONB of nullable numbers; nulls are dropped. */
    val metrics: Map<String, Double>,
) {
    companion object {
        fun fromJson(o: JsonObject) = PlayerGameLog(
            playerId = o.requireInt("player_id"),
            season = o.requireInt("season"),
            seasonPhase = o.string("season_type")?.let { SeasonPhase.from(it) ?: throw RowDecodeException("bad phase") } ?: SeasonPhase.REGULAR,
            gameId = o.string("game_id"),
            gameDate = parseDay(o.requireString("game_date")) ?: throw RowDecodeException("bad game_date"),
            playerType = o.requireString("player_type"),
            team = o.string("team"),
            opponent = o.string("opponent"),
            plays = o.int("plays") ?: 0,
            touches = o.int("touches") ?: 0,
            metrics = o.numberMap("metrics"),
        )
    }
}

/** Stats over the last N games: counting stats summed across the window. */
data class RecentFormWindow(
    val label: String,
    val span: Int,
    val games: Int,
    val plays: Int,
    val touches: Int,
    val metrics: Map<String, Double>,
) {
    companion object {
        /** Same three choices, same wording, on every surface. */
        val windows: List<Pair<String, Int>> = RecentWindow.entries.map { it.label to it.value }

        /** Captions follow the games in hand: one game in, "5 games" would claim a span not covered. */
        fun caption(games: Int, span: Int): String {
            val count = minOf(games, span)
            if (count >= span) return "$span games"
            return if (count == 1) "1 game" else "$count games"
        }

        fun build(label: String, span: Int, logs: List<PlayerGameLog>): RecentFormWindow {
            val combined = LinkedHashMap<String, Double>()
            for (log in logs) {
                for ((key, value) in log.metrics) combined[key] = (combined[key] ?: 0.0) + value
            }
            // The game log stores solo and assisted tackles apart; every consumer wants the total.
            val solo = combined["def_tackles_solo"]
            val assists = combined["def_tackle_assists"]
            if (solo != null || assists != null) combined["tackles"] = (solo ?: 0.0) + (assists ?: 0.0)
            return RecentFormWindow(label, span, logs.size, logs.sumOf { it.plays }, logs.sumOf { it.touches }, combined)
        }
    }
}

/** One player's league-anchored weekly window from `player_recent_form`. */
data class RecentForm(
    val playerId: Int,
    val season: Int,
    val seasonPhase: SeasonPhase,
    val playerType: String,
    val windowWeeks: Int,
    /** Date of the last game in the window. */
    val asOf: Instant?,
    val startWeek: Int?,
    val endWeek: Int?,
    val team: String?,
    val games: Int,
    val plays: Int,
    val touches: Int,
    val metrics: Map<String, Double>,
    val priorMetrics: Map<String, Double>,
    val delta: Map<String, Double>,
) {
    val id: String get() = "$playerId-${seasonPhase.raw}-$playerType-$windowWeeks"

    /** "Weeks 15-17" or "Week 17"; no games exist before Week 1. */
    val weekRangeLabel: String?
        get() {
            val start = startWeek ?: return null
            val end = endWeek ?: return null
            val first = maxOf(1, start)
            return if (first >= end) "Week $end" else "Weeks $first-$end"
        }

    /** Small samples make wild deltas; the floor depends on position. */
    val isSmallSample: Boolean get() = isSmallSample(2)

    fun isSmallSample(minimumGames: Int): Boolean {
        if (games < minimumGames) return true
        return when (playerType) {
            "qb" -> plays < 30
            "rb" -> plays < 18
            "wr" -> plays < 12
            "te" -> plays < 10
            else -> false
        }
    }

    companion object {
        fun fromJson(o: JsonObject) = RecentForm(
            playerId = o.requireInt("player_id"),
            season = o.requireInt("season"),
            seasonPhase = o.string("season_type")?.let { SeasonPhase.from(it) ?: throw RowDecodeException("bad phase") } ?: SeasonPhase.REGULAR,
            playerType = o.requireString("player_type"),
            windowWeeks = o.int("window_weeks") ?: o.requireInt("window_games"),
            // A Postgres date; the device calendar, as iOS reads it.
            asOf = o.string("as_of")?.let { parseDay(it, java.time.ZoneId.systemDefault()) },
            startWeek = o.int("start_week"),
            endWeek = o.int("end_week"),
            team = o.string("team"),
            games = o.int("games") ?: 0,
            plays = o.int("plays") ?: 0,
            touches = o.int("touches") ?: 0,
            metrics = o.numberMap("metrics"),
            priorMetrics = o.numberMap("prior_metrics"),
            delta = o.numberMap("delta"),
        )
    }
}

/** Season metric label to the rolling rollup's column, plus the window formatter. */
object RecentMetricKey {
    fun key(label: String): String? = when (label) {
        "Pass Yds" -> "pass_yards"
        "Pass TD" -> "pass_tds"
        "Cmp%" -> "cmp_pct"
        "Y/A" -> "ypa"
        "INT%" -> "int_rate"
        "Rating" -> "passer_rating"
        "EPA/Play" -> "passing_epa"
        "CPOE" -> "cpoe"
        "Time to Throw" -> "avg_time_to_throw"
        "Sack%" -> "sack_rate"
        "Rush Yds" -> "rush_yards"
        "Rush TD" -> "rush_tds"
        "Y/C" -> "ypc"
        "Rush EPA" -> "rushing_epa"
        "Rush 1D" -> "rush_first_downs"
        "Fumble%" -> "fumble_rate"
        "RYOE" -> "rush_yoe"
        "Rec" -> "receptions"
        "Rec Yds" -> "rec_yards"
        "Rec TD" -> "rec_tds"
        "YAC" -> "yac"
        "RACR" -> "racr"
        "Rec EPA" -> "receiving_epa"
        "Catch%" -> "catch_pct"
        "Separation" -> "avg_separation"
        "YAC+" -> "avg_yac_above_expectation"
        "Tackles" -> "tackles"
        "Sacks" -> "sacks"
        "INT" -> "def_ints"
        "PD" -> "passes_defended"
        "FF" -> "forced_fumbles"
        "TFL" -> "tfl"
        "QB Hits" -> "qb_hits"
        else -> null
    }

    fun lowerIsBetter(label: String): Boolean = label == "INT%" || label == "Sack%" || label == "Fumble%"

    fun decimals(label: String): Int = when (label) {
        "EPA/Play", "RACR", "Time to Throw" -> 2
        "Cmp%", "INT%", "Sack%", "Fumble%", "Catch%", "Y/A", "Y/C", "Rating", "CPOE", "Rush EPA", "Rec EPA", "RYOE",
        "Separation", "YAC+" -> 1
        else -> 0
    }

    fun format(value: Double, label: String): String {
        val places = decimals(label)
        if (label.endsWith("%")) return String.format(Locale.US, "%.1f%%", value)
        if (places == 0) return NumberFormat.getIntegerInstance(Locale.US).format(swiftRoundDouble(value).toLong())
        return String.format(Locale.US, "%.${places}f", value)
    }
}

/** Per-player and per-team game-count windows. */
enum class RecentWindow(val value: Int) {
    THREE(3), FIVE(5), EIGHT(8);

    val label: String get() = "Last $value games"
    val segmentLabel: String get() = "$value games"
    val shortLabel: String get() = "${value}G"

    companion object {
        fun of(value: Int): RecentWindow? = entries.firstOrNull { it.value == value }
    }
}

/** League-anchored windows used only by Trends. */
enum class TrendWindow(val value: Int) {
    THREE(3), FIVE(5), EIGHT(8);

    val label: String get() = "Last $value weeks"
    val segmentLabel: String get() = "$value weeks"

    companion object {
        fun of(value: Int): TrendWindow? = entries.firstOrNull { it.value == value }
    }
}

/** Keeps a followed list in the fan's order and in the selected season/phase. */
object FanStatsSelection {
    fun players(ids: List<Int>, from: List<Player>, season: Int, phase: SeasonPhase): List<Player> {
        val byId = LinkedHashMap<Int, Player>()
        for (p in from) if (p.season == season && p.seasonPhase == phase) byId.putIfAbsent(p.playerId, p)
        val seen = HashSet<Int>()
        return ids.mapNotNull { id -> if (seen.add(id)) byId[id] else null }
    }

    fun summary(player: Player): List<StandardStat> {
        val labels = when (player.positionGroup) {
            PlayerPositionGroup.QB -> listOf("Pass Yds", "Pass TD", "INT")
            PlayerPositionGroup.RB -> listOf("Rush Yds", "Rush TD", "Rec Yds")
            PlayerPositionGroup.WR, PlayerPositionGroup.TE -> listOf("Rec Yds", "Rec TD", "Rec/Tgt")
            PlayerPositionGroup.DEFENSE -> listOf("Tackles", "Sacks", "Def INT")
        }
        return labels.mapNotNull { label -> player.standardStats?.firstOrNull { it.label == label } }
    }
}
