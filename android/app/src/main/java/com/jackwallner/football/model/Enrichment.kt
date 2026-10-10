package com.jackwallner.football.model

import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.abs
import kotlinx.serialization.json.JsonObject

/**
 * Bio, contract, snaps and injury from `public.player_profiles`. Every field is
 * optional; screens leave a line out rather than wait for it.
 */
data class PlayerProfile(
    val playerId: Int,
    val season: Int,
    val jersey: Int? = null,
    val birthDate: LocalDate? = null,
    val heightInches: Int? = null,
    val weightPounds: Int? = null,
    val college: String? = null,
    val yearsExperience: Int? = null,
    val draftYear: Int? = null,
    val draftRound: Int? = null,
    val draftPick: Int? = null,
    val draftTeam: String? = null,
    /** Average per year, in millions. */
    val contractAPY: Double? = null,
    /** APY as a share of the cap in the year signed. */
    val contractCapShare: Double? = null,
    val contractYears: Int? = null,
    val contractYearSigned: Int? = null,
    val offenseSnaps: Int? = null,
    val defenseSnaps: Int? = null,
    val offenseSnapShare: Double? = null,
    val defenseSnapShare: Double? = null,
    val injuryWeek: Int? = null,
    val injuryStatus: String? = null,
    val injury: String? = null,
    val practiceStatus: String? = null,
) {
    fun age(on: LocalDate = LocalDate.now()): Int? = birthDate?.let { Period.between(it, on).years }

    /** "6-1, 196". */
    val sizeLabel: String?
        get() {
            val h = heightInches?.takeIf { it > 0 } ?: return null
            val height = "${h / 12}-${h % 12}"
            val w = weightPounds?.takeIf { it > 0 } ?: return height
            return "$height, $w"
        }

    /** "2023 R1 #20", or "Undrafted". */
    val draftLabel: String?
        get() {
            if (draftYear != null && draftRound != null && draftPick != null) return "$draftYear R$draftRound #$draftPick"
            return if (yearsExperience != null) "Undrafted" else null
        }

    /** "$42.2M/yr". */
    val contractLabel: String?
        get() {
            val apy = contractAPY?.takeIf { it > 0 } ?: return null
            return if (apy >= 10) "$${cFixed(apy, 1)}M/yr" else "$${cFixed(apy, 2)}M/yr"
        }

    fun snapShare(defense: Boolean): Double? = if (defense) defenseSnapShare else offenseSnapShare
    fun snaps(defense: Boolean): Int? = if (defense) defenseSnaps else offenseSnaps

    companion object {
        fun fromJson(o: JsonObject) = PlayerProfile(
            playerId = o.requireInt("player_id"),
            season = o.requireInt("season"),
            jersey = o.int("jersey"),
            birthDate = o.string("birth_date")?.let { raw ->
                try {
                    LocalDate.parse(raw.take(10))
                } catch (_: DateTimeParseException) {
                    null
                }
            },
            heightInches = o.int("height_in"),
            weightPounds = o.int("weight_lb"),
            college = o.string("college"),
            yearsExperience = o.int("years_exp"),
            draftYear = o.int("draft_year"),
            draftRound = o.int("draft_round"),
            draftPick = o.int("draft_pick"),
            draftTeam = o.string("draft_team"),
            contractAPY = o.double("contract_apy"),
            contractCapShare = o.double("contract_cap_pct"),
            contractYears = o.int("contract_years"),
            contractYearSigned = o.int("contract_year_signed"),
            offenseSnaps = o.int("off_snaps"),
            defenseSnaps = o.int("def_snaps"),
            offenseSnapShare = o.double("off_snap_pct"),
            defenseSnapShare = o.double("def_snap_pct"),
            injuryWeek = o.int("injury_week"),
            injuryStatus = o.string("injury_status"),
            injury = o.string("injury"),
            practiceStatus = o.string("practice_status"),
        )
    }
}

/** An injury-report entry that still describes an unplayed game. */
data class InjuryReport(val status: String, val injury: String?) {
    val isOut: Boolean get() = status.lowercase() == "out"

    /** "Q", "D", "Out". */
    val shortStatus: String
        get() = when (status.lowercase()) {
            "questionable" -> "Q"
            "doubtful" -> "D"
            else -> status
        }

