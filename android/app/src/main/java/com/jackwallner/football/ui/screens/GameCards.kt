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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.jackwallner.football.data.DashboardViewModel
import com.jackwallner.football.model.DateText
import com.jackwallner.football.model.Game
import com.jackwallner.football.model.GameBoxScore
import com.jackwallner.football.model.GameStatus
import com.jackwallner.football.model.GameWeek
import com.jackwallner.football.model.Player
import com.jackwallner.football.model.PlayerGameLog
import com.jackwallner.football.model.SeasonPhase
import com.jackwallner.football.model.displayTeamAbbr
import com.jackwallner.football.model.normalizedTeamAbbreviation
import com.jackwallner.football.model.teamFullName
import com.jackwallner.football.ui.LocalGraph
import com.jackwallner.football.ui.components.ChipTrailing
import com.jackwallner.football.ui.components.FitText
import com.jackwallner.football.ui.components.GridironChip
import com.jackwallner.football.ui.components.GridironSectionBar
import com.jackwallner.football.ui.components.PushedScreen
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
import kotlinx.coroutines.CancellationException

/**
 * A team's game this week, on its team page: the result, the next kickoff, or
 * the bye. It sits above the roster cards so the first thing a team page says
 * is what happened on the field. Under it: follow the club, open its schedule.
 */
@Composable
fun TeamWeekGameCard(viewModel: DashboardViewModel = LocalGraph.current.dashboard, team: String) {
    val navigator = LocalNavigator.current
    val week = viewModel.currentGameWeek ?: return
    val game = viewModel.currentGame(team)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (game != null) {
            WeekShell(
                week, viewModel, team,
                Modifier.clickable(onClickLabel = "Opens the game") { navigator.push(Route.Game(game.id)) }.testTag("teamWeekGame"),
            ) {
                TeamColorDot(game.opponent(team), 10.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    FitText(
                        "${game.matchupLabel(team)} · ${teamFullName(game.opponent(team))}",
                        GridironType.bodyBold, GridironPalette.ink, minScale = 0.85f,
                    )
                    Text(weekDetail(viewModel, game), style = GridironType.micro, color = GridironPalette.inkTertiary)
                }
                game.resultLine(team)?.let {
                    Text(it, style = GridironType.statMed, color = GamesTeamsUi.resultColor(game.result(team)))
                }
                SfIcon("chevron.right", 14.dp, GridironPalette.inkTertiary)
            }
        } else if (week.phase == SeasonPhase.REGULAR) {
            WeekShell(week, viewModel, team) {
                Text("Bye week", style = GridironType.bodyBold, color = GridironPalette.ink)
            }
        }
        TeamActions(team)
    }
}

@Composable
private fun TeamActions(team: String) {
    val favorites = LocalGraph.current.favorites
    val navigator = LocalNavigator.current
    val haptic = rememberHaptic()
    val abbr = normalizedTeamAbbreviation(team)
    val following = favorites.isFavoriteTeam(abbr)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        ChipLink(
            hint = if (following) "Stops pinning this team's games" else "Pins this team's games at the top of Games",
            modifier = Modifier.testTag("followTeam"),
            onClick = {
                favorites.setFavoriteTeam(if (following) null else abbr)
                haptic()
            },
        ) {
            GridironChip(
                title = if (following) "Your team" else "Follow team",
                icon = if (following) "star.fill" else "star",
                isActive = following,
            )
        }
        ChipLink(hint = "Opens the schedule", modifier = Modifier.testTag("teamSchedule"), onClick = { navigator.push(Route.TeamSchedule(abbr)) }) {
            GridironChip(title = "Schedule", icon = "calendar", trailing = ChipTrailing.None)
        }
    }
}

