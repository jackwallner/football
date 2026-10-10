package com.jackwallner.football.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jackwallner.football.data.DashboardViewModel
import com.jackwallner.football.data.KeyValueStore
import com.jackwallner.football.data.PaywallTrigger
import com.jackwallner.football.model.MetricCategory
import com.jackwallner.football.model.MetricCoverage
import com.jackwallner.football.model.NFLConference
import com.jackwallner.football.model.Player
import com.jackwallner.football.model.PlayerPositionGroup
import com.jackwallner.football.model.SeasonLabel
import com.jackwallner.football.model.SeasonPhase
import com.jackwallner.football.model.displayTeamAbbr
import com.jackwallner.football.model.teamFullName
import com.jackwallner.football.ui.HomeTopBar
import com.jackwallner.football.ui.LocalAppActions
import com.jackwallner.football.ui.LocalGraph
import com.jackwallner.football.ui.SeasonPhaseNavPill
import com.jackwallner.football.ui.components.BlurGateUnlock
import com.jackwallner.football.ui.components.ChipButton
import com.jackwallner.football.ui.components.ChipTrailing
import com.jackwallner.football.ui.components.DataFreshnessView
import com.jackwallner.football.ui.components.EmptyState
import com.jackwallner.football.ui.components.FitText
import com.jackwallner.football.ui.components.GridironChip
import com.jackwallner.football.ui.components.GridironMenu
import com.jackwallner.football.ui.components.GridironSegmented
import com.jackwallner.football.ui.components.GridironTabs
import com.jackwallner.football.ui.components.InfoNote
import com.jackwallner.football.ui.components.LeaderboardTableHeader
import com.jackwallner.football.ui.components.LeaderboardTableRow
import com.jackwallner.football.ui.components.LoadingCard
import com.jackwallner.football.ui.components.LoadingStatusBar
import com.jackwallner.football.ui.components.MenuItem
import com.jackwallner.football.ui.components.MenuSection
import com.jackwallner.football.ui.components.RefreshableBox
import com.jackwallner.football.ui.components.SearchField
import com.jackwallner.football.ui.components.Segment
import com.jackwallner.football.ui.components.SfIcon
import com.jackwallner.football.ui.components.SortDirectionButton
import com.jackwallner.football.ui.components.StatOption
import com.jackwallner.football.ui.components.StatPickerMenu
import com.jackwallner.football.ui.components.TextAction
import com.jackwallner.football.ui.components.bottomBarPadding
import com.jackwallner.football.ui.components.cardSlice
import com.jackwallner.football.ui.components.gateBlur
import com.jackwallner.football.ui.components.gridironCard
import com.jackwallner.football.ui.components.rememberHaptic
import com.jackwallner.football.ui.nav.LocalNavigator
import com.jackwallner.football.ui.nav.Route
import com.jackwallner.football.ui.nav.rememberRetained
import com.jackwallner.football.ui.theme.GridironGeo
import com.jackwallner.football.ui.theme.GridironPalette
import com.jackwallner.football.ui.theme.GridironType
import com.jackwallner.football.ui.theme.NFLTeamColor
import kotlinx.coroutines.launch

/** Which league-wide board the Stats tab is drawing. */
enum class StatsBoard { ADVANCED, STANDARD, BEST_WORST, CONTRACT_VALUE }

/** Traditional production stats offered for each position group. */
object StandardStatCatalog {
    fun stats(position: PlayerPositionGroup): List<String> = when (position) {
        PlayerPositionGroup.QB -> listOf("Pass Yds", "Pass TD", "INT", "Rush Yds", "Rush TD", "Car", "G")
        PlayerPositionGroup.RB -> listOf("Rush Yds", "Rush TD", "Car", "Rec Yds", "Rec TD", "G")
        PlayerPositionGroup.WR, PlayerPositionGroup.TE -> listOf("Rec Yds", "Rec TD", "Rush Yds", "Rush TD", "Car", "G")
        PlayerPositionGroup.DEFENSE -> listOf("Tackles", "Sacks", "Def INT", "G")
    }

    fun defaultDescending(stat: String, position: PlayerPositionGroup): Boolean = !(stat == "INT" && position == PlayerPositionGroup.QB)

    fun defaultStat(position: PlayerPositionGroup): String = stats(position).firstOrNull() ?: "G"

    /** A stat picked on purpose follows to any position that offers it; a default does not. */
    fun stat(current: String, from: PlayerPositionGroup, to: PlayerPositionGroup): String =
        if (current != defaultStat(from) && current in stats(to)) current else defaultStat(to)
}

