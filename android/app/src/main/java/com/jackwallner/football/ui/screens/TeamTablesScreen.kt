package com.jackwallner.football.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jackwallner.football.model.StandingsRow
import com.jackwallner.football.model.TeamRating
import com.jackwallner.football.model.displayTeamAbbr
import com.jackwallner.football.model.normalizedTeamAbbreviation
import com.jackwallner.football.model.teamFullName
import com.jackwallner.football.model.teamNickname
import com.jackwallner.football.ui.LocalGraph
import com.jackwallner.football.ui.components.FitText
import com.jackwallner.football.ui.components.PushedScreen
import com.jackwallner.football.ui.components.TeamColorDot
import com.jackwallner.football.ui.components.bottomBarPadding
import com.jackwallner.football.ui.components.bottomHairline
import com.jackwallner.football.ui.components.gridironCard
import com.jackwallner.football.ui.nav.LocalNavigator
import com.jackwallner.football.ui.nav.Route
import com.jackwallner.football.ui.theme.GridironGeo
import com.jackwallner.football.ui.theme.GridironPalette
import com.jackwallner.football.ui.theme.GridironType

/** Division standings as their own pushed page. */
@Composable
fun StandingsScreen() {
    val vm = LocalGraph.current.dashboard
    PushedScreen("Standings") {
        GamesTeamsUi.RefreshBox(onRefresh = { vm.load() }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp)) {
                StandingsTable(NFL_DIVISIONS)
                Spacer(Modifier.padding(bottomBarPadding()))
            }
        }
    }
}

/** The league ranked by power rating as its own pushed page. */
@Composable
fun PowerRankingsScreen() {
    val vm = LocalGraph.current.dashboard
    PushedScreen("Power Rankings") {
        GamesTeamsUi.RefreshBox(onRefresh = { vm.load() }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp)) {
                PowerRankingsTable()
                Spacer(Modifier.padding(bottomBarPadding()))
            }
        }
    }
}

/**
 * Division standings from posted finals, with each club's power rating beside
 * its record.
 */
@Composable
internal fun StandingsTable(divisions: List<Division>) {
    val vm = LocalGraph.current.dashboard
    val navigator = LocalNavigator.current
    val table = remember(vm.games) { vm.standings }
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        divisions.forEach { division ->
            val rows = StandingsRow.ordered(division.teams.mapNotNull { table[it] })
            Column(Modifier.fillMaxWidth().gridironCard().testTag("division_${division.name}")) {
                StandingsHeader(division.name)
                rows.forEachIndexed { index, row ->
                    StandingsLine(
                        row,
                        Modifier
                            .background(if (index % 2 == 0) GridironPalette.surface else GridironPalette.surfaceAlt)
                            .clickable { navigator.push(Route.Team(row.team)) },
                    )
                }
            }
        }
        Text(
            "Ordered by win percentage, then point differential, not the NFL's full tiebreakers. PWR is the StatScout Power Rating: points better or worse than an average team on a neutral field.",
            style = GridironType.micro,
            color = GridironPalette.inkTertiary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun StandingsHeader(title: String) {
    Row(
        Modifier.fillMaxWidth().height(GridironGeo.rowHeightHeader).background(GridironPalette.surfaceAlt)
            .bottomHairline(GridironPalette.divider).padding(horizontal = GridironGeo.padInline),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title.uppercase(), style = GridironType.micro, color = GridironPalette.inkTertiary, modifier = Modifier.weight(1f))
        HeaderCell("W-L", 44.dp)
        HeaderCell("DIFF", 44.dp)
        HeaderCell("STRK", 40.dp)
        HeaderCell("PWR", 48.dp)
    }
}

@Composable
private fun HeaderCell(text: String, width: Dp) {
    Text(text, style = GridironType.micro, color = GridironPalette.inkTertiary, textAlign = TextAlign.End, modifier = Modifier.width(width))
}

@Composable
private fun StandingsLine(row: StandingsRow, modifier: Modifier = Modifier) {
    val vm = LocalGraph.current.dashboard
    val rating = vm.teamRating(row.team)
    val diff = when {
        row.games == 0 -> "-"
        row.differential > 0 -> "+${row.differential}"
        else -> "${row.differential}"
    }
    val diffColor = when {
        row.differential > 0 -> GridironPalette.performanceHigh
        row.differential < 0 -> GridironPalette.performanceLow
        else -> GridironPalette.inkSecondary
    }
    val description = "${teamFullName(row.team)}, ${row.record}, point differential ${row.differential}" +
        (rating?.let { ", power rating ${TeamRating.signed(it.rating)}" } ?: "")
    Row(
        modifier.fillMaxWidth().height(44.dp).bottomHairline(GridironPalette.divider)
            .padding(horizontal = GridironGeo.padInline)
            .clearAndSetSemantics { contentDescription = description }
            .testTag("teamRow"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TeamColorDot(row.team, 10.dp)
            Text(displayTeamAbbr(row.team), style = GridironType.bodyBold, color = GridironPalette.ink, modifier = Modifier.width(40.dp))
            FitText(teamNickname(row.team), GridironType.small, GridironPalette.inkTertiary, Modifier.weight(1f, fill = false), minScale = 0.8f)
        }
        NumberCell(row.record, 44.dp, GridironPalette.ink)
        NumberCell(diff, 44.dp, diffColor)
        NumberCell(row.streak ?: "-", 40.dp, GridironPalette.inkSecondary)
        NumberCell(rating?.let { TeamRating.signed(it.rating) } ?: "-", 48.dp, GridironPalette.ink)
    }
}

@Composable
private fun NumberCell(text: String, width: Dp, color: Color) {
    Text(text, style = GridironType.statSmall, color = color, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(width))
}

/**
 * The league ranked by power rating, after Hawk Blogger's HB Power Rankings:
 * what a club does minus what it allows, adjusted for who it played.
 */
@Composable
internal fun PowerRankingsTable() {
    val vm = LocalGraph.current.dashboard
    val navigator = LocalNavigator.current
    val ratings = vm.teamRatings.values.sortedBy { it.rank }
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (ratings.isEmpty()) {
            Column(Modifier.fillMaxWidth().background(GridironPalette.surface, androidx.compose.foundation.shape.RoundedCornerShape(GridironGeo.radiusCard)).padding(vertical = 32.dp)) {
                GamesTeamsUi.Unavailable("chart.bar.xaxis", "Power ratings loading", "Ratings arrive with the next update. Pull to refresh.")
            }
        } else {
            val standings = remember(vm.games) { vm.standings }
            Column(Modifier.fillMaxWidth().gridironCard()) {
                PowerHeader()
                ratings.forEachIndexed { index, rating ->
                    PowerLine(
                        rating,
                        standings[normalizedTeamAbbreviation(rating.team)]?.record,
                        Modifier
                            .background(if (index % 2 == 0) GridironPalette.surface else GridironPalette.surfaceAlt)
                            .clickable { navigator.push(Route.Team(rating.team)) },
                    )
                }
            }
        }
        Text(
            powerFootnote(ratings),
            style = GridironType.micro,
            color = GridironPalette.inkTertiary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        )
    }
}