/** A chip with a 48dp touch target and an accessibility action label. */
@Composable
private fun ChipLink(hint: String, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.heightIn(min = 44.dp).clip(CircleShape).clickable(onClickLabel = hint, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private fun weekDetail(viewModel: DashboardViewModel, game: Game): String = when (game.status()) {
    GameStatus.FINAL -> if (viewModel.hasStats(game)) "Final · box score" else "Final · stats arriving"
    GameStatus.IN_PROGRESS, GameStatus.AWAITING_SCORE -> "In progress"
    GameStatus.UPCOMING -> "${game.dayLabel()}, ${game.kickoff?.let { GamesTeamsUi.shortTime(it) } ?: "time TBD"}"
}

@Composable
private fun WeekShell(
    week: GameWeek,
    viewModel: DashboardViewModel,
    team: String,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val header = (listOf(week.label) + listOfNotNull(viewModel.record(team))).joinToString(" · ").uppercase()
    Column(
        modifier.fillMaxWidth().gridironCard().padding(horizontal = GridironGeo.padInline, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(header, style = GridironType.micro, color = GridironPalette.inkSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/** One club's season: every week, the opponent, the result or the kickoff. */
@Composable
fun TeamScheduleScreen(team: String) {
    val vm = LocalGraph.current.dashboard
    val navigator = LocalNavigator.current
    val games = vm.schedule(team)
    val entries = scheduleEntries(vm, games)
    LaunchedEffect(Unit) { vm.loadGames() }
    PushedScreen(teamFullName(team)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 12.dp).gridironCard()) {
                GridironSectionBar("${vm.freeSeason} schedule", trailing = vm.record(team)?.let { record ->
                    { Text(record, style = GridironType.statSmall, color = GridironPalette.inkSecondary) }
                })
                entries.forEachIndexed { index, entry ->
                    val background = if (index % 2 == 0) GridironPalette.surface else GridironPalette.surfaceAlt
                    when (entry) {
                        is ScheduleEntry.Played -> ScheduleRow(
                            entry.game, team,
                            Modifier.background(background).clickable(onClickLabel = "Opens the game") { navigator.push(Route.Game(entry.game.id)) },
                        )
                        is ScheduleEntry.Bye -> ByeRow(entry.week, Modifier.background(background))
                    }
                }
                if (games.isEmpty()) {
                    Text(
                        if (vm.isGamesLoading) "Loading schedule" else "Schedule not published yet",
                        style = GridironType.small,
                        color = GridironPalette.inkTertiary,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 32.dp),
                    )
                }
            }
            Spacer(Modifier.padding(bottomBarPadding()))
        }
    }
}

private sealed interface ScheduleEntry {
    data class Played(val game: Game) : ScheduleEntry
    data class Bye(val week: Int) : ScheduleEntry
}

/** The schedule with the bye week slotted in where the club has no game. */
private fun scheduleEntries(vm: DashboardViewModel, games: List<Game>): List<ScheduleEntry> {
    val regularWeeks = vm.games.filter { it.seasonPhase == SeasonPhase.REGULAR }.map { it.week }.toSortedSet()
    val played = games.filter { it.seasonPhase == SeasonPhase.REGULAR }.map { it.week }.toSet()
    val result = mutableListOf<ScheduleEntry>()
    for (week in regularWeeks) {
        val game = games.firstOrNull { it.seasonPhase == SeasonPhase.REGULAR && it.week == week }
        if (game != null) result += ScheduleEntry.Played(game) else if (week !in played) result += ScheduleEntry.Bye(week)
    }
    result += games.filter { it.seasonPhase != SeasonPhase.REGULAR }.map { ScheduleEntry.Played(it) }
    return result
}

@Composable
private fun ByeRow(week: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 44.dp).bottomHairline(GridironPalette.divider).padding(horizontal = GridironGeo.padInline),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$week", style = GridironType.statSmall, color = GridironPalette.inkTertiary, modifier = Modifier.width(30.dp))
        Text("Bye week", style = GridironType.body, color = GridironPalette.inkTertiary)
    }
}

