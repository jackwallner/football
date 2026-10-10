package com.jackwallner.football.model

import java.text.NumberFormat
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.serialization.json.JsonObject

data class Player(
    val playerId: Int,
    val name: String,
    val team: String,
    val position: String,
    val handedness: String,
    val updatedAt: Instant,
    val season: Int?,
    val seasonPhase: SeasonPhase = SeasonPhase.REGULAR,
    val playerType: String? = null,
    val source: String? = null,
    val metrics: List<Metric>,
    val standardStats: List<StandardStat>?,
    val games: List<GameTrend> = emptyList(),
) {
    val id: String get() = "$playerId-${season ?: 0}-${seasonPhase.raw}"

    /** Metrics that carry a real rank. See [Metric.isUnranked]. */
    val rankedMetrics: List<Metric> get() = metrics.filter { !it.isUnranked }

    val overallPercentile: Int
        get() {
            val metrics = rankedMetrics
            if (metrics.isEmpty()) return 0
            // A rushing QB's headline shouldn't be diluted by averaging unrelated
            // skills: take the best category.
            val categories = metrics.map { it.category }.toSet()
            if (categories.size > 1) {
                val best = metrics.groupBy { it.category }.values
                    .maxOf { group -> group.sumOf { it.percentile }.toDouble() / group.size }
                return swiftRound(best)
            }
            return swiftRound(metrics.sumOf { it.percentile }.toDouble() / metrics.size)
        }

    val headlineMetric: Metric? get() = rankedMetrics.maxByOrNull { it.percentile }

    val latestGame: GameTrend? get() = games.maxByOrNull { it.date }

    val shareSummary: String
        get() {
            val headline = headlineMetric?.let { metric ->
                val valueText = if (metric.value.isEmpty()) "${metric.percentile.ordinal} percentile"
                else "${metric.value}, ${metric.percentile.ordinal} percentile"
                "${metric.label} $valueText"
            } ?: "${overallPercentile.ordinal} overall percentile"
            return "$name · $team $displayPosition\nOverall: ${overallPercentile.ordinal} percentile\nTop stat: $headline\nGridiron StatScout"
        }

    fun percentile(category: MetricCategory): Int? {
        val categoryMetrics = rankedMetrics.filter { it.category == category }
        if (categoryMetrics.isEmpty()) return null
        return swiftRound(categoryMetrics.sumOf { it.percentile }.toDouble() / categoryMetrics.size)
    }

    fun matchesPlayerType(category: MetricCategory?): Boolean {
        category ?: return true
        // An unknown role label falls through to "include" so a player who lost
        // it upstream but carries real metrics is never dropped.
        val type = playerType?.lowercase()?.takeIf { it in KNOWN_TYPES } ?: return true
        return when (category) {
            MetricCategory.PASSING -> type == "qb"
            MetricCategory.RUSHING -> type in setOf("qb", "rb", "wr", "te")
            MetricCategory.RECEIVING -> type in setOf("rb", "wr", "te")
            MetricCategory.DEFENSE -> type == "def"
        }
    }

    /** The category a player leads with, from the position group, else his most common metric category. */
    val primaryCategory: MetricCategory
        get() = when (playerType?.lowercase()) {
            "qb" -> MetricCategory.PASSING
            "rb" -> MetricCategory.RUSHING
            "wr", "te" -> MetricCategory.RECEIVING
            "def" -> MetricCategory.DEFENSE
            else -> metrics.groupingBy { it.category }.eachCount().maxByOrNull { it.value }?.key ?: MetricCategory.PASSING
        }

    /** Never "TBD" next to real stats: fall back to the player-type label. */
    val displayPosition: String
        get() {
            val trimmed = position.trim().uppercase()
            if (trimmed.isNotEmpty() && trimmed != "TBD" && trimmed != "—" && trimmed != "-") return position
            return playerType?.uppercase() ?: position
        }

    val initials: String
        get() {
            val parts = name.split(" ").filter { it.isNotEmpty() }
            val first = parts.firstOrNull() ?: return ""
            if (parts.size == 1) return first.take(1)
            val last = parts.last()
            val suffix = last.trim { !it.isLetterOrDigit() }.uppercase()
            if (suffix in setOf("JR", "SR", "II", "III", "IV", "V") && parts.size > 2) {
                return first.take(1) + parts[parts.size - 2].take(1)
            }
            return first.take(1) + last.take(1)
        }

    val isDefensivePlayer: Boolean get() = playerType?.lowercase() == "def" || positionGroup == PlayerPositionGroup.DEFENSE

    fun canCompareHeadToHead(other: Player): Boolean = isDefensivePlayer == other.isDefensivePlayer

    val positionGroup: PlayerPositionGroup
        get() = when (playerType?.lowercase()) {
            "qb" -> PlayerPositionGroup.QB
            "rb" -> PlayerPositionGroup.RB
            "wr" -> PlayerPositionGroup.WR
            "te" -> PlayerPositionGroup.TE
            "def" -> PlayerPositionGroup.DEFENSE
            else -> when (displayPosition.uppercase()) {
                "QB" -> PlayerPositionGroup.QB
                "RB", "FB" -> PlayerPositionGroup.RB
                "WR" -> PlayerPositionGroup.WR
                "TE" -> PlayerPositionGroup.TE
                else -> if (primaryCategory == MetricCategory.DEFENSE) PlayerPositionGroup.DEFENSE else PlayerPositionGroup.WR
            }
        }

    /** "16 att", "23 tgt", "31 car". Null when the line has none. */
    fun volumeCaption(category: MetricCategory): String? = when (category) {
        MetricCategory.PASSING -> MetricWeight.ATTEMPTS.value(this)?.let { "${it.toInt()} att" }
        MetricCategory.RUSHING -> MetricWeight.CARRIES.value(this)?.let { "${it.toInt()} car" }
        MetricCategory.RECEIVING -> MetricWeight.TARGETS.value(this)?.let { "${it.toInt()} tgt" }
        MetricCategory.DEFENSE -> MetricWeight.GAMES.value(this)?.let { "${it.toInt()} G" }
    }

    fun metrics(kind: MetricKind): List<Metric> =
        FootballMetricRegistry.sorted(metrics.filter { FootballMetricRegistry.kind(it) == kind })

    fun preferredHeadlineMetric(kind: MetricKind): Metric? {
        val candidates = metrics(kind)
        val preferred = if (kind == MetricKind.ADVANCED) positionGroup.preferredAdvancedMetrics else positionGroup.preferredTraditionalMetrics
        for (label in preferred) candidates.firstOrNull { it.label == label }?.let { return it }
        return candidates.firstOrNull()
    }

    companion object {
        private val KNOWN_TYPES = setOf("qb", "rb", "wr", "te", "def")
        private val labelPool = HashMap<String, String>()

        /** Labels repeat across thousands of rows; share one copy of each. */
        @Synchronized
        internal fun intern(value: String): String = labelPool.getOrPut(value) { value }

        /**
         * Mirrors `Player.init(from:)`: required id/name/team/position/handedness/
         * updated_at/metrics; one undecodable metric fails the row, as on iOS.
         */
        fun fromJson(o: JsonObject): Player {
            val metrics = o.array("metrics") ?: throw RowDecodeException("missing metrics")
            val updated = o.requireString("updated_at")
            return Player(
                playerId = o.requireInt("id"),
                name = o.requireString("name"),
                team = intern(o.requireString("team")),
                position = intern(o.requireString("position")),
                handedness = intern(o.requireString("handedness")),
                updatedAt = parseDate(updated) ?: throw RowDecodeException("bad updated_at"),
                season = o.int("season"),
                seasonPhase = o.string("season_type")?.let { SeasonPhase.from(it) ?: throw RowDecodeException("bad season_type") }
                    ?: SeasonPhase.REGULAR,
                playerType = o.string("player_type")?.let(::intern),
                source = o.string("source")?.let(::intern),
                metrics = metrics.map { Metric.fromJson(it as? JsonObject ?: throw RowDecodeException("bad metric")) },
                standardStats = o.array("standard_stats")?.map {
                    StandardStat.fromJson(it as? JsonObject ?: throw RowDecodeException("bad stat"))
                },
                games = o.array("games")?.map { GameTrend.fromJson(it as? JsonObject ?: throw RowDecodeException("bad game")) }
                    ?: emptyList(),
            )
        }
    }
}

