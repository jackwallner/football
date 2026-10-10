package com.jackwallner.football.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jackwallner.football.model.InjuryReport
import com.jackwallner.football.model.Metric
import com.jackwallner.football.model.MetricCategory
import com.jackwallner.football.model.Player
import com.jackwallner.football.model.PlayerProfile
import com.jackwallner.football.model.displayTeamAbbr
import com.jackwallner.football.model.displayTeamFullName
import com.jackwallner.football.model.normalizedTeamAbbreviation
import com.jackwallner.football.model.ordinal
import com.jackwallner.football.model.teamFullName
import com.jackwallner.football.ui.theme.GridironGeo
import com.jackwallner.football.ui.theme.GridironPalette
import com.jackwallner.football.ui.theme.GridironType
import com.jackwallner.football.ui.theme.NFLTeamColor
import com.jackwallner.football.ui.theme.sf
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.pow

private val TEXT_SHADOW = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 1f), 2f)

/** A single-line label that shrinks (down to [minScale]) before it truncates, like iOS `minimumScaleFactor`. */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    minScale: Float = 0.8f,
    textAlign: TextAlign? = null,
) {
    BoxWithConstraints(modifier) {
        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
        val maxPx = constraints.maxWidth
        val fitted = remember(text, style, maxPx) {
            if (maxPx == Int.MAX_VALUE) return@remember style
            var scale = 1f
            while (scale > minScale) {
                val width = measurer.measure(text, style.copy(fontSize = style.fontSize * scale), maxLines = 1).size.width
                if (width <= maxPx) break
                scale -= 0.05f
            }
            style.copy(fontSize = style.fontSize * scale.coerceAtLeast(minScale))
        }
        Text(text, style = fitted, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = textAlign, softWrap = false)
    }
}

@Composable
fun SfIcon(name: String, size: Dp, tint: Color, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Icon(sf(name), contentDescription = contentDescription, tint = tint, modifier = modifier.size(size))
}

@Composable
fun SfIcon(icon: ImageVector, size: Dp, tint: Color, modifier: Modifier = Modifier) {
    Icon(icon, contentDescription = null, tint = tint, modifier = modifier.size(size))
}

/** Initials in the team colour: the app never renders player photos. */
@Composable
fun PlayerHeadshot(team: String, initials: String, size: Dp, modifier: Modifier = Modifier, ring: Color? = null) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(NFLTeamColor.color(team))
            .border(if (ring != null) 2.dp else 0.5.dp, ring ?: Color.White.copy(alpha = 0.25f), CircleShape)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.34f).sp)
    }
}

@Composable
fun OverallPercentileBadge(percentile: Int, size: Dp = 64.dp) {
    val tier = when {
        percentile >= 90 -> "Elite"
        percentile >= 75 -> "Excellent"
        percentile >= 50 -> "Above Average"
        percentile >= 25 -> "Below Average"
        else -> "Poor"
    }
    Column(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(GridironGeo.radiusBadge))
            .background(GridironPalette.color(percentile))
            .clearAndSetSemantics { contentDescription = "Overall ${percentile.ordinal} percentile, $tier" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("$percentile", style = GridironType.statHero.copy(shadow = TEXT_SHADOW), color = Color.White)
        Text(percentile.ordinal, style = GridironType.micro.copy(shadow = TEXT_SHADOW), color = Color.White.copy(alpha = 0.9f))
    }
}

@Composable
fun TeamColorDot(abbr: String, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(NFLTeamColor.color(abbr)))
}

