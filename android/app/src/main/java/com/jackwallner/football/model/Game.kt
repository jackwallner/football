package com.jackwallner.football.model

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.json.JsonObject

/**
 * One NFL game from `public.games`. Scores are null until a final posts; there
 * is no live score feed, so a game past kickoff with no score is in progress.
 */
data class Game(
    val id: String,
    val season: Int,
    val seasonPhase: SeasonPhase = SeasonPhase.REGULAR,
    /** REG, WC, DIV, CON or SB. */
    val gameType: String = "REG",
    val week: Int,
    val kickoff: Instant?,
    val gameDate: Instant = kickoff ?: Instant.EPOCH,
    val awayTeam: String,
    val homeTeam: String,
    val awayScore: Int? = null,
    val homeScore: Int? = null,
    val overtime: Boolean = false,
    val stadium: String? = null,
) {
    val isFinal: Boolean get() = awayScore != null && homeScore != null

    fun involves(team: String): Boolean {
        val abbr = normalizedTeamAbbreviation(team)
        return normalizedTeamAbbreviation(awayTeam) == abbr || normalizedTeamAbbreviation(homeTeam) == abbr
    }

    fun opponent(team: String): String =
        if (normalizedTeamAbbreviation(awayTeam) == normalizedTeamAbbreviation(team)) homeTeam else awayTeam

    fun isHome(team: String): Boolean = normalizedTeamAbbreviation(homeTeam) == normalizedTeamAbbreviation(team)

    fun score(team: String): Int? = if (isHome(team)) homeScore else awayScore

    /** "W", "L" or "T" for a final, from [team]'s side. */
    fun result(team: String): String? {
        val mine = score(team) ?: return null
        val theirs = score(opponent(team)) ?: return null
        if (mine == theirs) return "T"
        return if (mine > theirs) "W" else "L"
    }

    /** "W 36-31", team score first. */
    fun resultLine(team: String): String? {
        val result = result(team) ?: return null
        val mine = score(team) ?: return null
        val theirs = score(opponent(team)) ?: return null
        return "$result $mine-$theirs${if (overtime) " OT" else ""}"
    }

    /** "vs HOU" at home, "at HOU" away. */
    fun matchupLabel(team: String): String = "${if (isHome(team)) "vs" else "at"} ${displayTeamAbbr(opponent(team))}"

    val roundLabel: String get() = GameWeek.label(week, gameType)

    fun status(now: Instant = Instant.now()): GameStatus {
        if (isFinal) return GameStatus.FINAL
        val kickoff = kickoff ?: return GameStatus.UPCOMING
        if (now < kickoff) return GameStatus.UPCOMING
        // Past a normal game length with no final, the score is on its way.
        return if (Duration.between(kickoff, now).seconds < 4.5 * 3_600) GameStatus.IN_PROGRESS else GameStatus.AWAITING_SCORE
    }

    /** "Sun 1:00 PM" in the phone's zone. */
    fun kickoffLabel(zone: ZoneId = ZoneId.systemDefault()): String {
        val kickoff = kickoff ?: return DateText.gameDay(gameDate)
        return DateTimeFormatter.ofPattern("EEE h:mm a", Locale.US).format(kickoff.atZone(zone))
    }

    /** "Sun, Sep 13". */
    fun dayLabel(zone: ZoneId = ZoneId.systemDefault()): String {
        val kickoff = kickoff ?: return DateText.gameDay(gameDate)
        return DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US).format(kickoff.atZone(zone))
    }

    val sortInstant: Instant get() = kickoff ?: gameDate

    companion object {
        fun fromJson(o: JsonObject): Game {
            val rawDate = o.requireString("game_date")
            return Game(
                id = o.requireString("game_id"),
                season = o.requireInt("season"),
                seasonPhase = o.string("season_type")?.let { SeasonPhase.from(it) ?: throw RowDecodeException("bad phase") } ?: SeasonPhase.REGULAR,
                gameType = o.string("game_type") ?: "REG",
                week = o.requireInt("week"),
                kickoff = o.string("kickoff_at")?.let(::parseDate),
                gameDate = parseDate(rawDate) ?: throw RowDecodeException("bad game_date"),
                awayTeam = o.requireString("away_team"),
                homeTeam = o.requireString("home_team"),
                awayScore = o.int("away_score"),
                homeScore = o.int("home_score"),
                overtime = o.bool("overtime") ?: false,
                stadium = o.string("stadium"),
            )
        }

        /** In progress, then finals, then upcoming, each by kickoff. */
        fun slateOrder(games: List<Game>, now: Instant = Instant.now()): List<Game> = games.sortedWith(
            compareBy<Game> { it.status(now).sortOrder }.thenBy { it.sortInstant }.thenBy { it.id },
        )
    }
}

enum class GameStatus(val sortOrder: Int) { IN_PROGRESS(0), AWAITING_SCORE(1), FINAL(2), UPCOMING(3) }

/** A selectable week of the schedule: regular-season weeks, then playoff rounds. */
data class GameWeek(val week: Int, val phase: SeasonPhase, val label: String) {
    val id: String get() = "${phase.raw}-$week"

    /** "Wk 1", "Wild Card". */
    val shortLabel: String get() = if (phase == SeasonPhase.REGULAR) "Wk $week" else label

    fun games(from: List<Game>): List<Game> = from.filter { it.week == week && it.seasonPhase == phase }

    companion object {
        fun label(week: Int, gameType: String): String = when (gameType.uppercase()) {
            "WC" -> "Wild Card"
            "DIV" -> "Divisional"
            "CON" -> "Conference"
            "SB" -> "Super Bowl"
            else -> "Week $week"
        }

        fun weeks(games: List<Game>): List<GameWeek> {
            val seen = LinkedHashMap<String, GameWeek>()
            for (game in games) {
                val w = GameWeek(
                    week = game.week,
                    phase = game.seasonPhase,
                    label = if (game.seasonPhase == SeasonPhase.REGULAR) "Week ${game.week}" else label(game.week, game.gameType),
                )
                seen[w.id] = w
            }
            return seen.values.sortedWith(compareBy<GameWeek> { if (it.phase == SeasonPhase.REGULAR) 0 else 1 }.thenBy { it.week })
        }

        /**
         * A week stays current until a day before the next week's first
         * kickoff, so the weekend's finals stay up through Wednesday.
         */
        fun current(games: List<Game>, now: Instant = Instant.now()): GameWeek? {
            val weeks = weeks(games)
            for ((index, week) in weeks.withIndex()) {
                if (index + 1 >= weeks.size) return week
                val nextFirst = firstKickoff(weeks[index + 1], games) ?: return week
                if (now < nextFirst.minusSeconds(24 * 3_600)) return week
            }
            return weeks.lastOrNull()
        }

        private fun firstKickoff(week: GameWeek, games: List<Game>): Instant? =
            games.filter { it.week == week.week && it.seasonPhase == week.phase }.mapNotNull { it.kickoff }.minOrNull()
    }
}

object DateText {
    private val GAME_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US)

    /** Game dates are Eastern midnight; format them in Eastern so the west coast keeps the right day. */
    fun gameDay(date: Instant): String = GAME_DAY.format(date.atZone(EASTERN))
}
