package com.jackwallner.football.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsFootball
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jackwallner.football.data.PaywallTrigger
import com.jackwallner.football.model.Game
import com.jackwallner.football.model.GameBoxScore
import com.jackwallner.football.model.GameDetail
import com.jackwallner.football.model.GameStatus
import com.jackwallner.football.model.PlayerGameLog
import com.jackwallner.football.model.RatedValue
import com.jackwallner.football.model.SeasonPhase
import com.jackwallner.football.model.displayTeamAbbr
import com.jackwallner.football.model.formatUpToOneDecimal
import com.jackwallner.football.model.normalizedTeamAbbreviation
import com.jackwallner.football.model.ordinal
import com.jackwallner.football.model.teamFullName
import com.jackwallner.football.ui.LocalGraph
import com.jackwallner.football.ui.components.FitText
import com.jackwallner.football.ui.components.GridironSectionBar
import com.jackwallner.football.ui.components.GridironSegmented
import com.jackwallner.football.ui.components.PercentileBarMini
import com.jackwallner.football.ui.components.PlusCTAStyle
import com.jackwallner.football.ui.components.PlusDirectCTA
import com.jackwallner.football.ui.components.PushedScreen
import com.jackwallner.football.ui.components.Segment
import com.jackwallner.football.ui.components.SfIcon
import com.jackwallner.football.ui.components.StatGlossaryLink
import com.jackwallner.football.ui.components.TeamColorDot
import com.jackwallner.football.ui.components.bottomBarPadding
import com.jackwallner.football.ui.components.bottomHairline
import com.jackwallner.football.ui.components.gridironCard
import com.jackwallner.football.ui.nav.LocalNavigator
import com.jackwallner.football.ui.nav.Route
import com.jackwallner.football.ui.nav.rememberRetained
import com.jackwallner.football.ui.theme.GridironGeo
import com.jackwallner.football.ui.theme.GridironPalette
import com.jackwallner.football.ui.theme.GridironType
import com.jackwallner.football.ui.theme.NFLTeamColor
import com.jackwallner.football.ui.theme.sf
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * One game: the score first, then the box score, then the few advanced numbers
 * the per-player feed can total honestly.
 */
@Composable
fun GameDetailScreen(gameId: String) {
    val graph = LocalGraph.current
    val vm = graph.dashboard
    val game = vm.game(gameId)
    val scope = rememberCoroutineScope()

    // Fetched data outlives a pushed player profile, like SwiftUI view state.
    var detail by rememberRetained("detail") { mutableStateOf<GameDetail?>(null) }
    var isDetailLoading by rememberRetained("detailLoading") { mutableStateOf(false) }
    var detailFailed by rememberRetained("detailFailed") { mutableStateOf(false) }
    var logs by rememberRetained("logs") { mutableStateOf<List<PlayerGameLog>>(emptyList()) }
    var isLoading by rememberRetained("logsLoading") { mutableStateOf(false) }
    var loadError by rememberRetained("logsError") { mutableStateOf<String?>(null) }
    var boxTeam by rememberRetained("boxTeam") { mutableStateOf("") }

    /**
     * Tracked apart from the box score, so a request still in flight or one
     * that failed never reads as "not published yet".
     */
    suspend fun loadDetail() {
        val current = vm.game(gameId)
        if (current == null || current.status() == GameStatus.UPCOMING) return
        isDetailLoading = detail == null
        try {
            vm.fetchGameDetail(gameId)?.let { detail = it }
            detailFailed = false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (detail == null) detailFailed = true
        } finally {
            isDetailLoading = false
        }
    }

    suspend fun loadLogs(force: Boolean) {
        val current = vm.game(gameId)
        if (current == null || !(current.status() == GameStatus.FINAL || vm.hasStats(current))) return
        if (!force && logs.isNotEmpty() && vm.hasStats(current)) return
        isLoading = logs.isEmpty()
        try {
            logs = vm.fetchGameLogs(gameId)
            loadError = null
        } catch (e: CancellationException) {
            isLoading = false
            throw e
        } catch (_: Exception) {
            if (logs.isEmpty()) loadError = "Couldn't load the box score. Pull to try again."
        }
        if (boxTeam.isEmpty()) boxTeam = current.awayTeam
        isLoading = false
    }

    LaunchedEffect(Unit) { vm.loadGames() }
    LaunchedEffect(gameId, game?.let { vm.hasStats(it) } ?: false, vm.freshnessRevision ?: "none") {
        coroutineScope {
            launch { loadDetail() }
            launch { loadLogs(force = false) }
        }
    }

    val title = game?.let { "${displayTeamAbbr(it.awayTeam)} at ${displayTeamAbbr(it.homeTeam)}" } ?: "Game"
    PushedScreen(title) {
        GamesTeamsUi.RefreshBox(onRefresh = {
            vm.loadGames(force = true)
            loadLogs(force = true)
            loadDetail()
        }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                when {
                    game != null -> {
                        Header(game)
                        DetailBody(game, detail, logs, isDetailLoading, detailFailed, isLoading, loadError, boxTeam, { boxTeam = it }) {
                            scope.launch { loadDetail() }
                        }
                    }
                    vm.isGamesLoading -> GamesTeamsUi.Spinner(modifier = Modifier.padding(vertical = 64.dp))
                    else -> GamesTeamsUi.Unavailable("calendar.badge.exclamationmark", "Game not found", modifier = Modifier.padding(vertical = 48.dp))
                }
                Spacer(Modifier.padding(bottomBarPadding()))
            }
        }
    }
}

