package com.jackwallner.football.model

import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * A basic box score rebuilt from one game's `player_game_logs` rows: sums of
 * published per-player counts, never averages or percentiles.
 */
class GameBoxScore(logs: List<PlayerGameLog>) {
    data class PlayerLine(val playerId: Int, val team: String, val playerType: String, val metrics: Map<String, Double>) {
        val id: String get() = "$playerId-$playerType"
        fun value(key: String): Double = metrics[key] ?: 0.0
        fun int(key: String): Int = swiftRound(value(key))
        val tackles: Double get() = value("def_tackles_solo") + value("def_tackle_assists")
    }

    data class TeamTotals(
        var passingYards: Double = 0.0,
        var sackYardsLost: Double = 0.0,
        var rushingYards: Double = 0.0,
        var firstDowns: Double = 0.0,
        var interceptionsThrown: Double = 0.0,
        var fumblesLost: Double = 0.0,
        var sacksTaken: Double = 0.0,
        var passAttempts: Double = 0.0,
        var carries: Double = 0.0,
        var offenseEPA: Double = 0.0,
        var hasEPA: Boolean = false,
        var passingEPA: Double = 0.0,
        var rushingEPA: Double = 0.0,
        var hasPassingEPA: Boolean = false,
        var hasRushingEPA: Boolean = false,
        var airYards: Double = 0.0,
        var yardsAfterCatch: Double = 0.0,
    ) {
        val totalYards: Double get() = passingYards - sackYardsLost + rushingYards
        val netPassingYards: Double get() = passingYards - sackYardsLost
        val turnovers: Double get() = interceptionsThrown + fumblesLost
        val dropbacks: Double get() = passAttempts + sacksTaken
        val plays: Double get() = dropbacks + carries
        val epaPerPlay: Double? get() = if (hasEPA && plays > 0) offenseEPA / plays else null
        val epaPerDropback: Double? get() = if (hasPassingEPA && dropbacks > 0) passingEPA / dropbacks else null
        val epaPerCarry: Double? get() = if (hasRushingEPA && carries > 0) rushingEPA / carries else null
        val yardsPerPlay: Double? get() = if (plays > 0) totalYards / plays else null
        val netYardsPerDropback: Double? get() = if (dropbacks > 0) netPassingYards / dropbacks else null
        val yardsPerCarry: Double? get() = if (carries > 0) rushingYards / carries else null
        val airYardsPerAttempt: Double? get() = if (passAttempts > 0) airYards / passAttempts else null
        val yacShare: Double? get() = if (passingYards > 0) yardsAfterCatch / passingYards * 100 else null
        val sackRate: Double? get() = if (dropbacks > 0) sacksTaken / dropbacks * 100 else null
        val firstDownRate: Double? get() = if (plays > 0) firstDowns / plays * 100 else null
    }

    data class Leader(val title: String, val line: PlayerLine, val summary: String)

    val lines: List<PlayerLine> = logs.map { log ->
        PlayerLine(log.playerId, normalizedTeamAbbreviation(log.team ?: ""), log.playerType, log.metrics)
    }

    val isEmpty: Boolean get() = lines.isEmpty()

    fun lines(team: String): List<PlayerLine> {
        val abbr = normalizedTeamAbbreviation(team)
        return lines.filter { it.team == abbr }
    }

    fun totals(team: String): TeamTotals {
        val t = TeamTotals()
        for (line in lines(team)) {
            t.passingYards += line.value("passing_yards")
            t.sackYardsLost += line.value("sack_yards_lost")
            t.rushingYards += line.value("rushing_yards")
            t.firstDowns += line.value("passing_first_downs") + line.value("rushing_first_downs")
            t.interceptionsThrown += line.value("interceptions")
            t.fumblesLost += line.value("rushing_fumbles_lost")
            t.sacksTaken += line.value("sacks_suffered")
            t.passAttempts += line.value("attempts")
            t.carries += line.value("carries")
            t.airYards += line.value("passing_air_yards")
            t.yardsAfterCatch += line.value("receiving_yac")
            line.metrics["passing_epa"]?.let { epa ->
                t.passingEPA += epa; t.offenseEPA += epa; t.hasPassingEPA = true; t.hasEPA = true
            }
            line.metrics["rushing_epa"]?.let { epa ->
                t.rushingEPA += epa; t.offenseEPA += epa; t.hasRushingEPA = true; t.hasEPA = true
            }
        }
        return t
    }

    fun passers(team: String) = lines(team).filter { it.value("attempts") > 0 }.sortedByDescending { it.value("attempts") }

    fun rushers(team: String) = lines(team).filter { it.value("carries") > 0 }
        .sortedWith(compareByDescending<PlayerLine> { it.value("rushing_yards") }.thenByDescending { it.value("carries") })