/** Swift's `round` (half away from zero) on a non-negative mean. */
internal fun swiftRound(value: Double): Int = swiftRoundDouble(value).toInt()

/** Swift's `.rounded()`: half away from zero (Java's `Math.round` rounds -0.5 up). */
internal fun swiftRoundDouble(value: Double): Double = if (value < 0) -Math.floor(-value + 0.5) else Math.floor(value + 0.5)

enum class SeasonPhase(val raw: String) {
    REGULAR("REG"),
    PLAYOFFS("POST");

    /** One name everywhere: "Regular Season", never the bare adjective. */
    val label: String get() = if (this == REGULAR) "Regular Season" else "Playoffs"

    companion object {
        fun from(raw: String): SeasonPhase? = entries.firstOrNull { it.raw == raw }
    }
}

data class Metric(
    val label: String,
    val value: String,
    val percentile: Int,
    val category: MetricCategory,
    /** Live season only: whether the player clears the prorated bar. Null means it qualified. */
    val qualified: Boolean? = null,
    /** False for counting stats built outside the registry, so a zero there is unranked too. */
    val rankable: Boolean? = null,
    val id: String = "${category.raw}-$label",
) {
    /**
     * A traditional counting stat at zero (0 INT, 0 sacks). The feed ranks the
     * tie's midpoint, so most of the league read 47th percentile at something
     * they had never done. The value shows; the rank is left out.
     */
    val isUnranked: Boolean
        get() {
            if (rankable == false) return true
            val definition = FootballMetricRegistry.definition(label, category) ?: return false
            if (definition.kind != MetricKind.TRADITIONAL) return false
            if (FootballMetricRegistry.aggregation(label, category) != MetricAggregation.Sum) return false
            val number = metricNumericValue(value) ?: return false
            return number == 0.0
        }

    /** Below the prorated playing-time bar for this metric. */
    val isSmallSample: Boolean get() = qualified == false

    companion object {
        fun fromJson(o: JsonObject): Metric {
            val category = MetricCategory.from(o.requireString("category")) ?: throw RowDecodeException("bad category")
            return Metric(
                label = Player.intern(o.requireString("label")),
                value = o.requireString("value").let { if (it.length <= 6) Player.intern(it) else it },
                percentile = o.requireInt("percentile"),
                category = category,
                qualified = o.bool("qualified"),
                rankable = o.bool("rankable"),
                id = o.string("id") ?: Player.intern("${category.raw}-${o.requireString("label")}"),
            )
        }
    }
}

