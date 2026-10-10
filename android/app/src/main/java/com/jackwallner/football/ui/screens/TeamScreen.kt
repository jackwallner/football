package com.jackwallner.football.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jackwallner.football.data.DashboardViewModel
import com.jackwallner.football.data.PaywallTrigger
import com.jackwallner.football.model.MetricCategory
import com.jackwallner.football.model.Player
import com.jackwallner.football.model.PlayerPositionGroup
import com.jackwallner.football.model.RecentMetricKey
import com.jackwallner.football.model.RecentWindow
import com.jackwallner.football.model.SeasonLabel
import com.jackwallner.football.model.SeasonPhase
import com.jackwallner.football.model.nflTeamAbbreviations
import com.jackwallner.football.model.teamFullName
import com.jackwallner.football.ui.LocalAppActions
import com.jackwallner.football.ui.LocalGraph
import com.jackwallner.football.ui.components.ChipButton
import com.jackwallner.football.ui.components.ChipTrailing
import com.jackwallner.football.ui.components.DataFreshnessView
import com.jackwallner.football.ui.components.FitText
import com.jackwallner.football.ui.components.GridironChip
import com.jackwallner.football.ui.components.GridironMenu
import com.jackwallner.football.ui.components.GridironNavPill
import com.jackwallner.football.ui.components.GridironSegmented
import com.jackwallner.football.ui.components.GridironTabs
import com.jackwallner.football.ui.components.GridironTopBar
import com.jackwallner.football.ui.components.LeaderboardTableHeader
import com.jackwallner.football.ui.components.LeaderboardTableRow
import com.jackwallner.football.ui.components.MenuItem
import com.jackwallner.football.ui.components.MenuSection
import com.jackwallner.football.ui.components.SearchField
import com.jackwallner.football.ui.components.Segment
import com.jackwallner.football.ui.components.SeasonPhasePicker
import com.jackwallner.football.ui.components.SfIcon
import com.jackwallner.football.ui.components.StatGlossaryLink
import com.jackwallner.football.ui.components.TextAction
import com.jackwallner.football.ui.components.bottomBarPadding
import com.jackwallner.football.ui.components.gridironCard
import com.jackwallner.football.ui.components.rememberHaptic
import com.jackwallner.football.ui.nav.LocalNavigator
import com.jackwallner.football.ui.nav.Route
import com.jackwallner.football.ui.theme.GridironGeo
import com.jackwallner.football.ui.theme.GridironPalette
import com.jackwallner.football.ui.theme.GridironType

private enum class TeamTab(val raw: String) { ADVANCED("Advanced"), STANDARD("Standard"), ROSTER("Roster") }

private enum class RosterMode(val raw: String) { SEASON("Season"), RECENT("Recent") }

private enum class RosterSide(val raw: String) {
    OFFENSE("Offense"), DEFENSE("Defense");

    val categories: List<MetricCategory>
        get() = if (this == OFFENSE) listOf(MetricCategory.PASSING, MetricCategory.RUSHING, MetricCategory.RECEIVING) else listOf(MetricCategory.DEFENSE)

    fun matches(player: Player): Boolean =
        if (player.positionGroup == PlayerPositionGroup.DEFENSE) this == DEFENSE else this == OFFENSE
}

private fun priorityMetrics(category: MetricCategory): List<String> = when (category) {
    MetricCategory.PASSING -> listOf("Pass Yds", "Pass TD", "Rating", "EPA/Play")
    MetricCategory.RUSHING -> listOf("Rush Yds", "Rush TD", "Y/C", "Rush EPA")
    MetricCategory.RECEIVING -> listOf("Rec Yds", "Rec", "Rec TD", "YAC")
    MetricCategory.DEFENSE -> listOf("Tackles", "Sacks", "INT")
}

