package com.jackwallner.football

import com.jackwallner.football.data.AppClock
import com.jackwallner.football.data.DashboardViewModel
import com.jackwallner.football.data.InMemoryStore
import com.jackwallner.football.data.KeyValueStore
import com.jackwallner.football.data.PlayerCaching
import com.jackwallner.football.data.StatcastProviding
import com.jackwallner.football.model.DataCoverage
import com.jackwallner.football.model.GameTrend
import com.jackwallner.football.model.Metric
import com.jackwallner.football.model.MetricCategory
import com.jackwallner.football.model.Player
import com.jackwallner.football.model.PlayerGameLog
import com.jackwallner.football.model.RecentForm
import com.jackwallner.football.model.SeasonPhase
import com.jackwallner.football.model.StandardStat
import com.jackwallner.football.model.StatScoutSeason
import com.jackwallner.football.model.nflTeamAbbreviations
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope

/** Shared builders so each test reads like its iOS counterpart. */
fun metric(
    label: String,
    value: String = "1",
    percentile: Int = 50,
    category: MetricCategory = MetricCategory.PASSING,
    qualified: Boolean? = null,
    rankable: Boolean? = null,
    id: String = "${category.raw}-$label",
) = Metric(label, value, percentile, category, qualified, rankable, id)

fun std(label: String, value: String) = StandardStat(label, value, "std-$label")

fun testPlayer(
    id: Int = 1,
    name: String = "Test",
    team: String = "KC",
    position: String = "QB",
    season: Int? = 2025,
    phase: SeasonPhase = SeasonPhase.REGULAR,
    type: String? = null,
    metrics: List<Metric> = emptyList(),
    standard: List<StandardStat>? = emptyList(),
    games: List<GameTrend> = emptyList(),
    updatedAt: Instant = Instant.parse("2026-04-28T12:00:00Z"),
) = Player(
    playerId = id, name = name, team = team, position = position, handedness = "",
    updatedAt = updatedAt, season = season, seasonPhase = phase, playerType = type,
    metrics = metrics, standardStats = standard, games = games,
)

/** A [StatcastProviding] whose only data is what the test hands it; every other feed is empty. */
open class MockProvider(
    private val players: List<Player> = emptyList(),
    private val error: Throwable? = null,
) : StatcastProviding {
    private fun check() { error?.let { throw it } }

    override suspend fun fetchHistoricalPlayers(): List<Player> {
        check()
        return players.filter { (it.season ?: 0) < StatScoutSeason.current }
    }

    override suspend fun fetchCurrentPlayers(): List<Player> {
        check()
        return players
    }

    override suspend fun fetchGameLogs(playerId: Int, season: Int, seasonPhase: SeasonPhase): List<PlayerGameLog> {
        check()
        return emptyList()
    }

    override suspend fun fetchTeamGameLogs(team: String, season: Int, seasonPhase: SeasonPhase, sinceDate: Instant): List<PlayerGameLog> {
        check()
        return emptyList()
    }

    override suspend fun fetchRecentForm(season: Int, seasonPhase: SeasonPhase, windowWeeks: Int): List<RecentForm> {
        check()
        return emptyList()
    }

    override suspend fun fetchDataCoverage(season: Int): DataCoverage? {
        check()
        return null
    }
}

class InMemoryPlayerCache(seed: List<Player> = emptyList()) : PlayerCaching {
    var savedPlayers: List<Player> = seed
        private set

    override fun loadCurrentPlayers(): List<Player> = savedPlayers
    override fun loadHistoricalPlayers(): List<Player> = emptyList()
    override fun saveCurrentPlayers(players: List<Player>) { savedPlayers = players }
}

/** A view model on the test scheduler, so `load()` is deterministic and virtual time is free. */
fun TestScope.dashboard(
    provider: StatcastProviding,
    cache: PlayerCaching? = null,
    store: KeyValueStore = InMemoryStore(),
) = DashboardViewModel(provider, cache, store, this, io = StandardTestDispatcher(testScheduler))

fun completeSeasonPlayers(season: Int, namePrefix: String): List<Player> =
    nflTeamAbbreviations.mapIndexed { index, team ->
        val type = listOf("qb", "rb", "wr", "te", "def")[index % 5]
        val (label, category) = when (type) {
            "qb" -> "EPA/Play" to MetricCategory.PASSING
            "rb" -> "EPA/Rush" to MetricCategory.RUSHING
            "wr", "te" -> "EPA/Tgt" to MetricCategory.RECEIVING
            else -> "Tackles" to MetricCategory.DEFENSE
        }
        testPlayer(
            id = 10_000 + index, name = "$namePrefix $team", team = team,
            position = if (type == "def") "LB" else type.uppercase(), season = season, type = type,
            metrics = listOf(metric(label, "1", 50, category, id = "metric-$index")),
            standard = listOf(StandardStat("G", "1", "games-$index")),
        )
    }

fun completeCurrentPlayers(namePrefix: String = "Cached") = completeSeasonPlayers(StatScoutSeason.current, namePrefix)

/** A clock the test moves by hand. */
class FakeClock(var instant: Instant = Instant.parse("2026-09-13T12:00:00Z"), private val zone: ZoneId = ZoneOffset.UTC) : AppClock {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = zone
}