    fun receivers(team: String) = lines(team).filter { it.value("targets") > 0 || it.value("receptions") > 0 }
        .sortedWith(compareByDescending<PlayerLine> { it.value("receiving_yards") }.thenByDescending { it.value("receptions") })

    fun defenders(team: String) = lines(team)
        .filter { it.tackles > 0 || it.value("def_sacks") > 0 || it.value("def_interceptions") > 0 }
        .sortedWith(compareByDescending<PlayerLine> { it.value("def_sacks") + it.value("def_interceptions") }.thenByDescending { it.tackles })

    /** The most valuable players of the game by total EPA, both teams. */
    fun epaLeaders(limit: Int = 5): List<PlayerLine> =
        lines.mapNotNull { line -> totalEPA(line)?.let { line to it } }.sortedByDescending { it.second }.take(limit).map { it.first }

    val leaders: List<Leader>
        get() {
            val result = mutableListOf<Leader>()
            lines.filter { it.value("attempts") > 0 }.maxByOrNull { it.value("passing_yards") }
                ?.let { result += Leader("Passing", it, passingSummary(it)) }
            lines.filter { it.value("carries") > 0 }.maxByOrNull { it.value("rushing_yards") }
                ?.let { result += Leader("Rushing", it, rushingSummary(it)) }
            lines.filter { it.value("receptions") > 0 }.maxByOrNull { it.value("receiving_yards") }
                ?.let { result += Leader("Receiving", it, receivingSummary(it)) }
            lines.filter { it.tackles > 0 || it.value("def_sacks") > 0 }
                .maxWithOrNull(compareBy<PlayerLine> { it.value("def_sacks") + it.value("def_interceptions") }.thenBy { it.tackles })
                ?.let { result += Leader("Defense", it, defenseSummary(it)) }
            return result
        }

    companion object {
        /** EPA across passing, rushing and receiving for one player; never summed to a team. */
        fun totalEPA(line: PlayerLine): Double? {
            val parts = listOf("passing_epa", "rushing_epa", "receiving_epa").mapNotNull { line.metrics[it] }
            return if (parts.isEmpty()) null else parts.sum()
        }

        fun passingSummary(line: PlayerLine): String {
            val parts = mutableListOf("${line.int("completions")}/${line.int("attempts")}", "${line.int("passing_yards")} yds")
            if (line.int("passing_tds") > 0) parts += "${line.int("passing_tds")} TD"
            if (line.int("interceptions") > 0) parts += "${line.int("interceptions")} INT"
            return parts.joinToString(", ")
        }

        fun rushingSummary(line: PlayerLine): String {
            val parts = mutableListOf("${line.int("carries")} car", "${line.int("rushing_yards")} yds")
            if (line.int("rushing_tds") > 0) parts += "${line.int("rushing_tds")} TD"
            return parts.joinToString(", ")
        }

        fun receivingSummary(line: PlayerLine): String {
            val parts = mutableListOf("${line.int("receptions")} rec", "${line.int("receiving_yards")} yds")
            if (line.int("receiving_tds") > 0) parts += "${line.int("receiving_tds")} TD"
            return parts.joinToString(", ")
        }

        fun defenseSummary(line: PlayerLine): String {
            val parts = mutableListOf("${swiftRound(line.tackles)} tkl")
            val sacks = line.value("def_sacks")
            if (sacks > 0) parts += "${formatUpToOneDecimal(sacks)} sck"
            if (line.int("def_interceptions") > 0) parts += "${line.int("def_interceptions")} INT"
            return parts.joinToString(", ")
        }

        /** A player's one-line game summary, whichever parts of the game they had. */
        fun summary(line: PlayerLine): String {
            val parts = mutableListOf<String>()
            if (line.value("attempts") > 0) parts += passingSummary(line)
            if (line.value("carries") > 0) parts += rushingSummary(line)
            if (line.value("targets") > 0 || line.value("receptions") > 0) parts += receivingSummary(line)
            if (parts.isEmpty() && (line.tackles > 0 || line.value("def_sacks") > 0)) parts += defenseSummary(line)
            return parts.joinToString(" · ")
        }
    }
}

/** "1", "1.5": Swift's `.precision(.fractionLength(0...1))`. */
fun formatUpToOneDecimal(value: Double): String {
    val rounded = swiftRoundDouble(value * 10) / 10
    return if (rounded == Math.floor(rounded)) rounded.toLong().toString() else String.format(Locale.US, "%.1f", rounded)
}

