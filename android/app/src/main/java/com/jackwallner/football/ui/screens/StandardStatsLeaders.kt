package com.jackwallner.football.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jackwallner.football.data.DashboardViewModel
import com.jackwallner.football.model.MetricCategory
import com.jackwallner.football.model.MetricCoverage
import com.jackwallner.football.model.Player
import com.jackwallner.football.model.PlayerPositionGroup
import com.jackwallner.football.model.SeasonLabel
import com.jackwallner.football.model.SeasonPhase
import com.jackwallner.football.model.StandardStat
import com.jackwallner.football.model.StandardStatSemantics
import com.jackwallner.football.model.displayTeamAbbr
import com.jackwallner.football.model.metricNumericValue
import com.jackwallner.football.model.ordinal
import com.jackwallner.football.model.teamFullName
import com.jackwallner.football.ui.LocalGraph
import com.jackwallner.football.ui.components.EmptyState
import com.jackwallner.football.ui.components.FitText
import com.jackwallner.football.ui.components.GridironTabs
import com.jackwallner.football.ui.components.InfoNote
import com.jackwallner.football.ui.components.PercentileBarMini
import com.jackwallner.football.ui.components.PlayerHeadshot
import com.jackwallner.football.ui.components.PushedScreen
import com.jackwallner.football.ui.components.RefreshableBox
import com.jackwallner.football.ui.components.SfIcon
import com.jackwallner.football.ui.components.StatOption
import com.jackwallner.football.ui.components.StatPickerMenu
import com.jackwallner.football.ui.components.TeamColorDot
import com.jackwallner.football.ui.components.bottomBarPadding
import com.jackwallner.football.ui.components.bottomHairline
import com.jackwallner.football.ui.components.cardSlice
import com.jackwallner.football.ui.components.rememberHaptic
import com.jackwallner.football.ui.nav.LocalNavigator
import com.jackwallner.football.ui.nav.Route
import com.jackwallner.football.ui.theme.GridironGeo
import com.jackwallner.football.ui.theme.GridironPalette
import com.jackwallner.football.ui.theme.GridironType

enum class StandardStatCategory(val raw: String) {
    PASSING("Passing"), RUSHING("Rushing"), RECEIVING("Receiving"), DEFENSE("Defense");

    val metricCategory: MetricCategory
        get() = when (this) {
            PASSING -> MetricCategory.PASSING
            RUSHING -> MetricCategory.RUSHING
            RECEIVING -> MetricCategory.RECEIVING
            DEFENSE -> MetricCategory.DEFENSE
        }

    val defaultPosition: PlayerPositionGroup
        get() = when (this) {
            PASSING -> PlayerPositionGroup.QB
            RUSHING -> PlayerPositionGroup.RB
            RECEIVING -> PlayerPositionGroup.WR
            DEFENSE -> PlayerPositionGroup.DEFENSE
        }

    companion object {
        fun from(raw: String): StandardStatCategory = entries.firstOrNull { it.raw == raw } ?: PASSING
    }
}

/**
 * Traditional leaderboard with the same position tabs and control vocabulary
 * as the Advanced board. [viewModel] and [boardState] are present on the Stats
 * tab and absent on a drill-down.
 */
