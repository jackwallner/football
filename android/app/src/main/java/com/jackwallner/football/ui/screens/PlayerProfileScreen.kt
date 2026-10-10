package com.jackwallner.football.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jackwallner.football.data.DashboardViewModel
import com.jackwallner.football.data.PaywallTrigger
import com.jackwallner.football.model.FootballMetricRegistry
import com.jackwallner.football.model.Metric
import com.jackwallner.football.model.MetricCategory
import com.jackwallner.football.model.MetricCoverage
import com.jackwallner.football.model.MetricFamily
import com.jackwallner.football.model.MetricKind
import com.jackwallner.football.model.Player
import com.jackwallner.football.model.PlayerGameLog
import com.jackwallner.football.model.PlayerPositionGroup
import com.jackwallner.football.model.RecentFormWindow
import com.jackwallner.football.model.RecentWindow
import com.jackwallner.football.model.SeasonLabel
import com.jackwallner.football.model.SeasonPhase
import com.jackwallner.football.model.StandardStatSemantics
import com.jackwallner.football.model.metricNumericValue
import com.jackwallner.football.model.swiftRoundDouble
import com.jackwallner.football.ui.LocalAppActions
import com.jackwallner.football.ui.LocalGraph
import com.jackwallner.football.ui.components.CrownYellow
import com.jackwallner.football.ui.components.DataFreshnessView
import com.jackwallner.football.ui.components.DualMetricBar
import com.jackwallner.football.ui.components.EmptyState
import com.jackwallner.football.ui.components.GridironMenu
import com.jackwallner.football.ui.components.GridironSectionBar
import com.jackwallner.football.ui.components.GridironSegmented
import com.jackwallner.football.ui.components.GridironSubSectionBar
import com.jackwallner.football.ui.components.GridironTopBar
import com.jackwallner.football.ui.components.InfoNote
import com.jackwallner.football.ui.components.MenuItem
import com.jackwallner.football.ui.components.MenuSection
import com.jackwallner.football.ui.components.MetricBar
import com.jackwallner.football.ui.components.PlayerIdentityStrip
import com.jackwallner.football.ui.components.RefreshableBox
import com.jackwallner.football.ui.components.Segment
import com.jackwallner.football.ui.components.SfIcon
import com.jackwallner.football.ui.components.StatGlossaryLink
import com.jackwallner.football.ui.components.StatScoutSheet
import com.jackwallner.football.ui.components.TextAction
import com.jackwallner.football.ui.components.bottomBarPadding
import com.jackwallner.football.ui.components.bottomHairline
import com.jackwallner.football.ui.components.gridironCard
import com.jackwallner.football.ui.components.rememberHaptic
import com.jackwallner.football.ui.nav.LocalNavigator
import com.jackwallner.football.ui.nav.Route
import com.jackwallner.football.ui.nav.rememberRetained
import com.jackwallner.football.ui.theme.GridironGeo
import com.jackwallner.football.ui.theme.GridironPalette
import com.jackwallner.football.ui.theme.GridironType
import java.text.NumberFormat
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlinx.coroutines.launch

enum class FormDisplayMode(val label: String) { SEASON("Season"), RECENT("Recent"), BOTH("Both") }

enum class PlayerStatTab(val label: String) { ADVANCED("Advanced"), STANDARD("Standard"), YEAR_COMPARE("Year Compare") }

/** Resolves the row for the season and phase the route carries, as iOS `PlayerProfileDestination` does. */
@Composable
fun PlayerProfileRoute(player: Player) {
    val vm = LocalGraph.current.dashboard
    val history = vm.playerHistories[player.playerId].orEmpty()
    val seasonPlayer = history.firstOrNull { it.season == player.season && it.seasonPhase == player.seasonPhase } ?: player
    PlayerProfileScreen(seasonPlayer, history, vm)
}

/** Profile state that must survive pushing a drill-down on top of the page. */
@Stable
private class ProfileState {
    var selectedTab by mutableStateOf(PlayerStatTab.ADVANCED)
    var selectedSeason by mutableStateOf<Int?>(null)
    var formMode by mutableStateOf(FormDisplayMode.SEASON)
    var recentWindowGames by mutableStateOf(5)
    var standardMode by mutableStateOf(FormDisplayMode.SEASON)
    var standardWindow by mutableStateOf(RecentWindow.FIVE)
    var recentLogs by mutableStateOf<List<PlayerGameLog>>(emptyList())
    var recentLogsKey by mutableStateOf<String?>(null)
    var recentLoading by mutableStateOf(false)
    var recentLoadingKey: String? = null
    var recentLoadError by mutableStateOf<String?>(null)
    var countedOpen = false
}