@Composable
private fun ScheduleRow(game: Game, team: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp).bottomHairline(GridironPalette.divider)
            .padding(horizontal = GridironGeo.padInline).testTag("scheduleRow"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (game.seasonPhase == SeasonPhase.REGULAR) "${game.week}" else game.gameType,
            style = GridironType.statSmall,
            color = GridironPalette.inkTertiary,
            modifier = Modifier.width(30.dp),
        )
        TeamColorDot(game.opponent(team), 10.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FitText(
                "${game.matchupLabel(team)} · ${teamFullName(game.opponent(team))}",
                GridironType.bodyBold, GridironPalette.ink, minScale = 0.85f,
            )
            Text(game.dayLabel(), style = GridironType.micro, color = GridironPalette.inkTertiary)
        }
        val line = game.resultLine(team)
        if (line != null) {
            Text(line, style = GridironType.statMed, color = GamesTeamsUi.resultColor(game.result(team)))
        } else {
            Text(
                if (game.status() == GameStatus.UPCOMING) game.kickoff?.let { GamesTeamsUi.shortTime(it) } ?: "TBD" else "In progress",
                style = GridironType.small,
                color = GridironPalette.inkSecondary,
            )
        }
        SfIcon("chevron.right", 14.dp, GridironPalette.inkTertiary)
    }
}

/**
 * A player's most recent game on his profile: opponent, result, his line, and
 * a way into the box score. Free, and real: the one-game answer to "how did he
 * look?" that the recent-form cards can't give until there are several games.
 *
 * @param viewModel the shared dashboard model, for the schedule and game logs.
 * @param player the player whose latest game is shown.
 * @param season the season the profile is reading.
 * @param phase the season type the profile is reading.
 */