/** The Stats tab's shared board state, kept across pushes like iOS `@State`. */
@Stable
class StatsBoardState(private val defaults: KeyValueStore) {
    private var boardState by mutableStateOf(
        StatsBoard.entries.firstOrNull { it.name == defaults.getString(BOARD_KEY) } ?: StatsBoard.STANDARD,
    )
    var board: StatsBoard
        get() = boardState
        set(value) {
            boardState = value
            defaults.putString(BOARD_KEY, value.name)
        }
    var standardStat by mutableStateOf("Pass Yds")
    var standardSortDescending by mutableStateOf(true)
    /** DEF has no advanced line until PFR publishes; leaving DEF puts Advanced back. */
    var fellBackFromAdvanced by mutableStateOf(false)

    private companion object {
        const val BOARD_KEY = "stats.board"
    }
}

/** Single home for every league-wide statistic. */
@Composable
fun StatsScreen() {
    val graph = LocalGraph.current
    val vm = graph.dashboard
    val actions = LocalAppActions.current
    val state = rememberRetained("statsBoard") { StatsBoardState(graph.defaults) }
    var showingFollowing by remember { mutableStateOf(false) }

    // Position changes: keep a chosen stat, fall back from Advanced on DEF.
    var lastPosition by remember { mutableStateOf(vm.selectedPosition) }
    LaunchedEffect(vm) {
        snapshotFlow { vm.selectedPosition }.collect { next ->
            val old = lastPosition
            lastPosition = next
            if (old == next) return@collect
            val kept = StandardStatCatalog.stat(state.standardStat, old, next)
            if (kept != state.standardStat) {
                state.standardStat = kept
                state.standardSortDescending = StandardStatCatalog.defaultDescending(kept, next)
            }
            if (vm.availableAdvancedSortMetrics.isEmpty() && state.board == StatsBoard.ADVANCED) {
                state.board = StatsBoard.STANDARD
                state.fellBackFromAdvanced = true
            } else if (state.fellBackFromAdvanced && vm.availableAdvancedSortMetrics.isNotEmpty()) {
                if (state.board == StatsBoard.STANDARD) state.board = StatsBoard.ADVANCED
                state.fellBackFromAdvanced = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(GridironPalette.canvas)) {
        HomeTopBar(leading = {
            SeasonPhaseNavPill(vm.availableSeasons) { actions.openTrialPitch(PaywallTrigger.LockedSeason(it)) }
        })
        if (vm.selectedSeason == vm.freeSeason && vm.selectedPhase == SeasonPhase.REGULAR) {
            DataFreshnessView(vm, Modifier.padding(horizontal = 12.dp).padding(top = 4.dp))
        }
        GridironSegmented(
            segments = listOf(Segment(false, "League leaders"), Segment(true, "Following", icon = "star.fill")),
            selection = showingFollowing,
            onSelect = { showingFollowing = it },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        if (showingFollowing) FollowingStatsView(vm)
        else when (state.board) {
            StatsBoard.ADVANCED -> AdvancedBoard(vm, state)
            StatsBoard.STANDARD -> StandardStatsLeadersView(
                players = vm.qualifiedSeasonPlayers.filter { it.positionGroup == vm.selectedPosition && vm.matchesSelectedConference(it) },
                selectedStat = state.standardStat,
                onSelectStat = { state.standardStat = it },
                selectedPosition = vm.selectedPosition,
                onSelectPosition = { vm.selectedPosition = it },
                sortDescending = state.standardSortDescending,
                onSortDescending = { state.standardSortDescending = it },
                boardState = state,
                viewModel = vm,
            )
            StatsBoard.BEST_WORST -> BestWorstBoard(vm, state)
            StatsBoard.CONTRACT_VALUE -> ContractValueBoard(vm, state)
        }
    }
}

@Composable
private fun BestWorstBoard(vm: DashboardViewModel, state: StatsBoardState) {
    val isPro = LocalGraph.current.subscriptions.isPro
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 4.dp)) { StatsViewMenu(vm, state) }
        if (isPro) MetricLeadersView(vm.allMetrics)
        else Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().gateBlur()) { MetricLeadersView(vm.allMetrics, interactive = false) }
            BlurGateUnlock(
                "See who leads and who trails on every metric in the league",
                PaywallTrigger.BestWorst,
                Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp).padding(bottom = 100.dp),
            )
        }
    }
}