@Composable
fun PlayerProfileScreen(player: Player, history: List<Player>, vm: DashboardViewModel) {
    val graph = LocalGraph.current
    val store = graph.subscriptions
    val favorites = graph.favorites
    val actions = LocalAppActions.current
    val navigator = LocalNavigator.current
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    val state = rememberRetained("profile-${player.id}") { ProfileState() }
    var showPercentileInfo by remember { mutableStateOf(false) }
    var showingPlayerPicker by remember { mutableStateOf(false) }
    val isPro = store.isPro

    val phaseHistory = history.filter { it.seasonPhase == player.seasonPhase }
    val activeSeason = state.selectedSeason ?: player.season
    val displayed = activeSeason?.let { s -> phaseHistory.firstOrNull { it.season == s } } ?: player
    val activePhase = player.seasonPhase
    val seasonLabel = activeSeason?.let { SeasonLabel.text(it, player.seasonPhase) } ?: "-"
    val recentFormSeasons = vm.recentFormSeasons
    val supportsRecentForm = (activeSeason ?: vm.freeSeason) in recentFormSeasons
    val allPlayers = remember(player.season, activePhase, vm.playerHistories) { vm.players(player.season ?: vm.selectedSeason, activePhase) }
    // Every percentile and comparison is measured against the season on screen, not the one the page opened in.
    val cohort = remember(activeSeason, allPlayers, vm.playerHistories) {
        if (activeSeason == null || activeSeason == player.season) allPlayers
        else vm.players(activeSeason, activePhase).ifEmpty { allPlayers }
    }
    val category = displayed.primaryCategory
    val curves = remember(cohort, category) { LeaguePercentileCurves(cohort, listOf(category), category.metricPriorityOrder) }
    val liveProfile = vm.profile(displayed)
    val effectiveForm = if (supportsRecentForm) state.formMode else FormDisplayMode.SEASON
    val effectiveStandard = if (isPro && supportsRecentForm) state.standardMode else FormDisplayMode.SEASON

    // First-impression pitch from the second profile open; a positive moment from the third.
    LaunchedEffect(Unit) {
        if (state.countedOpen) return@LaunchedEffect
        state.countedOpen = true
        val opens = graph.defaults.getInt(PROFILE_OPEN_COUNT) + 1
        graph.defaults.putInt(PROFILE_OPEN_COUNT, opens)
        if (!store.isPro && opens >= 2 && graph.paywallGate.shouldPresent(PaywallTrigger.PlayerScouting)) {
            actions.openTrialPitch(PaywallTrigger.PlayerScouting)
        } else if (opens >= 3) {
            actions.positiveMoment()
        }
    }

    suspend fun loadRecentLogs() {
        val season = activeSeason ?: return
        if (!store.isPro) return
        // The phase belongs in the key: a regular season and its playoffs are two sets of football.
        val key = "${player.playerId}-$season-${activePhase.raw}-${vm.freshnessRevision ?: "none"}"
        if (state.recentLogsKey == key && state.recentLogs.isNotEmpty()) return
        if (state.recentLoadingKey == key) return
        state.recentLoadingKey = key
        state.recentLoading = true
        state.recentLoadError = null
        try {
            val logs = vm.fetchGameLogs(player.playerId, season, activePhase)
            if (state.recentLoadingKey != key) return
            state.recentLogs = logs
            state.recentLogsKey = key
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            if (state.recentLoadingKey != key) return
            if (state.recentLogs.isEmpty() || state.recentLogsKey != key) {
                state.recentLogs = emptyList()
                state.recentLogsKey = null
                state.recentLoadError = "Couldn't load recent games. Check your connection and try again."
            }
        }
        state.recentLoadingKey = null
        state.recentLoading = false
    }

    LaunchedEffect(effectiveForm, state.recentWindowGames, activeSeason, isPro, vm.freshnessRevision) {
        if (isPro && effectiveForm != FormDisplayMode.SEASON) loadRecentLogs()
    }
    LaunchedEffect(effectiveStandard, state.standardWindow, activeSeason, isPro, vm.freshnessRevision) {
        if (isPro && effectiveStandard != FormDisplayMode.SEASON) loadRecentLogs()
    }

    val comparable = remember(cohort, displayed) {
        val myType = displayed.playerType?.lowercase()
        val mine = displayed.overallPercentile
        cohort.filter { it.playerId != player.playerId && (myType == null || it.playerType?.lowercase() == myType) }
            .sortedBy { abs(it.overallPercentile - mine) }
    }

    Column(Modifier.fillMaxSize().background(GridironPalette.canvas)) {
        GridironTopBar(
            title = player.name,
            onBack = { navigator.pop() },
            trailing = {
                val isFavorite = favorites.isFavorite(player.playerId)
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable {
                        favorites.toggleFavorite(player.playerId)
                        haptic()
                    }.semantics { contentDescription = if (isFavorite) "Unfollow ${player.name}" else "Follow ${player.name}" }.testTag("favoriteButton"),
                    contentAlignment = Alignment.Center,
                ) { SfIcon(if (isFavorite) "star.fill" else "star", 22.dp, if (isFavorite) CrownYellow else Color.White) }
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable {
                        if (store.isPro) showingPlayerPicker = true else actions.openTrialPitch(PaywallTrigger.PlayerComparison)
                    }.semantics { contentDescription = "Compare with another player" }.testTag("compareButton"),
                    contentAlignment = Alignment.Center,
                ) { SfIcon("person.2.fill", 22.dp, Color.White) }
            },
        )
        RefreshableBox({
            vm.load()
            state.recentLogsKey = null
            if (store.isPro && (effectiveForm != FormDisplayMode.SEASON || effectiveStandard != FormDisplayMode.SEASON)) loadRecentLogs()
        }, Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottomBarPadding())) {
                PlayerIdentityStrip(displayed, profile = liveProfile, injury = vm.injuryReport(displayed))
                DataFreshnessView(vm, Modifier.padding(horizontal = 12.dp).padding(top = 6.dp), season = activeSeason ?: player.season, phase = activePhase)
                val season = activeSeason ?: player.season
                if (season != null && season in recentFormSeasons) {
                    Box(Modifier.padding(horizontal = 12.dp)) { PlayerLastGameCard(viewModel = vm, player = displayed, season = season, phase = activePhase) }
                }
                Row(Modifier.padding(horizontal = 12.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlayerStatTab.entries.forEach { tab ->
                        val selected = state.selectedTab == tab
                        Box(
                            Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(GridironGeo.radiusCard))
                                .background(if (selected) GridironPalette.turf else GridironPalette.surface)
                                .clickable {
                                    state.selectedTab = tab
                                    if (tab == PlayerStatTab.YEAR_COMPARE && store.isPro && !vm.hasLoadedHistorical && !vm.isHistoricalLoading) {
                                        scope.launch { vm.loadHistoricalIfNeeded() }
                                    }
                                    haptic()
                                }
                                .testTag("profileTab_${tab.label}"),
                            contentAlignment = Alignment.Center,
                        ) { Text(tab.label, style = GridironType.bodyBold, color = if (selected) Color.White else GridironPalette.ink) }
                    }
                }

                val seasonMenu = @Composable {
                    val seasons = (phaseHistory.mapNotNull { it.season } + listOfNotNull(player.season)).distinct().sortedDescending()
                    if (seasons.size > 1) {
                        GridironMenu(listOf(MenuSection(null, seasons.map { s ->
                            val locked = s != vm.freeSeason && !store.isPro
                            MenuItem(SeasonLabel.text(s), checked = !locked && s == activeSeason, locked = locked) {
                                if (locked) actions.openTrialPitch(PaywallTrigger.PastSeason) else state.selectedSeason = s
                            }
                        }))) { open ->
                            Row(
                                Modifier.heightIn(min = 28.dp).clickable(onClick = open).semantics { contentDescription = "Season, $seasonLabel" },
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(seasonLabel, style = GridironType.micro, color = GridironPalette.inkSecondary, maxLines = 1)
                                SfIcon("chevron.down", 11.dp, GridironPalette.inkSecondary)
                            }
                        }
                    } else Text(seasonLabel, style = GridironType.micro, color = GridironPalette.inkSecondary, maxLines = 1)
                }

                when (state.selectedTab) {
                    PlayerStatTab.ADVANCED -> Column(Modifier.padding(horizontal = 12.dp).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PercentileCard(
                            displayed = displayed,
                            seasonLabel = seasonLabel,
                            seasonMenu = seasonMenu,
                            onInfo = { showPercentileInfo = true },
                            isPro = isPro,
                            supportsRecent = supportsRecentForm,
                            state = state,
                            effectiveForm = effectiveForm,
                            curves = curves,
                            freshnessStatus = vm.freshnessStatus,
                            onMetric = { m -> navigator.push(Route.Metric(m.label, m.category, activeSeason, activePhase)) },
                        )
                        defensivePendingNote(displayed, vm)?.let { InfoNote(it, Modifier.padding(horizontal = 0.dp)) }
                        if (liveProfile?.contractLabel != null) {
                            ContractValueCard(displayed, liveProfile, vm.contractValue(displayed), vm.dataCoverage?.week?.let { "through Week $it" })
                        }
                        if (!isPro) {
                            RecentFormCard(
                                player = player,
                                season = activeSeason ?: player.season ?: java.time.LocalDate.now().year,
                                leaguePlayers = cohort,
                                fetchGameLogs = { id, s, ph -> vm.fetchGameLogs(id, s, ph) },
                                freshnessRevision = vm.freshnessRevision,
                                freshnessStatus = vm.freshnessStatus,
                            )
                            ProUpsellCard(player.name) { actions.openTrialPitch(store.defaultUpgradeTrigger) }
                        }
                    }
                    PlayerStatTab.STANDARD -> Column(Modifier.padding(horizontal = 12.dp).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StandardStatsCard(
                            displayed = displayed,
                            cohort = cohort,
                            seasonMenu = seasonMenu,
                            supportsRecent = supportsRecentForm,
                            isPro = isPro,
                            state = state,
                            effectiveMode = effectiveStandard,
                            onLockedTap = { actions.openTrialPitch(PaywallTrigger.RecentForm) },
                            onStat = { label, cat -> navigator.push(Route.StandardStat(label, cat.raw, activeSeason, activePhase)) },
                        )
                        if (season != null && season in recentFormSeasons) {
                            PlayerGameLogCard(viewModel = vm, player = displayed, season = season, phase = activePhase)
                        }
                    }
                    PlayerStatTab.YEAR_COMPARE -> when {
                        !isPro -> YearComparePreview(player.name)
                        vm.isHistoricalLoading -> HistoricalLoadingCard(vm.loadingMessage, vm.loadingProgress)
                        !vm.hasLoadedHistorical -> LoadHistoryCard { scope.launch { vm.loadHistoricalIfNeeded() } }
                        history.size < 2 -> Box(Modifier.padding(horizontal = 12.dp).padding(top = 12.dp).fillMaxWidth().gridironCard().padding(vertical = 24.dp)) {
                            EmptyState("Not enough history", "calendar.badge.clock", "${player.name} doesn't have multiple seasons of data to compare.")
                        }
                        else -> YearComparisonView(phaseHistory)
                    }
                }
                Box(Modifier.padding(horizontal = 12.dp).padding(top = 12.dp)) { StatGlossaryLink() }
            }
        }
    }

    StatScoutSheet(showPercentileInfo, { showPercentileInfo = false }) { PercentileInfoSheet { showPercentileInfo = false } }
    StatScoutSheet(showingPlayerPicker, { showingPlayerPicker = false }) {
        PlayerPickerSheet(comparable, "Compare With", onCancel = { showingPlayerPicker = false }) { selected ->
            showingPlayerPicker = false
            navigator.push(Route.Comparison(displayed, selected))
        }
    }
}

