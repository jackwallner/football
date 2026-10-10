package com.jackwallner.football

import com.jackwallner.football.data.KeyValueStore
import com.jackwallner.football.data.PlayerCaching
import com.jackwallner.football.data.StatcastProviding
import com.jackwallner.football.model.DataCoverage
import com.jackwallner.football.model.DataFreshness
import com.jackwallner.football.model.DataFreshnessStatus
import com.jackwallner.football.model.GameTrend
import com.jackwallner.football.model.Metric
import com.jackwallner.football.model.MetricCategory
import com.jackwallner.football.model.Player
import com.jackwallner.football.model.PlayerGameLog
import com.jackwallner.football.model.RecentForm
import com.jackwallner.football.model.SeasonPhase
import com.jackwallner.football.model.StandardStat
import com.jackwallner.football.model.StatScoutSeason
import java.time.Duration
import java.time.Instant
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Deterministic, fictional NFL rows used only by the store screenshot harness,
 * a port of the iOS `ScreenshotFixtureAPI`. Every frame is still drawn by the
 * shipped screens; only the provider is swapped. Debug builds only.
 */
object ScreenshotFixtureApi : StatcastProviding {
    private val season = StatScoutSeason.current
    private val priorSeason = season - 1

    // Week 2 is a coherent fictional capture date for the 2026 season.
    private val asOf: Instant = Instant.parse("2026-09-16T20:00:00Z")
    private val opponents = listOf("LV", "LAC", "DEN", "CIN", "BUF")

    private val playersBySeason: Map<Int, List<Player>> by lazy {
        mapOf(season to makePlayers(season, prior = false), priorSeason to makePlayers(priorSeason, prior = true))
    }

    /** Seeds the fan context so the Following board is deterministic. */
    fun prepareDefaults(defaults: KeyValueStore) {
        defaults.putString("favorites.playerIds", "12001,12007,12011")
        defaults.putString("favoriteTeam", "KC")
        defaults.putBoolean("hasCompletedOnboarding", true)
        defaults.putString("stats.board", "STANDARD")
        defaults.remove("statcast.dataFreshness")
        defaults.remove("statcast.displayedDataRevision")
    }

    /** In-memory cache so the prior season loads through the same lazy path as production. */
    val cache = object : PlayerCaching {
        override fun loadCurrentPlayers(): List<Player> = emptyList()
        override fun loadHistoricalPlayers(): List<Player> = playersBySeason[priorSeason].orEmpty()
        override fun saveCurrentPlayers(players: List<Player>) = Unit
    }

    override suspend fun fetchHistoricalPlayers(): List<Player> = playersBySeason[priorSeason].orEmpty()

    override suspend fun fetchCurrentPlayers(): List<Player> = playersBySeason[season].orEmpty()

    override suspend fun fetchGameLogs(playerId: Int, season: Int, seasonPhase: SeasonPhase): List<PlayerGameLog> {
        if (seasonPhase != SeasonPhase.REGULAR || season != this.season) return emptyList()
        val player = playersBySeason[season]?.firstOrNull { it.playerId == playerId } ?: return emptyList()
        return makeGameLogs(player)
    }

    override suspend fun fetchTeamGameLogs(team: String, season: Int, seasonPhase: SeasonPhase, sinceDate: Instant): List<PlayerGameLog> {
        if (seasonPhase != SeasonPhase.REGULAR) return emptyList()
        val players = playersBySeason[season] ?: return emptyList()
        return players.filter { it.team == team }.flatMap(::makeGameLogs).filter { it.gameDate >= sinceDate }
    }

    override suspend fun fetchRecentForm(season: Int, seasonPhase: SeasonPhase, windowWeeks: Int): List<RecentForm> {
        if (seasonPhase != SeasonPhase.REGULAR || season != this.season) return emptyList()
        return playersBySeason[this.season].orEmpty().map { makeRecentForm(it, windowWeeks) }
    }

    override suspend fun fetchDataCoverage(season: Int): DataCoverage? =
        if (season == this.season) coverage() else null

    override suspend fun fetchDataFreshness(season: Int): DataFreshness? {
        if (season != this.season) return null
        return DataFreshness(
            status = DataFreshnessStatus.READY,
            revision = "screenshot-fixture-v12",
            sourcePublishedAt = asOf,
            publishedAt = asOf,
            checkedAt = asOf,
            coverage = coverage(),
            message = "Fixture data through Week 2",
            isCached = false,
        )
    }

    private fun coverage() = DataCoverage(asOf = asOf, week = 2, phase = SeasonPhase.REGULAR, gamesIncluded = 32)

    private data class Seed(val id: Int, val name: String, val team: String, val position: String, val type: String, val percentile: Int, val headline: Double)