/** Picking a statistic also selects the vocabulary that owns it. */
@Composable
fun StatsBoardStatPicker(vm: DashboardViewModel, state: StatsBoardState) {
    val position = vm.selectedPosition
    val active = when (state.board) {
        StatsBoard.ADVANCED -> vm.currentSortMetric ?: vm.sortLabel
        StatsBoard.STANDARD -> state.standardStat
        StatsBoard.BEST_WORST -> "Best & Worst"
        StatsBoard.CONTRACT_VALUE -> "Contract Value"
    }
    StatPickerMenu(
        activeLabel = active,
        advanced = vm.availableAdvancedSortMetrics.map {
            StatOption(it, it, state.board == StatsBoard.ADVANCED && it == vm.currentSortMetric)
        },
        standard = StandardStatCatalog.stats(position).map {
            StatOption(it, it, state.board == StatsBoard.STANDARD && it == state.standardStat)
        },
        onSelectAdvanced = {
            state.board = StatsBoard.ADVANCED
            vm.setUserSortMetric(it.id)
        },
        onSelectStandard = {
            state.board = StatsBoard.STANDARD
            state.standardStat = it.id
            state.standardSortDescending = StandardStatCatalog.defaultDescending(it.id, position)
        },
    )
}

/** Secondary board options: conference, qualifier, and Best & Worst / Contract Value. */
@Composable
fun StatsViewMenu(vm: DashboardViewModel, state: StatsBoardState) {
    val isPro = LocalGraph.current.subscriptions.isPro
    val board = state.board
    val isActive = vm.qualifierLevel != DashboardViewModel.QualifierLevel.ALL || vm.selectedConference != NFLConference.ALL ||
        board == StatsBoard.BEST_WORST || board == StatsBoard.CONTRACT_VALUE
    val leaderboard = board != StatsBoard.BEST_WORST && board != StatsBoard.CONTRACT_VALUE
    GridironMenu(
        listOf(
            MenuSection("Conference", NFLConference.entries.map { c ->
                MenuItem(c.label, checked = c == vm.selectedConference) { vm.selectedConference = c }
            }),
            MenuSection("Qualifier", DashboardViewModel.QualifierLevel.entries.map { level ->
                MenuItem("${level.label} · ${level.description}", checked = level == vm.qualifierLevel) { vm.qualifierLevel = level }
            }),
            MenuSection("Show", listOf(
                MenuItem("Leaderboard", checked = leaderboard) { if (!leaderboard) state.board = StatsBoard.ADVANCED },
                MenuItem(
                    if (isPro || board == StatsBoard.BEST_WORST) "Best & Worst" else "Best & Worst (StatScout+)",
                    checked = board == StatsBoard.BEST_WORST,
                    locked = !isPro && board != StatsBoard.BEST_WORST,
                ) { state.board = StatsBoard.BEST_WORST },
                MenuItem(
                    if (isPro || board == StatsBoard.CONTRACT_VALUE) "Contract Value" else "Contract Value (StatScout+)",
                    checked = board == StatsBoard.CONTRACT_VALUE,
                    locked = !isPro && board != StatsBoard.CONTRACT_VALUE,
                ) { state.board = StatsBoard.CONTRACT_VALUE },
            )),
        ),
        Modifier.testTag("viewMenu"),
    ) { open ->
        ChipButton(open, description = "View options") {
            GridironChip(title = "View", icon = "slider.horizontal.3", trailing = ChipTrailing.Chevron, isActive = isActive)
        }
    }
}

/** The stat picker, direction, search toggle and View menu row every board shares. */
@Composable
fun BoardControlRow(
    picker: @Composable () -> Unit,
    descending: Boolean,
    statLabel: String,
    onToggleDirection: () -> Unit,
    isSearchActive: Boolean,
    onToggleSearch: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = GridironGeo.controlRowGap - 6.dp).padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(start = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            picker()
            SortDirectionButton(descending, statLabel, onToggleDirection)
        }
        ChipButton(onToggleSearch, description = "Search players or teams", modifier = Modifier.testTag("searchToggle")) {
            GridironChip(icon = "magnifyingglass", isActive = isSearchActive)
        }
        trailing?.invoke()
    }
}

@Composable
fun SearchRow(text: String, onTextChange: (String) -> Unit, onCancel: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchField(text, onTextChange, Modifier.weight(1f), focusOnAppear = true)
        TextAction("Cancel", GridironType.small, GridironPalette.turf, onCancel)
    }
}