private const val PROFILE_OPEN_COUNT = "profileOpenCount"

private fun defensivePendingNote(displayed: Player, vm: DashboardViewModel): String? {
    if (displayed.positionGroup != PlayerPositionGroup.DEFENSE || displayed.season != vm.freeSeason || displayed.seasonPhase != SeasonPhase.REGULAR) return null
    return MetricCoverage.pendingNote(MetricCategory.DEFENSE, vm.dataFreshness?.advancedDefenseStatus, null)
}

@Composable
private fun PercentileCard(
    displayed: Player,
    seasonLabel: String,
    seasonMenu: @Composable () -> Unit,
    onInfo: () -> Unit,
    isPro: Boolean,
    supportsRecent: Boolean,
    state: ProfileState,
    effectiveForm: FormDisplayMode,
    curves: LeaguePercentileCurves,
    freshnessStatus: com.jackwallner.football.model.DataFreshnessStatus,
    onMetric: (Metric) -> Unit,
) {
    val kind = if (displayed.positionGroup == PlayerPositionGroup.DEFENSE) MetricKind.TRADITIONAL else MetricKind.ADVANCED
    val grouped = displayed.metrics(kind).groupBy { FootballMetricRegistry.definition(it.label, it.category)?.family ?: MetricFamily.PRODUCTION }
    val groups = MetricFamily.entries.mapNotNull { f -> grouped[f]?.takeIf { it.isNotEmpty() }?.let { f to FootballMetricRegistry.sorted(it) } }
    val category = displayed.primaryCategory
    val window = state.recentLogs.sortedByDescending { it.gameDate }.take(state.recentWindowGames).takeIf { it.isNotEmpty() }
        ?.let { RecentFormWindow.build("Last ${state.recentWindowGames}", state.recentWindowGames, it) }
    val specs = recentSpecs(category)

    fun recentMetric(season: Metric): Metric? {
        val w = window ?: return null
        val spec = specs.firstOrNull { it.label == season.label } ?: return null
        val v = spec.value(w.metrics) ?: return null
        val pct = curves.curve(spec.label)?.percentile(v) ?: return null
        return Metric(season.label, String.format(Locale.US, spec.format, v), pct, season.category, id = "recent-${spec.label}")
    }

    fun displayedMetrics(metrics: List<Metric>): List<Metric> {
        if (effectiveForm != FormDisplayMode.RECENT || !isPro) return metrics
        if (metrics.firstOrNull()?.category != category) return metrics
        val existing = metrics.map { it.label }.toSet()
        // A recent figure for a metric the season snapshot omits still gets its row.
        val stubs = specs.mapNotNull { spec ->
            if (spec.label in existing) return@mapNotNull null
            val stub = Metric(spec.label, "", 0, category, id = "recent-stub-${spec.label}")
            stub.takeIf { recentMetric(it) != null }
        }
        return metrics + stubs
    }

    Column(Modifier.fillMaxWidth().gridironCard()) {
        GridironSectionBar(
            if (displayed.positionGroup == PlayerPositionGroup.DEFENSE) "PRODUCTION PERCENTILES" else "ADVANCED PERCENTILES",
            trailing = {
                seasonMenu()
                Text(
                    "ⓘ",
                    style = GridironType.micro,
                    color = GridironPalette.linkBlue,
                    modifier = Modifier.padding(start = 4.dp).clip(CircleShape).clickable(onClick = onInfo).padding(6.dp)
                        .semantics { contentDescription = "About percentiles" },
                )
            },
        )
        if (isPro && supportsRecent) {
            Box(Modifier.fillMaxWidth().background(GridironPalette.surfaceAlt).padding(horizontal = GridironGeo.padInline, vertical = 6.dp)) {
                GridironSegmented(FormDisplayMode.entries.map { Segment(it, it.label) }, state.formMode, { state.formMode = it })
            }
        }
        if (effectiveForm != FormDisplayMode.SEASON && isPro && supportsRecent) {
            GridironSegmented(
                RecentWindow.entries.map { Segment(it, it.segmentLabel) },
                RecentWindow.of(state.recentWindowGames) ?: RecentWindow.THREE,
                { state.recentWindowGames = it.value },
                Modifier.padding(horizontal = GridironGeo.padInline),
            )
            when {
                state.recentLoading -> Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = GridironPalette.inkTertiary, strokeWidth = 2.dp)
                    Text("Loading recent games…", style = GridironType.small, color = GridironPalette.inkSecondary)
                }
                state.recentLoadError != null -> Text(state.recentLoadError!!, style = GridironType.small, color = GridironPalette.inkSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp))
                window == null -> Text(recentEmptyText(freshnessStatus, state.recentWindowGames), style = GridironType.small, color = GridironPalette.inkSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp))
            }
        }
        if (groups.isEmpty()) {
            Box(Modifier.padding(vertical = 24.dp)) {
                EmptyState("No metrics available", "chart.bar", "Percentile rankings are not available for this player in the $seasonLabel season.")
            }
        } else groups.forEach { (family, metrics) ->
            val rows = displayedMetrics(metrics)
            if (rows.isEmpty()) return@forEach
            GridironSubSectionBar(family.label.uppercase())
            rows.forEachIndexed { index, metric ->
                val recent = recentMetric(metric)
                val bg = if (index % 2 == 0) GridironPalette.surface else GridironPalette.surfaceAlt
                val rowModifier = Modifier.fillMaxWidth().background(bg).bottomHairline()
                when (effectiveForm) {
                    FormDisplayMode.SEASON -> Box(rowModifier.clickable { onMetric(metric) }.padding(horizontal = GridironGeo.padCard, vertical = 12.dp)) { MetricBar(metric) }
                    FormDisplayMode.RECENT -> when {
                        recent != null -> Box(rowModifier.padding(horizontal = GridironGeo.padCard, vertical = 12.dp)) { MetricBar(recent) }
                        !metric.id.startsWith("recent-stub-") -> Box(rowModifier.clickable { onMetric(metric) }.padding(horizontal = GridironGeo.padCard, vertical = 12.dp)) { MetricBar(metric) }
                    }
                    FormDisplayMode.BOTH -> Box(rowModifier.clickable { onMetric(metric) }.padding(horizontal = GridironGeo.padCard, vertical = 12.dp)) {
                        DualMetricBar(metric, recent, "Last ${state.recentWindowGames}")
                    }
                }
            }
        }
    }
}