    private val seeds = listOf(
        Seed(12001, "Caleb Mercer", "KC", "QB", "qb", 96, 0.31),
        Seed(12002, "Mason Reed", "SEA", "QB", "qb", 91, 0.24),
        Seed(12003, "Jordan Vale", "SF", "QB", "qb", 87, 0.19),
        Seed(12004, "Tyler Knox", "BUF", "QB", "qb", 83, 0.13),
        Seed(12005, "Darius Cole", "DET", "QB", "qb", 78, 0.09),
        Seed(12006, "Eli Brooks", "PHI", "QB", "qb", 73, 0.05),
        Seed(12007, "Marcus Hale", "KC", "RB", "rb", 94, 0.22),
        Seed(12008, "Devon Price", "BAL", "RB", "rb", 89, 0.17),
        Seed(12009, "Andre Lewis", "DET", "RB", "rb", 84, 0.12),
        Seed(12010, "Nico Grant", "DAL", "RB", "rb", 77, 0.06),
        Seed(12011, "Jalen Cross", "CIN", "WR", "wr", 95, 0.29),
        Seed(12012, "Cam Porter", "MIA", "WR", "wr", 86, 0.16),
        Seed(12013, "Theo Banks", "KC", "TE", "te", 90, 0.21),
        Seed(12014, "Roman Ellis", "GB", "TE", "te", 81, 0.11),
        Seed(12015, "Isaiah Boone", "PIT", "LB", "def", 93, 0.0),
        Seed(12016, "Malik Ford", "DAL", "EDGE", "def", 88, 0.0),
        Seed(12017, "Trent York", "SF", "CB", "def", 84, 0.0),
        Seed(12018, "Kade Rivers", "BUF", "S", "def", 79, 0.0),
    )

    private fun makePlayers(season: Int, prior: Boolean): List<Player> = seeds.map { seed ->
        val scale = if (prior) 0.84 else 1.0
        val percentile = if (prior) max(55, seed.percentile - 4) else seed.percentile
        Player(
            playerId = seed.id,
            name = seed.name,
            team = seed.team,
            position = seed.position,
            handedness = if (seed.type == "def") "" else "R",
            updatedAt = asOf,
            season = season,
            seasonPhase = SeasonPhase.REGULAR,
            playerType = seed.type,
            source = "screenshot-fixture",
            metrics = metrics(seed, scale, percentile),
            standardStats = standardStats(seed, scale),
            games = gameTrends(seed, prior),
        )
    }

    private fun metrics(seed: Seed, scale: Double, p: Int): List<Metric> {
        fun m(label: String, value: Double, percentile: Int, category: MetricCategory) =
            Metric(label = label, value = formatted(label, value), percentile = percentile, category = category, qualified = true)
        fun less(n: Int) = max(50, p - n)
        return when (seed.type) {
            "qb" -> listOf(
                m("EPA/Play", seed.headline * scale, p, MetricCategory.PASSING),
                m("CPOE", 6.4 * scale, less(3), MetricCategory.PASSING),
                m("INT%", 1.3 / scale, less(7), MetricCategory.PASSING),
                m("Sack%", 4.8 / scale, less(4), MetricCategory.PASSING),
                m("Time to Throw", 2.63 / scale, less(5), MetricCategory.PASSING),
                m("Aggressiveness", 18.2 * scale, less(8), MetricCategory.PASSING),
                m("Intended Air Yds", 8.7 * scale, less(2), MetricCategory.PASSING),
                m("EPA/Rush", 0.12 * scale, less(8), MetricCategory.RUSHING),
                m("RYOE", 34 * scale, less(12), MetricCategory.RUSHING),
            )
            "rb" -> listOf(
                m("EPA/Rush", seed.headline * scale, p, MetricCategory.RUSHING),
                m("RYOE", 49 * scale, less(3), MetricCategory.RUSHING),
                m("Explosive%", 14.2 * scale, less(7), MetricCategory.RUSHING),
                m("Rush EPA", 11.4 * scale, less(4), MetricCategory.RUSHING),
                m("Fumble%", 1.1 / scale, less(6), MetricCategory.RUSHING),
                m("EPA/Tgt", 0.16 * scale, less(7), MetricCategory.RECEIVING),
            )
            "wr", "te" -> listOf(
                m("EPA/Tgt", seed.headline * scale, p, MetricCategory.RECEIVING),
                m("WOPR", 0.51 * scale, less(3), MetricCategory.RECEIVING),
                m("Target Share", 24.8 * scale, less(5), MetricCategory.RECEIVING),
                m("RACR", 1.34 * scale, less(2), MetricCategory.RECEIVING),
                m("Separation", 3.1 * scale, less(8), MetricCategory.RECEIVING),
                m("YAC+", 2.4 * scale, less(5), MetricCategory.RECEIVING),
                m("Rec EPA", 17.7 * scale, less(3), MetricCategory.RECEIVING),
            )
            else -> listOf(
                m("Pressures", 29 * scale, p, MetricCategory.DEFENSE),
                m("Hurries", 21 * scale, less(4), MetricCategory.DEFENSE),
                m("QB KD", 8 * scale, less(6), MetricCategory.DEFENSE),
                m("Cmp% Allowed", 48.2 / scale, less(6), MetricCategory.DEFENSE),
                m("Yds/Tgt Allowed", 6.1 / scale, less(4), MetricCategory.DEFENSE),
                m("Rating Allowed", 71.4 / scale, less(5), MetricCategory.DEFENSE),
                m("Missed Tkl%", 7.8 / scale, less(4), MetricCategory.DEFENSE),
            )
        }
    }