/** The Advanced leaderboard (iOS `DashboardView`). */
@Composable
private fun AdvancedBoard(vm: DashboardViewModel, state: StatsBoardState) {
    val navigator = LocalNavigator.current
    val actions = LocalAppActions.current
    val store = LocalGraph.current.subscriptions
    val haptic = rememberHaptic()
    var isSearching by remember { mutableStateOf(false) }
    val activeSearch = isSearching || vm.searchText.isNotEmpty()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if ((vm.isLoading || vm.isHistoricalLoading) && vm.players.isNotEmpty()) {
                LoadingStatusBar(vm.loadingMessage, vm.loadingProgress, Modifier.padding(top = 8.dp))
            }
            GridironTabs(
                PlayerPositionGroup.entries.map { it.raw },
                vm.selectedPosition.raw,
                { raw -> PlayerPositionGroup.entries.firstOrNull { it.raw == raw }?.let { if (it != vm.selectedPosition) vm.selectedPosition = it } },
                Modifier.padding(top = 8.dp).semantics { contentDescription = "Position" },
            )
            if (vm.players.isNotEmpty()) {
                BoardControlRow(
                    picker = { StatsBoardStatPicker(vm, state) },
                    descending = vm.sortDescending,
                    statLabel = vm.currentSortMetric ?: vm.sortLabel,
                    onToggleDirection = vm::toggleSortDirection,
                    isSearchActive = activeSearch,
                    onToggleSearch = {
                        isSearching = !isSearching
                        if (!isSearching) vm.searchText = ""
                        haptic()
                    },
                    trailing = { StatsViewMenu(vm, state) },
                )
                AnimatedVisibility(activeSearch) {
                    SearchRow(vm.searchText, { vm.searchText = it }) {
                        isSearching = false
                        vm.searchText = ""
                    }
                }
            }
            RefreshableBox({ vm.load() }, Modifier.fillMaxSize()) {
                val sort = vm.currentSortMetricForDisplay
                val leaderboard = vm.leaderboard
                LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomBarPadding()) {
                    if (vm.players.isNotEmpty() && activeSearch) item { TeamResults(vm.searchedTeams.take(3)) }
                    item { Spacer(Modifier.height(8.dp)) }
                    val status = boardStatus(vm, leaderboard.isEmpty())
                    if (status != null) {
                        item { Box(Modifier.padding(horizontal = 12.dp).cardSlice(true, true)) { status() } }
                    } else {
                        item {
                            Box(Modifier.padding(horizontal = 12.dp).cardSlice(true, leaderboard.isEmpty())) {
                                LeaderboardTableHeader(vm.sortDescending, vm.currentSortMetric ?: vm.sortLabel, emptyList(), onToggleDirection = vm::toggleSortDirection)
                            }
                        }
                        itemsIndexed(leaderboard, key = { _, p -> p.id }) { index, player ->
                            Box(Modifier.padding(horizontal = 12.dp).cardSlice(false, index == leaderboard.lastIndex)) {
                                LeaderboardTableRow(
                                    rank = index + 1,
                                    player = player,
                                    metricLabel = sort.first,
                                    metricCategory = sort.second,
                                    volume = vm.volumeCaption(player, sort.second),
                                    isSmallSample = isSmallSample(vm, player, sort.first, sort.second),
                                ) { navigator.push(Route.PlayerProfile(player)) }
                            }
                        }
                    }
                    if (vm.players.isNotEmpty()) {
                        item { Spacer(Modifier.height(12.dp)) }
                        coverageNoteText(vm)?.let { note -> item { InfoNote(note) } }
                        item {
                            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                if (!store.isPro) {
                                    Row(
                                        Modifier.heightIn(min = 36.dp).clip(CircleShape).background(GridironPalette.turf)
                                            .clickable { actions.openTrialPitch(store.defaultUpgradeTrigger) }.padding(horizontal = 16.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        SfIcon("crown.fill", 12.dp, Color.White)
                                        Text(store.upgradeCTALabel, style = GridironType.micro, color = Color.White)
                                    }
                                }
                                TextAction("About StatScout", GridironType.micro, GridironPalette.inkTertiary, { navigator.push(Route.Settings) })
                            }
                        }
                    }
                }
            }
        }
        if (vm.isLoading && vm.players.isEmpty()) {
            LoadingCard(vm.loadingMessage, vm.loadingProgress, Modifier.align(Alignment.Center))
        }
    }
}

private fun isSmallSample(vm: DashboardViewModel, player: Player, label: String?, category: MetricCategory?): Boolean {
    label ?: return false
    val metric = player.metrics.firstOrNull { it.label == label && (category == null || it.category == category) } ?: return false
    return !vm.isQualified(player, metric)
}

