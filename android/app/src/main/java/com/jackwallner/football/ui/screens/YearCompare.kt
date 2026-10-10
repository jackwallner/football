package com.jackwallner.football.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jackwallner.football.data.PaywallTrigger
import com.jackwallner.football.model.MetricCategory
import com.jackwallner.football.model.Player
import com.jackwallner.football.model.StandardStatSemantics
import com.jackwallner.football.model.ordinal
import com.jackwallner.football.ui.components.BlurGateUnlock
import com.jackwallner.football.ui.components.CrownYellow
import com.jackwallner.football.ui.components.EmptyState
import com.jackwallner.football.ui.components.FitText
import com.jackwallner.football.ui.components.GridironMenu
import com.jackwallner.football.ui.components.GridironSubSectionBar
import com.jackwallner.football.ui.components.MenuItem
import com.jackwallner.football.ui.components.MenuSection
import com.jackwallner.football.ui.components.SfIcon
import com.jackwallner.football.ui.components.bottomHairline
import com.jackwallner.football.ui.components.gateBlur
import com.jackwallner.football.ui.components.gridironCard
import com.jackwallner.football.ui.theme.GridironGeo
import com.jackwallner.football.ui.theme.GridironPalette
import com.jackwallner.football.ui.theme.GridironType

/** The free-tier preview of Year Compare: a blurred mock of the real layout and the unlock. */
@Composable
fun YearComparePreview(playerName: String) {
    Box(Modifier.padding(horizontal = 12.dp).padding(top = 12.dp).fillMaxWidth().gridironCard()) {
        Column(Modifier.gateBlur().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().gridironCard().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                MockYear("2026", "Recent", Modifier.weight(1f))
                SfIcon("arrow.right", 16.dp, GridironPalette.inkTertiary)
                MockYear("2025", "Prior", Modifier.weight(1f))
            }
            Column(Modifier.fillMaxWidth().gridironCard()) {
                GridironSubSectionBar("SEASON TOTALS")
                ColumnHeader("2025", "2026")
                listOf(
                    Triple("Cmp/Att", "348/530", "385/566"),
                    Triple("Pass Yds", "3,650", "4,180"),
                    Triple("Pass TD", "24", "34"),
                    Triple("Rush Yds", "285", "412"),
                ).forEach { (label, prior, recent) ->
                    Row(Modifier.fillMaxWidth().height(48.dp).background(GridironPalette.surface).bottomHairline().padding(horizontal = GridironGeo.padInline), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = GridironType.body, color = GridironPalette.ink, maxLines = 1, modifier = Modifier.weight(1f))
                        Text(prior, style = GridironType.statSmall, color = GridironPalette.inkTertiary, modifier = Modifier.width(72.dp), textAlign = TextAlign.Center)
                        Text(recent, style = GridironType.statSmall, color = GridironPalette.turf, modifier = Modifier.width(72.dp), textAlign = TextAlign.Center)
                    }
                }
            }
        }
        BlurGateUnlock("See how $playerName evolved season to season", PaywallTrigger.YearCompare, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun MockYear(label: String, subtitle: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(GridironGeo.radiusCard)).background(GridironPalette.surfaceAlt).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = GridironType.statLarge, color = GridironPalette.ink)
        Text(subtitle, style = GridironType.micro, color = GridironPalette.inkTertiary)
    }
}

@Composable
private fun ColumnHeader(prior: String, recent: String) {
    Row(Modifier.fillMaxWidth().height(28.dp).background(GridironPalette.surfaceAlt).padding(horizontal = GridironGeo.padInline), verticalAlignment = Alignment.CenterVertically) {
        Text("STAT", style = GridironType.micro, color = GridironPalette.inkSecondary, modifier = Modifier.weight(1f))
        Text(prior, style = GridironType.micro, color = GridironPalette.inkSecondary, modifier = Modifier.width(72.dp), textAlign = TextAlign.Center)
        Text(recent, style = GridironType.micro, color = GridironPalette.inkSecondary, modifier = Modifier.width(72.dp), textAlign = TextAlign.Center)
    }
}

