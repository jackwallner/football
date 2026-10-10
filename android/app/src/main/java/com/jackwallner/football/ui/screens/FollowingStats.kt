package com.jackwallner.football.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jackwallner.football.data.DashboardViewModel
import com.jackwallner.football.model.FanStatsSelection
import com.jackwallner.football.model.Player
import com.jackwallner.football.model.StatScoutSeason
import com.jackwallner.football.model.TrendSide
import com.jackwallner.football.model.displayTeamAbbr
import com.jackwallner.football.model.teamFullName
import com.jackwallner.football.ui.LocalGraph
import com.jackwallner.football.ui.components.EmptyState
import com.jackwallner.football.ui.components.GridironMenu
import com.jackwallner.football.ui.components.GridironInlinePill
import com.jackwallner.football.ui.components.GridironSectionBar
import com.jackwallner.football.ui.components.GridironTopBar
import com.jackwallner.football.ui.components.MenuItem
import com.jackwallner.football.ui.components.MenuSection
import com.jackwallner.football.ui.components.PlayerHeadshot
import com.jackwallner.football.ui.components.RefreshableBox
import com.jackwallner.football.ui.components.SfIcon
import com.jackwallner.football.ui.components.StatScoutSheet
import com.jackwallner.football.ui.components.TeamColorDot
import com.jackwallner.football.ui.components.TextAction
import com.jackwallner.football.ui.components.bottomBarPadding
import com.jackwallner.football.ui.components.gridironCard
import com.jackwallner.football.ui.components.rememberHaptic
import com.jackwallner.football.ui.nav.LocalNavigator
import com.jackwallner.football.ui.nav.Route
import com.jackwallner.football.ui.theme.GridironGeo
import com.jackwallner.football.ui.theme.GridironPalette
import com.jackwallner.football.ui.theme.GridironType
import com.jackwallner.football.ui.components.CrownYellow

