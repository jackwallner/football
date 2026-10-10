package com.jackwallner.football.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jackwallner.football.data.DashboardViewModel
import com.jackwallner.football.data.PaywallTrigger
import com.jackwallner.football.model.ContractValue
import com.jackwallner.football.model.Player
import com.jackwallner.football.model.PlayerPositionGroup
import com.jackwallner.football.model.PlayerProfile
import com.jackwallner.football.model.SeasonLabel
import com.jackwallner.football.model.SeasonPhase
import com.jackwallner.football.model.displayTeamAbbr
import com.jackwallner.football.model.ordinal
import com.jackwallner.football.ui.LocalGraph
import com.jackwallner.football.ui.components.BlurGateUnlock
import com.jackwallner.football.ui.components.EmptyState
import com.jackwallner.football.ui.components.FitText
import com.jackwallner.football.ui.components.GridironSectionBar
import com.jackwallner.football.ui.components.GridironSegmented
import com.jackwallner.football.ui.components.GridironTabs
import com.jackwallner.football.ui.components.PercentileBarMini
import com.jackwallner.football.ui.components.PlayerHeadshot
import com.jackwallner.football.ui.components.RefreshableBox
import com.jackwallner.football.ui.components.Segment
import com.jackwallner.football.ui.components.bottomBarPadding
import com.jackwallner.football.ui.components.bottomHairline
import com.jackwallner.football.ui.components.gateBlur
import com.jackwallner.football.ui.components.gridironCard
import com.jackwallner.football.ui.nav.LocalNavigator
import com.jackwallner.football.ui.nav.Route
import com.jackwallner.football.ui.theme.GridironGeo
import com.jackwallner.football.ui.theme.GridironPalette
import com.jackwallner.football.ui.theme.GridironType
import java.util.Locale

/** Green for outplaying the deal, rust for under it, ink in between. */
val ContractValue.tint: Color
    get() = when (verdict) {
        ContractValue.Verdict.BARGAIN, ContractValue.Verdict.OUTPLAYING -> GridironPalette.performanceHigh
        ContractValue.Verdict.FAIR -> GridironPalette.inkSecondary
        ContractValue.Verdict.UNDER, ContractValue.Verdict.OVERPAID -> GridironPalette.performanceLow
    }