private data class MetricComparison(
    val label: String,
    val category: MetricCategory,
    val percentileA: Int,
    val percentileB: Int,
    val valueA: String,
    val valueB: String,
)

/** Any two seasons of one player, side by side: totals first, then each advanced category. */
@Composable
fun YearComparisonView(history: List<Player>) {
    val years = history.mapNotNull { it.season }.distinct().sortedDescending()
    var yearA by remember { mutableIntStateOf(0) }
    var yearB by remember { mutableIntStateOf(0) }
    LaunchedEffect(years) {
        if (years.isEmpty()) return@LaunchedEffect
        if (yearA !in years) yearA = years.first()
        if (yearB !in years || yearB == yearA) yearB = years.firstOrNull { it != yearA } ?: yearB
    }
    val recentYear = maxOf(yearA, yearB)
    val priorYear = minOf(yearA, yearB)
    val p1 = history.firstOrNull { it.season == recentYear }
    val p2 = history.firstOrNull { it.season == priorYear }

    @Composable
    fun YearButton(year: Int, other: Int, onPick: (Int) -> Unit, modifier: Modifier) {
        GridironMenu(listOf(MenuSection(null, years.filter { it != other }.map { y -> MenuItem("$y", checked = y == year) { onPick(y) } })), modifier) { open ->
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(GridironGeo.radiusCard)).background(GridironPalette.surfaceAlt).clickable(onClick = open).padding(vertical = 12.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (year > 0) "$year" else "Select", style = GridironType.statLarge, color = GridironPalette.ink)
                    Text(if (year == recentYear) "Recent" else "Prior", style = GridironType.micro, color = GridironPalette.inkTertiary)
                }
                SfIcon("chevron.down", 12.dp, GridironPalette.inkTertiary, Modifier.align(Alignment.CenterEnd).padding(end = 8.dp))
            }
        }
    }

    Column(Modifier.padding(horizontal = 12.dp).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().gridironCard().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            YearButton(yearB, yearA, { yearB = it }, Modifier.weight(1f))
            SfIcon("arrow.right", 16.dp, GridironPalette.inkTertiary)
            YearButton(yearA, yearB, { yearA = it }, Modifier.weight(1f))
        }
        if (p1 == null || p2 == null) {
            Box(Modifier.fillMaxWidth().gridironCard().padding(vertical = 24.dp)) {
                EmptyState(
                    "No Data Available",
                    "calendar.badge.clock",
                    if (years.isEmpty()) "No historical data is available for this player." else "Data for $recentYear or $priorYear is not available.",
                )
            }
        } else {
            val comparisons = buildComparisons(p1, p2)
            val standard = standardComparisons(p2, p1)
            if (comparisons.isEmpty() && standard.isEmpty()) {
                Box(Modifier.fillMaxWidth().gridironCard().padding(vertical = 24.dp)) {
                    EmptyState("No Comparable Metrics", "chart.bar.xaxis", "These seasons don't have overlapping standard or advanced stats.")
                }
            } else {
                if (standard.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().gridironCard()) {
                        GridironSubSectionBar("SEASON TOTALS")
                        ColumnHeader("$priorYear", "$recentYear")
                        standard.forEachIndexed { index, (label, prior, recent) ->
                            val winner = StandardStatSemantics.winner(label, prior, recent)
                            val spoken = buildString {
                                append("$label: $priorYear ${prior ?: "no data"}, $recentYear ${recent ?: "no data"}")
                                if (winner == StandardStatSemantics.Winner.LEFT) append(", $priorYear better")
                                if (winner == StandardStatSemantics.Winner.RIGHT) append(", $recentYear better")
                            }
                            Row(
                                Modifier.fillMaxWidth().height(48.dp).background(if (index % 2 == 0) GridironPalette.surface else GridironPalette.surfaceAlt)
                                    .bottomHairline().padding(horizontal = GridironGeo.padInline).clearAndSetSemantics { contentDescription = spoken },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(label, style = GridironType.body, color = GridironPalette.ink, modifier = Modifier.weight(1f))
                                RawValue(prior, winner == StandardStatSemantics.Winner.LEFT, Modifier.width(72.dp))
                                RawValue(recent, winner == StandardStatSemantics.Winner.RIGHT, Modifier.width(72.dp))
                            }
                        }
                    }
                }
                MetricCategory.entries.forEach { category ->
                    val items = comparisons.filter { it.category == category }
                    if (items.isEmpty()) return@forEach
                    Column(Modifier.fillMaxWidth().gridironCard()) {
                        GridironSubSectionBar("ADVANCED · " + category.raw.uppercase())
                        ColumnHeader("$priorYear", "$recentYear")
                        items.forEachIndexed { idx, item ->
                            Row(
                                Modifier.fillMaxWidth().height(48.dp).background(if (idx % 2 == 1) GridironPalette.surfaceAlt else GridironPalette.surface)
                                    .bottomHairline().padding(horizontal = GridironGeo.padInline)
                                    .clearAndSetSemantics {
                                        contentDescription = "${item.label}: $priorYear ${item.percentileB.ordinal} percentile, $recentYear ${item.percentileA.ordinal} percentile"
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(item.label, style = GridironType.body, color = GridironPalette.ink, maxLines = 1, modifier = Modifier.weight(1f))
                                YearValue(item.percentileB, item.valueB, item.percentileB > item.percentileA, Modifier.width(72.dp))
                                YearValue(item.percentileA, item.valueA, item.percentileA > item.percentileB, Modifier.width(72.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YearValue(percentile: Int, value: String, isWinner: Boolean, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isWinner) SfIcon("trophy.fill", 10.dp, CrownYellow)
            FitText(value.ifEmpty { "$percentile" }, GridironType.statSmall, if (isWinner) GridironPalette.turf else GridironPalette.inkSecondary, minScale = 0.7f)
        }
        if (value.isEmpty()) Text("PCTL", style = GridironType.micro, color = GridironPalette.inkTertiary)
    }
}

@Composable
private fun RawValue(value: String?, isWinner: Boolean, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        if (isWinner) SfIcon("trophy.fill", 10.dp, CrownYellow)
        Text(
            value ?: "-",
            style = GridironType.statSmall,
            color = when {
                value == null -> GridironPalette.inkTertiary
                isWinner -> GridironPalette.turf
                else -> GridironPalette.inkSecondary
            },
        )
    }
}

private fun buildComparisons(p1: Player, p2: Player): List<MetricComparison> {
    val m1 = p1.metrics.groupBy { it.label }
    val m2 = p2.metrics.groupBy { it.label }
    return (m1.keys + m2.keys).mapNotNull { label ->
        val a = m1[label]?.firstOrNull() ?: return@mapNotNull null
        val b = m2[label]?.firstOrNull() ?: return@mapNotNull null
        MetricComparison(label, a.category, a.percentile, b.percentile, a.value, b.value)
    }.sortedWith { a, b ->
        if (a.category == b.category) {
            if (a.category.sortMetrics(a.label, b.label)) -1 else if (a.category.sortMetrics(b.label, a.label)) 1 else 0
        } else a.category.ordinal.compareTo(b.category.ordinal)
    }
}

private val PREFERRED_ORDER = listOf(
    "G", "Cmp/Att", "Pass Yds", "Pass TD", "INT", "Car", "Rush Yds", "Rush TD", "Rec/Tgt", "Rec Yds", "Rec TD", "Tackles", "Sacks", "Def INT",
)

/** Union of two standard lines in the fixed reading order: (label, left value, right value). */
fun standardComparisons(left: Player, right: Player): List<Triple<String, String?, String?>> {
    val l = left.standardStats.orEmpty().associate { it.label to it.value }
    val r = right.standardStats.orEmpty().associate { it.label to it.value }
    return (l.keys + r.keys).sortedWith { x, y ->
        val fx = PREFERRED_ORDER.indexOf(x).let { if (it < 0) PREFERRED_ORDER.size else it }
        val fy = PREFERRED_ORDER.indexOf(y).let { if (it < 0) PREFERRED_ORDER.size else it }
        if (fx == fy) x.compareTo(y) else fx.compareTo(fy)
    }.map { Triple(it, l[it], r[it]) }
}
