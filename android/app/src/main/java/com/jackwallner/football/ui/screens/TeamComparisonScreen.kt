package com.jackwallner.football.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jackwallner.football.data.DashboardViewModel
import com.jackwallner.football.model.FootballMetricRegistry
import com.jackwallner.football.model.MetricAggregation
import com.jackwallner.football.model.MetricCategory
import com.jackwallner.football.model.MetricKind
import com.jackwallner.football.model.MetricValueFormat
import com.jackwallner.football.model.Player
import com.jackwallner.football.model.SeasonLabel
import com.jackwallner.football.model.SeasonPhase
import com.jackwallner.football.model.displayTeamAbbr
import com.jackwallner.football.model.normalizedTeamAbbreviation
import com.jackwallner.football.model.teamFullName
import com.jackwallner.football.ui.LocalGraph
import com.jackwallner.football.ui.components.CrownYellow
import com.jackwallner.football.ui.components.FitText
import com.jackwallner.football.ui.components.GridironSectionBar
import com.jackwallner.football.ui.components.PushedScreen
import com.jackwallner.football.ui.components.SearchField
import com.jackwallner.football.ui.components.SfIcon
import com.jackwallner.football.ui.components.TextAction
import com.jackwallner.football.ui.components.bottomBarPadding
import com.jackwallner.football.ui.components.bottomHairline
import com.jackwallner.football.ui.components.gridironCard
import com.jackwallner.football.ui.nav.Route
import com.jackwallner.football.ui.theme.GridironGeo
import com.jackwallner.football.ui.theme.GridironPalette
import com.jackwallner.football.ui.theme.GridironType
import com.jackwallner.football.ui.theme.NFLTeamColor
import java.text.NumberFormat
import java.util.Locale

/**
 * The team chooser sheet for Compare: every club alphabetically, with search.
 *
 * Present it inside a full-height `StatScoutSheet`; it calls [onDismiss] and
 * then [onSelect] when a club is tapped, and [onDismiss] alone from Cancel.
 */
@Composable
fun CompareTeamPicker(teams: List<String>, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    var searchText by rememberSaveable { mutableStateOf("") }
    val filteredTeams = remember(teams, searchText) {
        val sorted = teams.sortedBy { teamFullName(it) }
        if (searchText.isEmpty()) sorted
        else sorted.filter { it.contains(searchText, ignoreCase = true) || teamFullName(it).contains(searchText, ignoreCase = true) }
    }
    Column(Modifier.fillMaxSize().background(GridironPalette.canvas)) {
        Box(Modifier.fillMaxWidth().height(52.dp)) {
            TextAction("Cancel", GridironType.body, GridironPalette.turf, onDismiss, Modifier.align(Alignment.CenterStart).padding(start = 4.dp).testTag("pickerCancel"))
            Text("Select Team", style = GridironType.cardTitle, color = GridironPalette.ink, modifier = Modifier.align(Alignment.Center))
        }
        SearchField(searchText, { searchText = it }, Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp), prompt = "Search teams")
        LazyColumn(Modifier.fillMaxSize()) {
            items(filteredTeams, key = { it }) { team ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .background(GridironPalette.surface)
                        .bottomHairline(GridironPalette.divider)
                        .clickable {
                            onDismiss()
                            onSelect(team)
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("pickTeam_$team"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(NFLTeamColor.color(team)), contentAlignment = Alignment.Center) {
                        Text(displayTeamAbbr(team), style = GridironType.micro, color = Color.White)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(teamFullName(team), style = GridironType.bodyBold, color = GridironPalette.ink)
                        Text(displayTeamAbbr(team), style = GridironType.small, color = GridironPalette.inkTertiary)
                    }
                }
            }
        }
    }
}

private data class StatRow(
    val id: String,
    val label: String,
    val aText: String,
    val bText: String,
    /** Comparison values, sign-flipped for lower-is-better metrics so the trophy always goes to the greater number. Null means "show the value, don't declare a winner". */
    val aValue: Double?,
    val bValue: Double?,
)

private data class StatGroup(val title: String, val rows: List<StatRow>)

/**
 * Roster aggregates that are context rather than a verdict. Both are shares of
 * a team's own passing volume, so summing them across a roster measures how
 * much of the offence we track, not how good it is.
 */
private val DESCRIPTIVE_ONLY_LABELS = setOf("Target Share", "WOPR")

/**
 * Two clubs side by side: advanced rows from `metrics`, standard rows from
 * `standard_stats`, interleaved category by category. Each club carries its own
 * season and phase, as on the Compare tab.
 */
