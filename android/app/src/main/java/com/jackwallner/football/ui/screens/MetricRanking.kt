package com.jackwallner.football.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jackwallner.football.data.DashboardViewModel
import com.jackwallner.football.model.Player
import com.jackwallner.football.model.SeasonLabel
import com.jackwallner.football.ui.LocalGraph
import com.jackwallner.football.ui.components.EmptyState
import com.jackwallner.football.ui.components.GridironSectionBar
import com.jackwallner.football.ui.components.LeaderboardTableHeader
import com.jackwallner.football.ui.components.LeaderboardTableRow
import com.jackwallner.football.ui.components.PushedScreen
import com.jackwallner.football.ui.components.SfIcon
import com.jackwallner.football.ui.components.bottomBarPadding
import com.jackwallner.football.ui.components.cardSlice
import com.jackwallner.football.ui.components.rememberHaptic
import com.jackwallner.football.ui.nav.LocalNavigator
import com.jackwallner.football.ui.nav.Route
import com.jackwallner.football.ui.theme.GridironPalette
import com.jackwallner.football.ui.theme.GridironType

/** One metric's league ranking, for the season and phase the drill-down carries. */
@Composable
fun MetricRankingRoute(route: Route.Metric) {
    val vm = LocalGraph.current.dashboard
    val navigator = LocalNavigator.current
    val haptic = rememberHaptic()
    val season = route.season ?: vm.selectedSeason
    val phase = route.phase ?: vm.selectedPhase
    val label = route.label
    val category = route.category
    var descending by rememberSaveable { mutableStateOf(DashboardViewModel.defaultSortDescending(label, category)) }
    val players = vm.players(season, phase)
    fun isSmallSample(player: Player): Boolean {
        val metric = player.metrics.firstOrNull { it.label == label && it.category == category } ?: return false
        return !vm.isQualified(player, metric)
    }
    val sorted = players.filter { p -> p.metrics.any { it.label == label && it.category == category } }
        .sortedWith(DashboardViewModel.metricComparator(label, category, descending))
    // Same rule as the Stats board: small samples stay listed, below.
    val ranked = sorted.filterNot(::isSmallSample) + sorted.filter(::isSmallSample)

    PushedScreen("$label · ${category.raw}") {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomBarPadding()) {
            item { Spacer(Modifier.height(12.dp)) }
            item {
                Box(Modifier.padding(horizontal = 12.dp).cardSlice(true, ranked.isEmpty())) {
                    GridironSectionBar("$label · ${category.raw}", trailing = {
                        Text(SeasonLabel.text(season), style = GridironType.micro, color = GridironPalette.inkSecondary)
                        Row(
                            Modifier.padding(start = 12.dp).heightIn(min = 28.dp).clickable {
                                descending = !descending
                                haptic()
                            },
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label, style = GridironType.micro, color = GridironPalette.inkSecondary)
                            SfIcon(if (descending) "arrow.down" else "arrow.up", 11.dp, GridironPalette.inkSecondary)
                        }
                    })
                }
            }
            if (ranked.isEmpty()) {
                item {
                    Box(Modifier.padding(horizontal = 12.dp).cardSlice(false, true).padding(vertical = 12.dp)) {
                        EmptyState("No rankings found", "chart.bar", "No players have the $label metric for this season.")
                    }
                }
            } else {
                item { Box(Modifier.padding(horizontal = 12.dp).cardSlice(false, false)) { LeaderboardTableHeader(descending, label) { descending = !descending } } }
                itemsIndexed(ranked, key = { _, p -> p.id }) { index, player ->
                    Box(Modifier.padding(horizontal = 12.dp).cardSlice(false, index == ranked.lastIndex)) {
                        LeaderboardTableRow(
                            rank = index + 1,
                            player = player,
                            metricLabel = label,
                            metricCategory = category,
                            volume = vm.volumeCaption(player, category),
                            isSmallSample = isSmallSample(player),
                        ) { navigator.push(Route.PlayerProfile(player)) }
                    }
                }
            }
        }
    }
}