/** One metric the player's own game logs can rebuild exactly; rates come from summed numerators and denominators. */
private class RecentSpec(val label: String, val format: String, val value: (Map<String, Double>) -> Double?)

private fun total(key: String, label: String, format: String = "%.0f") = RecentSpec(label, format) { it[key] }

private fun rate(label: String, format: String, numerator: String, over: List<String>, scale: Double = 1.0) = RecentSpec(label, format) { m ->
    val den = over.sumOf { m[it] ?: 0.0 }
    val num = m[numerator]
    if (den > 0 && num != null) num / den * scale else null
}

/** Deliberately partial: Next Gen season aggregates and team shares have no per-game denominator. */
private fun recentSpecs(category: MetricCategory): List<RecentSpec> = when (category) {
    MetricCategory.PASSING -> listOf(
        total("passing_yards", "Pass Yds"),
        total("passing_tds", "Pass TD"),
        rate("Cmp%", "%.1f%%", "completions", listOf("attempts"), 100.0),
        rate("Y/A", "%.1f", "passing_yards", listOf("attempts")),
        rate("INT%", "%.1f%%", "interceptions", listOf("attempts"), 100.0),
        rate("EPA/Play", "%.2f", "passing_epa", listOf("attempts", "sacks_suffered")),
        rate("Sack%", "%.1f%%", "sacks_suffered", listOf("attempts", "sacks_suffered"), 100.0),
    )
    MetricCategory.RUSHING -> listOf(
        total("rushing_yards", "Rush Yds"),
        total("rushing_tds", "Rush TD"),
        total("rushing_first_downs", "Rush 1D"),
        total("rushing_epa", "Rush EPA", "%.1f"),
        total("rush_yoe", "RYOE", "%.1f"),
        rate("Y/C", "%.1f", "rushing_yards", listOf("carries")),
        rate("EPA/Rush", "%.2f", "rushing_epa", listOf("carries")),
        rate("Fumble%", "%.1f%%", "rushing_fumbles", listOf("carries"), 100.0),
    )
    MetricCategory.RECEIVING -> listOf(
        total("receiving_yards", "Rec Yds"),
        total("receptions", "Rec"),
        total("receiving_tds", "Rec TD"),
        total("receiving_yac", "YAC"),
        total("receiving_epa", "Rec EPA", "%.1f"),
        rate("Catch%", "%.1f%%", "receptions", listOf("targets"), 100.0),
        rate("EPA/Tgt", "%.2f", "receiving_epa", listOf("targets")),
        rate("RACR", "%.2f", "receiving_yards", listOf("receiving_air_yards")),
    )
    MetricCategory.DEFENSE -> listOf(
        total("tackles", "Tackles"),
        total("def_sacks", "Sacks", "%.1f"),
        total("def_interceptions", "INT"),
        total("def_pass_defended", "PD"),
        total("def_tackles_for_loss", "TFL"),
        total("def_qb_hits", "QB Hits"),
        total("def_fumbles_forced", "FF"),
    )
}