/** One percentile row: label, coloured track with the rank bubble, value. */
@Composable
fun MetricBar(metric: Metric, modifier: Modifier = Modifier, showValue: Boolean = true) {
    val description = if (metric.isUnranked) "${metric.label}: ${metric.value}, not ranked" else {
        val valueText = if (metric.value.isEmpty()) "${metric.percentile.ordinal} percentile" else "${metric.value}, ${metric.percentile.ordinal} percentile"
        "${metric.label}: $valueText${if (metric.isSmallSample) ", small sample" else ""}"
    }
    Row(
        modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(70.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            // Wraps to two lines (hyphenated) rather than shrinking, as on iOS.
            Text(
                metric.label,
                style = GridironType.bodyBold.copy(hyphens = androidx.compose.ui.text.style.Hyphens.Auto, lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph),
                color = GridironPalette.ink,
                maxLines = 2,
            )
            if (metric.isSmallSample && !metric.isUnranked) {
                Text("Small sample", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = GridironPalette.inkTertiary, maxLines = 1)
            }
        }
        Box(Modifier.weight(1f).height(28.dp), contentAlignment = Alignment.CenterStart) {
            if (metric.isUnranked) {
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(4.dp)).background(GridironPalette.surfaceSunk))
                Text(
                    "Not ranked",
                    style = GridironType.micro,
                    color = GridironPalette.inkTertiary,
                    modifier = Modifier.padding(start = 8.dp).clip(CircleShape).background(GridironPalette.surfaceSunk).padding(horizontal = 6.dp),
                )
            } else {
                PercentileTrack(metric.percentile.coerceIn(0, 100), dimmed = metric.isSmallSample)
            }
        }
        Box(Modifier.width(72.dp), contentAlignment = Alignment.CenterEnd) {
            if (showValue && metric.value.isNotEmpty()) {
                FitText(
                    metric.value,
                    GridironType.statMed,
                    if (metric.isSmallSample || metric.isUnranked) GridironPalette.inkSecondary else GridironPalette.ink,
                    minScale = 0.7f,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun PercentileTrack(percentile: Int, dimmed: Boolean) {
    val color = GridironPalette.color(percentile)
    val alpha = if (dimmed) 0.45f else 1f
    BoxWithConstraints(Modifier.fillMaxWidth().height(28.dp)) {
        val circle = 28.dp
        val trackWidth = maxWidth - circle
        val offset = circle / 2 + trackWidth * (percentile / 100f)
        Box(
            Modifier.align(Alignment.CenterStart).fillMaxWidth().height(10.dp).clip(RoundedCornerShape(4.dp))
                .background(GridironPalette.surfaceSunk.copy(alpha = alpha)),
        )
        Box(Modifier.align(Alignment.CenterStart).width(offset).height(10.dp).clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = alpha)))
        Box(
            Modifier.offset(x = offset - circle / 2).size(circle).clip(CircleShape).background(color.copy(alpha = alpha)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$percentile",
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum", shadow = TEXT_SHADOW),
                color = Color.White,
            )
        }
    }
}

/** Season and recent bars stacked; a missing window says so rather than vanishing. */
@Composable
fun DualMetricBar(season: Metric, recent: Metric?, recentCaption: String = "Recent") {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Season", style = GridironType.micro, color = GridironPalette.inkTertiary, modifier = Modifier.width(52.dp))
            MetricBar(season)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                recentCaption,
                style = GridironType.micro,
                color = if (recent == null) GridironPalette.inkTertiary else GridironPalette.turf,
                modifier = Modifier.width(52.dp),
            )
            if (recent != null) MetricBar(recent)
            else Text("Not available per game", style = GridironType.micro, color = GridironPalette.inkTertiary, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun SearchField(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusOnAppear: Boolean = false,
    prompt: String = "Search players or teams",
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(focusOnAppear) { if (focusOnAppear) runCatching { focus.requestFocus() } }
    Row(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(GridironGeo.radiusCard))
            .background(GridironPalette.surface)
            .border(1.dp, GridironPalette.hairline, RoundedCornerShape(GridironGeo.radiusCard))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SfIcon("magnifyingglass", 18.dp, GridironPalette.inkSecondary)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (text.isEmpty()) Text(prompt, style = GridironType.body, color = GridironPalette.inkSecondary)
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                singleLine = true,
                textStyle = GridironType.body.copy(color = GridironPalette.ink),
                cursorBrush = SolidColor(GridironPalette.turf),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("searchField"),
            )
        }
        if (text.isNotEmpty()) {
            SfIcon("xmark.circle.fill", 18.dp, GridironPalette.inkTertiary, Modifier.clickable { onTextChange("") }, "Clear search")
        }
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = GridironType.sectionTitle, color = GridironPalette.ink)
        Text(subtitle, style = GridironType.small, color = GridironPalette.inkSecondary)
    }
}