/** The empty, error and loading states in place of the board; null when there are rows to show. */
private fun boardStatus(vm: DashboardViewModel, empty: Boolean): (@Composable () -> Unit)? {
    if (!empty) return null
    return when {
        vm.searchText.isNotEmpty() -> { { EmptyState("No players found", "magnifyingglass", "Try a different search term.", Modifier.heightIn(min = 200.dp)) } }
        vm.errorMessage != null -> {
            {
                val offline = vm.lastFailureWasConnectivity
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                EmptyState(
                    if (offline) "No connection" else "Data Error",
                    if (offline) "wifi.exclamationmark" else "exclamationmark.triangle",
                    if (offline) "The current season needs a connection for its first update. Once it has loaded, your saved stats are here offline."
                    else vm.errorMessage,
                    Modifier.heightIn(min = 200.dp),
                    actionTitle = "Retry",
                ) { scope.launch { vm.load() } }
            }
        }
        vm.isHistoricalLoading -> {
            {
                Column(Modifier.fillMaxWidth().heightIn(min = 200.dp).padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = GridironPalette.inkTertiary)
                    Text("Loading ${SeasonLabel.text(vm.selectedSeason)}…", style = GridironType.small, color = GridironPalette.inkSecondary)
                }
            }
        }
        !vm.isLoading -> {
            {
                val hasSeasonData = vm.seasonPlayers.isNotEmpty()
                val needsFirstConnection = !hasSeasonData && vm.selectedSeason == vm.freeSeason && vm.lastFetchFailed
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                EmptyState(
                    when {
                        needsFirstConnection -> "Connect to load ${SeasonLabel.text(vm.selectedSeason)}"
                        hasSeasonData -> "No matching metrics"
                        else -> "No players yet"
                    },
                    if (needsFirstConnection) "wifi.exclamationmark" else "football.fill",
                    when {
                        needsFirstConnection -> "The current season needs a connection for its first update. Once it has loaded, your saved stats are here offline."
                        hasSeasonData -> "No metrics are available for ${vm.selectedPosition.raw} in ${SeasonLabel.text(vm.selectedSeason)}."
                        else -> "No player data is available for the ${SeasonLabel.text(vm.selectedSeason)} season."
                    },
                    Modifier.heightIn(min = 200.dp),
                    actionTitle = if (needsFirstConnection) "Try Again" else null,
                    action = if (needsFirstConnection) ({ scope.launch { vm.load() } }) else null,
                )
            }
        }
        else -> { { Spacer(Modifier.height(200.dp)) } }
    }
}

/** Why a season (or the live season so far) shows fewer advanced metrics, and what the bars mean. */
private fun coverageNoteText(vm: DashboardViewModel): String? {
    val category = vm.selectedPosition.primaryCategory
    val isLive = vm.selectedSeason == vm.freeSeason && vm.selectedPhase == SeasonPhase.REGULAR
    val pending = if (isLive) MetricCoverage.pendingNote(category, vm.dataFreshness?.advancedDefenseStatus, vm.dataFreshness?.nextGenStatus) else null
    val noun = if (vm.selectedPosition == PlayerPositionGroup.DEFENSE) "defender" else vm.selectedPosition.raw
    val cohort = if (isLive) "every $noun with a line this season" else "every qualified $noun"
    val minimum = if (vm.qualifierLevel == DashboardViewModel.QualifierLevel.ALL) "Dimmed rows are under the playing-time minimum."
    else "Players under the playing-time minimum are hidden; View shows them."
    val legend = if (isLive) "Bars show the percentile among $cohort. $minimum" else "Bars show the percentile among $cohort."
    return listOfNotNull(MetricCoverage.note(vm.selectedSeason, category), pending, legend).joinToString(" ")
}

/** Clubs matching the search, above the player rows: "chiefs" usually means Kansas City. */
@Composable
private fun TeamResults(teams: List<String>) {
    if (teams.isEmpty()) return
    val navigator = LocalNavigator.current
    Column(Modifier.padding(horizontal = 12.dp).padding(top = 8.dp).gridironCard()) {
        teams.forEachIndexed { index, team ->
            if (index > 0) com.jackwallner.football.ui.components.Hairline()
            Row(
                Modifier.fillMaxWidth().heightIn(min = GridironGeo.rowHeight).clickable { navigator.push(Route.Team(team)) }
                    .padding(horizontal = GridironGeo.padCard),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(NFLTeamColor.color(team)), contentAlignment = Alignment.Center) {
                    FitText(displayTeamAbbr(team), GridironType.micro, Color.White, minScale = 0.6f)
                }
                Text(teamFullName(team), style = GridironType.bodyBold, color = GridironPalette.ink)
                Text("TEAM PAGE", style = GridironType.micro, color = GridironPalette.inkTertiary, modifier = Modifier.weight(1f))
                SfIcon("chevron.right", 14.dp, GridironPalette.inkTertiary)
            }
        }
    }
}