data class StandardStat(val label: String, val value: String, val id: String = "std-$label") {
    companion object {
        fun fromJson(o: JsonObject) = StandardStat(
            label = Player.intern(o.requireString("label")),
            value = o.requireString("value").let { if (it.length <= 6) Player.intern(it) else it },
            id = o.string("id") ?: Player.intern("std-${o.requireString("label")}"),
        )
    }
}

data class GameTrend(
    val id: String,
    val date: Instant,
    val opponent: String,
    val summary: String,
    val percentileDelta: Int,
    val keyMetric: String,
) {
    companion object {
        fun fromJson(o: JsonObject) = GameTrend(
            id = o.requireString("id"),
            date = parseDate(o.requireString("date")) ?: throw RowDecodeException("bad date"),
            opponent = o.requireString("opponent"),
            summary = o.requireString("summary"),
            percentileDelta = o.requireInt("percentile_delta"),
            keyMetric = o.requireString("key_metric"),
        )
    }
}

/**
 * Shared meaning for `standard_stats` values. "8/11" is a 72.7% catch rate,
 * not eight.
 */
object StandardStatSemantics {
    enum class Winner { LEFT, RIGHT }

    fun numericValue(label: String, value: String): Double? = when (label.uppercase()) {
        "CMP/ATT", "REC/TGT" -> {
            val parts = value.split("/", limit = 2)
            if (parts.size != 2) null
            else {
                val numerator = metricNumericValue(parts[0])
                val denominator = metricNumericValue(parts[1])
                if (numerator == null || denominator == null || denominator <= 0) null else numerator / denominator * 100
            }
        }
        else -> metricNumericValue(value)
    }