/**
 * Recent-vs-prior change: the arrow points the way the number moved; green
 * always means better. Below half the last shown digit it is flat.
 */
@Composable
fun TrendArrow(delta: Double, decimals: Int = 1, lowerIsBetter: Boolean = false, modifier: Modifier = Modifier) {
    val isFlat = abs(delta) < 5 * 10.0.pow(-(decimals + 1).toDouble())
    val tint = when {
        isFlat -> GridironPalette.inkTertiary
        (if (lowerIsBetter) delta < 0 else delta > 0) -> GridironPalette.performanceHigh
        else -> GridironPalette.performanceLow
    }
    val text = if (isFlat) "0" else String.format(java.util.Locale.US, "%.${decimals}f", abs(delta))
    Row(
        modifier.clearAndSetSemantics { contentDescription = if (isFlat) "No recent change" else "${if (delta > 0) "Up" else "Down"} $text recently" },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!isFlat) SfIcon(if (delta > 0) "arrow.up" else "arrow.down", 10.dp, tint)
        Text(text, style = GridironType.micro.copy(fontFeatureSettings = "tnum"), color = tint)
    }
}

@Composable
fun PercentileBarMini(percentile: Int, modifier: Modifier = Modifier, height: Dp = 7.dp, tint: Color? = null) {
    BoxWithConstraints(modifier.height(height)) {
        Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(GridironPalette.surfaceSunk))
        Box(
            Modifier.width(maxWidth * (percentile.coerceIn(0, 100) / 100f)).height(height).clip(RoundedCornerShape(height / 2))
                .background(tint ?: GridironPalette.color(percentile)),
        )
    }
}

@Composable
fun InlineLoadError(message: String, retry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = GridironGeo.padInline, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(message, style = GridironType.small, color = GridironPalette.inkSecondary, textAlign = TextAlign.Center)
        TextAction("Try Again", GridironType.smallBold, GridironPalette.turf, retry)
    }
}

/** A plain text button with an Android-sized touch target. */
@Composable
fun TextAction(title: String, style: TextStyle, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, style = style, color = color.copy(alpha = if (enabled) 1f else 0.5f), textAlign = TextAlign.Center)
    }
}