private val COUNTING_STATS = setOf("G", "PASS YDS", "PASS TD", "INT", "CAR", "RUSH YDS", "RUSH TD", "REC", "REC YDS", "REC TD", "TACKLES", "SACKS", "DEF INT", "TGT ALLOWED")

private val STANDARD_RECENT_KEYS = mapOf(
    "PASS YDS" to "passing_yards", "PASS TD" to "passing_tds", "INT" to "interceptions",
    "CAR" to "carries", "RUSH YDS" to "rushing_yards", "RUSH TD" to "rushing_tds",
    "REC" to "receptions", "REC YDS" to "receiving_yards", "REC TD" to "receiving_tds",
    "TACKLES" to "tackles", "SACKS" to "def_sacks", "DEF INT" to "def_interceptions",
)

/** Which board a traditional stat belongs to, so a row opens the leaderboard that lists it. */
private fun standardCategory(label: String, fallback: StandardStatCategory): StandardStatCategory = when (label.uppercase()) {
    "PASS YDS", "PASS TD", "INT", "CMP/ATT" -> StandardStatCategory.PASSING
    "CAR", "RUSH YDS", "RUSH TD" -> StandardStatCategory.RUSHING
    "REC/TGT", "REC YDS", "REC TD" -> StandardStatCategory.RECEIVING
    "TACKLES", "SACKS", "DEF INT" -> StandardStatCategory.DEFENSE
    else -> fallback
}

