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


@Composable fun GamesScreen(isActive: Boolean) { HomeTopBar("Games"); Pending("Games") }
@Composable fun TeamsScreen(isActive: Boolean) { HomeTopBar("Teams"); Pending("Teams") }
@Composable fun GameDetailScreen(gameId: String) = PushedScreen("Game") { Pending(gameId) }
@Composable fun TeamScheduleScreen(team: String) = PushedScreen("Schedule") { Pending(team) }
@Composable fun TeamScreen(abbr: String) = PushedScreen(abbr) { Pending(abbr) }
@Composable fun TeamComparisonScreen(a: String, b: String) = PushedScreen("Teams") { Pending("Teams") }
@Composable fun StandingsScreen() = PushedScreen("Standings") { Pending("Standings") }
@Composable fun PowerRankingsScreen() = PushedScreen("Power") { Pending("Power") }