@Composable
fun PlayerLastGameCard(
    viewModel: DashboardViewModel = LocalGraph.current.dashboard,
    player: Player,
    season: Int,
    phase: SeasonPhase,
) {
    val navigator = LocalNavigator.current
    var log by remember { mutableStateOf<PlayerGameLog?>(null) }
    var loadedKey by remember { mutableStateOf<String?>(null) }
    val key = "${player.playerId}-$season-${phase.raw}-${viewModel.freshnessRevision ?: "none"}"

    LaunchedEffect(key) {
        if (loadedKey == key) return@LaunchedEffect
        try {
            val logs = viewModel.fetchGameLogs(player.playerId, season, phase)
            log = logs.maxByOrNull { it.gameDate }
            loadedKey = key
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            loadedKey = null
        }
    }

    val current = log ?: return
    val game = current.gameId?.let { viewModel.game(it) }
    val team = current.team ?: player.team
    val line = GameBoxScore(listOf(current)).lines[0]
    val summary = GameBoxScore.summary(line)
    Column(
        Modifier.padding(top = 10.dp).fillMaxWidth().gridironCard()
            .then(
                if (game != null) Modifier.clickable(onClickLabel = "Opens the game") { navigator.push(Route.Game(game.id)) }
                else Modifier,
            )
            .padding(horizontal = GridironGeo.padInline, vertical = 10.dp)
            .testTag("lastGameCard"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            val title = if (game != null) "Last game · ${game.roundLabel}" else "Last game · ${DateText.gameDay(current.gameDate)}"
            Text(title.uppercase(), style = GridironType.micro, color = GridironPalette.inkSecondary, modifier = Modifier.weight(1f))
            if (game != null) {
                Text("Box score", style = GridironType.micro, color = GridironPalette.turf)
                SfIcon("chevron.right", 12.dp, GridironPalette.turf)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val matchup = if (game != null) "${game.matchupLabel(team)} · ${game.dayLabel()}" else "vs ${displayTeamAbbr(current.opponent ?: "")}"
            Text(matchup, style = GridironType.bodyBold, color = GridironPalette.ink, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
            game?.resultLine(team)?.let {
                Text(it, style = GridironType.statSmall, color = GamesTeamsUi.resultColor(game.result(team)))
            }
        }
        if (summary.isNotEmpty()) {
            Text(summary, style = GridironType.small.copy(fontFeatureSettings = "tnum"), color = GridironPalette.inkSecondary)
        }
    }
}

/**
 * Every game this season, newest first: week, opponent, result, and the
 * player's line. The week-by-week view a 17-game sport is read in, free, and
 * the natural companion to the Pro rolling windows.
 *
 * @param viewModel the shared dashboard model, for the schedule and game logs.
 * @param player the player whose games are listed.
 * @param season the season the profile is reading.
 * @param phase the season type the profile is reading.
 */
@Composable
fun PlayerGameLogCard(
    viewModel: DashboardViewModel = LocalGraph.current.dashboard,
    player: Player,
    season: Int,
    phase: SeasonPhase,
) {
    var entries by remember { mutableStateOf<List<GameLogEntry>>(emptyList()) }
    var loadedKey by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    val key = "${player.playerId}-$season-${phase.raw}-${viewModel.freshnessRevision ?: "none"}"

    LaunchedEffect(key) {
        if (loadedKey == key) return@LaunchedEffect
        try {
            val logs = viewModel.fetchGameLogs(player.playerId, season, phase)
            entries = gameLogEntries(logs, player.team)
            loadedKey = key
            failed = false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            failed = true
        }
    }

    Column(Modifier.fillMaxWidth().gridironCard().testTag("gameLogCard")) {
        GridironSectionBar("GAME LOG")
        if (entries.isEmpty()) {
            Text(
                if (failed) "Couldn't load games. Pull to refresh." else if (loadedKey == null) "Loading games…" else "No games yet this season.",
                style = GridironType.small,
                color = GridironPalette.inkSecondary,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 20.dp),
            )
        } else {
            entries.forEachIndexed { index, entry ->
                GameLogRow(entry, viewModel, Modifier.background(if (index % 2 == 0) GridironPalette.surface else GridironPalette.surfaceAlt))
            }
        }
    }
}

/** One game in a player's log. */
private data class GameLogEntry(
    val id: String,
    val gameId: String?,
    val gameDate: java.time.Instant,
    val team: String,
    val opponent: String?,
    val summary: String,
)

/**
 * One entry per game; a player with two roles in a game (a rushing QB is one
 * row, a two-way player two) reads as one line.
 */
private fun gameLogEntries(logs: List<PlayerGameLog>, fallbackTeam: String): List<GameLogEntry> =
    logs.groupBy { it.gameId ?: it.gameDate.toString() }
        .map { (id, rows) ->
            val first = rows[0]
            val summary = GameBoxScore(rows).lines.map { GameBoxScore.summary(it) }.filter { it.isNotEmpty() }.joinToString(" · ")
            GameLogEntry(id, first.gameId, first.gameDate, first.team ?: fallbackTeam, first.opponent, summary)
        }
        .sortedByDescending { it.gameDate }

@Composable
private fun GameLogRow(entry: GameLogEntry, viewModel: DashboardViewModel, modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val game = entry.gameId?.let { viewModel.game(it) }
    Row(
        modifier.fillMaxWidth()
            .then(if (game != null) Modifier.clickable(onClickLabel = "Opens the game") { navigator.push(Route.Game(game.id)) } else Modifier)
            .bottomHairline(GridironPalette.divider)
            .padding(horizontal = GridironGeo.padInline, vertical = 10.dp)
            .testTag("gameLogRow"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            game?.let { if (it.seasonPhase == SeasonPhase.REGULAR) "${it.week}" else it.gameType } ?: "-",
            style = GridironType.statSmall,
            color = GridironPalette.inkTertiary,
            modifier = Modifier.width(28.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    game?.matchupLabel(entry.team) ?: "vs ${displayTeamAbbr(entry.opponent ?: "")}",
                    style = GridironType.bodyBold,
                    color = GridironPalette.ink,
                )
                game?.resultLine(entry.team)?.let {
                    Text(it, style = GridironType.statSmall, color = GamesTeamsUi.resultColor(game.result(entry.team)))
                }
                Spacer(Modifier.weight(1f))
                Text(DateText.gameDay(entry.gameDate), style = GridironType.micro, color = GridironPalette.inkTertiary)
            }
            Text(
                entry.summary.ifEmpty { "No box score line" },
                style = GridironType.small.copy(fontFeatureSettings = "tnum"),
                color = GridironPalette.inkSecondary,
            )
        }
    }
}