    companion object {
        fun current(profile: PlayerProfile?, upcomingWeek: Int?): InjuryReport? {
            profile ?: return null
            val status = profile.injuryStatus?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val week = profile.injuryWeek ?: return null
            upcomingWeek ?: return null
            if (week < upcomingWeek) return null
            return InjuryReport(status, profile.injury)
        }
    }
}

/** A club's power rating: points per game better or worse than average on a neutral field. */
data class TeamRating(
    val season: Int,
    val team: String,
    val rank: Int,
    val games: Int,
    val throughWeek: Int,
    val rating: Double,
    val offense: Double,
    val defense: Double,
    val schedule: Double,
    val wins: Int,
    val losses: Int,
    val ties: Int,
    val pointsFor: Int,
    val pointsAgainst: Int,
) {
    companion object {
        fun signed(value: Double): String {
            val rounded = swiftRoundDouble(value * 10) / 10.0
            if (rounded == 0.0) return "0.0"
            return String.format(Locale.US, "%+.1f", rounded)
        }

        fun fromJson(o: JsonObject) = TeamRating(
            season = o.requireInt("season"),
            team = o.requireString("team"),
            rank = o.requireInt("rank"),
            games = o.requireInt("games"),
            throughWeek = o.requireInt("through_week"),
            rating = o.double("rating") ?: throw RowDecodeException("rating"),
            offense = o.double("offense") ?: throw RowDecodeException("offense"),
            defense = o.double("defense") ?: throw RowDecodeException("defense"),
            schedule = o.double("schedule") ?: throw RowDecodeException("schedule"),
            wins = o.requireInt("wins"),
            losses = o.requireInt("losses"),
            ties = o.requireInt("ties"),
            pointsFor = o.requireInt("points_for"),
            pointsAgainst = o.requireInt("points_against"),
        )
    }
}

/** A projected margin for an unplayed game. */
data class GameProjection(val gameId: String, val homeMargin: Double, val homeWinProbability: Double) {
    /** "SEA by 4.5", "Toss-up" under a point. */
    fun label(home: String, away: String): String {
        val margin = abs(homeMargin)
        if (margin < 1) return "Toss-up"
        val favourite = if (homeMargin > 0) home else away
        return "${displayTeamAbbr(favourite)} by ${String.format(Locale.US, "%.1f", Math.round(margin * 2) / 2.0)}"
    }

    fun winProbability(team: String, home: String): Double =
        if (normalizedTeamAbbreviation(team) == normalizedTeamAbbreviation(home)) homeWinProbability else 1 - homeWinProbability

    companion object {
        fun fromJson(o: JsonObject) = GameProjection(
            gameId = o.requireString("game_id"),
            homeMargin = o.double("home_margin") ?: throw RowDecodeException("home_margin"),
            homeWinProbability = o.double("home_win_prob") ?: throw RowDecodeException("home_win_prob"),
        )
    }
}

/** A club's line in the standings, from posted finals. */
data class StandingsRow(
    val team: String,
    val wins: Int = 0,
    val losses: Int = 0,
    val ties: Int = 0,
    val pointsFor: Int = 0,
    val pointsAgainst: Int = 0,
    /** "W2", "L1"; null before a game. */
    val streak: String? = null,
) {
    val games: Int get() = wins + losses + ties
    val differential: Int get() = pointsFor - pointsAgainst
    val winPercentage: Double get() = if (games == 0) 0.0 else (wins + ties / 2.0) / games
    val record: String get() = if (ties > 0) "$wins-$losses-$ties" else "$wins-$losses"

    companion object {
        /** Regular-season finals only, oldest first so the streak reads off the end. */
        fun build(games: List<Game>, teams: List<String>): Map<String, StandingsRow> {
            val rows = teams.associateWith { StandingsRow(it) }.toMutableMap()
            val finals = games.filter { it.seasonPhase == SeasonPhase.REGULAR && it.isFinal }.sortedBy { it.sortInstant }
            val results = HashMap<String, MutableList<String>>()
            for (game in finals) {
                for (side in listOf(game.homeTeam, game.awayTeam)) {
                    val team = normalizedTeamAbbreviation(side)
                    val row = rows[team] ?: continue
                    val result = game.result(side) ?: continue
                    val mine = game.score(side) ?: continue
                    val theirs = game.score(game.opponent(side)) ?: continue
                    rows[team] = row.copy(
                        wins = row.wins + if (result == "W") 1 else 0,
                        losses = row.losses + if (result == "L") 1 else 0,
                        ties = row.ties + if (result != "W" && result != "L") 1 else 0,
                        pointsFor = row.pointsFor + mine,
                        pointsAgainst = row.pointsAgainst + theirs,
                    )
                    results.getOrPut(team) { mutableListOf() }.add(result)
                }
            }
            for ((team, list) in results) {
                val last = list.lastOrNull() ?: continue
                val run = list.asReversed().takeWhile { it == last }.size
                rows[team] = rows.getValue(team).copy(streak = "$last$run")
            }
            return rows
        }

        /** Win percentage, then point differential, then name. */
        fun ordered(rows: List<StandingsRow>): List<StandingsRow> = rows.sortedWith(
            compareByDescending<StandingsRow> { it.winPercentage }.thenByDescending { it.differential }.thenBy { it.team },
        )
    }
}