    fun higherIsBetter(label: String): Boolean = label.uppercase() != "INT"

    /** Midpoint rank against every peer carrying the same stat. */
    fun percentile(label: String, value: String, peerValues: List<String>): Int {
        val current = numericValue(label, value) ?: return 50
        var values = peerValues.mapNotNull { numericValue(label, it) }
        if (values.isEmpty()) values = listOf(current)
        val below = values.count { it < current }
        val equal = values.count { it == current }
        val raw = (below + equal / 2.0) / values.size * 100
        val oriented = if (higherIsBetter(label)) raw else 100 - raw
        return swiftRound(oriented).coerceIn(1, 100)
    }

    fun winner(label: String, left: String?, right: String?): Winner? {
        val l = left?.let { numericValue(label, it) } ?: return null
        val r = right?.let { numericValue(label, it) } ?: return null
        if (l == r) return null
        val leftWins = if (higherIsBetter(label)) l > r else l < r
        return if (leftWins) Winner.LEFT else Winner.RIGHT
    }
}

/**
 * The leading number in a formatted feed value: "6.2%" -> 6.2, "3,322" -> 3322,
 * "+2.3" -> 2.3. Matches Foundation's `Scanner.scanDouble` on these inputs.
 */
fun metricNumericValue(value: String): Double? {
    var s = value.trim().replace(",", "")
    if (s.startsWith(".")) s = "0$s"
    if (s.startsWith("-.")) s = "-0" + s.drop(1)
    val match = NUMBER_PREFIX.find(s) ?: return null
    return match.value.toDoubleOrNull()
}

private val NUMBER_PREFIX = Regex("^[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?")

/**
 * The display shape of a metric value, inferred from the feed's own strings so
 * an aggregate of "6.2%" renders as a percentage with the same precision.
 */
data class MetricValueFormat(
    val decimals: Int = 0,
    val isPercent: Boolean = false,
    val isSigned: Boolean = false,
    val hasGrouping: Boolean = false,
) {
    fun string(value: Double): String {
        var text = when {
            decimals == 0 && hasGrouping -> NumberFormat.getIntegerInstance(Locale.US).format(Math.round(value))
            decimals == 0 -> Math.round(value).toString()
            else -> String.format(Locale.US, "%.${decimals}f", value)
        }
        if (isSigned && value > 0) text = "+$text"
        if (isPercent) text += "%"
        return text
    }

    companion object {
        fun inferred(samples: List<String>): MetricValueFormat {
            var format = MetricValueFormat()
            for (sample in samples) {
                val trimmed = sample.trim()
                if (trimmed.endsWith("%")) format = format.copy(isPercent = true)
                if (trimmed.startsWith("+")) format = format.copy(isSigned = true)
                if (trimmed.contains(",")) format = format.copy(hasGrouping = true)
                val dot = trimmed.indexOf('.')
                val digits = if (dot < 0) 0 else trimmed.substring(dot + 1).takeWhile { it.isDigit() }.length
                format = format.copy(decimals = maxOf(format.decimals, digits))
            }
            return format
        }
    }
}