/** The leaderboard header: RANK / PLAYER / TEAM, then the metric chooser and direction toggle. */
@Composable
fun LeaderboardTableHeader(
    sortDescending: Boolean,
    sortLabel: String = "OVERALL",
    metrics: List<String> = emptyList(),
    onSelectMetric: (String) -> Unit = {},
    onToggleDirection: () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().height(GridironGeo.rowHeightHeader).background(GridironPalette.surfaceAlt)
            .bottomHairline(GridironPalette.divider).padding(horizontal = GridironGeo.padInline),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("RANK", style = GridironType.micro, color = GridironPalette.inkTertiary, modifier = Modifier.width(42.dp))
        Text("PLAYER", style = GridironType.micro, color = GridironPalette.inkTertiary, modifier = Modifier.weight(1f))
        Text("TEAM", style = GridironType.micro, color = GridironPalette.inkTertiary, modifier = Modifier.width(44.dp))
        Row(Modifier.width(104.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            val label = @Composable { open: () -> Unit ->
                Row(
                    Modifier.widthIn(max = 82.dp).clickable(onClick = open).padding(vertical = 4.dp).semantics { contentDescription = "Metric, $sortLabel" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    FitText(sortLabel.uppercase(), GridironType.micro, GridironPalette.turf, Modifier.widthIn(max = 70.dp), minScale = 0.7f)
                    if (metrics.isNotEmpty()) SfIcon("chevron.down", 10.dp, GridironPalette.turf)
                }
            }
            // With no metric list the label is part of the sort toggle, as on the iOS board.
            if (metrics.isEmpty()) label(onToggleDirection)
            else GridironMenu(sections = listOf(MenuSection(null, metrics.map { MenuItem(it, checked = it == sortLabel) { onSelectMetric(it) } }))) { open ->
                label(open)
            }
            Box(
                Modifier.size(width = 22.dp, height = 28.dp).clickable(onClick = onToggleDirection)
                    .semantics { contentDescription = if (sortDescending) "Highest first" else "Lowest first" },
                contentAlignment = Alignment.Center,
            ) { SfIcon(if (sortDescending) "arrow.down" else "arrow.up", 11.dp, GridironPalette.turf) }
        }
    }
}

/**
 * One banded leaderboard row. A player without the sorted metric gets a muted
 * dash, never a bar drawn from a different number.
 */
@Composable
fun LeaderboardTableRow(
    rank: Int,
    player: Player,
    metricLabel: String? = null,
    metricCategory: MetricCategory? = null,
    trendDelta: Double? = null,
    trendDecimals: Int = 3,
    valueOverride: String? = null,
    volume: String? = null,
    isSmallSample: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val metric = metricLabel?.let { label ->
        if (metricCategory != null) player.metrics.firstOrNull { it.label == label && it.category == metricCategory }
        else player.metrics.firstOrNull { it.label == label }
    }
    val percentile = metric?.percentile ?: 0
    val valueText = valueOverride ?: metric?.let { if (it.value.isNotEmpty()) it.value else it.percentile.ordinal } ?: "-"
    val valueColor = when {
        isSmallSample || metric?.isUnranked == true -> GridironPalette.inkTertiary
        valueOverride == null -> GridironPalette.textColor(percentile)
        else -> GridironPalette.ink
    }
    val subtitle = listOfNotNull(player.displayPosition, volume, if (isSmallSample) "small sample" else null).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().height(52.dp)
            .background(if (rank % 2 == 1) GridironPalette.surface else GridironPalette.surfaceAlt)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = GridironGeo.padInline)
            .testTag("leaderboardRow"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$rank", style = GridironType.statSmall, color = GridironPalette.inkSecondary, modifier = Modifier.width(42.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PlayerHeadshot(player.team, player.initials, 36.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FitText(player.name, GridironType.bodyBold, GridironPalette.ink, minScale = 0.85f)
                FitText(subtitle, GridironType.micro, GridironPalette.inkTertiary, minScale = 0.85f)
            }
        }
        Row(Modifier.width(44.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            TeamColorDot(player.team, 6.dp)
            Text(displayTeamAbbr(player.team), style = GridironType.small, color = GridironPalette.inkSecondary, maxLines = 1)
        }
        Row(
            Modifier.width(if (trendDelta == null) 92.dp else 56.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (metric != null || valueOverride != null) {
                if (valueOverride == null) {
                    if (metric?.isUnranked == true) Spacer(Modifier.width(40.dp))
                    else PercentileBarMini(percentile, Modifier.width(40.dp).alphaIf(isSmallSample, 0.35f))
                }
                FitText(valueText, GridironType.statSmall, valueColor, Modifier.width(48.dp), minScale = 0.7f, textAlign = TextAlign.End)
            } else {
                Text("-", style = GridironType.statSmall, color = GridironPalette.inkTertiary, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
            }
        }
        if (trendDelta != null) {
            Box(Modifier.width(46.dp), contentAlignment = Alignment.CenterEnd) { TrendArrow(trendDelta, trendDecimals) }
        }
    }
}

fun Modifier.alphaIf(condition: Boolean, alpha: Float): Modifier =
    if (condition) this.then(Modifier.graphicsAlpha(alpha)) else this

private fun Modifier.graphicsAlpha(alpha: Float): Modifier = this.alpha(alpha)

/** A hairline along the bottom edge, drawn rather than laid out. */
fun Modifier.bottomHairline(color: Color = GridironPalette.divider, width: Dp = GridironGeo.hairline): Modifier = drawBehind {
    val stroke = width.toPx()
    drawRect(color, topLeft = Offset(0f, size.height - stroke), size = androidx.compose.ui.geometry.Size(size.width, stroke))
}

fun Modifier.topHairline(color: Color = GridironPalette.divider, width: Dp = GridironGeo.hairline): Modifier = drawBehind {
    drawRect(color, size = androidx.compose.ui.geometry.Size(size.width, width.toPx()))
}

/** The card surface every module sits on. */
fun Modifier.gridironCard(stroke: Color = GridironPalette.hairline): Modifier = this
    .clip(RoundedCornerShape(GridironGeo.radiusCard))
    .background(GridironPalette.surface)
    .border(GridironGeo.hairline, stroke, RoundedCornerShape(GridironGeo.radiusCard))

@Composable
fun GridironSectionBar(title: String, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().height(GridironGeo.rowHeightHeader).background(GridironPalette.surfaceSunk),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title.uppercase(), style = GridironType.sectionTitle, color = GridironPalette.ink, modifier = Modifier.padding(start = GridironGeo.padCard))
        Spacer(Modifier.weight(1f))
        if (trailing != null) Row(Modifier.padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

@Composable
fun GridironSubSectionBar(title: String, trailing: String? = null, trailingColor: Color = GridironPalette.inkSecondary) {
    Row(
        Modifier.fillMaxWidth().height(26.dp).background(GridironPalette.surfaceAlt).bottomHairline().padding(horizontal = GridironGeo.padCard),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title.uppercase(), style = GridironType.micro, color = GridironPalette.inkSecondary)
        Spacer(Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = GridironType.statSmall, color = trailingColor)
    }
}

/** Underlined text tabs: the metric-category tier. */
@Composable
fun GridironTabs(tabs: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val haptic = rememberHaptic()
    Row(modifier.fillMaxWidth().background(GridironPalette.surface).bottomHairline(GridironPalette.hairline)) {
        tabs.forEach { tab ->
            val isSelected = selected == tab
            Column(
                Modifier.weight(1f).clickable {
                    onSelect(tab)
                    haptic()
                }.semantics { contentDescription = tab }.testTag("tab_$tab"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                    FitText(tab.uppercase(), GridironType.smallBold, if (isSelected) GridironPalette.ink else GridironPalette.inkTertiary, minScale = 0.75f)
                }
                Box(Modifier.fillMaxWidth().height(3.dp).background(if (isSelected) GridironPalette.turf else Color.Transparent))
            }
        }
    }
}

/** Player header on the midnight band: name, club, bio and draft lines. */
@Composable
fun PlayerIdentityStrip(player: Player, showOverallBadge: Boolean = false, profile: PlayerProfile? = null, injury: InjuryReport? = null) {
    val bioLine = if (profile == null) positionAndHandedness(player) else listOfNotNull(
        profile.jersey?.let { "#$it" },
        player.displayPosition,
        profile.age(LocalDate.now())?.let { "$it yrs" },
        profile.sizeLabel,
    ).joinToString(" · ")
    val originLine = profile?.let { p -> listOfNotNull(p.college, p.draftLabel).takeIf { it.isNotEmpty() }?.joinToString(" · ") }
    Row(
        Modifier.fillMaxWidth().background(GridironPalette.midnight).padding(GridironGeo.padPage),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PlayerHeadshot(player.team, player.initials, 72.dp, ring = Color.White)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FitText(player.name, GridironType.playerName, GridironPalette.inkOnDark, minScale = 0.7f)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FitText(displayTeamFullName(player.team), GridironType.bodyBold, Color.White.copy(alpha = 0.85f), Modifier.weight(1f, fill = false))
                if (injury != null) InjuryBadge(injury)
            }
            FitText(bioLine, GridironType.small, Color.White.copy(alpha = 0.65f))
            if (originLine != null) FitText(originLine, GridironType.small, Color.White.copy(alpha = 0.65f))
        }
        if (showOverallBadge) OverallPercentileBadge(player.overallPercentile)
    }
}