// MARK: - Header

@Composable
private fun Header(game: Game) {
    val status = game.status()
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 12.dp).gridironCard()
            .padding(vertical = 18.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TeamColumn(game.awayTeam, game, "Away", Modifier.weight(1f))
            Column(
                Modifier.widthIn(min = 110.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (game.isFinal) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        ScoreText(game.awayScore, game.result(game.awayTeam) != "L")
                        Text("-", style = GridironType.statLarge, color = GridironPalette.inkTertiary)
                        ScoreText(game.homeScore, game.result(game.homeTeam) != "L")
                    }
                } else {
                    Text(
                        if (status == GameStatus.UPCOMING) game.kickoffLabel() else "In progress",
                        style = GridironType.cardTitle,
                        color = if (status == GameStatus.UPCOMING) GridironPalette.ink else GridironPalette.performanceLow,
                    )
                }
                Text(statusLine(game, status), style = GridironType.micro, color = GridironPalette.inkTertiary)
            }
            TeamColumn(game.homeTeam, game, "Home", Modifier.weight(1f))
        }
        Text(
            listOfNotNull(game.roundLabel, game.dayLabel(), game.stadium).joinToString(" · "),
            style = GridironType.micro,
            color = GridironPalette.inkTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TeamColumn(team: String, game: Game, label: String, modifier: Modifier = Modifier) {
    val vm = LocalGraph.current.dashboard
    val navigator = LocalNavigator.current
    val record = if (game.seasonPhase == SeasonPhase.REGULAR) vm.record(team, game) else null
    Column(
        modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(GridironGeo.radiusCard))
            .clickable(onClickLabel = "Opens the team") { navigator.push(Route.Team(normalizedTeamAbbreviation(team))) }
            .padding(vertical = 4.dp)
            .testTag("gameTeam_$label"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(NFLTeamColor.color(team)), contentAlignment = Alignment.Center) {
            FitText(
                displayTeamAbbr(team), GridironType.smallBold, Color.White,
                Modifier.padding(horizontal = 4.dp), minScale = 0.7f, textAlign = TextAlign.Center,
            )
        }
        Text(
            teamFullName(team),
            style = GridironType.smallBold,
            color = GridironPalette.ink,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        Text(
            (listOf(label) + listOfNotNull(record)).joinToString(" · "),
            style = GridironType.micro,
            color = GridironPalette.inkTertiary,
        )
    }
}

@Composable
private fun ScoreText(score: Int?, winner: Boolean) {
    Text(
        score?.toString() ?: "-",
        style = GridironType.statHero,
        color = if (winner) GridironPalette.ink else GridironPalette.inkTertiary,
    )
}

private fun statusLine(game: Game, status: GameStatus): String = when (status) {
    GameStatus.FINAL -> if (game.overtime) "Final/OT" else "Final"
    GameStatus.IN_PROGRESS, GameStatus.AWAITING_SCORE -> "Score posts at the final"
    GameStatus.UPCOMING -> game.kickoff?.let { GamesTeamsUi.monthDay(it) } ?: ""
}

// MARK: - Body

/**
 * Advanced first, the way analytics box scores read: how the game swung, how
 * efficiently each offense played, the plays that decided it, who drove it.
 * The traditional box score follows for the counts.
 */
@Composable
private fun DetailBody(
    game: Game,
    detail: GameDetail?,
    logs: List<PlayerGameLog>,
    isDetailLoading: Boolean,
    detailFailed: Boolean,
    isLoading: Boolean,
    loadError: String?,
    boxTeam: String,
    onBoxTeam: (String) -> Unit,
    onRetryDetail: () -> Unit,
) {
    val boxScore = remember(logs) { GameBoxScore(logs) }
    if (detail != null || logs.isNotEmpty()) {
        if (detail != null) {
            if (detail.winProbability.size > 2) WinProbabilityCard(detail, game)
            EfficiencyCard(detail, game)
            if (detail.bigPlays.isNotEmpty()) BigPlaysCard(detail, game)
            PlayerEfficiencyCards(detail, game)
        } else if (isDetailLoading) {
            GamesTeamsUi.Spinner("Loading advanced breakdown", Modifier.padding(vertical = 40.dp))
        } else if (detailFailed) {
            Notice(
                sf("wifi.exclamationmark"),
                "Couldn't load the advanced breakdown",
                "Win probability, EPA and success rate didn't load. Check your connection and try again.",
                action = "Try again" to onRetryDetail,
            )
        } else {
            Notice(
                sf("chart.xyaxis.line"),
                "Advanced breakdown on the way",
                "Win probability, EPA and success rate post once play-by-play is published, usually within a few hours of the final.",
            )
        }

        if (logs.isNotEmpty()) {
            SectionHeading("Box score")
            LeadersCard(boxScore, game)
            TeamStatsCard(boxScore, game)
            BoxScoreCard(boxScore, game, boxTeam, onBoxTeam)
        }

        Footnote(
            "Percentiles rank each number against every team game (or every player game with enough volume) this season, and update as new games arrive. EPA is expected points added; success rate is the share of plays with positive EPA.",
        )
        Box(Modifier.padding(horizontal = 12.dp).padding(top = 12.dp)) { StatGlossaryLink() }
    } else if (isLoading) {
        GamesTeamsUi.Spinner("Loading box score", Modifier.padding(vertical = 40.dp))
    } else if (loadError != null) {
        Footnote(loadError)
    } else {
        when (game.status()) {
            GameStatus.FINAL -> Notice(
                Icons.Filled.Schedule,
                "Stats arriving",
                "The final score is in. Player stats usually post within a few hours of the final whistle.",
            )
            GameStatus.IN_PROGRESS, GameStatus.AWAITING_SCORE -> Notice(
                Icons.Filled.SportsFootball,
                "Game in progress",
                "The score and box score post when the game goes final.",
            )
            GameStatus.UPCOMING -> Notice(
                sf("calendar"),
                "Not started yet",
                "The score and box score post here when the game goes final. Scout both rosters from the team pages above.",
            )
        }
    }
}

@Composable
private fun SectionHeading(title: String) {
    Text(
        title.uppercase(),
        style = GridironType.sectionTitle,
        color = GridironPalette.inkSecondary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 24.dp),
    )
}

