package com.jackwallner.football.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.jackwallner.football.data.DashboardViewModel
import com.jackwallner.football.data.PaywallTrigger
import com.jackwallner.football.model.DataFreshnessStatus
import com.jackwallner.football.model.Metric
import com.jackwallner.football.model.MetricCategory
import com.jackwallner.football.model.Player
import com.jackwallner.football.model.PlayerGameLog
import com.jackwallner.football.model.RecentFormWindow
import com.jackwallner.football.model.RecentWindow
import com.jackwallner.football.model.SeasonPhase
import com.jackwallner.football.model.StatScoutSeason
import com.jackwallner.football.ui.LocalGraph
import com.jackwallner.football.ui.components.BlurGateUnlock
import com.jackwallner.football.ui.components.CrownYellow
import com.jackwallner.football.ui.components.DualMetricBar
import com.jackwallner.football.ui.components.GridironSectionBar
import com.jackwallner.football.ui.components.GridironSegmented
import com.jackwallner.football.ui.components.GridironSubSectionBar
import com.jackwallner.football.ui.components.InlineLoadError
import com.jackwallner.football.ui.components.MetricBar
import com.jackwallner.football.ui.components.Segment
import com.jackwallner.football.ui.components.SfIcon
import com.jackwallner.football.ui.components.bottomHairline
import com.jackwallner.football.ui.components.gridironCard
import com.jackwallner.football.ui.nav.LocalNavigator
import com.jackwallner.football.ui.nav.Route
import com.jackwallner.football.ui.nav.rememberRetained
import com.jackwallner.football.ui.theme.GridironGeo
import com.jackwallner.football.ui.theme.GridironPalette
import com.jackwallner.football.ui.theme.GridironType
import java.time.Instant
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Offense or defense: the two halves of a roster the team cards aggregate. */
internal enum class TeamSide(val raw: String, val label: String) {
    OFFENSE("offense", "Offense"),
    DEFENSE("defense", "Defense");

    /** Metric categories this side aggregates. */
    val categories: List<MetricCategory>
        get() = if (this == OFFENSE) listOf(MetricCategory.PASSING, MetricCategory.RUSHING, MetricCategory.RECEIVING) else listOf(MetricCategory.DEFENSE)

    /** True when a game-log row (player_type in qb/rb/wr/te/def) belongs to this side. */
    fun includes(playerType: String): Boolean =
        if (this == DEFENSE) playerType.lowercase() == "def" else playerType.lowercase() != "def"
}

/** Fetches a club's game logs: (team, season, phase, since). */
typealias TeamGameLogFetcher = suspend (String, Int, SeasonPhase, Instant) -> List<PlayerGameLog>

/**
 * Maps a raw metric value onto the league's full-season percentile scale by
 * reusing the app's own value-to-percentile calibration: for each metric every
 * qualified player's (raw season value, season percentile) pair, sorted by
 * value, interpolated. This lets a recent-window bar sit on the same ruler as
 * the season bar.
 */
internal class TeamPercentileCurve private constructor(private val points: List<Pair<Double, Double>>) {
    /** Interpolates the percentile (1-100) for a value. Direction is encoded in the points themselves. */
    fun percentile(value: Double): Int {
        if (value <= points.first().first) return clamp(points.first().second)
        if (value >= points.last().first) return clamp(points.last().second)
        for (i in 1 until points.size) {
            if (value <= points[i].first) {
                val (v0, p0) = points[i - 1]
                val (v1, p1) = points[i]
                val t = if (v1 == v0) 0.0 else (value - v0) / (v1 - v0)
                return clamp(p0 + t * (p1 - p0))
            }
        }
        return clamp(points.last().second)
    }

    private fun clamp(p: Double): Int = Math.round(p).toInt().coerceIn(1, 100)

    companion object {
        /** Two points are enough to interpolate between, and two is what an opening week provides. */
        fun from(raw: List<Pair<Double, Double>>): TeamPercentileCurve? {
            val sorted = raw.sortedBy { it.first }
            return if (sorted.size >= 2) TeamPercentileCurve(sorted) else null
        }
    }
}

/** Per-metric curves for one player population, keyed by the season metric label. */
internal class TeamPercentileCurves(players: List<Player>, categories: List<MetricCategory>, labels: List<String>) {
    private val byLabel: Map<String, TeamPercentileCurve>

    init {
        val pool = players.filter { player -> categories.any { player.matchesPlayerType(it) } }
        val result = HashMap<String, TeamPercentileCurve>()
        for (label in labels) {
            val points = ArrayList<Pair<Double, Double>>()
            for (player in pool) {
                val metric = player.metrics.firstOrNull { it.label == label && it.category in categories } ?: continue
                val value = DashboardViewModel.rawNumeric(metric.value) ?: continue
                points += value to metric.percentile.toDouble()
            }
            TeamPercentileCurve.from(points)?.let { result[label] = it }
        }
        byLabel = result
    }

