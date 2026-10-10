package com.jackwallner.football.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jackwallner.football.data.PaywallTrigger
import com.jackwallner.football.model.DataFreshnessStatus
import com.jackwallner.football.model.Metric
import com.jackwallner.football.model.MetricCategory
import com.jackwallner.football.model.Player
import com.jackwallner.football.model.PlayerGameLog
import com.jackwallner.football.model.RecentFormWindow
import com.jackwallner.football.model.RecentWindow
import com.jackwallner.football.model.SeasonPhase
import com.jackwallner.football.model.metricNumericValue
import com.jackwallner.football.model.swiftRoundDouble
import com.jackwallner.football.ui.LocalGraph
import com.jackwallner.football.ui.components.BlurGateUnlock
import com.jackwallner.football.ui.components.CrownYellow
import com.jackwallner.football.ui.components.GridironSegmented
import com.jackwallner.football.ui.components.InlineLoadError
import com.jackwallner.football.ui.components.MetricBar
import com.jackwallner.football.ui.components.Segment
import com.jackwallner.football.ui.components.SfIcon
import com.jackwallner.football.ui.components.bottomHairline
import com.jackwallner.football.ui.components.gateBlur
import com.jackwallner.football.ui.components.gridironCard
import com.jackwallner.football.ui.theme.GridironGeo
import com.jackwallner.football.ui.theme.GridironPalette
import com.jackwallner.football.ui.theme.GridironType
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/**
 * Maps a raw value onto the league's season percentile scale by interpolating
 * the feed's own (value, percentile) pairs, so a recent-window bar sits on the
 * same ruler as the season bar. Direction lives in the points themselves.
 */
class LeaguePercentileCurve private constructor(private val points: List<Pair<Double, Double>>) {
    fun percentile(v: Double): Int {
        if (v <= points.first().first) return clamp(points.first().second)
        if (v >= points.last().first) return clamp(points.last().second)
        for (i in 1 until points.size) {
            if (v <= points[i].first) {
                val (v0, p0) = points[i - 1]
                val (v1, p1) = points[i]
                val t = if (v1 == v0) 0.0 else (v - v0) / (v1 - v0)
                return clamp(p0 + t * (p1 - p0))
            }
        }
        return clamp(points.last().second)
    }

    private fun clamp(p: Double): Int = swiftRoundDouble(p).toInt().coerceIn(1, 100)

    companion object {
        /** Two points are enough to interpolate, and two is what an opening week provides. */
        fun of(raw: List<Pair<Double, Double>>): LeaguePercentileCurve? {
            val sorted = raw.sortedBy { it.first }
            return if (sorted.size >= 2) LeaguePercentileCurve(sorted) else null
        }
    }
}

/** Per-metric curves for one population, keyed by season metric label. */
class LeaguePercentileCurves(players: List<Player>, categories: List<MetricCategory>, labels: List<String>) {
    private val byLabel: Map<String, LeaguePercentileCurve>

    init {
        val pool = players.filter { p -> categories.any { p.matchesPlayerType(it) } }
        byLabel = labels.mapNotNull { label ->
            val pts = pool.mapNotNull { p ->
                val m = p.metrics.firstOrNull { it.label == label && it.category in categories } ?: return@mapNotNull null
                val v = metricNumericValue(m.value) ?: return@mapNotNull null
                v to m.percentile.toDouble()
            }
            LeaguePercentileCurve.of(pts)?.let { label to it }
        }.toMap()
    }

    fun curve(label: String): LeaguePercentileCurve? = byLabel[label]
}

/** The empty-window line shared by the profile's recent cards. */
fun recentEmptyText(status: DataFreshnessStatus?, windowGames: Int): String = when (status) {
    DataFreshnessStatus.PENDING, DataFreshnessStatus.PARTIAL, DataFreshnessStatus.CHECKING -> "Recent game data is still arriving"
    DataFreshnessStatus.OFFLINE, DataFreshnessStatus.FAILED -> "Recent game data is unavailable right now"
    else -> "No games in the last $windowGames games"
}

/**
 * Last 3 / 5 / 8 game form for one player. Free users see a blurred static
 * teaser with no game-log fetch; StatScout+ loads the logs once and windows them locally.
 */