    private fun formatted(label: String, value: Double): String = when (label) {
        "CPOE", "Aggressiveness", "Target Share", "Explosive%", "Cmp% Allowed", "Missed Tkl%",
        "INT%", "Sack%", "Fumble%" -> "%.1f%%".format(value)
        "EPA/Play", "EPA/Rush", "WOPR", "RACR", "EPA/Tgt" -> "%.2f".format(value)
        "Time to Throw" -> "%.2f s".format(value)
        else -> "%.1f".format(value)
    }

    private fun standardStats(seed: Seed, scale: Double): List<StandardStat> {
        fun s(label: String, value: String) = StandardStat(label = label, value = value, id = label.lowercase().replace(" ", "-"))
        fun n(base: Double, factor: Double) = (base * factor * scale).roundToInt()
        val games = max(1, (5 * scale).roundToInt())
        val rank = 0.72 + seed.percentile / 100.0 * 0.28
        return when (seed.type) {
            "qb" -> listOf(
                s("G", "$games"), s("Cmp/Att", "${n(122.0, rank)}/${n(178.0, rank)}"),
                s("Pass Yds", "${n(1_650.0, rank)}"), s("Pass TD", "${n(16.0, rank)}"),
                s("INT", "${max(1, (2 * scale).roundToInt())}"),
                s("Car", "${n(18.0, rank)}"), s("Rush Yds", "${n(108.0, rank)}"),
                s("Rush TD", "${max(1, n(2.0, rank))}"),
            )
            "rb" -> listOf(
                s("G", "$games"), s("Car", "${n(82.0, rank)}"),
                s("Rush Yds", "${n(496.0, rank)}"), s("Rush TD", "${n(6.0, rank)}"),
                s("Rec/Tgt", "${n(19.0, rank)}/${n(25.0, rank)}"),
                s("Rec Yds", "${n(164.0, rank)}"), s("Rec TD", "${max(1, n(2.0, rank))}"),
            )
            "wr", "te" -> {
                val rec = if (seed.type == "wr") 31.0 else 23.0
                val tgt = if (seed.type == "wr") 46.0 else 34.0
                listOf(
                    s("G", "$games"), s("Rec/Tgt", "${n(rec, rank)}/${n(tgt, rank)}"),
                    s("Rec Yds", "${n(412.0, rank)}"), s("Rec TD", "${n(4.0, rank)}"),
                    s("YAC", "${n(146.0, rank)}"), s("Car", "${n(3.0, rank)}"),
                    s("Rush Yds", "${n(19.0, rank)}"), s("Rush TD", "0"),
                )
            }
            else -> listOf(
                s("G", "$games"), s("Tackles", "${n(42.0, rank)}"),
                s("Sacks", "%.1f".format(3.5 * rank * scale)), s("Def INT", "${max(1, n(2.0, rank))}"),
                s("PD", "${n(6.0, rank)}"), s("TFL", "${n(5.0, rank)}"),
                s("QB Hits", "${n(9.0, rank)}"), s("FF", "${max(1, n(1.0, rank))}"),
            )
        }
    }

    private fun weeksBefore(weeks: Int): Instant = asOf.minus(Duration.ofDays(7L * weeks))

    private fun gameTrends(seed: Seed, prior: Boolean): List<GameTrend> = (0 until 5).map { index ->
        GameTrend(
            id = "${seed.id}-${if (prior) "prior" else "current"}-$index",
            date = weeksBefore(index),
            opponent = opponents[index],
            summary = if (index == 0) "Strong finish" else "Complete game",
            percentileDelta = (if (prior) 1 else 2) * (5 - index),
            keyMetric = if (seed.type == "def") "Pressures" else "EPA/Play",
        )
    }

