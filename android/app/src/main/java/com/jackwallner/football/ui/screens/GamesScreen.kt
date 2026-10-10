package com.jackwallner.football.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jackwallner.football.model.Game
import com.jackwallner.football.model.GameProjection
import com.jackwallner.football.model.GameStatus
import com.jackwallner.football.model.GameWeek
import com.jackwallner.football.model.SeasonPhase
import com.jackwallner.football.model.displayTeamAbbr
import com.jackwallner.football.model.nflTeamAbbreviations
import com.jackwallner.football.model.normalizedTeamAbbreviation
import com.jackwallner.football.model.teamFullName
import com.jackwallner.football.ui.HomeTopBar
import com.jackwallner.football.ui.LocalGraph
import com.jackwallner.football.ui.components.CrownYellow
import com.jackwallner.football.ui.components.FitText
import com.jackwallner.football.ui.components.GridironSectionBar
import com.jackwallner.football.ui.components.SfIcon
import com.jackwallner.football.ui.components.TeamColorDot
import com.jackwallner.football.ui.components.bottomBarPadding
import com.jackwallner.football.ui.components.bottomHairline
import com.jackwallner.football.ui.components.gridironCard
import com.jackwallner.football.ui.components.rememberHaptic
import com.jackwallner.football.ui.nav.LocalNavigator
import com.jackwallner.football.ui.nav.Route
import com.jackwallner.football.ui.theme.GridironGeo
import com.jackwallner.football.ui.theme.GridironPalette
import com.jackwallner.football.ui.theme.GridironType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The week's slate: who played whom, the finals, what is on next.
 *
 * This is the football-first front door. Scores, schedule and box scores are
 * free for everyone; the analysis layers stay where they were.
 */
@Composable
fun GamesScreen(isActive: Boolean) {
    val graph = LocalGraph.current
    val vm = graph.dashboard
    val favorites = graph.favorites
    var selectedWeekId by rememberSaveable { mutableStateOf<String?>(null) }

    val weeks = GameWeek.weeks(vm.games)
    val selectedWeek = weeks.firstOrNull { it.id == selectedWeekId } ?: vm.currentGameWeek
    val slate = selectedWeek?.let { Game.slateOrder(it.games(vm.games)) } ?: emptyList()
    val byeTeams = byeTeams(selectedWeek, slate)
    val weekDateRange = weekDateRange(selectedWeek, slate)
    val favoriteGame = favorites.team?.let { team -> slate.firstOrNull { it.involves(team) } }

    Column(Modifier.fillMaxSize()) {
        HomeTopBar("Games · ${vm.freeSeason}")
        GamesTeamsUi.RefreshBox(onRefresh = { vm.loadGames(force = true) }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                if (vm.games.isEmpty()) {
                    EmptyState()
                } else {
                    WeekSelector(weeks, selectedWeek?.id, Modifier.padding(top = 10.dp)) { selectedWeekId = it }
                    weekDateRange?.let {
                        Text(
                            it,
                            style = GridironType.micro,
                            color = GridironPalette.inkTertiary,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp),
                        )
                    }
                    SlateContent(slate, favoriteGame, byeTeams)
                }
                Spacer(Modifier.padding(bottomBarPadding()))
            }
        }
    }

    // Re-reads the schedule while this tab is on screen: every two minutes while
    // a game is under way or a final is still waiting on its stats, every ten
    // otherwise. The backend itself only updates every fifteen minutes on a game
    // day, so anything faster would be noise.
    LaunchedEffect(isActive) {
        if (!isActive) return@LaunchedEffect
        while (true) {
            vm.loadGames()
            val now = Instant.now()
            val busy = vm.games.any { game ->
                when (game.status(now)) {
                    GameStatus.IN_PROGRESS, GameStatus.AWAITING_SCORE -> true
                    GameStatus.FINAL -> {
                        val kickoff = game.kickoff
                        kickoff != null && !vm.hasStats(game) && java.time.Duration.between(kickoff, now).seconds < 12 * 3_600
                    }
                    GameStatus.UPCOMING -> false
                }
            }
            delay(if (busy) 120_000L else 600_000L)
        }
    }
}

/** Clubs on this week's schedule, for the bye line. */
private fun byeTeams(week: GameWeek?, slate: List<Game>): List<String> {
    if (week?.phase != SeasonPhase.REGULAR || slate.isEmpty()) return emptyList()
    val playing = slate.flatMap { listOf(normalizedTeamAbbreviation(it.awayTeam), normalizedTeamAbbreviation(it.homeTeam)) }.toSet()
    return nflTeamAbbreviations.filter { it !in playing }
}

/**
 * "Week 3 · Sep 24 - 28", so the slate and the Stats caption ("Through
 * Week 3") are plainly two different things.
 */