@Composable
private fun Footnote(text: String) {
    Text(
        text,
        style = GridironType.micro,
        color = GridironPalette.inkTertiary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp),
    )
}

@Composable
private fun FootnoteRow(text: String) {
    Text(
        text,
        style = GridironType.micro,
        color = GridironPalette.inkTertiary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = GridironGeo.padCard, vertical = 10.dp),
    )
}

/** A titled card: the section bar, then rows. */
@Composable
private fun CardBlock(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 12.dp).gridironCard()) {
        GridironSectionBar(title)
        content()
    }
}

@Composable
private fun Notice(
    icon: ImageVector,
    title: String,
    text: String,
    action: Pair<String, () -> Unit>? = null,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 12.dp).gridironCard().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SfIcon(icon, 24.dp, GridironPalette.inkTertiary)
        Text(title, style = GridironType.cardTitle, color = GridironPalette.ink, textAlign = TextAlign.Center)
        Text(text, style = GridironType.small, color = GridironPalette.inkSecondary, textAlign = TextAlign.Center)
        action?.let { (label, perform) ->
            Box(
                Modifier.padding(top = 4.dp).heightIn(min = 44.dp).clip(CircleShape).background(GridironPalette.surfaceSunk)
                    .clickable(onClick = perform).padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = GridironType.smallBold, color = GridironPalette.turf) }
        }
    }
}

// MARK: - Box score pieces