/** One club's page: this week's game, then Advanced, Standard and Roster tabs. */
@Composable
fun TeamScreen(abbr: String) {
    val graph = LocalGraph.current
    val vm = graph.dashboard
    val isPro = graph.subscriptions.isPro
    val navigator = LocalNavigator.current
    val actions = LocalAppActions.current
    val haptic = rememberHaptic()

    var selectedTab by rememberSaveable { mutableStateOf(TeamTab.ADVANCED) }
    var searchText by rememberSaveable { mutableStateOf("") }
    var isSearching by rememberSaveable { mutableStateOf(false) }
    // Default to Passing so the roster always shows a meaningful sort metric.
    var selectedCategory by rememberSaveable { mutableStateOf<MetricCategory?>(MetricCategory.PASSING) }
    var sortDescending by rememberSaveable { mutableStateOf(true) }
    var lastDefaultedSortKey by rememberSaveable { mutableStateOf<String?>(null) }
    var rosterSide by rememberSaveable { mutableStateOf(RosterSide.OFFENSE) }
    var qualifierLevel by rememberSaveable { mutableStateOf(DashboardViewModel.QualifierLevel.ALL) }
    var rosterMode by rememberSaveable { mutableStateOf(RosterMode.SEASON) }
    var rosterWindow by rememberSaveable { mutableStateOf(RecentWindow.FIVE) }
    var userSortLabel by rememberSaveable { mutableStateOf<String?>(null) }

    // The roster for the season and phase that are selected right now, so the
    // nav-bar picker moves the rows, cards, category filters and sort metrics
    // together instead of leaving the page on the season it was opened in.
    val players = vm.players(abbr)
    val displaySeason = vm.selectedSeason
    val leaguePlayers = vm.seasonPlayers
    val displayPhase = vm.selectedPhase

    // Recent form exists for the live season only: the rolling windows and the
    // per-game logs behind them are no longer kept for finished seasons, so every
    // Recent control on this screen is hidden (not locked) on a historical one.
    val supportsRecent = vm.supportsRecentForm(displaySeason)
    val isRosterRecent = rosterMode == RosterMode.RECENT && isPro && supportsRecent

    val availableSortLabels: List<String> = selectedCategory?.let { category ->
        val present = players.flatMap { p -> p.metrics.filter { it.category == category }.map { it.label } }.toSet()
        val ordered = category.metricPriorityOrder.filter { it in present }
        ordered + (present - category.metricPriorityOrder.toSet()).sorted()
    } ?: emptyList()

    val sortMetric: Pair<String, MetricCategory>? = selectedCategory?.let { category ->
        val user = userSortLabel
        if (user != null && user in availableSortLabels) return@let user to category
        priorityMetrics(category).firstOrNull { label ->
            players.any { p -> p.metrics.any { it.label == label && it.category == category } }
        }?.let { it to category }
    }
    val sortLabel = if (selectedCategory == null) "Overall" else sortMetric?.first ?: "Top Category"

    // "All" is the whole roster at once, so no one metric means the same thing
    // down the column; each row would show its own overall percentile instead.
    val rowLabel = sortMetric?.first
    val rowCategory = sortMetric?.second

    fun recentFormFor(player: Player) = vm.recentForm(player.playerId, rosterWindow)
    val recentKey = sortMetric?.first?.let { RecentMetricKey.key(it) }
    fun recentValue(player: Player): Double? = recentKey?.let { recentFormFor(player)?.metrics?.get(it) }
    fun recentDelta(player: Player): Double? = recentKey?.let { recentFormFor(player)?.delta?.get(it) }
    fun recentValueText(player: Player): String {
        val label = sortMetric?.first ?: return "-"
        val value = recentValue(player) ?: return "-"
        return RecentMetricKey.format(value, label)
    }
    val hasRecentData = vm.recentFormByWindow[rosterWindow.value] != null

    fun fallbackPercentile(player: Player): Int =
        selectedCategory?.let { player.percentile(it) } ?: player.overallPercentile

    fun isQualified(player: Player): Boolean = when (qualifierLevel) {
        DashboardViewModel.QualifierLevel.ALL -> true
        DashboardViewModel.QualifierLevel.QUALIFIED -> DashboardViewModel.hasQualifyingMetric(player, selectedCategory)
    }

    val filteredPlayers: List<Player> = run {
        val bySearch = if (searchText.isEmpty()) players else players.filter {
            it.name.contains(searchText, ignoreCase = true) || it.displayPosition.contains(searchText, ignoreCase = true)
        }
        val bySide = bySearch.filter { rosterSide.matches(it) }
        val byCategory = bySide.filter { player -> selectedCategory == null || player.metrics.any { it.category == selectedCategory } }
        val byQualifier = byCategory.filter { isQualified(it) }
        when {
            isRosterRecent && sortMetric != null -> byQualifier.filter { recentValue(it) != null }.sortedWith { a, b ->
                val first = recentValue(a) ?: 0.0
                val second = recentValue(b) ?: 0.0
                if (sortDescending) second.compareTo(first) else first.compareTo(second)
            }
            sortMetric == null -> byQualifier.sortedWith { a, b ->
                if (sortDescending) fallbackPercentile(b).compareTo(fallbackPercentile(a)) else fallbackPercentile(a).compareTo(fallbackPercentile(b))
            }
            else -> byQualifier.sortedWith(DashboardViewModel.metricComparator(sortMetric.first, sortMetric.second, sortDescending))
        }
    }

    // Season changes through the nav-bar menu rotate the roster data beneath us,
    // and a playoff roster carries a different set of metrics than the regular
    // season's: re-default the sort direction so the chip never displays a
    // metric the new data doesn't have.
    val sortKey = sortMetric?.let { "${it.second.raw}|${it.first}" } ?: "-"
    LaunchedEffect(sortKey) {
        if (sortKey != lastDefaultedSortKey) {
            lastDefaultedSortKey = sortKey
            sortDescending = DashboardViewModel.defaultSortDescending(sortMetric?.first, sortMetric?.second)
        }
    }

    LaunchedEffect(rosterMode, rosterWindow, displaySeason, displayPhase, isPro, vm.freshnessRevision ?: "none") {
        if (isRosterRecent) vm.loadRecentFormIfNeeded(rosterWindow)
    }

    val fetchTeamGameLogs: TeamGameLogFetcher = { team, season, phase, since -> vm.fetchTeamGameLogs(team, season, phase, since) }
    val openTeamPitch = { actions.openTrialPitch(PaywallTrigger.TeamView) }

    Column(Modifier.fillMaxSize().background(GridironPalette.canvas)) {
        GridironTopBar(
            onBack = { navigator.pop() },
            leading = { TeamSwitcher(abbr) },
            trailing = {
                SeasonPhasePicker(
                    // A single team page, so no All Time: a career row can't be attributed to one franchise.
                    seasons = vm.seasonsExcludingAllTime,
                    selectedSeason = vm.selectedSeason,
                    selectedPhase = vm.selectedPhase,
                    isSeasonLocked = vm::isSeasonLocked,
                    onSelectSeason = { season ->
                        if (vm.isSeasonLocked(season)) actions.openTrialPitch(PaywallTrigger.LockedSeason(season)) else vm.selectSeason(season)
                    },
                    onSelectPhase = { vm.selectedPhase = it },
                ) { open ->
                    // No glyph and the short year alone: the team name is long ("Jacksonville
                    // Jaguars"), and spelling the phase out as well would crowd the bar.
                    GridironNavPill(
                        title = SeasonLabel.text(vm.selectedSeason) + if (vm.selectedPhase == SeasonPhase.PLAYOFFS) " · Playoffs" else "",
                        onClick = open,
                        description = "Season and season type, ${SeasonLabel.text(vm.selectedSeason)}, ${vm.selectedPhase.label}",
                    )
                }
            },
        )
        GamesTeamsUi.RefreshBox(onRefresh = { vm.load() }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                DataFreshnessView(vm, Modifier.padding(horizontal = 12.dp).padding(top = 10.dp), season = displaySeason, phase = displayPhase)
                if (displaySeason == vm.freeSeason) {
                    Box(Modifier.padding(horizontal = 12.dp).padding(top = 10.dp)) { TeamWeekGameCard(vm, abbr) }
                }
                TabSelector(selectedTab, Modifier.padding(horizontal = 12.dp).padding(top = 12.dp)) { selectedTab = it }

                when (selectedTab) {
                    TeamTab.ADVANCED -> Column(Modifier.padding(horizontal = 12.dp).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TeamRankingsCard(
                            team = abbr,
                            season = displaySeason,
                            players = players,
                            leaguePlayers = leaguePlayers,
                            fetchTeamGameLogs = fetchTeamGameLogs,
                            // Explicit tap, always answer it; the gate only caps automatic pop-ups.
                            onUpgradeTap = openTeamPitch,
                            seasonPhase = displayPhase,
                            freshnessRevision = vm.freshnessRevision,
                            freshnessStatus = vm.freshnessStatus,
                            supportsRecent = supportsRecent,
                        )
                    }
                    TeamTab.STANDARD -> Box(Modifier.padding(horizontal = 12.dp).padding(top = 12.dp)) {
                        TeamStandardCard(
                            team = abbr,
                            season = displaySeason,
                            players = players,
                            leaguePlayers = leaguePlayers,
                            fetchTeamGameLogs = fetchTeamGameLogs,
                            onUpgradeTap = openTeamPitch,
                            seasonPhase = displayPhase,
                            supportsRecent = supportsRecent,
                        )
                    }
                    TeamTab.ROSTER -> Column {
                        GamesTeamsUi.PickerRow(
                            groups = listOfNotNull(
                                RosterSide.entries.size to {
                                    GridironSegmented(
                                        RosterSide.entries.map { Segment(it, it.raw) },
                                        rosterSide,
                                        { side ->
                                            rosterSide = side
                                            selectedCategory = side.categories[0]
                                            userSortLabel = null
                                        },
                                    )
                                },
                                if (supportsRecent) RosterMode.entries.size to {
                                    GridironSegmented(
                                        RosterMode.entries.map { Segment(it, it.raw, isLocked = !isPro && it == RosterMode.RECENT) },
                                        rosterMode,
                                        { rosterMode = it },
                                        onLockedTap = { actions.openTrialPitch(PaywallTrigger.RecentForm) },
                                    )
                                } else null,
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp).padding(top = 12.dp),
                        )

                        if (isRosterRecent) {
                            GridironSegmented(
                                RecentWindow.entries.map { Segment(it, it.segmentLabel) },
                                rosterWindow,
                                { rosterWindow = it },
                                Modifier.padding(horizontal = 12.dp).padding(top = 8.dp),
                            )
                        }

                        if (rosterSide.categories.size > 1) {
                            GridironTabs(
                                tabs = rosterSide.categories.map { it.raw },
                                selected = (selectedCategory ?: rosterSide.categories[0]).raw,
                                onSelect = { raw ->
                                    selectedCategory = MetricCategory.entries.firstOrNull { it.raw == raw }
                                    userSortLabel = null
                                },
                                modifier = Modifier.padding(horizontal = 12.dp).padding(top = 8.dp),
                            )
                        }

                        if (players.isNotEmpty()) {
                            SortControlsRow(
                                sortLabel = sortLabel,
                                sortDescending = sortDescending,
                                searchActive = isSearching || searchText.isNotEmpty(),
                                qualifierLevel = qualifierLevel,
                                sortLabels = availableSortLabels,
                                currentSortLabel = sortMetric?.first,
                                onFlip = {
                                    sortDescending = !sortDescending
                                    haptic()
                                },
                                onToggleSearch = {
                                    isSearching = !isSearching
                                    if (!isSearching) searchText = ""
                                    haptic()
                                },
                                onPickLabel = {
                                    userSortLabel = it
                                    haptic()
                                },
                                onPickQualifier = {
                                    qualifierLevel = it
                                    haptic()
                                },
                                onDirection = { sortDescending = it },
                            )
                            if (isSearching || searchText.isNotEmpty()) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SearchField(searchText, { searchText = it }, Modifier.weight(1f), focusOnAppear = true)
                                    TextAction("Cancel", GridironType.small, GridironPalette.turf, {
                                        isSearching = false
                                        searchText = ""
                                    })
                                }
                            }
                        }

                        RosterSection(
                            abbr = abbr,
                            season = displaySeason,
                            players = players,
                            filteredPlayers = filteredPlayers,
                            noCategoryMatch = searchText.isEmpty() && selectedCategory != null,
                            isRosterRecent = isRosterRecent,
                            hasRecentData = hasRecentData,
                            rosterWindow = rosterWindow,
                            sortLabel = sortLabel,
                            sortDescending = sortDescending,
                            sortLabels = availableSortLabels,
                            rowLabel = rowLabel,
                            rowCategory = rowCategory,
                            onToggleDirection = {
                                sortDescending = !sortDescending
                                haptic()
                            },
                            onPickLabel = { userSortLabel = it },
                            recentDelta = ::recentDelta,
                            recentValueText = ::recentValueText,
                        )
                    }
                }

                Box(Modifier.padding(horizontal = 12.dp).padding(top = 12.dp)) { StatGlossaryLink() }
                // Lets content scroll under the floating tab bar.
                Spacer(Modifier.padding(bottomBarPadding()))
            }
        }
    }
}