/** Every qualified player at a position ranked by production against pay; the leader shows above the blur for everyone. */
@Composable
fun ContractValueBoard(vm: DashboardViewModel, state: StatsBoardState) {
    val isPro = LocalGraph.current.subscriptions.isPro
    val positions = listOf(PlayerPositionGroup.QB, PlayerPositionGroup.RB, PlayerPositionGroup.WR, PlayerPositionGroup.TE)
    var showingBargains by rememberSaveable { mutableStateOf(true) }
    // The board has no defense yet (see ContractValue.positions).
    LaunchedEffect(Unit) { if (vm.selectedPosition !in positions) vm.selectedPosition = PlayerPositionGroup.QB }
    val rows = vm.contractValueBoard(showingBargains)

    Column(Modifier.fillMaxSize()) {
        GridironTabs(
            positions.map { it.raw },
            vm.selectedPosition.raw,
            { raw -> positions.firstOrNull { it.raw == raw }?.let { vm.selectedPosition = it } },
            Modifier.padding(top = 8.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GridironSegmented(
                listOf(Segment(true, "Bargains"), Segment(false, "Overpaid")),
                showingBargains,
                { showingBargains = it },
                Modifier.weight(1f),
            )
            StatsViewMenu(vm, state)
        }
        RefreshableBox({ vm.load() }, Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottomBarPadding())) {
                Box(Modifier.padding(horizontal = 12.dp).padding(top = 8.dp)) {
                    when {
                        vm.selectedSeason != vm.freeSeason || vm.selectedPhase != SeasonPhase.REGULAR -> Empty(
                            "Current season only",
                            "Contract Value ranks this season's production against the contracts players are on now. Switch the season pill back to ${SeasonLabel.text(vm.freeSeason)}.",
                        )
                        rows.isEmpty() -> Empty(
                            if (vm.profiles.isEmpty()) "Contracts loading" else "Not enough qualified players yet",
                            if (vm.profiles.isEmpty()) "Contract data arrives with the next update. Pull to refresh."
                            else "Each position needs five qualified players with a contract before the board can rank them.",
                        )
                        else -> Column(Modifier.fillMaxWidth().gridironCard()) {
                            Header()
                            ValueRow(vm, 1, rows.first())
                            if (isPro) rows.drop(1).forEachIndexed { i, entry -> ValueRow(vm, i + 2, entry) }
                            else Box {
                                Column(Modifier.gateBlur()) { rows.drop(1).take(8).forEachIndexed { i, entry -> ValueRow(vm, i + 2, entry, interactive = false) } }
                                BlurGateUnlock("See every bargain and overpay at every position", PaywallTrigger.ContractValue, Modifier.align(Alignment.BottomCenter))
                            }
                        }
                    }
                }
                Text(
                    "Pay is each deal's yearly average as a share of the cap when it was signed; play is the player's average percentile on the stats he qualifies for. Both are ranked among qualified ${vm.selectedPosition.raw}s with a contract, and value is play minus pay. Contracts: OverTheCap via nflverse.",
                    style = GridironType.micro,
                    color = GridironPalette.inkTertiary,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun Empty(title: String, detail: String) {
    Box(Modifier.fillMaxWidth().gridironCard().padding(vertical = 8.dp)) { EmptyState(title, "dollarsign.circle", detail) }
}

@Composable
private fun Header() {
    Row(
        Modifier.fillMaxWidth().height(GridironGeo.rowHeightHeader).background(GridironPalette.surfaceAlt).bottomHairline()
            .padding(horizontal = GridironGeo.padInline),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("RANK", style = GridironType.micro, color = GridironPalette.inkTertiary, modifier = Modifier.width(36.dp))
        Text("PLAYER", style = GridironType.micro, color = GridironPalette.inkTertiary, modifier = Modifier.weight(1f))
        Text("PAY / PLAY", style = GridironType.micro, color = GridironPalette.inkTertiary, modifier = Modifier.width(72.dp), textAlign = TextAlign.End)
        Text("VALUE", style = GridironType.micro, color = GridironPalette.inkTertiary, modifier = Modifier.width(52.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun ValueRow(vm: DashboardViewModel, rank: Int, entry: Pair<Player, ContractValue>, interactive: Boolean = true) {
    val navigator = LocalNavigator.current
    val (player, value) = entry
    val contract = vm.profile(player)?.contractLabel
    Row(
        Modifier.fillMaxWidth().height(52.dp).background(if (rank % 2 == 1) GridironPalette.surface else GridironPalette.surfaceAlt)
            .clickable(enabled = interactive) { navigator.push(Route.PlayerProfile(player)) }
            .padding(horizontal = GridironGeo.padInline)
            .clearAndSetSemantics {
                contentDescription = "$rank. ${player.name}, paid like the ${value.payPercentile.ordinal} percentile, producing like the ${value.productionPercentile.ordinal}. Value ${value.scoreLabel}, ${value.verdict.label}"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$rank", style = GridironType.statSmall, color = GridironPalette.inkSecondary, modifier = Modifier.width(36.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PlayerHeadshot(player.team, player.initials, 36.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FitText(player.name, GridironType.bodyBold, GridironPalette.ink, minScale = 0.85f)
                Text(listOfNotNull(displayTeamAbbr(player.team), contract).joinToString(" · "), style = GridironType.micro, color = GridironPalette.inkTertiary, maxLines = 1)
            }
        }
        Text("${value.payPercentile} / ${value.productionPercentile}", style = GridironType.statSmall, color = GridironPalette.inkSecondary, modifier = Modifier.width(72.dp), textAlign = TextAlign.End)
        Text(value.scoreLabel, style = GridironType.statMed, color = value.tint, modifier = Modifier.width(52.dp), textAlign = TextAlign.End)
    }
}

/** The profile's Contract Value card: pay rank, production rank, and the gap. Free. */
@Composable
fun ContractValueCard(player: Player, profile: PlayerProfile, value: ContractValue?, coverage: String?) {
    Column(Modifier.fillMaxWidth().gridironCard()) {
        GridironSectionBar("CONTRACT VALUE", trailing = value?.let { v ->
            { Text("${v.scoreLabel} · ${v.verdict.label}", style = GridironType.smallBold, color = v.tint) }
        })
        Column(Modifier.fillMaxWidth().padding(GridironGeo.padCard), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            contractLine(profile)?.let { Text(it, style = GridironType.body, color = GridironPalette.ink) }
            if (value != null) {
                PayBar("Pay", value.payPercentile, neutral = true)
                PayBar("Play", value.productionPercentile, neutral = false)
                val group = player.positionGroup.raw
                val through = coverage?.let { " $it." } ?: "."
                Text(
                    "Paid like the ${value.payPercentile.ordinal} percentile $group, producing like the ${value.productionPercentile.ordinal}, among ${value.poolSize} qualified ${group}s with a contract$through Contracts: OverTheCap via nflverse.",
                    style = GridironType.micro,
                    color = GridironPalette.inkTertiary,
                )
            } else {
                Text(unavailableReason(player, profile), style = GridironType.small, color = GridironPalette.inkSecondary)
            }
        }
    }
}

/** "$42.2M/yr · 14.0% of cap · 4 yrs, signed 2026". */
private fun contractLine(profile: PlayerProfile): String? {
    val apy = profile.contractLabel ?: return null
    val parts = mutableListOf(apy)
    profile.contractCapShare?.takeIf { it > 0 }?.let { parts += String.format(Locale.US, "%.1f%% of cap", it * 100) }
    val years = profile.contractYears
    val signed = profile.contractYearSigned
    if (years != null && signed != null) parts += "$years yr${if (years == 1) "" else "s"}, signed $signed"
    return parts.joinToString(" · ")
}

private fun unavailableReason(player: Player, profile: PlayerProfile): String = when {
    profile.contractLabel == null -> "No active contract on file."
    player.positionGroup !in ContractValue.positions ->
        "Value rankings cover offense for now. Defenders join once advanced defensive stats publish for the season."
    else -> "Ranks once ${player.name} clears the playing-time minimum at his position."
}

@Composable
private fun PayBar(label: String, percentile: Int, neutral: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = GridironType.smallBold, color = GridironPalette.inkSecondary, modifier = Modifier.width(34.dp))
        // Pay is drawn in ink: a high pay rank is a cost, not a strength.
        PercentileBarMini(percentile, Modifier.weight(1f), height = 8.dp, tint = if (neutral) GridironPalette.inkTertiary else null)
        Text(
            percentile.ordinal,
            style = GridironType.statSmall,
            color = if (neutral) GridironPalette.inkSecondary else GridironPalette.textColor(percentile),
            modifier = Modifier.width(44.dp),
            textAlign = TextAlign.End,
        )
    }
}
