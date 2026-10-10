package com.jackwallner.football.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.jackwallner.football.model.Player
import com.jackwallner.football.ui.HomeTopBar
import com.jackwallner.football.ui.components.PushedScreen
import com.jackwallner.football.ui.nav.Route

@Composable internal fun Pending(name: String) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(name) }

@Composable fun FollowingStatsScreen() = PushedScreen("Following") { FollowingStatsView(com.jackwallner.football.ui.LocalGraph.current.dashboard) }
@Composable fun YearComparisonScreen(player: Player) { val vm = com.jackwallner.football.ui.LocalGraph.current.dashboard; PushedScreen(player.name) { YearComparisonView(vm.playerHistories[player.playerId].orEmpty().filter { it.seasonPhase == player.seasonPhase }) } }