@Composable
fun RecentFormCard(
    player: Player,
    season: Int,
    leaguePlayers: List<Player>,
    fetchGameLogs: (suspend (Int, Int, SeasonPhase) -> List<PlayerGameLog>)?,
    freshnessRevision: String?,
    freshnessStatus: DataFreshnessStatus?,
) {
    val isPro = LocalGraph.current.subscriptions.isPro
    val scope = rememberCoroutineScope()
    var logs by remember { mutableStateOf<List<PlayerGameLog>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var windowGames by remember { mutableStateOf(5) }
    val category = player.primaryCategory
    val isDefense = category == MetricCategory.DEFENSE
    val curves = remember(isPro, leaguePlayers.size, category) {
        if (isPro) LeaguePercentileCurves(leaguePlayers, listOf(category), category.metricPriorityOrder) else null
    }

    suspend fun load() {
        if (!isPro || fetchGameLogs == null) return
        loading = true
        loadError = null
        try {
            logs = fetchGameLogs(player.playerId, season, player.seasonPhase)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            loadError = "Couldn't load recent games."
        }
        loading = false
    }
    LaunchedEffect(player.playerId, season, player.seasonPhase, freshnessRevision, isPro) { load() }

    val window = logs.sortedByDescending { it.gameDate }.take(windowGames).takeIf { it.isNotEmpty() }
        ?.let { RecentFormWindow.build("Last $windowGames", windowGames, it) }

    Column(Modifier.fillMaxWidth().gridironCard()) {
        Column(Modifier.fillMaxWidth().background(GridironPalette.surfaceAlt).bottomHairline(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.padding(horizontal = GridironGeo.padInline).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SfIcon("flame.fill", 14.dp, GridironPalette.turf)
                Text("RECENT FORM", style = GridironType.micro, color = GridironPalette.inkSecondary, modifier = Modifier.weight(1f))
                if (!isPro) {
                    Row(
                        Modifier.clip(CircleShape).background(CrownYellow).padding(horizontal = 7.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SfIcon("crown.fill", 10.dp, GridironPalette.midnight)
                        Text("STATSCOUT+", style = GridironType.micro.copy(fontWeight = FontWeight.Bold), color = GridironPalette.midnight)
                    }
                }
            }
            GridironSegmented(
                RecentWindow.entries.map { Segment(it, it.segmentLabel) },
                RecentWindow.of(windowGames) ?: RecentWindow.THREE,
                { windowGames = it.value },
                Modifier.padding(horizontal = GridironGeo.padInline).padding(bottom = 6.dp),
            )
        }
        if (isPro) {
            when {
                loading -> Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = GridironPalette.inkTertiary, strokeWidth = 2.dp)
                    Text("Loading recent games…", style = GridironType.small, color = GridironPalette.inkSecondary)
                }
                loadError != null -> InlineLoadError(loadError!!) { scope.launch { load() } }
                window != null -> StatsBody(window, isDefense, recentRows(window, category, curves))
                else -> Column(Modifier.fillMaxWidth().padding(vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SfIcon("calendar.badge.exclamationmark", 24.dp, GridironPalette.inkTertiary)
                    Text(recentEmptyText(freshnessStatus, windowGames), style = GridironType.small, color = GridironPalette.inkSecondary)
                }
            }
        } else {
            Box {
                Column(Modifier.gateBlur()) { Teaser(isDefense, category) }
                BlurGateUnlock("See last 3 / 5 / 8 game form for any player", PaywallTrigger.RecentForm, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

@Composable
private fun Teaser(isDefense: Boolean, category: MetricCategory) {
    val sample = if (isDefense) listOf(
        Metric("Tackles", "22", 94, MetricCategory.DEFENSE),
        Metric("Sacks", "3", 88, MetricCategory.DEFENSE),
        Metric("INT", "1", 81, MetricCategory.DEFENSE),
        Metric("PD", "4", 76, MetricCategory.DEFENSE),
    ) else listOf(
        Metric("Rec Yds", "312", 94, category),
        Metric("Rec", "24", 88, category),
        Metric("Rec TD", "3", 81, category),
        Metric("YAC", "6.1", 76, category),
    )
    Row(Modifier.padding(GridironGeo.padInline), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Summary("G", "3")
        Summary("Plays", "48")
        if (!isDefense) Summary("Touches", "31")
    }
    MetricBarList(sample)
}

@Composable
private fun StatsBody(w: RecentFormWindow, isDefense: Boolean, rows: List<Metric>) {
    Row(Modifier.fillMaxWidth().padding(GridironGeo.padInline), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Summary("G", "${w.games}")
        Summary("Plays", "${w.plays}")
        if (!isDefense) Summary("Touches", "${w.touches}")
        Spacer(Modifier.weight(1f))
        if (w.plays < 10) {
            Text(
                "SMALL SAMPLE",
                style = GridironType.micro,
                color = Color.White,
                modifier = Modifier.clip(CircleShape).background(GridironPalette.inkTertiary).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
    MetricBarList(rows)
}

@Composable
fun MetricBarList(rows: List<Metric>) {
    rows.forEachIndexed { index, metric ->
        Box(
            Modifier.fillMaxWidth().background(if (index % 2 == 0) GridironPalette.surface else GridironPalette.surfaceAlt).bottomHairline()
                .padding(horizontal = GridironGeo.padCard, vertical = 12.dp),
        ) { MetricBar(metric) }
    }
}

@Composable
private fun Summary(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, style = GridironType.micro, color = GridironPalette.inkTertiary)
        Text(value, style = GridironType.bodyBold, color = GridironPalette.ink)
    }
}

private data class CardSpec(val key: String, val label: String, val format: String)

private fun cardSpecs(category: MetricCategory): List<CardSpec> = when (category) {
    MetricCategory.PASSING -> listOf(CardSpec("passing_yards", "Pass Yds", "%.0f"), CardSpec("passing_tds", "Pass TD", "%.0f"))
    MetricCategory.RUSHING -> listOf(CardSpec("rushing_yards", "Rush Yds", "%.0f"), CardSpec("rushing_tds", "Rush TD", "%.0f"))
    MetricCategory.RECEIVING -> listOf(
        CardSpec("receiving_yards", "Rec Yds", "%.0f"), CardSpec("receptions", "Rec", "%.0f"), CardSpec("receiving_tds", "Rec TD", "%.0f"),
    )
    MetricCategory.DEFENSE -> listOf(CardSpec("tackles", "Tackles", "%.0f"), CardSpec("def_sacks", "Sacks", "%.1f"), CardSpec("def_interceptions", "INT", "%.0f"))
}

/** Window values as Metrics on the league season curve; nothing is drawn that can't be placed. */
private fun recentRows(w: RecentFormWindow, category: MetricCategory, curves: LeaguePercentileCurves?): List<Metric> =
    cardSpecs(category).mapNotNull { spec ->
        val v = w.metrics[spec.key] ?: return@mapNotNull null
        val pct = curves?.curve(spec.label)?.percentile(v) ?: return@mapNotNull null
        Metric(spec.label, String.format(Locale.US, spec.format, v), pct, category, id = spec.key)
    }