private fun weekDateRange(week: GameWeek?, slate: List<Game>): String? {
    week ?: return null
    val days = slate.mapNotNull { it.kickoff }
    val first = days.minOrNull() ?: return null
    val last = days.maxOrNull() ?: return null
    val zone = ZoneId.systemDefault()
    val a = first.atZone(zone).toLocalDate()
    val b = last.atZone(zone).toLocalDate()
    val style = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    val range = when {
        a == b -> style.format(a)
        a.year == b.year && a.month == b.month -> "${style.format(a)} - ${b.dayOfMonth}"
        else -> "${style.format(a)} - ${style.format(b)}"
    }
    return "${week.label} · $range"
}

@Composable
private fun EmptyState() {
    val vm = LocalGraph.current.dashboard
    val scope = rememberCoroutineScope()
    if (vm.isGamesLoading) {
        GamesTeamsUi.Spinner("Loading games", Modifier.padding(vertical = 64.dp))
        return
    }
    val failed = vm.gamesError != null
    GamesTeamsUi.Unavailable(
        icon = if (failed) "wifi.slash" else "calendar",
        title = if (failed) "Couldn't load games" else "No games scheduled",
        description = vm.gamesError ?: "The ${vm.freeSeason} schedule isn't published yet.",
        modifier = Modifier.padding(vertical = 48.dp),
    ) {
        Box(
            Modifier.heightIn(min = 44.dp).clip(CircleShape).background(GridironPalette.turf)
                .clickable { scope.launch { vm.loadGames(force = true) } }
                .testTag("gamesRetry").padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Try Again", style = GridironType.smallBold, color = Color.White) }
    }
}

// MARK: - Week selector

@Composable
private fun WeekSelector(weeks: List<GameWeek>, selectedId: String?, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    val haptic = rememberHaptic()
    val listState = rememberLazyListState()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val viewportPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        val halfChipPx = with(LocalDensity.current) { 28.dp.roundToPx() }
        // Keep the chosen week centred, on arrival and whenever it changes.
        LaunchedEffect(selectedId, weeks.size, viewportPx) {
            val index = weeks.indexOfFirst { it.id == selectedId }
            if (index >= 0) listState.animateScrollToItem(index, scrollOffset = -(viewportPx / 2 - halfChipPx))
        }
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            itemsIndexed(weeks, key = { _, week -> week.id }) { _, week ->
                val isSelected = week.id == selectedId
                Box(
                    Modifier.heightIn(min = 44.dp).clip(CircleShape)
                        .clickable {
                            onSelect(week.id)
                            haptic()
                        }
                        .semantics {
                            contentDescription = week.label
                            selected = isSelected
                            role = Role.Button
                        }
                        .testTag("weekChip_${week.id}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.height(32.dp).clip(CircleShape)
                            .background(if (isSelected) GridironPalette.turf else GridironPalette.surface)
                            .border(0.5.dp, if (isSelected) Color.Transparent else GridironPalette.hairline, CircleShape)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            week.shortLabel,
                            style = GridironType.smallBold,
                            color = if (isSelected) Color.White else GridironPalette.inkSecondary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

// MARK: - Slate

@Composable
private fun SlateContent(slate: List<Game>, favoriteGame: Game?, byeTeams: List<String>) {
    val graph = LocalGraph.current
    val vm = graph.dashboard
    val favorites = graph.favorites
    if (favoriteGame != null) GameSection("Your team", listOf(favoriteGame))
    val now = Instant.now()
    val remaining = slate.filter { it.id != favoriteGame?.id }
    val live = remaining.filter { it.status(now) == GameStatus.IN_PROGRESS || it.status(now) == GameStatus.AWAITING_SCORE }
    val finals = remaining.filter { it.status(now) == GameStatus.FINAL }
    val upcoming = remaining.filter { it.status(now) == GameStatus.UPCOMING }
    if (live.isNotEmpty()) GameSection("In progress", live)
    if (finals.isNotEmpty()) GameSection("Final", finals)
    if (upcoming.isNotEmpty()) GameSection("Upcoming", upcoming)

    if (upcoming.any { vm.projection(it) != null }) {
        Footnote(
            "Projected margins come from StatScout Power Ratings: each club's efficiency and scoring against an average team, adjusted for schedule, plus two points for home field. Details on the Teams tab.",
            top = 12,
        )
    }
    if (byeTeams.isNotEmpty()) {
        Footnote("Bye: " + byeTeams.joinToString(", ") { displayTeamAbbr(it) }, top = 12)
    }
    Footnote(
        if (favorites.team == null) {
            "Scores post when each game goes final, stats usually within a few hours. Follow a team from its page to pin its game here."
        } else {
            "Scores post when each game goes final. Player stats usually follow within a few hours."
        },
        top = 8,
    )
}

@Composable
private fun Footnote(text: String, top: Int) {
    Text(
        text,
        style = GridironType.micro,
        color = GridironPalette.inkTertiary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = top.dp),
    )
}

@Composable
private fun GameSection(title: String, games: List<Game>) {
    val graph = LocalGraph.current
    val vm = graph.dashboard
    val navigator = LocalNavigator.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 12.dp).gridironCard()) {
        GridironSectionBar(title)
        games.forEachIndexed { index, game ->
            GameRow(
                game = game,
                hasStats = vm.hasStats(game),
                highlight = graph.favorites.team,
                awayRecord = vm.record(game.awayTeam, game),
                homeRecord = vm.record(game.homeTeam, game),
                projection = vm.projection(game),
                modifier = Modifier
                    .background(if (index % 2 == 0) GridironPalette.surface else GridironPalette.surfaceAlt)
                    .clickable(onClickLabel = "Opens the game") { navigator.push(Route.Game(game.id)) },
            )
        }
    }
}

// MARK: - Row

/**
 * Two stacked team lines with scores, and the status on the right.
 *
 * [awayRecord] and [homeRecord] are each club's record through this game: after
 * it for a final, going into it for one still to be played. [projection] is the
 * power ratings' projected margin, upcoming games only.
 */
@Composable
fun GameRow(
    game: Game,
    hasStats: Boolean,
    modifier: Modifier = Modifier,
    highlight: String? = null,
    awayRecord: String? = null,
    homeRecord: String? = null,
    projection: GameProjection? = null,
) {
    val status = game.status()
    Row(
        modifier
            .fillMaxWidth()
            .bottomHairline(GridironPalette.divider)
            .padding(horizontal = GridironGeo.padInline, vertical = 10.dp)
            .clearAndSetSemantics { contentDescription = accessibilityText(game, status, hasStats, projection) }
            .testTag("gameRow"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TeamLine(game, game.awayTeam, game.awayScore, status, awayRecord, highlight)
            TeamLine(game, game.homeTeam, game.homeScore, status, homeRecord, highlight)
        }
        Column(Modifier.width(104.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                statusTitle(game, status),
                style = GridironType.smallBold,
                color = if (status == GameStatus.IN_PROGRESS) GridironPalette.performanceLow else GridironPalette.ink,
                textAlign = TextAlign.End,
            )
            statusDetail(game, status, hasStats, projection)?.let {
                Text(it, style = GridironType.micro, color = GridironPalette.inkTertiary, textAlign = TextAlign.End)
            }
        }
        SfIcon("chevron.right", 14.dp, GridironPalette.inkTertiary)
    }
}

@Composable
private fun TeamLine(game: Game, team: String, score: Int?, status: GameStatus, record: String?, highlight: String?) {
    val isWinner = game.result(team) == "W"
    val dim = status == GameStatus.FINAL && !isWinner && game.result(team) != "T"
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TeamColorDot(team, 10.dp)
        Text(
            displayTeamAbbr(team),
            style = GridironType.bodyBold,
            color = if (dim) GridironPalette.inkTertiary else GridironPalette.ink,
            modifier = Modifier.width(40.dp),
        )
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FitText(teamFullName(team), GridironType.small, GridironPalette.inkTertiary, Modifier.weight(1f, fill = false), minScale = 0.85f)
            record?.let {
                Text(it, style = GridironType.micro.copy(fontFeatureSettings = "tnum"), color = GridironPalette.inkTertiary, maxLines = 1, softWrap = false)
            }
            if (highlight != null && normalizedTeamAbbreviation(highlight) == normalizedTeamAbbreviation(team)) {
                SfIcon("star.fill", 10.dp, CrownYellow)
            }
        }
        if (score != null) {
            Text(
                "$score",
                style = GridironType.statMed,
                color = if (dim) GridironPalette.inkTertiary else GridironPalette.ink,
            )
        }
    }
}