enum class MetricCategory(val raw: String) {
    PASSING("Passing"),
    RUSHING("Rushing"),
    RECEIVING("Receiving"),
    DEFENSE("Defense");

    /** Registry-driven display order: advanced first, then traditional. */
    val metricPriorityOrder: List<String>
        get() = FootballMetricRegistry.definitions.filter { it.category == this }.sortedBy { it.priority }.map { it.label }

    fun sortMetrics(a: String, b: String): Boolean {
        val order = metricPriorityOrder
        val ia = order.indexOf(a).let { if (it < 0) order.size else it }
        val ib = order.indexOf(b).let { if (it < 0) order.size else it }
        return ia < ib
    }

    companion object {
        fun from(raw: String): MetricCategory? = entries.firstOrNull { it.raw.equals(raw, ignoreCase = true) }
    }
}

enum class PlayerPositionGroup(val raw: String) {
    QB("QB"), RB("RB"), WR("WR"), TE("TE"), DEFENSE("DEF");

    val cohortDescription: String get() = if (this == DEFENSE) "Among defensive players" else "Among ${raw}s"

    val primaryCategory: MetricCategory
        get() = when (this) {
            QB -> MetricCategory.PASSING
            RB -> MetricCategory.RUSHING
            WR, TE -> MetricCategory.RECEIVING
            DEFENSE -> MetricCategory.DEFENSE
        }

    val preferredAdvancedMetrics: List<String>
        get() = when (this) {
            QB -> listOf("EPA/Play", "CPOE", "Rating")
            RB -> listOf("EPA/Rush", "RYOE", "Explosive%", "Rush EPA")
            WR, TE -> listOf("EPA/Tgt", "WOPR", "YAC+", "Rec EPA")
            // Pressures exists for every defender, not only the ones targeted enough to rank in coverage.
            DEFENSE -> listOf("Pressures", "Rating Allowed", "Cmp% Allowed", "Missed Tkl%")
        }

    val preferredTraditionalMetrics: List<String>
        get() = when (this) {
            QB -> listOf("Pass Yds", "Pass TD", "Cmp%")
            RB -> listOf("Rush Yds", "Rush TD", "Car")
            WR, TE -> listOf("Rec Yds", "Rec TD", "Rec")
            DEFENSE -> listOf("Tackles", "Sacks", "Def INT")
        }
}

enum class MetricKind(val label: String) { ADVANCED("Advanced"), TRADITIONAL("Traditional") }

enum class MetricFamily(val label: String) {
    EFFICIENCY("Efficiency"), ACCURACY("Accuracy"), PRESSURE("Pressure"), AGGRESSIVENESS("Aggressiveness"),
    RUSHING("Rushing"), EXPECTED_PRODUCTION("Expected"), EXPLOSIVENESS("Explosiveness"), USAGE("Usage"),
    RECEIVING("Receiving"), SEPARATION("Separation"), YAC("YAC"), PRODUCTION("Production"),
    PASS_RUSH("Pass Rush"), TURNOVERS("Turnovers"), COVERAGE("Coverage"), TACKLING("Tackling"),
}

data class MetricDefinition(
    val label: String,
    val category: MetricCategory,
    val kind: MetricKind,
    val family: MetricFamily,
    val positions: Set<PlayerPositionGroup>,
    val higherIsBetter: Boolean,
    val priority: Int,
    val description: String,
)

/** How several players' values for one metric collapse into one number. */
sealed interface MetricAggregation {
    data object Sum : MetricAggregation
    data class Weighted(val weight: MetricWeight) : MetricAggregation
}

/** The denominator a rate was computed against, read off the standard line. */
enum class MetricWeight {
    ATTEMPTS, CARRIES, TARGETS, TARGETS_ALLOWED, GAMES;

    /** Null when the player has no volume, which drops him from a weighted mean. */
    fun value(player: Player): Double? = when (this) {
        ATTEMPTS -> secondComponent("Cmp/Att", player)
        TARGETS -> secondComponent("Rec/Tgt", player)
        TARGETS_ALLOWED -> plain("Tgt Allowed", player)
        CARRIES -> plain("Car", player)
        GAMES -> plain("G", player)
    }