    fun curve(label: String): TeamPercentileCurve? = byLabel[label]
}

private enum class FormMode(val raw: String) {
    SEASON("Season"), RECENT("Recent"), BOTH("Both");

    val usesRecent: Boolean get() = this != SEASON
}

/**
 * Team "percentile rankings" card, the team-level analogue of the player
 * profile's percentile card. Aggregates the roster into one synthetic
 * "team-as-a-player" and renders its profile as percentile bars on the league
 * ruler, with a Season / Recent toggle that mirrors the player page.
 *
 * Season is the roster's mean of every metric for the active side, placed on
 * the league curve; tapping a bar opens that metric's leaderboard. Recent is
 * the same aggregation over the last 3 / 5 / 8 games of game logs, StatScout+
 * only, with the standard blur and call to action.
 *
 * @param players the roster for this team and season.
 * @param leaguePlayers the league pool used to build the value-to-percentile curve.
 * @param seasonPhase which half of the year the numbers come from; the rolling
 *   window is filtered to the same phase.
 * @param fetchTeamGameLogs fetches (team, season, phase, since) game logs.
 * @param freshnessRevision changes after a validated publisher revision.
 * @param supportsRecent false on a historical season, where Recent and Both are hidden.
 */
@Composable
fun TeamRankingsCard(
    team: String,
    season: Int,
    players: List<Player>,
    leaguePlayers: List<Player>,
    fetchTeamGameLogs: TeamGameLogFetcher?,
    onUpgradeTap: () -> Unit,
    seasonPhase: SeasonPhase = SeasonPhase.REGULAR,
    freshnessRevision: String? = null,
    freshnessStatus: DataFreshnessStatus? = null,
    supportsRecent: Boolean = true,
) {
    val isPro = LocalGraph.current.subscriptions.isPro
    val scope = rememberCoroutineScope()
    var side by rememberSaveable { mutableStateOf(TeamSide.OFFENSE) }
    var mode by rememberSaveable { mutableStateOf(FormMode.SEASON) }
    var windowGames by rememberSaveable { mutableStateOf(5) }
    var logs by rememberRetained("teamForm.logs") { mutableStateOf<List<PlayerGameLog>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    val curves = remember(leaguePlayers.size, players.size) {
        val rosterLabels = players.flatMap { p -> p.metrics.map { it.label } }
        val recentLabels = MetricCategory.entries.flatMap { it.metricPriorityOrder }
        val labels = (rosterLabels + recentLabels).distinct()
        TeamPercentileCurves(leaguePlayers, TeamSide.OFFENSE.categories, labels) to
            TeamPercentileCurves(leaguePlayers, TeamSide.DEFENSE.categories, labels)
    }.let { if (side == TeamSide.DEFENSE) it.second else it.first }

    // The mode actually rendered. `mode` is view state and the season is a
    // parameter, so a user who picked Recent on the live season and then walked
    // back to 2018 would otherwise sit in front of a permanently empty window.
    val effectiveMode = if (supportsRecent) mode else FormMode.SEASON

    suspend fun load() {
        // Free users see a static teaser, never real team game logs.
        val fetch = fetchTeamGameLogs ?: return
        if (!isPro) return
        loading = true
        loadError = null
        try {
            // Pull a wide window (the last ~120 days of the season) so the client
            // can slice the most recent 3 / 5 / 8 games out of it. Anchored to the
            // season's own end, not to today.
            logs = fetch(team, season, seasonPhase, StatScoutSeason.gameLogWindowStart(season))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            loadError = "Couldn't load team form. Check your connection and try again."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(team, season, seasonPhase, effectiveMode, isPro, freshnessRevision ?: "none") {
        if (effectiveMode.usesRecent && isPro) load()
    }

    val form = FormData(team, side, players, curves, logs, windowGames)

    Column(Modifier.fillMaxWidth().gridironCard().testTag("teamRankingsCard")) {
        GridironSectionBar("TEAM ADVANCED STATS", trailing = if (isPro) null else ({ ProBadge() }))

        GamesTeamsUi.PickerRow(
            groups = listOfNotNull(
                TeamSide.entries.size to {
                    GridironSegmented(TeamSide.entries.map { Segment(it, it.label) }, side, { side = it })
                },
                if (supportsRecent) FormMode.entries.size to {
                    GridironSegmented(
                        FormMode.entries.map { Segment(it, it.raw, isLocked = !isPro && it != FormMode.SEASON) },
                        mode, { mode = it },
                        onLockedTap = { onUpgradeTap() },
                    )
                } else null,
            ),
            modifier = Modifier.background(GridironPalette.surfaceAlt).padding(horizontal = GridironGeo.padInline, vertical = 8.dp),
        )

        if (effectiveMode.usesRecent) {
            GridironSegmented(
                RecentWindow.entries.map { Segment(it, it.segmentLabel) },
                RecentWindow.of(windowGames) ?: RecentWindow.THREE,
                { windowGames = it.value },
                Modifier.background(GridironPalette.surfaceAlt).padding(horizontal = GridironGeo.padInline).padding(bottom = 8.dp),
            )
        }

        when (effectiveMode) {
            FormMode.SEASON -> SeasonBars(form, players.isEmpty())
            FormMode.RECENT -> RecentSection(form, isPro, loading, loadError, freshnessStatus) { scope.launch { load() } }
            FormMode.BOTH -> BothSection(form, loading, loadError) { scope.launch { load() } }
        }
    }
}

/** The pieces of derived state the sections share. */
private class FormData(
    val team: String,
    val side: TeamSide,
    val players: List<Player>,
    val curves: TeamPercentileCurves,
    val logs: List<PlayerGameLog>,
    val windowGames: Int,
) {
    /** Smallest team-window play count we'll treat as trustworthy. */
    val smallSamplePlaysThreshold = 40

    /** One bar per roster metric: the roster mean placed on the league curve. Each keeps its own category so the leaderboard link routes correctly. */
    val seasonRows: List<Metric> by lazy { aggregateSeasonRows() }

    private val sideLogs: List<PlayerGameLog>
        get() {
            val onSide = logs.filter { side.includes(it.playerType) }
            // The most recent N game dates across the roster define the window.
            val recentDates = onSide.map { it.gameDate }.distinct().sortedDescending().take(windowGames).toSet()
            return onSide.filter { it.gameDate in recentDates }
        }

    val recentWindow: RecentFormWindow? by lazy {
        val window = sideLogs
        if (window.isEmpty()) null else RecentFormWindow.build("Last $windowGames", windowGames, window)
    }

    private fun aggregateSeasonRows(): List<Metric> {
        val cats = side.categories
        val pool = players.filter { p -> cats.any { p.matchesPlayerType(it) } }
        if (pool.isEmpty()) return emptyList()

        // Ordered (label, category) pairs present on the roster.
        val pairs = mutableListOf<Pair<String, MetricCategory>>()
        val seen = HashSet<String>()
        for (cat in cats) {
            val present = pool.flatMap { p -> p.metrics.filter { it.category == cat }.map { it.label } }.toSet()
            for (label in cat.metricPriorityOrder) {
                if (label in present && seen.add(label)) pairs += label to cat
            }
        }

        return pairs.mapNotNull { (label, category) ->
            var sum = 0.0
            var count = 0.0
            for (player in pool) {
                val metric = player.metrics.firstOrNull { it.label == label && it.category == category } ?: continue
                val value = DashboardViewModel.rawNumeric(metric.value) ?: continue
                sum += value
                count += 1
            }
            if (count <= 0) return@mapNotNull null
            val average = sum / count
            val pct = curves.curve(label)?.percentile(average) ?: return@mapNotNull null
            Metric(label = label, value = formattedValue(average, label), percentile = pct, category = category, id = "teamavg-$label")
        }
    }

    private fun formattedValue(v: Double, label: String): String = when {
        label.endsWith("%") -> String.format(Locale.US, "%.1f%%", v)
        abs(v) >= 100 -> String.format(Locale.US, "%.0f", v)
        else -> String.format(Locale.US, "%.1f", v)
    }

    /** Maps a recent game-log metric key onto the matching season aggregate label. */
    private val recentSpecs: List<Triple<String, String, String>>
        get() = if (side == TeamSide.DEFENSE) {
            listOf(
                Triple("tackles", "Tackles", "%.0f"),
                Triple("def_sacks", "Sacks", "%.1f"),
                Triple("def_interceptions", "INT", "%.0f"),
            )
        } else {
            listOf(
                Triple("passing_yards", "Pass Yds", "%.0f"),
                Triple("passing_tds", "Pass TD", "%.0f"),
                Triple("rushing_yards", "Rush Yds", "%.0f"),
                Triple("rushing_tds", "Rush TD", "%.0f"),
                Triple("receiving_yards", "Rec Yds", "%.0f"),
                Triple("receptions", "Rec", "%.0f"),
            )
        }

    /** The metric category a season label belongs to. */
    private fun categoryFor(label: String): MetricCategory {
        for (cat in side.categories) if (label in cat.metricPriorityOrder) return cat
        return side.categories.firstOrNull() ?: MetricCategory.PASSING
    }

    /** The recent-window bar for a season label, or null when the window has no game-log data for it. */
    fun recentMetric(label: String, window: RecentFormWindow): Metric? {
        val spec = recentSpecs.firstOrNull { it.second == label } ?: return null
        val value = window.metrics[spec.first] ?: return null
        val pct = curves.curve(label)?.percentile(value) ?: return null
        return Metric(
            label = label,
            value = String.format(Locale.US, spec.third, value),
            percentile = pct,
            category = categoryFor(label),
            id = "team-recent-${spec.first}",
        )
    }

    /**
     * Recent mode mirrors the season list: every season aggregate bar is shown.
     * Metrics with game-log data in the window render the recent value (re-placed
     * on the league curve); the rest fall back to their season aggregate bar.
     */
    fun recentDisplayRows(window: RecentFormWindow): List<Metric> {
        val existing = seasonRows.map { it.label }.toSet()
        val stubs = recentSpecs.mapNotNull { spec ->
            if (spec.second in existing || recentMetric(spec.second, window) == null) return@mapNotNull null
            Metric(
                label = spec.second, value = "", percentile = 0, category = categoryFor(spec.second),
                id = "team-recent-stub-${spec.first}",
            )
        }
        return (seasonRows + stubs).map { recentMetric(it.label, window) ?: it }
    }
}

@Composable
private fun ProBadge() {
    Row(
        Modifier.clip(CircleShape).background(CrownYellow).padding(horizontal = 7.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SfIcon("crown.fill", 11.dp, GridironPalette.midnight)
        Text("STATSCOUT+", style = GridironType.micro.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = GridironPalette.midnight)
    }
}

// MARK: - Season

@Composable
private fun SeasonBars(form: FormData, rosterEmpty: Boolean) {
    val navigator = LocalNavigator.current
    val rows = form.seasonRows
    if (rows.isEmpty()) {
        EmptyAggregate(
            if (rosterEmpty) "No games played yet this season" else "Not enough ${form.side.label.lowercase()} data to aggregate",
        )
        return
    }
    GridironSubSectionBar(form.side.label.uppercase())
    Column {
        rows.forEachIndexed { index, metric ->
            BarRow(
                metric, index,
                Modifier.clickable(onClickLabel = "See the league leaderboard for ${metric.label}") {
                    navigator.push(Route.Metric(metric.label, metric.category))
                },
            )
        }
    }
    Text(
        "Season to date, averaged across the ${form.side.label.lowercase()} roster",
        style = GridironType.micro,
        color = GridironPalette.inkTertiary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = GridironGeo.padCard, vertical = 10.dp),
    )
}

@Composable
private fun BarRow(metric: Metric, index: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth()
            .background(if (index % 2 == 0) GridironPalette.surface else GridironPalette.surfaceAlt)
            .bottomHairline(GridironPalette.divider)
            .padding(horizontal = GridironGeo.padCard, vertical = 12.dp),
    ) { MetricBar(metric) }
}

@Composable
private fun EmptyAggregate(text: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SfIcon("chart.bar.xaxis", 26.dp, GridironPalette.inkTertiary)
        Text(text, style = GridironType.small, color = GridironPalette.inkSecondary)
    }
}

// MARK: - Both

@Composable
private fun BothSection(form: FormData, loading: Boolean, loadError: String?, retry: () -> Unit) {
    val seasonRows = form.seasonRows
    val window = form.recentWindow
    val recentRows = if (window == null) emptyMap() else seasonRows.mapNotNull { row ->
        form.recentMetric(row.label, window)?.let { row.label to it }
    }.toMap()
    when {
        loading -> GamesTeamsUi.InlineSpinner("Loading recent games…")
        loadError != null -> InlineLoadError(loadError, retry)
        else -> Column {
            seasonRows.forEachIndexed { index, metric ->
                Box(
                    Modifier.fillMaxWidth()
                        .background(if (index % 2 == 0) GridironPalette.surface else GridironPalette.surfaceAlt)
                        .bottomHairline(GridironPalette.divider)
                        .padding(horizontal = GridironGeo.padCard, vertical = 10.dp),
                ) { DualMetricBar(metric, recentRows[metric.label], "Last ${form.windowGames}G") }
            }
        }
    }
}

// MARK: - Recent

@Composable
private fun RecentSection(
    form: FormData,
    isPro: Boolean,
    loading: Boolean,
    loadError: String?,
    freshnessStatus: DataFreshnessStatus?,
    retry: () -> Unit,
) {
    if (isPro) {
        RecentBars(form, loading, loadError, freshnessStatus, retry)
    } else {
        Box(Modifier.fillMaxWidth()) {
            // The blurred sample is hidden from screen readers: it is illustrative, not real.
            Box(Modifier.blur(8.dp).clearAndSetSemantics { }) { RecentTeaser(form.side) }
            BlurGateUnlock(
                "See every team's last 3 / 5 / 8 game form", PaywallTrigger.TeamView,
                Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * Static, non-fetching preview for free users: illustrative team bars in the
 * recent-form layout. No game logs are fetched (no network or battery cost) and
 * no real team data is shown, so the blur can't be read through to leak the
 * actual recent numbers.
 */
@Composable
private fun RecentTeaser(side: TeamSide) {
    val sample = if (side == TeamSide.OFFENSE) {
        listOf(
            Metric(id = "tt_passyd", label = "Pass Yds", value = "3,980", percentile = 84, category = MetricCategory.PASSING),
            Metric(id = "tt_rushyd", label = "Rush Yds", value = "1,720", percentile = 77, category = MetricCategory.RUSHING),
            Metric(id = "tt_recyd", label = "Rec Yds", value = "3,910", percentile = 71, category = MetricCategory.RECEIVING),
            Metric(id = "tt_yac", label = "YAC", value = "5.4", percentile = 66, category = MetricCategory.RECEIVING),
        )
    } else {
        listOf(
            Metric(id = "tt_tackles", label = "Tackles", value = "78", percentile = 81, category = MetricCategory.DEFENSE),
            Metric(id = "tt_sacks", label = "Sacks", value = "11", percentile = 76, category = MetricCategory.DEFENSE),
            Metric(id = "tt_int", label = "INT", value = "4", percentile = 70, category = MetricCategory.DEFENSE),
            Metric(id = "tt_pd", label = "PD", value = "9", percentile = 73, category = MetricCategory.DEFENSE),
        )
    }
    Column {
        Row(Modifier.padding(GridironGeo.padInline), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryStat("G", "14")
            SummaryStat("Plays", "521")
            if (side == TeamSide.OFFENSE) SummaryStat("Touches", "318")
        }
        GridironSubSectionBar(side.label.uppercase())
        sample.forEachIndexed { index, metric -> BarRow(metric, index) }
    }
}

@Composable
private fun RecentBars(form: FormData, loading: Boolean, loadError: String?, freshnessStatus: DataFreshnessStatus?, retry: () -> Unit) {
    val window = form.recentWindow
    when {
        loading -> GamesTeamsUi.InlineSpinner("Loading recent games…")
        loadError != null -> InlineLoadError(loadError, retry)
        window != null -> {
            RecentSummaryRow(form, window)
            val rows = form.recentDisplayRows(window)
            if (rows.isNotEmpty()) {
                GridironSubSectionBar(form.side.label.uppercase())
                rows.forEachIndexed { index, metric -> BarRow(metric, index) }
            }
        }
        else -> Column(
            Modifier.fillMaxWidth().padding(vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SfIcon("calendar.badge.exclamationmark", 26.dp, GridironPalette.inkTertiary)
            Text(emptyStateText(form, freshnessStatus), style = GridironType.small, color = GridironPalette.inkSecondary)
        }
    }
}

private fun emptyStateText(form: FormData, status: DataFreshnessStatus?): String = when (status) {
    DataFreshnessStatus.PENDING, DataFreshnessStatus.PARTIAL, DataFreshnessStatus.CHECKING -> "Recent team data is still arriving"
    DataFreshnessStatus.OFFLINE, DataFreshnessStatus.FAILED -> "Recent team data is unavailable right now"
    else -> "No ${form.side.label.lowercase()} data in the last ${form.windowGames} games"
}

@Composable
private fun RecentSummaryRow(form: FormData, window: RecentFormWindow) {
    Row(Modifier.padding(GridironGeo.padInline), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        SummaryStat("G", "${window.games}")
        SummaryStat("Plays", "${window.plays}")
        if (form.side == TeamSide.OFFENSE) SummaryStat("Touches", "${window.touches}")
        Box(Modifier.weight(1f))
        if (window.plays < form.smallSamplePlaysThreshold) {
            Text(
                "SMALL SAMPLE",
                style = GridironType.micro,
                color = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.clip(CircleShape).background(GridironPalette.inkTertiary).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, style = GridironType.micro, color = GridironPalette.inkTertiary)
        Text(value, style = GridironType.bodyBold, color = GridironPalette.ink)
    }
}