@Composable
fun TeamComparisonScreen(route: Route.TeamComparison) {
    val vm = LocalGraph.current.dashboard
    val a = route.teamA
    val b = route.teamB
    val sA = route.seasonA
    val pA = route.phaseA
    val sB = route.seasonB
    val pB = route.phaseB
    val rosterA = vm.players(sA, pA).filter { normalizedTeamAbbreviation(it.team) == normalizedTeamAbbreviation(a) }
    val rosterB = vm.players(sB, pB).filter { normalizedTeamAbbreviation(it.team) == normalizedTeamAbbreviation(b) }
    val groups = remember(rosterA, rosterB) { statGroups(rosterA, rosterB) }

    PushedScreen("Team Comparison") {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TeamHeader(a, rosterA.size, sA, pA, Modifier.weight(1f))
                SfIcon("arrow.left.arrow.right", 18.dp, GridironPalette.inkTertiary)
                TeamHeader(b, rosterB.size, sB, pB, Modifier.weight(1f))
            }

            if (groups.isEmpty()) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(GridironGeo.radiusCard)).background(GridironPalette.surface).padding(vertical = 32.dp)) {
                    GamesTeamsUi.Unavailable("sum", "No aggregate stats", "Neither selected roster has standard stats for this context.")
                }
            } else {
                groups.forEach { group ->
                    Column(Modifier.fillMaxWidth().gridironCard().testTag("compareGroup")) {
                        GridironSectionBar(group.title)
                        group.rows.forEachIndexed { index, row -> StatRowView(row, index) }
                    }
                }
            }

            Text(
                "Totals sum the selected roster's player season lines. Traded players bring their full-season line, so these are roster aggregates rather than official team totals.",
                style = GridironType.micro,
                color = GridironPalette.inkTertiary,
            )
            Spacer(Modifier.padding(bottomBarPadding()))
        }
    }
}

/**
 * Advanced rows come from `metrics`, standard rows from `standard_stats`, so
 * they are built separately and interleaved category by category. Without the
 * advanced half the one screen that answers "which of these two teams is
 * better" couldn't reference a single advanced number.
 */
private fun statGroups(rosterA: List<Player>, rosterB: List<Player>): List<StatGroup> =
    MetricCategory.entries.flatMap { category ->
        listOf(
            StatGroup(category.raw.uppercase() + " · ADVANCED", advancedRows(category, rosterA, rosterB)),
            StatGroup(category.raw.uppercase() + " · STANDARD", standardRows(category, rosterA, rosterB)),
        )
    }
        .map { group -> StatGroup(group.title, group.rows.filter { it.aText != "-" || it.bText != "-" }) }
        .filter { it.rows.isNotEmpty() }

/** Every advanced metric that either roster has for this category, in the registry's own display order. */
private fun advancedRows(category: MetricCategory, rosterA: List<Player>, rosterB: List<Player>): List<StatRow> {
    val labels = FootballMetricRegistry.definitions
        .filter { it.category == category && it.kind == MetricKind.ADVANCED }
        .sortedBy { it.priority }
        .map { it.label }
    return labels.mapNotNull { label ->
        val a = aggregate(label, category, rosterA)
        val b = aggregate(label, category, rosterB)
        if (a == null && b == null) return@mapNotNull null
        val format = MetricValueFormat.inferred(
            (rosterA + rosterB).mapNotNull { player -> player.metrics.firstOrNull { it.label == label && it.category == category }?.value },
        )
        // Lower-is-better metrics (Sack%, INT%, Fumble%) must not hand the trophy
        // to the bigger number. Flipping the sign of both sides is enough.
        val higherIsBetter = FootballMetricRegistry.definition(label, category)?.higherIsBetter ?: true
        val sign = if (higherIsBetter) 1.0 else -1.0
        // Some aggregates describe a roster without ranking it. A team's summed
        // Target Share is mostly a count of how many of its receivers cleared the
        // qualification bar, so awarding a trophy would score roster shape as if
        // it were quality. Null values keep the row and suppress the marker.
        val comparable = label !in DESCRIPTIVE_ONLY_LABELS
        StatRow(
            id = category.raw + "-adv-" + label,
            label = label,
            aText = a?.let { format.string(it) } ?: "-",
            bText = b?.let { format.string(it) } ?: "-",
            aValue = if (comparable) a?.times(sign) else null,
            bValue = if (comparable) b?.times(sign) else null,
        )
    }
}

/** Pools one metric across a roster using the registry's aggregation rule. */
private fun aggregate(label: String, category: MetricCategory, roster: List<Player>): Double? {
    val rule = FootballMetricRegistry.aggregation(label, category)
    val values = roster.mapNotNull { player ->
        val metric = player.metrics.firstOrNull { it.label == label && it.category == category } ?: return@mapNotNull null
        val value = DashboardViewModel.rawNumeric(metric.value) ?: return@mapNotNull null
        when (rule) {
            MetricAggregation.Sum -> value to 1.0
            // No volume means no rate to trust: drop the player rather than let
            // an unweighted value slide in as if it were weight 1.
            is MetricAggregation.Weighted -> rule.weight.value(player)?.let { value to it }
        }
    }
    if (values.isEmpty()) return null
    return when (rule) {
        MetricAggregation.Sum -> values.sumOf { it.first }
        is MetricAggregation.Weighted -> {
            val totalWeight = values.sumOf { it.second }
            if (totalWeight <= 0) null else values.sumOf { it.first * it.second } / totalWeight
        }
    }
}