    private fun plain(label: String, player: Player): Double? {
        val raw = player.standardStats?.firstOrNull { it.label == label }?.value ?: return null
        return metricNumericValue(raw)?.takeIf { it > 0 }
    }

    /** "18/29" -> 29. */
    private fun secondComponent(label: String, player: Player): Double? {
        val raw = player.standardStats?.firstOrNull { it.label == label }?.value ?: return null
        val parts = raw.split("/", limit = 2)
        if (parts.size != 2) return null
        return metricNumericValue(parts[1])?.takeIf { it > 0 }
    }
}

object FootballMetricRegistry {
    private val QB = setOf(PlayerPositionGroup.QB)
    private val RUSHERS = setOf(PlayerPositionGroup.QB, PlayerPositionGroup.RB, PlayerPositionGroup.WR, PlayerPositionGroup.TE)
    private val BACKS = setOf(PlayerPositionGroup.QB, PlayerPositionGroup.RB)
    private val CATCHERS = setOf(PlayerPositionGroup.RB, PlayerPositionGroup.WR, PlayerPositionGroup.TE)
    private val DEF = setOf(PlayerPositionGroup.DEFENSE)

    private fun d(
        label: String, category: MetricCategory, kind: MetricKind, family: MetricFamily,
        positions: Set<PlayerPositionGroup>, priority: Int, description: String, higherIsBetter: Boolean = true,
    ) = MetricDefinition(label, category, kind, family, positions, higherIsBetter, priority, description)

    private val P = MetricCategory.PASSING
    private val R = MetricCategory.RUSHING
    private val C = MetricCategory.RECEIVING
    private val D = MetricCategory.DEFENSE
    private val A = MetricKind.ADVANCED
    private val T = MetricKind.TRADITIONAL

