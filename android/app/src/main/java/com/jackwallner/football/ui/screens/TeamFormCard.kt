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
import com.jackwallner.football.data.PaywallTrigger
import com.jackwallner.football.model.DataFreshnessStatus
import com.jackwallner.football.model.FootballMetricRegistry
import com.jackwallner.football.model.Metric
import com.jackwallner.football.model.MetricAggregation
import com.jackwallner.football.model.MetricCategory
import com.jackwallner.football.model.Player
import com.jackwallner.football.model.PlayerGameLog
import com.jackwallner.football.model.RecentFormWindow
import com.jackwallner.football.model.RecentWindow
import com.jackwallner.football.model.RosterAggregate
import com.jackwallner.football.model.SeasonPhase
import com.jackwallner.football.model.StatScoutSeason
import com.jackwallner.football.model.normalizedTeamAbbreviation
import com.jackwallner.football.model.swiftRoundDouble
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
 * Every club's pooled value for each metric, the ruler a team bar sits on.
 * Counting stats are per game so a club with a bye is not ranked on one game
 * fewer; rates use the registry's volume weighting.
 */
internal class TeamLeagueTable(players: List<Player>, categories: List<MetricCategory>) {
    private val valuesByKey: Map<String, List<Double>>

    init {
        val pool = players.filter { p -> categories.any { p.matchesPlayerType(it) } }
        val byTeam = pool.groupBy { normalizedTeamAbbreviation(it.team) }
        val result = HashMap<String, List<Double>>()
        for (category in categories) {
            for (label in category.metricPriorityOrder) {
                val values = byTeam.values.mapNotNull { teamValue(label, category, it) }
                if (values.size >= 2) result[key(label, category)] = values
            }
        }
        valuesByKey = result
    }

    /** Share of the other clubs this value beats, 1 to 99, direction-aware. */
    fun percentile(value: Double, label: String, category: MetricCategory): Int? {
        val values = valuesByKey[key(label, category)] ?: return null
        val higherIsBetter = FootballMetricRegistry.definition(label, category)?.higherIsBetter ?: true
        val worse = values.count { if (higherIsBetter) it < value else it > value }
        val ties = values.count { it == value }
        // The club itself is one of the ties; it neither beats nor loses to itself.
        val others = maxOf(1, values.size - 1)
        val score = (worse + maxOf(0, ties - 1) / 2.0) / others * 100
        return swiftRoundDouble(score).toInt().coerceIn(1, 99)
    }

    companion object {
        fun key(label: String, category: MetricCategory): String = category.raw + "|" + label

        /** A club's value: summed totals divided by its games, or the weighted rate. */
        fun teamValue(label: String, category: MetricCategory, roster: List<Player>): Double? {
            val pooled = RosterAggregate.value(label, category, roster) ?: return null
            if (!isPerGame(label, category)) return pooled
            val games = RosterAggregate.teamGames(roster) ?: return null
            return pooled / games
        }

        fun isPerGame(label: String, category: MetricCategory): Boolean =
            FootballMetricRegistry.aggregation(label, category) == MetricAggregation.Sum

        fun displayLabel(label: String, category: MetricCategory): String =
            if (isPerGame(label, category)) "$label/G" else label

        fun baseLabel(displayLabel: String): String =
            if (displayLabel.endsWith("/G")) displayLabel.dropLast(2) else displayLabel
    }
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
 * Season pools every metric for the active side (totals per game, rates
 * weighted by volume) and ranks the club against the other 31; tapping a bar
 * opens that metric's leaderboard. Recent is the last 3 / 5 / 8 games of game
 * logs per game on the same club ruler, StatScout+ only, with the standard blur
 * and call to action.
 *
 * @param players the roster for this team and season.
 * @param leaguePlayers the league pool every club's value is pooled from.
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

    // Rebuilt after a stats update or a season change, not only when the list changes.
    val table = remember(leaguePlayers, season, freshnessRevision) {
        TeamLeagueTable(leaguePlayers, TeamSide.OFFENSE.categories) to
            TeamLeagueTable(leaguePlayers, TeamSide.DEFENSE.categories)
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

    val form = FormData(team, side, players, table, logs, windowGames)

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
    val table: TeamLeagueTable,
    val logs: List<PlayerGameLog>,
    val windowGames: Int,
) {
    /** Smallest team-window play count we'll treat as trustworthy. */
    val smallSamplePlaysThreshold = 40

    /**
     * One bar per roster metric: the club's pooled value (totals per game, rates
     * weighted by volume) ranked against the other 31 clubs. Each keeps its own
     * category so the leaderboard link routes correctly.
     */
    val seasonRows: List<Metric> by lazy { aggregateSeasonRows() }

    private val sideLogs: List<PlayerGameLog>
        get() {
            val onSide = logs.filter { side.includes(it.playerType) }
            // The most recent N game dates across the roster define the window.
            // Dedupe first: every player has a row per game.
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
                if (label in RosterAggregate.descriptiveOnlyLabels) continue
                if (label in present && seen.add(label)) pairs += label to cat
            }
        }