private fun standardRows(category: MetricCategory, rosterA: List<Player>, rosterB: List<Player>): List<StatRow> = when (category) {
    MetricCategory.PASSING -> listOf(
        pairedRow("Cmp/Att", rosterA, rosterB),
        totalRow("Pass Yds", rosterA, rosterB),
        totalRow("Pass TD", rosterA, rosterB),
        totalRow("INT", rosterA, rosterB, higherIsBetter = false),
    )
    MetricCategory.RUSHING -> listOf(
        totalRow("Car", rosterA, rosterB),
        totalRow("Rush Yds", rosterA, rosterB),
        totalRow("Rush TD", rosterA, rosterB),
    )
    MetricCategory.RECEIVING -> listOf(
        pairedRow("Rec/Tgt", rosterA, rosterB),
        totalRow("Rec Yds", rosterA, rosterB),
        totalRow("Rec TD", rosterA, rosterB),
    )
    MetricCategory.DEFENSE -> listOf(
        totalRow("Tackles", rosterA, rosterB),
        totalRow("Sacks", rosterA, rosterB),
        totalRow("Def INT", rosterA, rosterB),
    )
}

/**
 * `higherIsBetter = false` flips which side gets the trophy. Interceptions
 * thrown needed it: as a plain total it handed the marker to whichever offence
 * turned the ball over more, which is backwards.
 */
private fun totalRow(label: String, rosterA: List<Player>, rosterB: List<Player>, higherIsBetter: Boolean = true): StatRow {
    val a = total(label, rosterA)
    val b = total(label, rosterB)
    val sign = if (higherIsBetter) 1.0 else -1.0
    return StatRow(label, label, format(a, label), format(b, label), a?.times(sign), b?.times(sign))
}

private fun pairedRow(label: String, rosterA: List<Player>, rosterB: List<Player>): StatRow =
    StatRow(label, label, pairedTotal(label, rosterA), pairedTotal(label, rosterB), null, null)

private fun total(label: String, roster: List<Player>): Double? {
    val values = roster.mapNotNull { player ->
        player.standardStats?.firstOrNull { it.label == label }?.let { DashboardViewModel.rawNumeric(it.value) }
    }
    return if (values.isEmpty()) null else values.sum()
}

private fun pairedTotal(label: String, roster: List<Player>): String {
    val pairs = roster.mapNotNull { player ->
        val raw = player.standardStats?.firstOrNull { it.label == label }?.value ?: return@mapNotNull null
        val components = raw.split("/", limit = 2)
        if (components.size != 2) return@mapNotNull null
        val first = DashboardViewModel.rawNumeric(components[0]) ?: return@mapNotNull null
        val second = DashboardViewModel.rawNumeric(components[1]) ?: return@mapNotNull null
        first to second
    }
    if (pairs.isEmpty()) return "-"
    return format(pairs.sumOf { it.first }, label) + "/" + format(pairs.sumOf { it.second }, label)
}

private fun format(value: Double?, label: String): String {
    value ?: return "-"
    if (label == "Sacks" && Math.round(value).toDouble() != value) return String.format(Locale.US, "%.1f", value)
    return NumberFormat.getIntegerInstance(Locale.US).format(Math.round(value))
}

@Composable
private fun TeamHeader(team: String, rosterCount: Int, season: Int, phase: SeasonPhase, modifier: Modifier = Modifier) {
    Column(
        modifier.gridironCard().padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(58.dp).clip(CircleShape).background(NFLTeamColor.color(team)), contentAlignment = Alignment.Center) {
            Text(displayTeamAbbr(team), style = GridironType.smallBold, color = Color.White)
        }
        FitText(teamFullName(team), GridironType.bodyBold, GridironPalette.ink, minScale = 0.7f, textAlign = TextAlign.Center)
        Text(
            "$rosterCount tracked · ${SeasonLabel.text(season)} ${phase.label}",
            style = GridironType.micro,
            color = GridironPalette.inkTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StatRowView(row: StatRow, index: Int) {
    Row(
        Modifier.fillMaxWidth().height(56.dp)
            .background(if (index % 2 == 0) GridironPalette.surface else GridironPalette.surfaceAlt)
            .bottomHairline(GridironPalette.divider)
            .padding(horizontal = GridironGeo.padInline),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatValue(row.aText, row.aValue, row.bValue, Modifier.weight(1f))
        Text(row.label, style = GridironType.smallBold, color = GridironPalette.ink, textAlign = TextAlign.Center, modifier = Modifier.width(88.dp))
        StatValue(row.bText, row.bValue, row.aValue, Modifier.weight(1f))
    }
}

@Composable
private fun StatValue(text: String, value: Double?, other: Double?, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        if (value != null && other != null && value > other) SfIcon("trophy.fill", 13.dp, CrownYellow)
        Text(
            text,
            style = GridironType.statMed,
            color = if (text == "-") GridironPalette.inkTertiary else GridironPalette.turf,
            maxLines = 1,
        )
    }
}