    val definitions: List<MetricDefinition> = listOf(
        d("EPA/Play", P, A, MetricFamily.EFFICIENCY, QB, 10, "Passing expected points added divided by attempts plus sacks. EPA measures the change in expected points from before to after a play."),
        d("CPOE", P, A, MetricFamily.ACCURACY, QB, 20, "Completion percentage minus the completion rate expected from throw difficulty, in percentage points."),
        d("INT%", P, A, MetricFamily.ACCURACY, QB, 30, "Passing interceptions divided by attempts.", higherIsBetter = false),
        d("Sack%", P, A, MetricFamily.PRESSURE, QB, 40, "Sacks taken divided by attempts plus sacks.", higherIsBetter = false),
        d("Time to Throw", P, A, MetricFamily.PRESSURE, QB, 50, "Average seconds from the snap until the passer releases the ball."),
        d("Aggressiveness", P, A, MetricFamily.AGGRESSIVENESS, QB, 60, "Percentage of attempts thrown into tight coverage, with a defender within one yard of the receiver."),
        d("Intended Air Yds", P, A, MetricFamily.AGGRESSIVENESS, QB, 70, "Average vertical distance the ball travels from the line of scrimmage to the intended target."),
        d("Pass Yds", P, T, MetricFamily.PRODUCTION, QB, 110, "Total passing yards."),
        d("Pass TD", P, T, MetricFamily.PRODUCTION, QB, 120, "Total passing touchdowns."),
        d("Cmp%", P, T, MetricFamily.ACCURACY, QB, 130, "Completions divided by passing attempts."),
        d("Y/A", P, T, MetricFamily.EFFICIENCY, QB, 140, "Passing yards per attempt."),
        d("Rating", P, T, MetricFamily.EFFICIENCY, QB, 150, "NFL passer rating calculated from completion rate, yards per attempt, touchdown rate, and interception rate. Maximum 158.3."),

        d("EPA/Rush", R, A, MetricFamily.EFFICIENCY, RUSHERS, 10, "Expected points added per rushing attempt."),
        d("RYOE", R, A, MetricFamily.EXPECTED_PRODUCTION, BACKS, 20, "Total rushing yards gained above or below the yards expected by the Next Gen Stats model."),
        d("Explosive%", R, A, MetricFamily.EXPLOSIVENESS, RUSHERS, 30, "Percentage of carries gaining at least 10 yards."),
        d("Rush EPA", R, A, MetricFamily.PRODUCTION, RUSHERS, 40, "Total expected points added on rushing plays."),
        d("Fumble%", R, A, MetricFamily.EFFICIENCY, RUSHERS, 50, "Rushing fumbles divided by carries.", higherIsBetter = false),
        d("Rush Yds", R, T, MetricFamily.PRODUCTION, RUSHERS, 110, "Total rushing yards."),
        d("Rush TD", R, T, MetricFamily.PRODUCTION, RUSHERS, 120, "Total rushing touchdowns."),
        d("Y/C", R, T, MetricFamily.EFFICIENCY, RUSHERS, 130, "Rushing yards per carry."),
        d("Rush 1D", R, T, MetricFamily.PRODUCTION, RUSHERS, 140, "Rushing first downs."),

        d("EPA/Tgt", C, A, MetricFamily.EFFICIENCY, CATCHERS, 10, "Expected points added per target."),
        d("WOPR", C, A, MetricFamily.USAGE, CATCHERS, 20, "Weighted opportunity rating: 1.5 × target share plus 0.7 × air-yards share."),
        d("Target Share", C, A, MetricFamily.USAGE, CATCHERS, 30, "Player targets as a share of the team's pass attempts."),
        d("RACR", C, A, MetricFamily.EFFICIENCY, CATCHERS, 40, "Receiving yards divided by receiving air yards."),
        d("Separation", C, A, MetricFamily.SEPARATION, CATCHERS, 50, "Average yards between the targeted receiver and the nearest defender at pass arrival."),
        d("YAC+", C, A, MetricFamily.YAC, CATCHERS, 60, "Average yards after catch gained above or below the Next Gen Stats expectation."),
        d("Rec EPA", C, A, MetricFamily.PRODUCTION, CATCHERS, 70, "Total expected points added on receiving plays."),
        d("Rec", C, T, MetricFamily.PRODUCTION, CATCHERS, 110, "Total receptions."),
        d("Rec Yds", C, T, MetricFamily.PRODUCTION, CATCHERS, 120, "Total receiving yards."),
        d("Rec TD", C, T, MetricFamily.PRODUCTION, CATCHERS, 130, "Total receiving touchdowns."),
        d("YAC", C, T, MetricFamily.YAC, CATCHERS, 140, "Yards after catch."),
        d("Catch%", C, T, MetricFamily.EFFICIENCY, CATCHERS, 150, "Receptions divided by targets."),

        // Coverage metrics describe what a defender allowed, so lower is better on all four.
        d("Pressures", D, A, MetricFamily.PASS_RUSH, DEF, 10, "Quarterback pressures: sacks, hits and hurries credited to this defender. Pro-Football-Reference, 2018 onward."),
        d("Hurries", D, A, MetricFamily.PASS_RUSH, DEF, 20, "Times the defender forced the quarterback to move off his spot or throw early without hitting him. 2018 onward."),
        d("QB KD", D, A, MetricFamily.PASS_RUSH, DEF, 30, "Quarterback knockdowns: times the defender put the passer on the ground, sack or not. 2018 onward."),
        d("Cmp% Allowed", D, A, MetricFamily.COVERAGE, DEF, 40, "Completion percentage on passes thrown at this defender. Needs at least 20 targets to be ranked. 2018 onward.", higherIsBetter = false),
        d("Yds/Tgt Allowed", D, A, MetricFamily.COVERAGE, DEF, 50, "Yards allowed per pass thrown at this defender. Needs at least 20 targets to be ranked. 2018 onward.", higherIsBetter = false),
        d("Rating Allowed", D, A, MetricFamily.COVERAGE, DEF, 60, "Passer rating on throws into this defender's coverage. Needs at least 20 targets to be ranked. 2018 onward.", higherIsBetter = false),
        d("Missed Tkl%", D, A, MetricFamily.TACKLING, DEF, 70, "Share of this defender's tackle attempts that he missed. Needs at least 20 combined tackles to be ranked. 2018 onward.", higherIsBetter = false),

        d("Tackles", D, T, MetricFamily.PRODUCTION, DEF, 110, "Total tackles."),
        d("TFL", D, T, MetricFamily.PRODUCTION, DEF, 120, "Tackles for loss."),
        d("PD", D, T, MetricFamily.PRODUCTION, DEF, 130, "Passes defended."),
        d("Sacks", D, T, MetricFamily.PASS_RUSH, DEF, 140, "Total sacks."),
        d("QB Hits", D, T, MetricFamily.PASS_RUSH, DEF, 150, "Quarterback hits."),
        d("INT", D, T, MetricFamily.TURNOVERS, DEF, 160, "Defensive interceptions."),
        d("FF", D, T, MetricFamily.TURNOVERS, DEF, 170, "Forced fumbles."),
    )

