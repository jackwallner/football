package com.jackwallner.football.ui.screens

import androidx.compose.runtime.Composable
import com.jackwallner.football.data.DashboardViewModel
import com.jackwallner.football.model.Player
import com.jackwallner.football.model.SeasonPhase

// Temporary: replaced by GameCards.kt when the Games port lands.
@Composable fun PlayerLastGameCard(viewModel: DashboardViewModel, player: Player, season: Int, phase: SeasonPhase) = Unit
@Composable fun PlayerGameLogCard(viewModel: DashboardViewModel, player: Player, season: Int, phase: SeasonPhase) = Unit