private fun positionAndHandedness(player: Player): String {
    val pos = player.displayPosition.trim()
    val hand = player.handedness.trim { it.isWhitespace() || !it.isLetterOrDigit() }
    return when {
        pos.isEmpty() && hand.isEmpty() -> ""
        hand.isEmpty() -> pos
        pos.isEmpty() -> hand
        else -> "$pos · $hand"
    }
}

/** "OUT · Hamstring", "Q · Ankle". */
@Composable
fun InjuryBadge(report: InjuryReport) {
    Text(
        listOfNotNull(report.shortStatus.uppercase(), report.injury).joinToString(" · "),
        style = GridironType.micro,
        color = Color.White,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (report.isOut) GridironPalette.performanceLow else Color(0.72f, 0.49f, 0.08f))
            .padding(horizontal = 7.dp, vertical = 3.dp)
            .semantics { contentDescription = "Injury report: ${report.status}${report.injury?.let { ", $it" } ?: ""}" },
    )
}

@Composable
fun TeamIdentityStrip(team: String, season: Int? = null) {
    val abbr = normalizedTeamAbbreviation(team)
    Row(
        Modifier.fillMaxWidth().background(GridironPalette.midnight).padding(GridironGeo.padPage),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(NFLTeamColor.color(abbr)), contentAlignment = Alignment.Center) {
            Text(abbr, style = GridironType.pageTitle, color = Color.White)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FitText(teamFullName(abbr), GridironType.playerName, GridironPalette.inkOnDark, minScale = 0.7f)
            Text("${season ?: LocalDate.now().year} Season", style = GridironType.small, color = Color.White.copy(alpha = 0.65f))
        }
    }
}