    private val byKey: Map<Pair<String, MetricCategory>, MetricDefinition> =
        definitions.associateBy { it.label to it.category }

    fun definition(label: String, category: MetricCategory): MetricDefinition? = byKey[label to category]

    /**
     * Rates are weighted by the volume they were measured over; totals and
     * shares of a team's own volume are summed.
     */
    fun aggregation(label: String, category: MetricCategory): MetricAggregation = when (category) {
        MetricCategory.PASSING -> if (label == "Pass Yds" || label == "Pass TD") MetricAggregation.Sum
        else MetricAggregation.Weighted(MetricWeight.ATTEMPTS)
        MetricCategory.RUSHING -> if (label in setOf("Rush Yds", "Rush TD", "Rush 1D", "Rush EPA", "RYOE")) MetricAggregation.Sum
        else MetricAggregation.Weighted(MetricWeight.CARRIES)
        MetricCategory.RECEIVING -> if (label in setOf("Rec", "Rec Yds", "Rec TD", "YAC", "Rec EPA", "Target Share", "WOPR")) MetricAggregation.Sum
        else MetricAggregation.Weighted(MetricWeight.TARGETS)
        MetricCategory.DEFENSE -> when (label) {
            "Cmp% Allowed", "Yds/Tgt Allowed", "Rating Allowed", "ADOT" -> MetricAggregation.Weighted(MetricWeight.TARGETS_ALLOWED)
            "Missed Tkl%" -> MetricAggregation.Weighted(MetricWeight.GAMES)
            else -> MetricAggregation.Sum
        }
    }

    fun kind(metric: Metric): MetricKind = definition(metric.label, metric.category)?.kind ?: MetricKind.ADVANCED

    fun isSupported(metric: Metric, position: PlayerPositionGroup): Boolean =
        definition(metric.label, metric.category)?.positions?.contains(position) ?: true

    fun sorted(metrics: List<Metric>): List<Metric> = metrics.sortedWith { lhs, rhs ->
        val left = definition(lhs.label, lhs.category)?.priority ?: Int.MAX_VALUE
        val right = definition(rhs.label, rhs.category)?.priority ?: Int.MAX_VALUE
        if (left == right) lhs.label.compareTo(rhs.label) else left.compareTo(right)
    }
}

data class TeamRoute(val abbr: String)

/** "1st", "22nd", "113th". */
val Int.ordinal: String
    get() {
        val mod100 = this % 100
        val suffix = if (mod100 in 11..13) "th" else when (this % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
        return "$this$suffix"
    }

internal fun Double.roundToIntSafe(): Int = if (isNaN()) 0 else roundToInt()