        return pairs.mapNotNull { (label, category) ->
            val value = TeamLeagueTable.teamValue(label, category, pool) ?: return@mapNotNull null
            val pct = table.percentile(value, label, category) ?: return@mapNotNull null
            Metric(
                label = TeamLeagueTable.displayLabel(label, category),
                value = formattedValue(value, label),
                percentile = pct,
                category = category,
                id = "teamavg-$label",
            )
        }
    }

    private fun formattedValue(v: Double, label: String): String = when {
        label.contains("%") -> String.format(Locale.US, "%.1f%%", v)
        label in setOf("EPA/Play", "EPA/Rush", "EPA/Tgt", "RACR") -> String.format(Locale.US, "%.2f", v)
        abs(v) >= 100 -> String.format(Locale.US, "%.0f", v)
        else -> String.format(Locale.US, "%.1f", v)
    }

    /** Maps a recent game-log metric key onto the matching season aggregate label. */
    private val recentSpecs: List<Pair<String, String>>
        get() = if (side == TeamSide.DEFENSE) {
            listOf("tackles" to "Tackles", "def_sacks" to "Sacks", "def_interceptions" to "INT")
        } else {
            listOf(
                "passing_yards" to "Pass Yds",
                "passing_tds" to "Pass TD",
                "rushing_yards" to "Rush Yds",
                "rushing_tds" to "Rush TD",
                "receiving_yards" to "Rec Yds",
                "receptions" to "Rec",
            )
        }

    /** The metric category a season label belongs to. */
    private fun categoryFor(label: String): MetricCategory {
        for (cat in side.categories) if (label in cat.metricPriorityOrder) return cat
        return side.categories.firstOrNull() ?: MetricCategory.PASSING
    }

    /**
     * The recent-window bar for a season label (base or "/G" display label), or
     * null when the window has no game-log data for it. Window totals are
     * divided by the games in the window and ranked against every club's season
     * per-game figure, so three games are never measured on a ruler of
     * full-season totals.
     */
    fun recentMetric(label: String, window: RecentFormWindow): Metric? {
        val base = TeamLeagueTable.baseLabel(label)
        val category = categoryFor(base)
        val spec = recentSpecs.firstOrNull { it.second == base } ?: return null
        val total = window.metrics[spec.first] ?: return null
        if (window.games <= 0) return null
        val perGame = total / window.games
        val pct = table.percentile(perGame, base, category) ?: return null
        return Metric(
            label = TeamLeagueTable.displayLabel(base, category),
            value = formattedValue(perGame, base),
            percentile = pct,
            category = category,
            id = "team-recent-${spec.first}",
        )
    }

    /**
     * Recent mode mirrors the season list: every season aggregate bar is shown.
     * Metrics with game-log data in the window render the recent value (re-placed
     * on the league curve); the rest fall back to their season aggregate bar.
     */
    fun recentDisplayRows(window: RecentFormWindow): List<Metric> {
        val existing = seasonRows.map { TeamLeagueTable.baseLabel(it.label) }.toSet()
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
                    navigator.push(Route.Metric(TeamLeagueTable.baseLabel(metric.label), metric.category))
                },
            )
        }
    }
    Text(
        "Season to date: totals per game, rates weighted by volume. Bars rank each club against the other 31.",
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
    // The club's own snap count for the window, rebuilt from the box-score
    // columns. Summing each player's plays counted every pass twice (the
    // passer's attempt and the receiver's target) and gave a defense zero.
    val m = window.metrics
    val plays = Math.round((m["attempts"] ?: 0.0) + (m["sacks_suffered"] ?: 0.0) + (m["carries"] ?: 0.0)).toInt()
    val touches = Math.round((m["carries"] ?: 0.0) + (m["receptions"] ?: 0.0)).toInt()
    val offense = form.side == TeamSide.OFFENSE
    Row(Modifier.padding(GridironGeo.padInline), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        SummaryStat("G", "${window.games}")
        if (offense) {
            SummaryStat("Plays", "$plays")
            SummaryStat("Touches", "$touches")
        }
        Box(Modifier.weight(1f))
        if (if (offense) plays < form.smallSamplePlaysThreshold else window.games < 2) {
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