@Composable
private fun LeadersCard(boxScore: GameBoxScore, game: Game) {
    CardBlock("Game leaders") {
        boxScore.leaders.forEachIndexed { index, leader ->
            PlayerRow(leader.line, index, game) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        TeamColorDot(leader.line.team, 6.dp)
                        Text(
                            "${leader.title.uppercase()} · ${displayTeamAbbr(leader.line.team)}",
                            style = GridironType.micro,
                            color = GridironPalette.inkTertiary,
                        )
                    }
                    NameText(leader.line, game)
                    Text(
                        leader.summary,
                        style = GridironType.small.copy(fontFeatureSettings = "tnum"),
                        color = GridironPalette.inkSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun TeamStatsCard(boxScore: GameBoxScore, game: Game) {
    val away = boxScore.totals(game.awayTeam)
    val home = boxScore.totals(game.homeTeam)
    CardBlock("Team stats") {
        Row(
            Modifier.fillMaxWidth().height(30.dp).background(GridironPalette.surfaceAlt).padding(horizontal = GridironGeo.padCard),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(displayTeamAbbr(game.awayTeam), style = GridironType.smallBold, color = GridironPalette.inkSecondary, modifier = Modifier.width(64.dp))
            Spacer(Modifier.weight(1f))
            Text(
                displayTeamAbbr(game.homeTeam),
                style = GridironType.smallBold,
                color = GridironPalette.inkSecondary,
                textAlign = TextAlign.End,
                modifier = Modifier.width(64.dp),
            )
        }
        ComparisonRow("Total yards", away.totalYards, home.totalYards, game, higherIsBetter = true)
        ComparisonRow("Passing yards", away.passingYards - away.sackYardsLost, home.passingYards - home.sackYardsLost, game, higherIsBetter = true)
        ComparisonRow("Rushing yards", away.rushingYards, home.rushingYards, game, higherIsBetter = true)
        ComparisonRow("First downs", away.firstDowns, home.firstDowns, game, higherIsBetter = true)
        ComparisonRow("Sacks taken", away.sacksTaken, home.sacksTaken, game, higherIsBetter = false)
        FootnoteRow("Totals add up each team's player lines.")
    }
}

@Composable
private fun ComparisonRow(label: String, away: Double, home: Double, game: Game, higherIsBetter: Boolean) {
    val awayBetter = if (higherIsBetter) away > home else away < home
    val homeBetter = if (higherIsBetter) home > away else home < away
    val awayText = formatCount(away)
    val homeText = formatCount(home)
    Row(
        Modifier.fillMaxWidth().height(36.dp).bottomHairline(GridironPalette.divider).padding(horizontal = GridironGeo.padCard)
            .clearAndSetSemantics {
                contentDescription = "$label: ${teamFullName(game.awayTeam)} $awayText, ${teamFullName(game.homeTeam)} $homeText"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            awayText,
            style = GridironType.statMed.copy(fontWeight = if (awayBetter) FontWeight.Bold else FontWeight.Normal),
            color = if (awayBetter) GridironPalette.ink else GridironPalette.inkSecondary,
            modifier = Modifier.width(64.dp),
        )
        Spacer(Modifier.weight(1f))
        Text(label, style = GridironType.small, color = GridironPalette.inkSecondary)
        Spacer(Modifier.weight(1f))
        Text(
            homeText,
            style = GridironType.statMed.copy(fontWeight = if (homeBetter) FontWeight.Bold else FontWeight.Normal),
            color = if (homeBetter) GridironPalette.ink else GridironPalette.inkSecondary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(64.dp),
        )
    }
}

private fun formatCount(value: Double): String = NumberFormat.getIntegerInstance(Locale.US).format(swiftRoundValue(value))

private fun swiftRoundValue(value: Double): Long = if (value < 0) -Math.floor(-value + 0.5).toLong() else Math.floor(value + 0.5).toLong()

// MARK: - Advanced

@Composable
private fun WinProbabilityCard(detail: GameDetail, game: Game) {
    val points = detail.winProbability
    val end = max(3600.0, points.lastOrNull()?.elapsed ?: 3600.0)
    val homeColor = NFLTeamColor.color(game.homeTeam)
    val homeAbbr = displayTeamAbbr(game.homeTeam)
    val awayAbbr = displayTeamAbbr(game.awayTeam)
    CardBlock("Win probability") {
        Column(Modifier.padding(GridironGeo.padCard), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            WinProbabilityChart(
                points, end, homeColor, homeAbbr, awayAbbr,
                Modifier.fillMaxWidth().height(160.dp)
                    .semantics { contentDescription = "Win probability chart for ${teamFullName(game.homeTeam)}" },
            )
            Text(
                "${teamFullName(game.homeTeam)} chance to win, play by play. Up is $homeAbbr, down is $awayAbbr.",
                style = GridironType.micro,
                color = GridironPalette.inkTertiary,
            )
        }
    }
}

/** Home win probability as a step line, quarter gridlines under it. */
@Composable
private fun WinProbabilityChart(
    points: List<GameDetail.WinProbabilityPoint>,
    end: Double,
    homeColor: Color,
    homeAbbr: String,
    awayAbbr: String,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = GridironType.micro.copy(color = GridironPalette.inkTertiary)
    val ticks = listOf(0.0, 900.0, 1800.0, 2700.0) + if (end > 3600) listOf(3600.0) else emptyList()
    Canvas(modifier) {
        val left = 36.dp.toPx()
        val bottom = 18.dp.toPx()
        val top = 6.dp.toPx()
        val right = 6.dp.toPx()
        val plotW = size.width - left - right
        val plotH = size.height - top - bottom
        fun x(elapsed: Double) = left + (elapsed / end).toFloat() * plotW
        fun y(pct: Double) = top + (1f - (pct / 100.0).toFloat()) * plotH

        for (tick in ticks) {
            drawLine(GridironPalette.divider, Offset(x(tick), top), Offset(x(tick), top + plotH), strokeWidth = 1f)
            val label = if (tick >= 3600) "OT" else "Q${(tick / 900).toInt() + 1}"
            drawText(measurer, label, Offset(x(tick) + 2.dp.toPx(), top + plotH + 2.dp.toPx()), style = labelStyle)
        }
        drawLine(
            GridironPalette.divider, Offset(left, y(50.0)), Offset(left + plotW, y(50.0)),
            strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
        )
        // The leading labels: home at the top, even in the middle, away at the bottom.
        listOf(100.0 to homeAbbr, 50.0 to "50%", 0.0 to awayAbbr).forEach { (pct, text) ->
            val layout = measurer.measure(text, labelStyle)
            val ty = (y(pct) - layout.size.height / 2f).coerceIn(0f, size.height - bottom - layout.size.height)
            drawText(layout, topLeft = Offset(0f, ty))
        }

        val path = Path()
        points.forEachIndexed { index, point ->
            val px = x(point.elapsed)
            val py = y(point.homeWinProbability * 100)
            if (index == 0) path.moveTo(px, py)
            else {
                // stepEnd: hold the previous value until this point, then jump.
                path.lineTo(px, y(points[index - 1].homeWinProbability * 100))
                path.lineTo(px, py)
            }
        }
        drawPath(path, homeColor, style = Stroke(width = 2.dp.toPx()))
    }
}

private enum class RateStyle {
    EPA, PERCENT, DECIMAL, SIGNED_DECIMAL, COUNT;

    fun format(value: Double): String = when (this) {
        EPA -> signed(value, 2)
        PERCENT -> String.format(Locale.US, "%.0f", value * 100) + "%"
        DECIMAL -> String.format(Locale.US, "%.1f", value)
        SIGNED_DECIMAL -> signed(value, 1)
        COUNT -> formatCount(value)
    }

    private fun signed(value: Double, decimals: Int): String {
        val text = String.format(Locale.US, "%.${decimals}f", value)
        return if (value > 0) "+$text" else text
    }
}

private data class EfficiencyMetric(
    val label: String,
    val key: String,
    val style: RateStyle,
    /** Shown instead of the rate when present, e.g. "3/9" on third down. */
    val fraction: Pair<String, String>? = null,
)

private val efficiencyMetrics = listOf(
    EfficiencyMetric("EPA per play", "epa_per_play", RateStyle.EPA),
    EfficiencyMetric("Success rate", "success_rate", RateStyle.PERCENT),
    EfficiencyMetric("Dropback EPA", "pass_epa_per_dropback", RateStyle.EPA),
    EfficiencyMetric("Dropback success", "pass_success_rate", RateStyle.PERCENT),
    EfficiencyMetric("Rush EPA", "rush_epa_per_carry", RateStyle.EPA),
    EfficiencyMetric("Rush success", "rush_success_rate", RateStyle.PERCENT),
    EfficiencyMetric("Explosive plays", "explosive_play_rate", RateStyle.PERCENT),
    EfficiencyMetric("Early-down pass rate", "early_down_pass_rate", RateStyle.PERCENT),
    EfficiencyMetric("Pass rate over expected", "pass_rate_over_expected", RateStyle.PERCENT),
    EfficiencyMetric("Yards per play", "yards_per_play", RateStyle.DECIMAL),
    EfficiencyMetric("Third down", "third_down_rate", RateStyle.PERCENT, "third_down_conversions" to "third_down_attempts"),
    EfficiencyMetric("Red zone TDs", "red_zone_td_rate", RateStyle.PERCENT, "red_zone_tds" to "red_zone_trips"),
    EfficiencyMetric("CPOE", "cpoe", RateStyle.SIGNED_DECIMAL),
    EfficiencyMetric("Avg depth of target", "adot", RateStyle.DECIMAL),
    EfficiencyMetric("Sack rate", "sack_rate", RateStyle.PERCENT),
    EfficiencyMetric("Turnovers", "turnovers", RateStyle.COUNT),
)

@Composable
private fun EfficiencyCard(detail: GameDetail, game: Game) {
    val away = detail.stats(game.awayTeam)
    val home = detail.stats(game.homeTeam)
    CardBlock("Team efficiency") {
        Row(
            Modifier.fillMaxWidth().height(30.dp).background(GridironPalette.surfaceAlt).padding(horizontal = GridironGeo.padCard),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TeamLabel(game.awayTeam)
            Spacer(Modifier.weight(1f))
            Text("Bars: percentile vs all team games", style = GridironType.micro, color = GridironPalette.inkTertiary)
            Spacer(Modifier.weight(1f))
            TeamLabel(game.homeTeam)
        }
        efficiencyMetrics.forEachIndexed { index, metric ->
            if (away[metric.key] != null || home[metric.key] != null) EfficiencyRow(metric, away, home, index, game)
        }
    }
}

@Composable
private fun TeamLabel(team: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        TeamColorDot(team, 8.dp)
        Text(displayTeamAbbr(team), style = GridironType.smallBold, color = GridironPalette.ink)
    }
}

@Composable
private fun EfficiencyRow(
    metric: EfficiencyMetric,
    away: Map<String, RatedValue>,
    home: Map<String, RatedValue>,
    index: Int,
    game: Game,
) {
    fun text(side: Map<String, RatedValue>): String {
        metric.fraction?.let { (madeKey, triesKey) ->
            val made = side[madeKey]
            val tries = side[triesKey]
            if (made != null && tries != null && tries.value > 0) return "${made.value.toInt()}/${tries.value.toInt()}"
        }
        return side[metric.key]?.let { metric.style.format(it.value) } ?: "-"
    }
    val awayText = text(away)
    val homeText = text(home)
    val description = "${metric.label}: ${teamFullName(game.awayTeam)} $awayText" +
        (away[metric.key]?.percentile?.let { ", ${it.ordinal} percentile" } ?: "") + ", " +
        "${teamFullName(game.homeTeam)} $homeText" +
        (home[metric.key]?.percentile?.let { ", ${it.ordinal} percentile" } ?: "")
    Row(
        Modifier.fillMaxWidth().height(40.dp)
            .background(if (index % 2 == 0) GridironPalette.surface else GridironPalette.surfaceAlt)
            .padding(horizontal = GridironGeo.padCard)
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RatedCell(awayText, away[metric.key]?.percentile, leading = true)
        FitText(
            metric.label, GridironType.small, GridironPalette.inkSecondary,
            Modifier.weight(1f), minScale = 0.8f, textAlign = TextAlign.Center,
        )
        RatedCell(homeText, home[metric.key]?.percentile, leading = false)
    }
}

/** A value over a thin percentile bar, the value tinted by its rank. */
@Composable
private fun RatedCell(text: String, percentile: Int?, leading: Boolean) {
    Column(
        Modifier.width(64.dp),
        horizontalAlignment = if (leading) Alignment.Start else Alignment.End,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(text, style = GridironType.statMed, color = percentile?.let { GridironPalette.textColor(it) } ?: GridironPalette.ink, maxLines = 1)
        if (percentile != null) {
            PercentileBarMini(percentile, Modifier.width(44.dp).graphicsLayer(scaleX = if (leading) 1f else -1f), height = 4.dp)
        } else {
            Spacer(Modifier.size(width = 44.dp, height = 4.dp))
        }
    }
}

@Composable
private fun BigPlaysCard(detail: GameDetail, game: Game) {
    CardBlock("Plays that swung it") {
        detail.bigPlays.forEachIndexed { index, play ->
            val isHome = normalizedTeamAbbreviation(play.team) == normalizedTeamAbbreviation(game.homeTeam)
            val swing = if (isHome) play.homeWPA else -play.homeWPA
            val swingText = String.format(Locale.US, "%+.0f", swing * 100).let { if (it == "-0") "+0" else it } + "%"
            Row(
                Modifier.fillMaxWidth()
                    .background(if (index % 2 == 0) GridironPalette.surface else GridironPalette.surfaceAlt)
                    .padding(horizontal = GridironGeo.padInline, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.width(58.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Q${play.qtr} ${if (play.clock.startsWith("0")) play.clock.drop(1) else play.clock}",
                        style = GridironType.micro,
                        color = GridironPalette.inkTertiary,
                        maxLines = 1,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        TeamColorDot(play.team, 6.dp)
                        Text(displayTeamAbbr(play.team), style = GridironType.micro, color = GridironPalette.inkSecondary)
                    }
                }
                Text(
                    cleanDescription(play.description),
                    style = GridironType.small,
                    color = GridironPalette.ink,
                    maxLines = 3,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    swingText,
                    style = GridironType.statMed,
                    color = if (swing >= 0) GridironPalette.performanceHigh else GridironPalette.performanceLow,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(48.dp),
                )
            }
        }
        FootnoteRow("Change in the offense's win probability on the play.")
    }
}

private val jerseyPrefix = Regex("""\b\d{1,2}-(?=[A-Z])""")

/**
 * Play-by-play text starts with the clock and formation tags the row already
 * shows: "(1:41) (Shotgun) 17-J.Allen pass deep middle...".
 */
internal fun cleanDescription(text: String): String {
    var result = text
    while (result.startsWith("(")) {
        val close = result.indexOf(')')
        if (close < 0) break
        result = result.substring(close + 1).trim { it == ' ' || it == '\t' }
    }
    return result.replace(jerseyPrefix, "")
}

@Composable
private fun PlayerEfficiencyCards(detail: GameDetail, game: Game) {
    val isPro = LocalGraph.current.subscriptions.isPro
    if (isPro) {
        val passers = detail.players(GameDetail.Role.PASSER)
        if (passers.isNotEmpty()) {
            CardBlock("Passing efficiency") {
                TableHeader(listOf("DB", "EPA", "EPA/DB", "SUCC", "CPOE"), 44.dp)
                passers.forEachIndexed { index, line ->
                    EfficiencyPlayerRow(
                        line, index, game,
                        listOf(
                            (line.dropbacks?.toString() ?: "-") to null,
                            (line.epa?.let { RateStyle.EPA.format(it) } ?: "-") to null,
                            rated(line.epaPerDropback, RateStyle.EPA),
                            rated(line.successRate, RateStyle.PERCENT),
                            rated(line.cpoe, RateStyle.SIGNED_DECIMAL),
                        ),
                    )
                }
            }
        }
        val rushers = detail.players(GameDetail.Role.RUSHER)
        if (rushers.isNotEmpty()) {
            CardBlock("Rushing efficiency") {
                TableHeader(listOf("CAR", "EPA", "EPA/C", "SUCC"), 44.dp)
                rushers.forEachIndexed { index, line ->
                    EfficiencyPlayerRow(
                        line, index, game,
                        listOf(
                            (line.carries?.toString() ?: "-") to null,
                            (line.epa?.let { RateStyle.EPA.format(it) } ?: "-") to null,
                            rated(line.epaPerCarry, RateStyle.EPA),
                            rated(line.successRate, RateStyle.PERCENT),
                        ),
                    )
                }
            }
        }
        val receivers = detail.players(GameDetail.Role.RECEIVER)
        if (receivers.isNotEmpty()) {
            CardBlock("Receiving efficiency") {
                TableHeader(listOf("TGT", "EPA", "EPA/T", "SUCC", "ADOT"), 44.dp)
                receivers.forEachIndexed { index, line ->
                    EfficiencyPlayerRow(
                        line, index, game,
                        listOf(
                            (line.targets?.toString() ?: "-") to null,
                            (line.epa?.let { RateStyle.EPA.format(it) } ?: "-") to null,
                            rated(line.epaPerTarget, RateStyle.EPA),
                            rated(line.successRate, RateStyle.PERCENT),
                            rated(line.adot, RateStyle.DECIMAL),
                        ),
                    )
                }
            }
        }
    } else {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 12.dp).gridironCard().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SfIcon("lock.fill", 22.dp, GridironPalette.inkTertiary)
            Text("Every player's efficiency", style = GridironType.cardTitle, color = GridironPalette.ink)
            Text(
                "EPA per dropback, success rate, CPOE and depth of target for every passer, rusher and receiver, ranked against the season.",
                style = GridironType.small,
                color = GridironPalette.inkSecondary,
                textAlign = TextAlign.Center,
            )
            PlusDirectCTA(PaywallTrigger.AdvancedBoxScore, PlusCTAStyle.CAPSULE)
        }
    }
}