/**
 * Production set against pay, both as percentile ranks inside one pool: this
 * season's qualified players at the position who have an active deal.
 */
data class ContractValue(val payPercentile: Int, val productionPercentile: Int, val poolSize: Int) {
    val score: Int get() = productionPercentile - payPercentile

    enum class Verdict(val label: String) {
        BARGAIN("Bargain"),
        OUTPLAYING("Outplaying his deal"),
        FAIR("Paid about right"),
        UNDER("Below his deal"),
        OVERPAID("Overpaid so far"),
    }

    val verdict: Verdict
        get() = when {
            score >= 25 -> Verdict.BARGAIN
            score >= 10 -> Verdict.OUTPLAYING
            score >= -9 -> Verdict.FAIR
            score >= -24 -> Verdict.UNDER
            else -> Verdict.OVERPAID
        }

    val scoreLabel: String get() = if (score > 0) "+$score" else "$score"

    companion object {
        /** Offense only until PFR's advanced defensive table publishes. */
        val positions = setOf(PlayerPositionGroup.QB, PlayerPositionGroup.RB, PlayerPositionGroup.WR, PlayerPositionGroup.TE)

        fun compute(players: List<Player>, profiles: Map<Int, PlayerProfile>, isQualified: (Player) -> Boolean): Map<Int, ContractValue> {
            val out = HashMap<Int, ContractValue>()
            val groups = players.filter { it.positionGroup in positions }.groupBy { it.positionGroup }
            for ((_, group) in groups) {
                val pool = group.mapNotNull { player ->
                    if (!isQualified(player)) return@mapNotNull null
                    val share = profiles[player.playerId]?.contractCapShare?.takeIf { it > 0 } ?: return@mapNotNull null
                    val production = production(player) ?: return@mapNotNull null
                    Triple(player.playerId, share, production)
                }
                if (pool.size < 5) continue
                val pays = pool.map { it.second }
                val productions = pool.map { it.third }
                for (entry in pool) {
                    out[entry.first] = ContractValue(
                        payPercentile = midpointPercentile(entry.second, pays),
                        productionPercentile = midpointPercentile(entry.third, productions),
                        poolSize = pool.size,
                    )
                }
            }
            return out
        }

        fun production(player: Player): Double? {
            val category = player.positionGroup.primaryCategory
            val ranked = player.metrics.filter { it.category == category && it.qualified != false && !it.isUnranked }
            if (ranked.isEmpty()) return null
            return ranked.sumOf { it.percentile }.toDouble() / ranked.size
        }

        fun midpointPercentile(value: Double, values: List<Double>): Int {
            if (values.isEmpty()) return 50
            val below = values.count { it < value }
            val equal = values.count { it == value }
            val raw = (below + equal / 2.0) / values.size * 100
            return swiftRound(raw).coerceIn(1, 99)
        }
    }
}

internal val UTC: ZoneId = ZoneOffset.UTC
internal fun Instant.toUtcDate(): LocalDate = atZone(UTC).toLocalDate()