@Composable
private fun StandardStatsCard(
    displayed: Player,
    cohort: List<Player>,
    seasonMenu: @Composable () -> Unit,
    supportsRecent: Boolean,
    isPro: Boolean,
    state: ProfileState,
    effectiveMode: FormDisplayMode,
    onLockedTap: () -> Unit,
    onStat: (String, StandardStatCategory) -> Unit,
) {
    val fallback = when (displayed.playerType?.lowercase()) {
        "qb" -> StandardStatCategory.PASSING
        "rb" -> StandardStatCategory.RUSHING
        "wr", "te" -> StandardStatCategory.RECEIVING
        else -> StandardStatCategory.DEFENSE
    }
    val group = displayed.positionGroup
    fun peerValues(label: String): List<String> {
        val key = label.uppercase()
        return cohort.mapNotNull { other ->
            if (other.positionGroup != group) null else other.standardStats?.firstOrNull { it.label.uppercase() == key }?.value
        }
    }
    fun percentile(label: String, value: String) = StandardStatSemantics.percentile(label, value, peerValues(label))
    fun metrics(counting: Boolean): List<Metric> = displayed.standardStats.orEmpty()
        .filter { (it.label.uppercase() in COUNTING_STATS) == counting }
        .map { stat ->
            Metric(
                label = stat.label.uppercase(),
                value = stat.value,
                percentile = percentile(stat.label, stat.value),
                category = standardCategory(stat.label, fallback).metricCategory,
                // A zero count has no honest rank, except where fewer is better: a passer's 0 INT ranks at the top.
                rankable = if (counting && StandardStatSemantics.higherIsBetter(stat.label) && metricNumericValue(stat.value) == 0.0) false else null,
                id = "std-${stat.label}",
            )
        }
    val span = state.standardWindow.value
    val window = state.recentLogs.sortedByDescending { it.gameDate }.take(span).takeIf { it.isNotEmpty() }?.let { RecentFormWindow.build("Last $span", span, it) }
    val caption = window?.games?.let { RecentFormWindow.caption(it, span) } ?: state.standardWindow.segmentLabel
    fun recent(season: Metric): Metric? {
        val key = STANDARD_RECENT_KEYS[season.label] ?: return null
        val value = window?.metrics?.get(key) ?: return null
        val text = if (season.label == "SACKS") String.format(Locale.US, "%.1f", value)
        else NumberFormat.getIntegerInstance(Locale.US).format(swiftRoundDouble(value).toLong())
        return Metric(season.label, text, percentile(season.label, text), season.category, id = "std-recent-${season.label}")
    }
    fun statKey(display: String) = displayed.standardStats.orEmpty().firstOrNull { it.label.uppercase() == display }?.label ?: display

    Column(Modifier.fillMaxWidth().gridironCard()) {
        GridironSectionBar("STANDARD STATS", trailing = { seasonMenu() })
        if (supportsRecent) {
            Box(Modifier.fillMaxWidth().background(GridironPalette.surfaceAlt).padding(horizontal = GridironGeo.padInline, vertical = 6.dp)) {
                GridironSegmented(
                    FormDisplayMode.entries.map { Segment(it, it.label, isLocked = !isPro && it != FormDisplayMode.SEASON) },
                    state.standardMode,
                    { state.standardMode = it },
                    onLockedTap = { onLockedTap() },
                )
            }
            if (effectiveMode != FormDisplayMode.SEASON) {
                Box(Modifier.fillMaxWidth().background(GridironPalette.surfaceAlt).padding(horizontal = GridironGeo.padInline).padding(bottom = 4.dp)) {
                    GridironSegmented(RecentWindow.entries.map { Segment(it, it.segmentLabel) }, state.standardWindow, { state.standardWindow = it })
                }
            }
        }
        if (displayed.standardStats.isNullOrEmpty()) {
            Box(Modifier.padding(vertical = 24.dp)) { EmptyState("Standard stats unavailable", "chart.bar", "Traditional stats are not available for this player.") }
        } else {
            val rates = metrics(false)
            val counts = metrics(true)
            // Only label the two groups when there are two of them.
            val labelled = rates.isNotEmpty() && counts.isNotEmpty()
            @Composable
            fun Rows(list: List<Metric>, start: Int) {
                list.forEachIndexed { offset, metric ->
                    val r = recent(metric)
                    Box(
                        Modifier.fillMaxWidth().background(if ((start + offset) % 2 == 0) GridironPalette.surface else GridironPalette.surfaceAlt)
                            .bottomHairline().clickable { onStat(statKey(metric.label), standardCategory(metric.label, fallback)) }
                            .padding(horizontal = GridironGeo.padCard, vertical = 12.dp),
                    ) {
                        when (effectiveMode) {
                            FormDisplayMode.SEASON -> MetricBar(metric)
                            FormDisplayMode.RECENT -> MetricBar(r ?: metric)
                            FormDisplayMode.BOTH -> DualMetricBar(metric, r, caption)
                        }
                    }
                }
            }
            if (rates.isNotEmpty()) {
                if (labelled) GridironSubSectionBar("RATE")
                Rows(rates, 0)
            }
            if (counts.isNotEmpty()) {
                if (labelled) GridironSubSectionBar("VOLUME")
                Rows(counts, rates.size)
            }
        }
    }
}