    private fun makeGameLogs(player: Player): List<PlayerGameLog> = (0 until 5).map { index ->
        val type = player.playerType ?: "qb"
        PlayerGameLog(
            playerId = player.playerId,
            season = season,
            seasonPhase = SeasonPhase.REGULAR,
            gameDate = weeksBefore(4 - index),
            playerType = type,
            team = player.team,
            opponent = opponents[index],
            plays = plays(type),
            touches = touches(type),
            metrics = gameMetrics(type, 1.0 + index * 0.04),
        )
    }

    private fun plays(type: String) = when (type) {
        "qb" -> 34
        "rb" -> 21
        "wr" -> 13
        "te" -> 11
        else -> 0
    }

    private fun touches(type: String) = when (type) {
        "qb" -> 31
        "rb" -> 17
        "wr" -> 8
        "te" -> 7
        else -> 0
    }

    /** The iOS fixture's short keys plus the feed's canonical names, so every card reads a value. */
    private val canonicalKeys = mapOf(
        "pass_yards" to "passing_yards", "pass_tds" to "passing_tds",
        "rush_yards" to "rushing_yards", "rush_tds" to "rushing_tds",
        "rec_yards" to "receiving_yards", "rec_tds" to "receiving_tds", "yac" to "receiving_yac",
    )

    private fun gameMetrics(type: String, f: Double): Map<String, Double> =
        baseGameMetrics(type, f).let { base -> base + base.mapNotNull { (k, v) -> canonicalKeys[k]?.let { it to v } } }

    private fun baseGameMetrics(type: String, f: Double): Map<String, Double> = when (type) {
        "qb" -> mapOf(
            "passing_epa" to 0.24 * f, "cpoe" to 4.8 * f, "ypa" to 8.3 * f,
            "cmp_pct" to 69.0 * f, "passer_rating" to 108.0 * f, "int_rate" to 1.0 / f,
            "sack_rate" to 4.0 / f, "avg_time_to_throw" to 2.55 / f,
            "pass_yards" to 278 * f, "pass_tds" to 2 * f, "completions" to 24 * f,
            "attempts" to 34 * f, "interceptions" to 0.0, "rush_yards" to 18 * f,
            "rush_tds" to 0.0, "carries" to 4 * f,
        )
        "rb" -> mapOf(
            "rushing_epa" to 0.18 * f, "rush_yoe" to 9.0 * f,
            "ypc" to 5.8 * f, "rush_yards" to 96 * f, "rush_tds" to 1 * f,
            "carries" to 17 * f, "rush_first_downs" to 5 * f,
            "receptions" to 4 * f, "rec_yards" to 33 * f, "rec_tds" to 0.0,
            "catch_pct" to 80.0 * f, "fumble_rate" to 0.5 / f,
        )
        "wr", "te" -> mapOf(
            "receiving_epa" to 0.35 * f, "catch_pct" to 72.0 * f,
            "avg_separation" to 3.2 * f, "avg_yac_above_expectation" to 2.4 * f,
            "racr" to 1.42 * f, "rec_yards" to (if (type == "wr") 92 else 67) * f,
            "receptions" to (if (type == "wr") 7 else 5) * f, "rec_tds" to 1 * f,
            "targets" to (if (type == "wr") 10 else 7) * f, "yac" to 37 * f,
        )
        else -> mapOf(
            "tackles" to 8 * f, "sacks" to 0.5 * f, "def_ints" to 0.0,
            "passes_defended" to 1 * f, "tfl" to 1 * f, "qb_hits" to 2 * f,
            "forced_fumbles" to 0.0,
        )
    }

    private fun makeRecentForm(player: Player, windowWeeks: Int): RecentForm {
        val current = when (player.playerId) { 12001 -> 0.34; 12002 -> 0.19; else -> 0.07 }
        val prior = current - when (player.playerId) { 12001 -> 0.18; 12002 -> 0.04; else -> 0.01 }
        val type = player.playerType ?: "qb"
        val metrics = gameMetrics(type, 1.0).toMutableMap()
        val priorMetrics = metrics.toMutableMap()
        val delta = metrics.mapValues { 0.0 }.toMutableMap()
        if (type == "qb") {
            metrics["passing_epa"] = current
            priorMetrics["passing_epa"] = prior
            delta["passing_epa"] = current - prior
            metrics["ypa"] = 8.5 + current
            priorMetrics["ypa"] = 7.4 + prior
            delta["ypa"] = current - prior + 0.9
        }
        val games = min(windowWeeks, 2)
        return RecentForm(
            playerId = player.playerId,
            season = season,
            seasonPhase = SeasonPhase.REGULAR,
            playerType = type,
            windowWeeks = windowWeeks,
            asOf = asOf,
            startWeek = max(1, 3 - windowWeeks),
            endWeek = 2,
            team = player.team,
            games = games,
            plays = max(plays(type) * games, 1),
            touches = touches(type) * games,
            metrics = metrics,
            priorMetrics = priorMetrics,
            delta = delta,
        )
    }
}