private fun rated(value: RatedValue?, style: RateStyle): Pair<String, Int?> =
    if (value == null) "-" to null else style.format(value.value) to value.percentile

@Composable
private fun EfficiencyPlayerRow(line: GameDetail.PlayerLine, index: Int, game: Game, cells: List<Pair<String, Int?>>) {
    val vm = LocalGraph.current.dashboard
    val navigator = LocalNavigator.current
    val player = vm.player(line.playerId, game.season, game.seasonPhase)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp)
            .background(if (index % 2 == 0) GridironPalette.surface else GridironPalette.surfaceAlt)
            .then(if (player != null) Modifier.clickable { navigator.push(Route.PlayerProfile(player)) } else Modifier)
            .padding(horizontal = GridironGeo.padInline)
            .testTag("efficiencyRow"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            TeamColorDot(line.team, 7.dp)
            FitText(player?.name ?: line.name ?: "Player", GridironType.bodyBold, GridironPalette.ink, Modifier.weight(1f, fill = false), minScale = 0.8f)
        }
        cells.forEach { (text, percentile) ->
            FitText(
                text,
                GridironType.statSmall.copy(fontWeight = if (percentile == null) FontWeight.Normal else FontWeight.SemiBold),
                percentile?.let { GridironPalette.textColor(it) } ?: GridironPalette.ink,
                Modifier.width(44.dp),
                minScale = 0.8f,
                textAlign = TextAlign.End,
            )
        }
    }
}