/**
 * The team name in the bar doubles as a switcher menu: tap to jump to any other
 * team without popping back to the Teams list.
 */
@Composable
private fun TeamSwitcher(team: String) {
    val navigator = LocalNavigator.current
    val teams = nflTeamAbbreviations.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { teamFullName(it) })
    GridironMenu(
        sections = listOf(
            MenuSection(null, teams.map { abbr -> MenuItem(teamFullName(abbr), checked = abbr == team) { navigator.push(Route.Team(abbr)) } }),
        ),
    ) { open ->
        Row(
            Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(GridironGeo.radiusCard)).clickable(onClick = open)
                .semantics { contentDescription = "Team, ${teamFullName(team)}. Switch to another team" }
                .testTag("teamSwitcher"),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FitText(teamFullName(team), GridironType.bodyBold, Color.White, Modifier.widthIn(max = 170.dp), minScale = 0.8f)
            SfIcon("chevron.down", 14.dp, Color.White.copy(alpha = 0.85f))
        }
    }
}

/**
 * Mirrors the player profile's tab selector with equal-width turf controls that
 * swap the card content below: the team's percentile profile, its traditional
 * line, and its sortable roster.
 */
@Composable
private fun TabSelector(selected: TeamTab, modifier: Modifier = Modifier, onSelect: (TeamTab) -> Unit) {
    val haptic = rememberHaptic()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TeamTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Box(
                Modifier.weight(1f).height(44.dp)
                    .clip(RoundedCornerShape(GridironGeo.radiusCard))
                    .background(if (isSelected) GridironPalette.turf else GridironPalette.surface)
                    .clickable {
                        onSelect(tab)
                        haptic()
                    }
                    .semantics {
                        this.selected = isSelected
                        role = Role.Tab
                    }
                    .testTag("teamTab_${tab.raw}"),
                contentAlignment = Alignment.Center,
            ) {
                FitText(
                    tab.raw, GridironType.smallBold, if (isSelected) Color.White else GridironPalette.ink,
                    Modifier.padding(horizontal = 4.dp), minScale = 0.85f, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Mirrors the Stats tab's sort UI: a chip showing the active metric (tap flips
 * direction), and a magnifying-glass toggle that expands an inline search row.
 */
@Composable
private fun SortControlsRow(
    sortLabel: String,
    sortDescending: Boolean,
    searchActive: Boolean,
    qualifierLevel: DashboardViewModel.QualifierLevel,
    sortLabels: List<String>,
    currentSortLabel: String?,
    onFlip: () -> Unit,
    onToggleSearch: () -> Unit,
    onPickLabel: (String) -> Unit,
    onPickQualifier: (DashboardViewModel.QualifierLevel) -> Unit,
    onDirection: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.heightIn(min = 44.dp).clip(CircleShape).clickable(onClick = onFlip)
                .semantics { contentDescription = "Sorted by $sortLabel, ${if (sortDescending) "highest first" else "lowest first"}. Tap to flip sort direction" }
                .testTag("sortChip"),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier.height(30.dp).clip(CircleShape).background(GridironPalette.surface)
                    .border(0.5.dp, GridironPalette.hairline, CircleShape).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(sortLabel, style = GridironType.smallBold, color = GridironPalette.ink, maxLines = 1)
                SfIcon(if (sortDescending) "arrow.down" else "arrow.up", 12.dp, GridironPalette.turf)
            }
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onToggleSearch)
                .semantics { contentDescription = "Search" }.testTag("rosterSearch"),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(30.dp).clip(CircleShape)
                    .background(if (searchActive) GridironPalette.turf else GridironPalette.surface)
                    .border(0.5.dp, if (searchActive) Color.Transparent else GridironPalette.hairline, CircleShape),
                contentAlignment = Alignment.Center,
            ) { SfIcon("magnifyingglass", 16.dp, if (searchActive) Color.White else GridironPalette.inkSecondary) }
        }
        GridironMenu(
            sections = buildList {
                if (sortLabels.isNotEmpty()) {
                    add(MenuSection("Sort by", sortLabels.map { label -> MenuItem(label, checked = label == currentSortLabel) { onPickLabel(label) } }))
                }
                add(
                    MenuSection(
                        "Minimum playing time",
                        DashboardViewModel.QualifierLevel.entries.map { level ->
                            MenuItem("${level.label} · ${level.description}", checked = level == qualifierLevel) { onPickQualifier(level) }
                        },
                    ),
                )
                add(
                    MenuSection(
                        "Direction",
                        listOf(
                            MenuItem("Highest first", checked = sortDescending, icon = "arrow.down") { onDirection(true) },
                            MenuItem("Lowest first", checked = !sortDescending, icon = "arrow.up") { onDirection(false) },
                        ),
                    ),
                )
            },
        ) { open ->
            ChipButton(open, description = "Filters", modifier = Modifier.testTag("rosterFilters")) {
                GridironChip(
                    title = "Filters",
                    icon = "line.3.horizontal.decrease.circle",
                    trailing = ChipTrailing.Chevron,
                    isActive = qualifierLevel != DashboardViewModel.QualifierLevel.ALL,
                )
            }
        }
    }
}