/** A number with its percentile among this season's team or player games. */
data class RatedValue(val value: Double, val percentile: Int? = null) {
    companion object {
        fun from(element: kotlinx.serialization.json.JsonElement?): RatedValue? = when (element) {
            is JsonPrimitive -> if (element.isString) null else element.doubleOrNull?.let { RatedValue(it) }
            is JsonObject -> element.double("value")?.let { RatedValue(it, element.int("pct")) }
            else -> null
        }
    }
}

/** Play-by-play breakdown for one game, from `public.game_details`. */
data class GameDetail(
    val gameId: String,
    val awayTeam: String,
    val homeTeam: String,
    val away: Map<String, RatedValue>,
    val home: Map<String, RatedValue>,
    val players: List<PlayerLine>,
    val winProbability: List<WinProbabilityPoint>,
    val bigPlays: List<BigPlay>,
) {
    enum class Role { PASSER, RUSHER, RECEIVER }

    data class PlayerLine(
        val role: Role,
        val playerId: Int,
        val name: String?,
        val team: String,
        val dropbacks: Int?,
        val carries: Int?,
        val targets: Int?,
        val epa: Double?,
        val epaPerDropback: RatedValue?,
        val epaPerCarry: RatedValue?,
        val epaPerTarget: RatedValue?,
        val successRate: RatedValue?,
        val cpoe: RatedValue?,
        val adot: RatedValue?,
    ) {
        val id: String get() = "${role.name.lowercase()}-$playerId"

        companion object {
            fun fromJson(o: JsonObject): PlayerLine {
                val role = when (o.requireString("role")) {
                    "passer" -> Role.PASSER
                    "rusher" -> Role.RUSHER
                    "receiver" -> Role.RECEIVER
                    else -> throw RowDecodeException("role")
                }
                // A present-but-malformed rated value fails the line, as Swift's decoder does.
                fun rated(key: String): RatedValue? {
                    val el = o[key] ?: return null
                    if (el is kotlinx.serialization.json.JsonNull) return null
                    return RatedValue.from(el) ?: throw RowDecodeException(key)
                }
                return PlayerLine(
                    role = role,
                    playerId = o.requireInt("player_id"),
                    name = o.string("name"),
                    team = o.requireString("team"),
                    dropbacks = o.int("dropbacks"),
                    carries = o.int("carries"),
                    targets = o.int("targets"),
                    epa = o.double("epa"),
                    epaPerDropback = rated("epa_per_dropback"),
                    epaPerCarry = rated("epa_per_carry"),
                    epaPerTarget = rated("epa_per_target"),
                    successRate = rated("success_rate"),
                    cpoe = rated("cpoe"),
                    adot = rated("adot"),
                )
            }
        }
    }

    data class BigPlay(val qtr: Int, val clock: String, val team: String, val description: String, val epa: Double?, val homeWPA: Double) {
        val id: String get() = "$qtr-$clock-${description.take(24)}"

        companion object {
            fun fromJson(o: JsonObject) = BigPlay(
                qtr = o.requireInt("qtr"),
                clock = o.requireString("clock"),
                team = o.requireString("team"),
                description = o.requireString("description"),
                epa = o.double("epa"),
                homeWPA = o.double("home_wpa") ?: throw RowDecodeException("home_wpa"),
            )
        }
    }

    data class WinProbabilityPoint(val elapsed: Double, val homeWinProbability: Double)

    fun stats(team: String): Map<String, RatedValue> =
        if (normalizedTeamAbbreviation(team) == normalizedTeamAbbreviation(homeTeam)) home else away

    fun players(role: Role): List<PlayerLine> =
        players.filter { it.role == role }.sortedByDescending { it.epa ?: Double.NEGATIVE_INFINITY }

    companion object {
        fun fromJson(o: JsonObject): GameDetail {
            val sides = o.obj("team_stats")
            fun side(key: String): Map<String, RatedValue> {
                val raw = sides?.obj(key) ?: return emptyMap()
                return raw.mapNotNull { (k, v) -> RatedValue.from(v)?.let { k to it } }.toMap()
            }
            val wp = (o["win_probability"] as? JsonArray).orEmpty().mapNotNull { pair ->
                val values = (pair as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull } ?: return@mapNotNull null
                if (values.size == 2) WinProbabilityPoint(values[0], values[1]) else null
            }
            return GameDetail(
                gameId = o.requireString("game_id"),
                awayTeam = o.requireString("away_team"),
                homeTeam = o.requireString("home_team"),
                away = side("away"),
                home = side("home"),
                players = o["players"]?.lenientList(PlayerLine::fromJson).orEmpty(),
                winProbability = wp,
                bigPlays = o["big_plays"]?.lenientList(BigPlay::fromJson).orEmpty(),
            )
        }
    }
}