@Composable
fun rememberHaptic(): () -> Unit {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    return remember(haptic) { { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick) } }
}

/**
 * One slice of a card drawn as separate lazy items: the outline's sides on every
 * slice, its top edge and corners on the first, its bottom on the last.
 */
fun Modifier.cardSlice(first: Boolean, last: Boolean, stroke: Color = GridironPalette.hairline): Modifier {
    val radius = GridironGeo.radiusCard
    val shape = RoundedCornerShape(
        topStart = if (first) radius else 0.dp,
        topEnd = if (first) radius else 0.dp,
        bottomStart = if (last) radius else 0.dp,
        bottomEnd = if (last) radius else 0.dp,
    )
    return this.clip(shape).background(GridironPalette.surface).drawWithContent {
        drawContent()
        val w = GridironGeo.hairline.toPx()
        val r = radius.toPx()
        drawRect(stroke, topLeft = Offset(0f, if (first) r else 0f), size = androidx.compose.ui.geometry.Size(w, size.height - (if (first) r else 0f) - (if (last) r else 0f)))
        drawRect(stroke, topLeft = Offset(size.width - w, if (first) r else 0f), size = androidx.compose.ui.geometry.Size(w, size.height - (if (first) r else 0f) - (if (last) r else 0f)))
        if (first) {
            drawRect(stroke, topLeft = Offset(r, 0f), size = androidx.compose.ui.geometry.Size(size.width - 2 * r, w))
            drawArc(stroke, 180f, 90f, false, topLeft = Offset(w / 2, w / 2), size = androidx.compose.ui.geometry.Size(2 * r, 2 * r), style = androidx.compose.ui.graphics.drawscope.Stroke(w))
            drawArc(stroke, 270f, 90f, false, topLeft = Offset(size.width - 2 * r - w / 2, w / 2), size = androidx.compose.ui.geometry.Size(2 * r, 2 * r), style = androidx.compose.ui.graphics.drawscope.Stroke(w))
        }
        if (last) {
            drawRect(stroke, topLeft = Offset(r, size.height - w), size = androidx.compose.ui.geometry.Size(size.width - 2 * r, w))
            drawArc(stroke, 90f, 90f, false, topLeft = Offset(w / 2, size.height - 2 * r - w / 2), size = androidx.compose.ui.geometry.Size(2 * r, 2 * r), style = androidx.compose.ui.graphics.drawscope.Stroke(w))
            drawArc(stroke, 0f, 90f, false, topLeft = Offset(size.width - 2 * r - w / 2, size.height - 2 * r - w / 2), size = androidx.compose.ui.geometry.Size(2 * r, 2 * r), style = androidx.compose.ui.graphics.drawscope.Stroke(w))
        }
    }
}