/** Your team and the players you follow, in the selected season and phase. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FollowingStatsView(vm: DashboardViewModel) {
    val favorites = LocalGraph.current.favorites
    val navigator = LocalNavigator.current
    var showingPlayers by remember { mutableStateOf(false) }
    val players = FanStatsSelection.players(favorites.playerIds, vm.seasonPlayers, vm.selectedSeason, vm.selectedPhase)
    val missing = favorites.playerIds.toSet().size - players.size
    RefreshableBox({ vm.load() }, Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), contentPadding = bottomBarPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Spacer(Modifier.height(0.dp)) }
            val team = favorites.team
            if (team != null && !StatScoutSeason.isAllTime(vm.selectedSeason)) item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(GridironPalette.surface)
                        .clickable { navigator.push(Route.Team(team)) }.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TeamColorDot(team, 12.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("YOUR TEAM", style = GridironType.micro, color = GridironPalette.inkSecondary)
                        Text(teamFullName(team), style = GridironType.bodyBold, color = GridironPalette.ink)
                    }
                    SfIcon("chevron.right", 18.dp, GridironPalette.inkTertiary)
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Your players", style = GridironType.cardTitle, color = GridironPalette.ink, modifier = Modifier.weight(1f))
                    TextAction(if (players.isEmpty()) "Follow players" else "Manage", GridironType.smallBold, GridironPalette.turf, { showingPlayers = true })
                }
            }
            if (favorites.playerIds.isEmpty()) item {
                EmptyState(
                    "Keep your players close",
                    "star",
                    "Follow your favorites for their season stats and a shortcut to every profile. Following is free.",
                    actionTitle = "Choose players",
                ) { showingPlayers = true }
            }
            itemsIndexed(players, key = { _, p -> p.playerId }) { _, player ->
                var menu by remember { mutableStateOf(false) }
                Box {
                    PlayerCard(player, Modifier.combinedClickable(onClick = { navigator.push(Route.PlayerProfile(player)) }, onLongClick = { menu = true }))
                    DropdownMenu(menu, { menu = false }, containerColor = GridironPalette.surface) {
                        DropdownMenuItem(
                            text = { Text("Unfollow ${player.name}", style = GridironType.body, color = GridironPalette.ink) },
                            leadingIcon = { SfIcon("star.slash", 18.dp, GridironPalette.inkSecondary) },
                            onClick = {
                                menu = false
                                favorites.toggleFavorite(player.playerId)
                            },
                        )
                    }
                }
            }
            if (missing > 0) item {
                Text(
                    "$missing followed ${if (missing == 1) "player has" else "players have"} no published stats for this season and phase yet. They stay on your list.",
                    style = GridironType.small,
                    color = GridironPalette.inkSecondary,
                )
            }
        }
    }
    StatScoutSheet(showingPlayers, { showingPlayers = false }) { FollowPlayersSheet(vm) { showingPlayers = false } }
}

@Composable
private fun PlayerCard(player: Player, modifier: Modifier) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(GridironPalette.surface).then(modifier).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PlayerHeadshot(player.team, player.initials, 36.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(player.name, style = GridironType.bodyBold, color = GridironPalette.ink)
                Text("${displayTeamAbbr(player.team)} · ${player.displayPosition}", style = GridironType.small, color = GridironPalette.inkSecondary)
            }
            SfIcon("chevron.right", 18.dp, GridironPalette.inkTertiary)
        }
        val stats = FanStatsSelection.summary(player)
        if (stats.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                stats.forEach { stat ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stat.value, style = GridironType.statMed, color = GridironPalette.turf)
                        Text(stat.label, style = GridironType.small, color = GridironPalette.inkSecondary)
                    }
                }
            }
        } else {
            Text("Open profile for available metrics", style = GridironType.small, color = GridironPalette.inkSecondary)
        }
    }
}

/** Pick the players you follow, in bulk. Free: following is what makes the app yours. */
@Composable
fun FollowPlayersSheet(vm: DashboardViewModel, side: TrendSide = TrendSide.QB, onDone: () -> Unit) {
    val favorites = LocalGraph.current.favorites
    val haptic = rememberHaptic()
    var searchText by remember { mutableStateOf("") }
    var listSide by remember { mutableStateOf(side) }
    val all = vm.players(vm.selectedSeason)
    val followed = favorites.playerIds.mapNotNull { id -> all.firstOrNull { it.playerId == id } }
    val query = searchText.trim().lowercase()
    val candidates = all.filter { (it.playerType ?: "") == listSide.playerType }
        .filter { query.isEmpty() || it.name.lowercase().contains(query) || it.team.lowercase().contains(query) }
        .sortedBy { it.name.lowercase() }

    @Composable
    fun FollowRow(player: Player, index: Int) {
        val following = favorites.isFavorite(player.playerId)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).background(if (index % 2 == 0) GridironPalette.surface else GridironPalette.surfaceAlt)
                .clickable {
                    favorites.toggleFavorite(player.playerId)
                    haptic()
                }
                .semantics { contentDescription = if (following) "Unfollow ${player.name}" else "Follow ${player.name}" }
                .padding(horizontal = GridironGeo.padInline),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerHeadshot(player.team, player.initials, 34.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(player.name, style = GridironType.bodyBold, color = GridironPalette.ink, maxLines = 1)
                Text("${player.team} · ${player.position}", style = GridironType.micro, color = GridironPalette.inkTertiary)
            }
            SfIcon(if (following) "star.fill" else "star", 20.dp, if (following) CrownYellow else GridironPalette.inkTertiary)
        }
    }

    Column(Modifier.fillMaxSize().background(GridironPalette.canvas)) {
        GridironTopBar(title = "Follow Players", trailing = { TextAction("Done", GridironType.smallBold, androidx.compose.ui.graphics.Color.White, onDone) })
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
            item {
                Row(
                    Modifier.padding(12.dp).fillMaxWidth().height(44.dp).clip(CircleShape).background(GridironPalette.surface)
                        .border(0.5.dp, GridironPalette.hairline, CircleShape).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SfIcon("magnifyingglass", 16.dp, GridironPalette.inkTertiary)
                    Box(Modifier.weight(1f)) {
                        if (searchText.isEmpty()) Text("Search players", style = GridironType.body, color = GridironPalette.inkTertiary)
                        BasicTextField(
                            searchText, { searchText = it }, singleLine = true,
                            textStyle = GridironType.body.copy(color = GridironPalette.ink),
                            cursorBrush = SolidColor(GridironPalette.turf),
                            modifier = Modifier.fillMaxWidth().testTag("followSearch"),
                        )
                    }
                    if (searchText.isNotEmpty()) SfIcon("xmark.circle.fill", 18.dp, GridironPalette.inkTertiary, Modifier.clickable { searchText = "" })
                }
            }
            // Always present, even at zero, so the list below never jumps under the next tap.
            item {
                Column(Modifier.padding(horizontal = 12.dp).fillMaxWidth().gridironCard()) {
                    GridironSectionBar("FOLLOWING (${followed.size})")
                    if (followed.isEmpty()) {
                        Box(Modifier.fillMaxWidth().height(GridironGeo.rowHeight), contentAlignment = Alignment.Center) {
                            Text("Nobody yet. Tap a star to follow.", style = GridironType.small, color = GridironPalette.inkSecondary)
                        }
                    } else followed.forEachIndexed { i, p -> FollowRow(p, i) }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 12.dp).padding(top = 12.dp).fillMaxWidth().gridironCard()) {
                    Box(Modifier.padding(12.dp)) {
                        GridironMenu(listOf(MenuSection(null, TrendSide.entries.map { option ->
                            MenuItem(option.label, checked = option == listSide) { listSide = option }
                        }))) { open -> GridironInlinePill(listSide.label, open, icon = "person.fill") }
                    }
                    if (candidates.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                            Text("No players match “$searchText”.", style = GridironType.small, color = GridironPalette.inkSecondary)
                        }
                    } else candidates.forEachIndexed { i, p -> FollowRow(p, i) }
                }
            }
            item { Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) }
        }
    }
}