// MARK: - Traditional box score

@Composable
private fun BoxScoreCard(boxScore: GameBoxScore, game: Game, boxTeam: String, onBoxTeam: (String) -> Unit) {
    val team = boxTeam.ifEmpty { game.awayTeam }
    Column {
        GridironSegmented(
            segments = listOf(
                Segment(game.awayTeam, teamFullName(game.awayTeam)),
                Segment(game.homeTeam, teamFullName(game.homeTeam)),
            ),
            selection = team,
            onSelect = onBoxTeam,
            modifier = Modifier.padding(horizontal = 12.dp).padding(top = 16.dp),
        )
        CardBlock("Passing") {
            TableHeader(listOf("C/ATT", "YDS", "TD", "INT"))
            boxScore.passers(team).forEachIndexed { index, line ->
                TableRow(
                    line, index, game,
                    listOf("${line.int("completions")}/${line.int("attempts")}", "${line.int("passing_yards")}", "${line.int("passing_tds")}", "${line.int("interceptions")}"),
                )
            }
        }
        CardBlock("Rushing") {
            TableHeader(listOf("CAR", "YDS", "AVG", "TD"))
            boxScore.rushers(team).forEachIndexed { index, line ->
                val carries = line.value("carries")
                TableRow(
                    line, index, game,
                    listOf(
                        "${line.int("carries")}", "${line.int("rushing_yards")}",
                        if (carries > 0) String.format(Locale.US, "%.1f", line.value("rushing_yards") / carries) else "-",
                        "${line.int("rushing_tds")}",
                    ),
                )
            }
        }
        CardBlock("Receiving") {
            TableHeader(listOf("REC", "TGT", "YDS", "TD"))
            boxScore.receivers(team).forEachIndexed { index, line ->
                TableRow(
                    line, index, game,
                    listOf("${line.int("receptions")}", "${line.int("targets")}", "${line.int("receiving_yards")}", "${line.int("receiving_tds")}"),
                )
            }
        }
        CardBlock("Defense") {
            TableHeader(listOf("TKL", "SCK", "INT", "PD"))
            boxScore.defenders(team).take(12).forEachIndexed { index, line ->
                TableRow(
                    line, index, game,
                    listOf(
                        "${swiftRoundValue(line.tackles)}",
                        formatUpToOneDecimal(line.value("def_sacks")),
                        "${line.int("def_interceptions")}",
                        "${line.int("def_pass_defended")}",
                    ),
                )
            }
        }
    }
}