@Composable
private fun RosterSection(
    abbr: String,
    season: Int,
    players: List<Player>,
    filteredPlayers: List<Player>,
    noCategoryMatch: Boolean,
    isRosterRecent: Boolean,
    hasRecentData: Boolean,
    rosterWindow: RecentWindow,
    sortLabel: String,
    sortDescending: Boolean,
    sortLabels: List<String>,
    rowLabel: String?,
    rowCategory: MetricCategory?,
    onToggleDirection: () -> Unit,
    onPickLabel: (String) -> Unit,
    recentDelta: (Player) -> Double?,
    recentValueText: (Player) -> String,
) {
    val navigator = LocalNavigator.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 12.dp).gridironCard()) {
        when {
            players.isEmpty() -> GamesTeamsUi.Unavailable(
                "person.2.slash", "No players tracked",
                "No players are tracked for ${teamFullName(abbr)} in the $season season.",
                Modifier.padding(vertical = 48.dp),
            )
            filteredPlayers.isEmpty() -> GamesTeamsUi.Unavailable(
                "magnifyingglass", "No players found",
                if (noCategoryMatch) "No players match the selected category for this team." else "Try a different search term.",
                Modifier.padding(vertical = 48.dp),
            )
            isRosterRecent && !hasRecentData -> GamesTeamsUi.InlineSpinner("Loading the last ${rosterWindow.value} games…", Modifier.padding(vertical = 8.dp))
            else -> {
                LeaderboardTableHeader(
                    sortDescending = sortDescending,
                    sortLabel = if (isRosterRecent) "$sortLabel · ${rosterWindow.value}G" else sortLabel,
                    metrics = sortLabels,
                    onSelectMetric = onPickLabel,
                    onToggleDirection = onToggleDirection,
                )
                filteredPlayers.forEachIndexed { index, player ->
                    LeaderboardTableRow(
                        rank = index + 1,
                        player = player,
                        metricLabel = rowLabel,
                        metricCategory = rowCategory,
                        trendDelta = if (isRosterRecent) recentDelta(player) else null,
                        trendDecimals = rowLabel?.let { RecentMetricKey.decimals(it) } ?: 3,
                        valueOverride = if (isRosterRecent) recentValueText(player) else null,
                        onClick = { navigator.push(Route.PlayerProfile(player)) },
                    )
                }
            }
        }
    }
}