private fun statusTitle(game: Game, status: GameStatus): String = when (status) {
    GameStatus.FINAL -> if (game.overtime) "Final/OT" else "Final"
    GameStatus.IN_PROGRESS -> "In progress"
    GameStatus.AWAITING_SCORE -> "Final soon"
    GameStatus.UPCOMING -> game.kickoffLabel()
}

private fun statusDetail(game: Game, status: GameStatus, hasStats: Boolean, projection: GameProjection?): String? = when (status) {
    GameStatus.FINAL -> if (hasStats) game.dayLabel() else "Stats arriving"
    GameStatus.IN_PROGRESS, GameStatus.AWAITING_SCORE -> "Score at final"
    GameStatus.UPCOMING -> projection?.label(game.homeTeam, game.awayTeam) ?: game.kickoff?.let { GamesTeamsUi.monthDay(it) }
}

private fun accessibilityText(game: Game, status: GameStatus, hasStats: Boolean, projection: GameProjection?): String {
    val away = teamFullName(game.awayTeam)
    val home = teamFullName(game.homeTeam)
    return when (status) {
        GameStatus.FINAL -> {
            val score = "$away ${game.awayScore ?: 0}, $home ${game.homeScore ?: 0}"
            "$score, ${statusTitle(game, status)}" + if (hasStats) "" else ", stats arriving"
        }
        GameStatus.IN_PROGRESS, GameStatus.AWAITING_SCORE -> "$away at $home, in progress"
        GameStatus.UPCOMING -> {
            val projected = projection?.let { ", projected ${it.label(game.homeTeam, game.awayTeam)}" } ?: ""
            "$away at $home, ${game.dayLabel()} at ${game.kickoff?.let { GamesTeamsUi.shortTime(it) } ?: "time TBD"}$projected"
        }
    }
}
