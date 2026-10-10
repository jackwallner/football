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

@Composable fun StatsScreen() { HomeTopBar("Stats"); Pending("Stats") }
@Composable fun TrendsScreen(isActive: Boolean) { HomeTopBar("Trends"); Pending("Trends") }
@Composable fun CompareScreen(isActive: Boolean) { HomeTopBar("Compare"); Pending("Compare") }
@Composable fun PlayerProfileRoute(player: Player) = PushedScreen(player.name) { Pending("Profile") }
@Composable fun MetricRankingRoute(route: Route.Metric) = PushedScreen(route.label) { Pending(route.label) }
@Composable fun StandardStatRoute(route: Route.StandardStat) = PushedScreen(route.stat) { Pending(route.stat) }
@Composable fun PlayerComparisonScreen(a: Player, b: Player) = PushedScreen("Compare") { Pending("H2H") }
@Composable fun YearComparisonScreen(player: Player) = PushedScreen("Year") { Pending("Year") }
@Composable fun SettingsScreen() = PushedScreen("Settings") { Pending("Settings") }
@Composable fun StatGlossaryScreen() = PushedScreen("Glossary") { Pending("Glossary") }
@Composable fun FollowingStatsScreen() = PushedScreen("Following") { Pending("Following") }
@Composable fun FeedbackSheet(onDismiss: () -> Unit) = Pending("Feedback")
@Composable fun ConfigMissingScreen() = Pending("Config missing")