private fun powerFootnote(ratings: List<TeamRating>): String {
    val through = ratings.firstOrNull()?.let {
        if (it.throughWeek > 0) "Through Week ${it.throughWeek}. " else "Preseason: last season's ratings, regressed. "
    } ?: ""
    return through + "Points per game better or worse than an average team on a neutral field: EPA per dropback and per run plus points, for minus against, adjusted for schedule. Early in the season last year counts as five games of evidence. Read two ratings like a spread, with about two points for home field. Modeled on Hawk Blogger's HB Power Rankings."
}

@Composable
private fun PowerHeader() {
    Row(
        Modifier.fillMaxWidth().height(GridironGeo.rowHeightHeader).background(GridironPalette.surfaceAlt)
            .bottomHairline(GridironPalette.divider).padding(horizontal = GridironGeo.padInline),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("#", style = GridironType.micro, color = GridironPalette.inkTertiary, modifier = Modifier.width(28.dp))
        Text("TEAM", style = GridironType.micro, color = GridironPalette.inkTertiary, modifier = Modifier.weight(1f))
        HeaderCell("OFF", 44.dp)
        HeaderCell("DEF", 44.dp)
        HeaderCell("RATING", 60.dp)
    }
}

@Composable
private fun PowerLine(rating: TeamRating, record: String?, modifier: Modifier = Modifier) {
    val description = "${rating.rank}. ${teamFullName(rating.team)}, rating ${TeamRating.signed(rating.rating)}, " +
        "offense ${TeamRating.signed(rating.offense)}, defense ${TeamRating.signed(rating.defense)}"
    Row(
        modifier.fillMaxWidth().height(GridironGeo.rowHeight).bottomHairline(GridironPalette.divider)
            .padding(horizontal = GridironGeo.padInline)
            .clearAndSetSemantics { contentDescription = description }
            .testTag("powerRow"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${rating.rank}", style = GridironType.statSmall, color = GridironPalette.inkSecondary, modifier = Modifier.width(28.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TeamColorDot(rating.team, 10.dp)
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                FitText(teamFullName(rating.team), GridironType.bodyBold, GridironPalette.ink, minScale = 0.8f)
                record?.let { Text(it, style = GridironType.micro, color = GridironPalette.inkTertiary) }
            }
        }
        Text(TeamRating.signed(rating.offense), style = GridironType.statSmall, color = tint(rating.offense), textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
        Text(TeamRating.signed(rating.defense), style = GridironType.statSmall, color = tint(rating.defense), textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
        Text(TeamRating.signed(rating.rating), style = GridironType.statMed, color = tint(rating.rating), textAlign = TextAlign.End, modifier = Modifier.width(60.dp))
    }
}

private fun tint(value: Double): Color = when {
    value >= 1 -> GridironPalette.performanceHigh
    value <= -1 -> GridironPalette.performanceLow
    else -> GridironPalette.inkSecondary
}