@Composable
private fun TableHeader(columns: List<String>, width: androidx.compose.ui.unit.Dp = 46.dp) {
    Row(
        Modifier.fillMaxWidth().height(26.dp).background(GridironPalette.surfaceAlt).padding(horizontal = GridironGeo.padInline),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("PLAYER", style = GridironType.micro, color = GridironPalette.inkTertiary, modifier = Modifier.weight(1f))
        columns.forEach { column ->
            FitText(column, GridironType.micro, GridironPalette.inkTertiary, Modifier.width(width), minScale = 0.8f, textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun TableRow(line: GameBoxScore.PlayerLine, index: Int, game: Game, values: List<String>) {
    PlayerRow(line, index, game) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { NameText(line, game) }
            values.forEach { value ->
                FitText(value, GridironType.statSmall, GridironPalette.ink, Modifier.width(46.dp), minScale = 0.8f, textAlign = TextAlign.End)
            }
        }
    }
}

/** A tappable row when the player is in the live dataset, a plain one if not. */
@Composable
private fun PlayerRow(line: GameBoxScore.PlayerLine, index: Int, game: Game, content: @Composable () -> Unit) {
    val vm = LocalGraph.current.dashboard
    val navigator = LocalNavigator.current
    val player = vm.player(line.playerId, game.season, game.seasonPhase)
    Box(
        Modifier.fillMaxWidth().heightIn(min = 40.dp)
            .background(if (index % 2 == 0) GridironPalette.surface else GridironPalette.surfaceAlt)
            .then(if (player != null) Modifier.clickable { navigator.push(Route.PlayerProfile(player)) } else Modifier)
            .padding(horizontal = GridironGeo.padInline, vertical = 8.dp)
            .testTag("boxScoreRow"),
        contentAlignment = Alignment.CenterStart,
    ) { content() }
}

@Composable
private fun NameText(line: GameBoxScore.PlayerLine, game: Game) {
    val vm = LocalGraph.current.dashboard
    val name = vm.player(line.playerId, game.season, game.seasonPhase)?.name ?: "Player ${line.playerId}"
    FitText(name, GridironType.bodyBold, GridironPalette.ink, minScale = 0.85f)
}