@Composable
private fun ProUpsellCard(name: String, onTap: () -> Unit) {
    val store = LocalGraph.current.subscriptions
    Column(Modifier.fillMaxWidth().gridironCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SfIcon("crown.fill", 16.dp, CrownYellow)
            Text("StatScout+", style = GridironType.smallBold, color = GridironPalette.ink)
        }
        Text("Get the full scouting picture on $name.", style = GridironType.body, color = GridironPalette.ink)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "chart.line.uptrend.xyaxis" to "Year-over-year trends across every metric",
                "person.2.fill" to "Head-to-head comparisons vs any player",
                "calendar.badge.clock" to "Every past season, not just this one",
                "arrow.down.circle.fill" to "Saved offline - works on the road",
            ).forEach { (icon, text) ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(16.dp), contentAlignment = Alignment.Center) { SfIcon(icon, 14.dp, GridironPalette.turf) }
                    Text(text, style = GridironType.small, color = GridironPalette.inkSecondary)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp)).background(GridironPalette.turf).clickable(onClick = onTap),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(store.paywallBlurCTA, style = GridironType.bodyBold, color = Color.White)
            SfIcon("arrow.right", 14.dp, Color.White)
        }
        store.paywallBlurSubtext?.let { Text(it, style = GridironType.micro, color = GridironPalette.inkTertiary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun HistoricalLoadingCard(message: String, progress: Double) {
    Column(
        Modifier.padding(horizontal = 12.dp).padding(top = 12.dp).fillMaxWidth().gridironCard().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val value = progress.coerceIn(0.0, 1.0).toFloat()
        LinearProgressIndicator(progress = { value }, color = GridironPalette.turf, trackColor = GridironPalette.surfaceSunk, modifier = Modifier.fillMaxWidth())
        Text(message, style = GridironType.bodyBold, color = GridironPalette.ink)
        Text("${(value * 100).toInt()}%", style = GridironType.micro, color = GridironPalette.inkTertiary)
    }
}

@Composable
private fun LoadHistoryCard(onLoad: () -> Unit) {
    Column(
        Modifier.padding(horizontal = 12.dp).padding(top = 12.dp).fillMaxWidth().gridironCard().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SfIcon("clock.arrow.circlepath", 36.dp, GridironPalette.turf)
        Text("Load past seasons", style = GridironType.bodyBold, color = GridironPalette.ink)
        Text("Year Compare loads historical data only when you need it.", style = GridironType.body, color = GridironPalette.inkSecondary, textAlign = TextAlign.Center)
        Box(
            Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).background(GridironPalette.turf).clickable(onClick = onLoad).padding(horizontal = 18.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Load History", style = GridironType.bodyBold, color = Color.White) }
    }
}

@Composable
fun PercentileInfoSheet(onDone: () -> Unit) {
    Column(Modifier.fillMaxSize().background(GridironPalette.canvas)) {
        GridironTopBar(title = "About Percentiles", trailing = { TextAction("Done", GridironType.smallBold, Color.White, onDone) })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Percentile Rankings", style = GridironType.playerName, color = GridironPalette.ink)
            Text(
                "Percentile rankings compare a player to others at the same position. A 90th percentile means the player ranks in the top 10% of the league for that metric.",
                style = GridironType.body,
                color = GridironPalette.inkSecondary,
            )
            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple("flame.fill", "Elite (75-100): Green bars", GridironPalette.performanceHigh),
                    Triple("minus", "Average (25-75): Charcoal bars", GridironPalette.inkSecondary),
                    Triple("snowflake", "Below Average (0-25): Rust bars", GridironPalette.performanceLow),
                ).forEach { (icon, text, color) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SfIcon(icon, 16.dp, color)
                        Text(text, style = GridironType.bodyBold, color = color)
                    }
                }
            }
            Text(
                "Stats update after new source data is validated. Advanced metrics may arrive later than game totals. Not every metric is tracked for every player.",
                style = GridironType.small,
                color = GridironPalette.inkTertiary,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
