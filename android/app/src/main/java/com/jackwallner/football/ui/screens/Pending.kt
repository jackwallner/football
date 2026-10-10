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

@Composable private fun Pending(name: String) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(name) }

@Composable fun StatsScreen() { HomeTopBar("Stats"); Pending("Stats") }
@Composable fun GamesScreen(isActive: Boolean) { HomeTopBar("Games"); Pending("Games") }
@Composable fun TrendsScreen(isActive: Boolean) { HomeTopBar("Trends"); Pending("Trends") }
@Composable fun TeamsScreen(isActive: Boolean) { HomeTopBar("Teams"); Pending("Teams") }
@Composable fun CompareScreen(isActive: Boolean) { HomeTopBar("Compare"); Pending("Compare") }
@Composable fun PlayerProfileRoute(player: Player) = PushedScreen(player.name) { Pending("Profile") }
@Composable fun GameDetailScreen(gameId: String) = PushedScreen("Game") { Pending(gameId) }
@Composable fun TeamScheduleScreen(team: String) = PushedScreen("Schedule") { Pending(team) }
@Composable fun TeamScreen(abbr: String) = PushedScreen(abbr) { Pending(abbr) }
@Composable fun MetricRankingRoute(route: Route.Metric) = PushedScreen(route.label) { Pending(route.label) }
@Composable fun StandardStatRoute(route: Route.StandardStat) = PushedScreen(route.stat) { Pending(route.stat) }
@Composable fun PlayerComparisonScreen(a: Player, b: Player) = PushedScreen("Compare") { Pending("H2H") }
@Composable fun YearComparisonScreen(player: Player) = PushedScreen("Year") { Pending("Year") }
@Composable fun TeamComparisonScreen(a: String, b: String) = PushedScreen("Teams") { Pending("Teams") }
@Composable fun StandingsScreen() = PushedScreen("Standings") { Pending("Standings") }
@Composable fun PowerRankingsScreen() = PushedScreen("Power") { Pending("Power") }
@Composable fun SettingsScreen() = PushedScreen("Settings") { Pending("Settings") }
@Composable fun StatGlossaryScreen() = PushedScreen("Glossary") { Pending("Glossary") }
@Composable fun FollowingStatsScreen() = PushedScreen("Following") { Pending("Following") }
@Composable fun FeedbackSheet(onDismiss: () -> Unit) = Pending("Feedback")
@Composable fun ConfigMissingScreen() = Pending("Config missing")