@Composable
fun StandardStatsLeadersView(
    players: List<Player>,
    selectedStat: String,
    onSelectStat: (String) -> Unit,
    selectedPosition: PlayerPositionGroup,
    onSelectPosition: (PlayerPositionGroup) -> Unit,
    sortDescending: Boolean,
    onSortDescending: (Boolean) -> Unit,
    boardState: StatsBoardState? = null,
    viewModel: DashboardViewModel? = null,
) {
    val navigator = LocalNavigator.current
    val haptic = rememberHaptic()
    var isSearching by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }

    // A stat the new position does not offer falls back to its default.
    LaunchedEffect(selectedPosition) {
        if (selectedStat !in StandardStatCatalog.stats(selectedPosition)) {
            val next = StandardStatCatalog.defaultStat(selectedPosition)
            onSelectStat(next)
            onSortDescending(StandardStatCatalog.defaultDescending(next, selectedPosition))
        }
    }

    fun standardStat(player: Player): StandardStat? = player.standardStats?.firstOrNull { it.label.equals(selectedStat, ignoreCase = true) }
    fun numericStat(player: Player): Double? = standardStat(player)?.let { StandardStatSemantics.numericValue(it.label, it.value) }
    fun games(player: Player): Double = player.standardStats?.firstOrNull { it.label == "G" }?.value?.let(::metricNumericValue) ?: 0.0

    val filtered = players.filter { it.positionGroup == selectedPosition && numericStat(it) != null }
    val sorted = filtered.sortedWith { a, b ->
        val av = numericStat(a) ?: 0.0
        val bv = numericStat(b) ?: 0.0
        if (av != bv) (if (sortDescending) bv.compareTo(av) else av.compareTo(bv)) else games(b).compareTo(games(a))
    }
    val sampleLabel = when {
        viewModel == null -> "PLAYER"
        viewModel.qualifierLevel == DashboardViewModel.QualifierLevel.QUALIFIED -> "QUALIFIED PLAYERS"
        else -> "ALL PLAYERS"
    }
    val pendingNote = viewModel?.takeIf { it.selectedSeason == it.freeSeason && it.selectedPhase == SeasonPhase.REGULAR }?.let {
        MetricCoverage.pendingNote(selectedPosition.primaryCategory, it.dataFreshness?.advancedDefenseStatus, null)
    }

    fun volumeText(player: Player): String? {
        val stats = player.standardStats.orEmpty()
        fun value(label: String) = stats.firstOrNull { it.label.equals(label, ignoreCase = true) }?.value
        fun denominator(pair: String?) = pair?.split("/")?.lastOrNull()
        val stat = selectedStat.uppercase()
        if (stat.startsWith("PASS") || stat == "INT" || stat == "CMP/ATT" || stat == "RATING" || stat == "Y/A") {
            return denominator(value("Cmp/Att"))?.let { "$it att" }
        }
        if (stat.startsWith("RUSH") || stat == "Y/C") return value("Car")?.let { "$it car" }
        if (stat.startsWith("REC")) return denominator(value("Rec/Tgt"))?.let { "$it tgt" }
        if (selectedPosition == PlayerPositionGroup.DEFENSE && viewModel != null) {
            viewModel.volumeCaption(player, MetricCategory.DEFENSE)?.takeIf { it.endsWith("snaps") }?.let { return it }
        }
        if (stat == "G") return null
        val g = value("G") ?: return null
        return if (g == "1") "1 game" else "$g games"
    }

    Column(Modifier.fillMaxSize().background(GridironPalette.canvas)) {
        GridironTabs(
            PlayerPositionGroup.entries.map { it.raw },
            selectedPosition.raw,
            { raw -> PlayerPositionGroup.entries.firstOrNull { it.raw == raw }?.let(onSelectPosition) },
            Modifier.padding(top = 8.dp).semantics { contentDescription = "Position" },
        )
        BoardControlRow(
            picker = {
                if (boardState != null && viewModel != null) StatsBoardStatPicker(viewModel, boardState)
                else StatPickerMenu(
                    activeLabel = selectedStat,
                    standard = StandardStatCatalog.stats(selectedPosition).map { StatOption(it, it, it == selectedStat) },
                    onSelectStandard = {
                        onSelectStat(it.id)
                        onSortDescending(StandardStatCatalog.defaultDescending(it.id, selectedPosition))
                    },
                )
            },
            descending = sortDescending,
            statLabel = selectedStat,
            onToggleDirection = { onSortDescending(!sortDescending) },
            isSearchActive = isSearching,
            onToggleSearch = {
                isSearching = !isSearching
                if (!isSearching) searchText = ""
            },
            trailing = if (boardState != null && viewModel != null) ({ StatsViewMenu(viewModel, boardState) }) else null,
        )
        AnimatedVisibility(isSearching) {
            SearchRow(searchText, { searchText = it }) {
                isSearching = false
                searchText = ""
            }
        }
        RefreshableBox({ viewModel?.load() }, Modifier.fillMaxSize()) {
            val query = searchText.trim()
            val ranked = sorted.withIndex().filter { (_, p) ->
                query.isEmpty() || p.name.contains(query, true) || p.team.contains(query, true) || teamFullName(p.team).contains(query, true)
            }
            val peerValues = filtered.mapNotNull { standardStat(it)?.value }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomBarPadding()) {
                item { Spacer(Modifier.height(8.dp)) }
                item {
                    Row(
                        Modifier.padding(horizontal = 12.dp).cardSlice(first = true, last = false).fillMaxWidth().height(GridironGeo.rowHeightHeader)
                            .background(GridironPalette.surfaceAlt).bottomHairline()
                            .clickable {
                                onSortDescending(!sortDescending)
                                haptic()
                            }
                            .padding(horizontal = GridironGeo.padInline),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("RANK", style = GridironType.micro, color = GridironPalette.inkTertiary, modifier = Modifier.width(42.dp))
                        FitText(sampleLabel, GridironType.micro, GridironPalette.inkTertiary, Modifier.weight(1f), minScale = 0.75f)
                        Text("TEAM", style = GridironType.micro, color = GridironPalette.inkTertiary, modifier = Modifier.width(44.dp))
                        Row(Modifier.width(100.dp), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                            FitText(selectedStat.uppercase(), GridironType.micro, GridironPalette.turf, minScale = 0.7f)
                            SfIcon(if (sortDescending) "arrow.down" else "arrow.up", 10.dp, GridironPalette.turf)
                        }
                    }
                }
                when {
                    viewModel?.isLoading == true && players.isEmpty() -> item {
                        Box(Modifier.padding(horizontal = 12.dp).cardSlice(false, true).fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(color = GridironPalette.inkTertiary)
                                Text("Loading player stats", style = GridironType.small, color = GridironPalette.inkSecondary)
                            }
                        }
                    }
                    sorted.isEmpty() -> item {
                        Box(Modifier.padding(horizontal = 12.dp).cardSlice(false, true).padding(vertical = 24.dp)) {
                            EmptyState("No data available", "chart.bar", "No ${selectedPosition.raw} players have $selectedStat data for this season.")
                        }
                    }
                    else -> {
                        if (ranked.isEmpty()) item {
                            Box(Modifier.padding(horizontal = 12.dp).cardSlice(false, true)) {
                                EmptyState("No Results for “$searchText”", "magnifyingglass", "Check the spelling or try a new search.")
                            }
                        }
                        itemsIndexed(ranked, key = { _, it -> it.value.id }) { position, (index, player) ->
                            val rank = index + 1
                            val stat = standardStat(player)
                            val pct = stat?.let { StandardStatSemantics.percentile(it.label, it.value, peerValues) } ?: 50
                            // A zero count has no honest rank; a passer's 0 INT is the best line, not an absence.
                            val isZero = numericStat(player) == 0.0 && StandardStatSemantics.higherIsBetter(selectedStat)
                            val display = stat?.value ?: "-"
                            Row(
                                Modifier.padding(horizontal = 12.dp).cardSlice(false, position == ranked.lastIndex).fillMaxWidth().height(52.dp)
                                    .background(if (rank % 2 == 0) GridironPalette.surfaceAlt else GridironPalette.surface)
                                    .bottomHairline()
                                    .clickable { navigator.push(Route.PlayerProfile(player)) }
                                    .padding(horizontal = GridironGeo.padInline)
                                    .testTag("standardRow"),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("$rank", style = GridironType.statSmall, color = GridironPalette.inkSecondary, modifier = Modifier.width(36.dp))
                                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    PlayerHeadshot(player.team, player.initials, 36.dp)
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        FitText(player.name, GridironType.bodyBold, GridironPalette.ink, minScale = 0.85f)
                                        Text(listOfNotNull(player.displayPosition, volumeText(player)).joinToString(" · "), style = GridironType.micro, color = GridironPalette.inkTertiary, maxLines = 1)
                                    }
                                }
                                Row(Modifier.width(44.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    TeamColorDot(player.team, 6.dp)
                                    Text(displayTeamAbbr(player.team), style = GridironType.small, color = GridironPalette.inkSecondary, maxLines = 1)
                                }
                                Row(
                                    Modifier.clearAndSetSemantics {
                                        contentDescription = if (isZero) "$selectedStat: $display, not ranked" else "$selectedStat: $display, ${pct.ordinal} percentile"
                                    },
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (isZero) Spacer(Modifier.width(34.dp)) else PercentileBarMini(pct, Modifier.width(34.dp))
                                    FitText(display, GridironType.statMed, if (isZero) GridironPalette.inkTertiary else GridironPalette.turf, Modifier.width(58.dp), minScale = 0.7f, textAlign = TextAlign.End)
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
                pendingNote?.let { note -> item { InfoNote(note, Modifier.padding(horizontal = 4.dp)) } }
            }
        }
    }
}

/** Standalone traditional-stat drill-down reached from a player or team page. */
@Composable
fun StandardStatRoute(route: Route.StandardStat) {
    val vm = LocalGraph.current.dashboard
    val season = route.season ?: vm.selectedSeason
    val phase = route.phase ?: vm.selectedPhase
    val category = StandardStatCategory.from(route.category)
    var stat by rememberSaveable { mutableStateOf(route.stat) }
    var position by remember { mutableStateOf(category.defaultPosition) }
    var descending by rememberSaveable { mutableStateOf(StandardStatCatalog.defaultDescending(route.stat, category.defaultPosition)) }
    val title = route.stat + " · " + if (phase == SeasonPhase.REGULAR) SeasonLabel.text(season) else SeasonLabel.text(season, phase)
    PushedScreen(title) {
        StandardStatsLeadersView(
            players = vm.players(season, phase),
            selectedStat = stat,
            onSelectStat = { stat = it },
            selectedPosition = position,
            onSelectPosition = { position = it },
            sortDescending = descending,
            onSortDescending = { descending = it },
        )
    }
}
