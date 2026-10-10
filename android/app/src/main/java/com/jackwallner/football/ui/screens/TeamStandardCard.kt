package com.jackwallner.football.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jackwallner.football.data.DashboardViewModel
import com.jackwallner.football.data.PaywallTrigger
import com.jackwallner.football.model.Metric
import com.jackwallner.football.model.MetricCategory
import com.jackwallner.football.model.Player
import com.jackwallner.football.model.PlayerGameLog
import com.jackwallner.football.model.RecentWindow
import com.jackwallner.football.model.SeasonPhase
import com.jackwallner.football.model.StatScoutSeason
import com.jackwallner.football.ui.LocalGraph
import com.jackwallner.football.ui.components.BlurGateUnlock
import com.jackwallner.football.ui.components.GridironSectionBar
import com.jackwallner.football.ui.components.GridironSegmented
import com.jackwallner.football.ui.components.GridironSubSectionBar
import com.jackwallner.football.ui.components.InlineLoadError
import com.jackwallner.football.ui.components.MetricBar
import com.jackwallner.football.ui.components.Segment
import com.jackwallner.football.ui.components.SfIcon
import com.jackwallner.football.ui.components.TrendArrow
import com.jackwallner.football.ui.components.bottomHairline
import com.jackwallner.football.ui.components.gridironCard
import com.jackwallner.football.ui.nav.rememberRetained
import com.jackwallner.football.ui.theme.GridironGeo
import com.jackwallner.football.ui.theme.GridironPalette
import com.jackwallner.football.ui.theme.GridironType
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * The traditional line for a whole club, percentile-mapped against the other 31.
 *
 * The percentile card next to it answers "how good is this offence"; this
 * answers "what actually happened". The ruler here is the league's thirty-two
 * clubs, not its several hundred players: a team's 6.9 yards per attempt means
 * nothing against individual quarterbacks' spread, and everything against the
 * other clubs'.
 *
 * @param players the roster for this team and season.
 * @param leaguePlayers every player in the season, used to build the thirty-two team lines.
 * @param seasonPhase see [TeamRankingsCard].
 * @param fetchTeamGameLogs fetches (team, season, phase, since) game logs.
 * @param supportsRecent false on a historical season, where the Recent control is hidden.
 */
@Composable
fun TeamStandardCard(
    team: String,
    season: Int,
    players: List<Player>,
    leaguePlayers: List<Player>,
    fetchTeamGameLogs: TeamGameLogFetcher?,
    onUpgradeTap: () -> Unit,
    seasonPhase: SeasonPhase = SeasonPhase.REGULAR,
    supportsRecent: Boolean = true,
) {
    val isPro = LocalGraph.current.subscriptions.isPro
    val scope = rememberCoroutineScope()
    var side by rememberSaveable { mutableStateOf(TeamSide.OFFENSE) }
    var showingRecent by rememberSaveable { mutableStateOf(false) }
    var windowGames by rememberSaveable { mutableStateOf(5) }
    var logs by rememberRetained("teamStandard.logs") { mutableStateOf<List<PlayerGameLog>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // What the card actually renders. The toggle survives a season change (it is
    // view state, the season is a parameter), so a user who turned Recent on for
    // the live season and then walked back to 2018 would otherwise sit in front
    // of a permanently empty window.
    val isRecent = showingRecent && supportsRecent
    val line = StandardLines(team, side, players, leaguePlayers, logs, windowGames)

    suspend fun load() {
        val fetch = fetchTeamGameLogs ?: return
        if (!isPro) return
        loading = true
        loadError = null
        try {
            // Wide enough to cover the longest window plus byes, then trimmed to
            // the last N distinct game dates. Anchored to the season's own end, not to today.
            logs = fetch(team, season, seasonPhase, StatScoutSeason.gameLogWindowStart(season))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            loadError = "Couldn't load team form. Pull to refresh."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(team, season, seasonPhase, isRecent, isPro) {
        if (isRecent && isPro) load()
    }

    Column(Modifier.fillMaxWidth().gridironCard().testTag("teamStandardCard")) {
        GridironSectionBar("TEAM STANDARD STATS")

        GamesTeamsUi.PickerRow(
            groups = listOfNotNull(
                TeamSide.entries.size to {
                    GridironSegmented(TeamSide.entries.map { Segment(it, it.label) }, side, { side = it })
                },
                if (supportsRecent) 2 to {
                    GridironSegmented(
                        listOf(Segment(false, "Season"), Segment(true, "Recent", isLocked = !isPro)),
                        showingRecent, { showingRecent = it },
                        onLockedTap = { onUpgradeTap() },
                    )
                } else null,
            ),
            modifier = Modifier.background(GridironPalette.surfaceAlt).padding(horizontal = GridironGeo.padInline, vertical = 8.dp),
        )

        if (isRecent) {
            GridironSegmented(
                RecentWindow.entries.map { Segment(it, it.segmentLabel) },
                line.window,
                { windowGames = it.value },
                Modifier.background(GridironPalette.surfaceAlt).padding(horizontal = GridironGeo.padInline).padding(bottom = 8.dp),
            )
        }

        if (isRecent) RecentContent(line, isPro, loading, loadError) { scope.launch { load() } }
        else SeasonContent(line)
    }
}

/** Rate stats and the roster quantity each is a rate of. */
private val RATE_WEIGHTS = mapOf(
    "CMP%" to "ATT", "Y/A" to "ATT", "INT%" to "ATT",
    "Y/C" to "CAR",
    "CATCH%" to "TGT", "Y/R" to "REC",
)

private val OFFENSE_ORDER = listOf(
    "CMP%", "Y/A", "INT%", "Y/C", "CATCH%", "Y/R",
    "PASS YDS", "PASS TD", "INT", "CAR", "RUSH YDS", "RUSH TD",
    "REC", "REC YDS", "REC TD", "G",
)
private val DEFENSE_ORDER = listOf("TACKLES", "SACKS", "DEF INT", "G")

/** Lower is better. Only one on an offensive line: interceptions thrown, and the rate of them. */
private val LOWER_IS_BETTER = setOf("INT", "INT%")

/** Game-log key to the label it's displayed under: the rollup's own naming, so the two stay in step. */
private fun countingWindowKeys(side: TeamSide): List<Pair<String, String>> =
    if (side == TeamSide.DEFENSE) {
        listOf("def_tackles_solo" to "TACKLES", "def_sacks" to "SACKS", "def_interceptions" to "DEF INT")
    } else {
        listOf(
            "passing_yards" to "PASS YDS", "passing_tds" to "PASS TD",
            "rushing_yards" to "RUSH YDS", "rushing_tds" to "RUSH TD",
            "receptions" to "REC", "receiving_yards" to "REC YDS",
        )
    }

/**
 * The derived numbers behind the card: the club's line, the league's lines and
 * the game-log window. Football's `standard_stats` carries composites (Cmp/Att,
 * Rec/Tgt) rather than separate denominators, so the rates are derived from the
 * game-log counts in the window path and from the composite split here.
 */
private class StandardLines(
    val team: String,
    val side: TeamSide,
    private val players: List<Player>,
    private val leaguePlayers: List<Player>,
    private val logs: List<PlayerGameLog>,
    val windowGames: Int,
) {
    val order: List<String> get() = if (side == TeamSide.DEFENSE) DEFENSE_ORDER else OFFENSE_ORDER
    val rateLabels: List<String> get() = order.filter { it in RATE_WEIGHTS }
    val countingLabels: List<String> get() = order.filter { it !in RATE_WEIGHTS }
    val window: RecentWindow get() = RecentWindow.of(windowGames) ?: RecentWindow.FIVE

    val seasonLine: Map<String, Double> by lazy { teamLine(players) }

    /** The other thirty-one, plus this one: the distribution a bar is drawn against. */
    val league: List<Map<String, Double>> by lazy {
        leaguePlayers.groupBy { it.team }.values.map { teamLine(it) }.filter { it.isNotEmpty() }
    }

    /**
     * One club's standard line: counting stats summed, rates rebuilt from the
     * quantity they're a rate of. Cmp/Att and Rec/Tgt are split into their two
     * counts first and the rates derived from the sums: numerators and
     * denominators, never pre-divided rates.
     */
    private fun teamLine(roster: List<Player>): Map<String, Double> {
        val cats = side.categories
        val pool = roster.filter { p -> cats.any { p.matchesPlayerType(it) } }
        if (pool.isEmpty()) return emptyMap()

        val totals = HashMap<String, Double>()
        for (player in pool) {
            for (stat in player.standardStats.orEmpty()) {
                val label = stat.label.uppercase()
                if (label == "CMP/ATT" || label == "REC/TGT") {
                    val parts = stat.value.split("/").mapNotNull { DashboardViewModel.rawNumeric(it) }
                    if (parts.size != 2) continue
                    if (label == "CMP/ATT") {
                        totals["CMP"] = (totals["CMP"] ?: 0.0) + parts[0]
                        totals["ATT"] = (totals["ATT"] ?: 0.0) + parts[1]
                    } else {
                        totals["REC"] = (totals["REC"] ?: 0.0) + parts[0]
                        totals["TGT"] = (totals["TGT"] ?: 0.0) + parts[1]
                    }
                    continue
                }
                val value = DashboardViewModel.rawNumeric(stat.value) ?: continue
                // Games played is per player, so summing it across a roster is
                // meaningless. Take the maximum, which is the club's own count.
                if (label == "G") totals["G"] = maxOf(totals["G"] ?: 0.0, value)
                else totals[label] = (totals[label] ?: 0.0) + value
            }
        }
        return withDerivedRates(totals)
    }

    /** The rate stats, rebuilt from the summed counts. */
    private fun withDerivedRates(totals: Map<String, Double>): Map<String, Double> {
        val out = HashMap(totals)
        val att = totals["ATT"]
        if (att != null && att > 0) {
            totals["CMP"]?.let { out["CMP%"] = it / att * 100 }
            totals["PASS YDS"]?.let { out["Y/A"] = it / att }
            totals["INT"]?.let { out["INT%"] = it / att * 100 }
        }
        val car = totals["CAR"]
        val rushYds = totals["RUSH YDS"]
        if (car != null && car > 0 && rushYds != null) out["Y/C"] = rushYds / car
        val tgt = totals["TGT"]
        val rec = totals["REC"]
        if (tgt != null && tgt > 0 && rec != null) out["CATCH%"] = rec / tgt * 100
        val recYds = totals["REC YDS"]
        if (rec != null && rec > 0 && recYds != null) out["Y/R"] = recYds / rec
        // Bookkeeping totals, never displayed on their own row.
        out.remove("CMP")
        out.remove("ATT")
        out.remove("TGT")
        return out
    }

    /**
     * Rank against whichever clubs have the stat, however few that is. A rank
     * among the clubs that have a number is the honest answer at every point in
     * the season, including week one when only a few have played.
     */
    fun percentile(label: String, value: Double): Int {
        var values = league.mapNotNull { it[label] }
        if (values.isEmpty()) values = listOf(value)
        val below = values.count { it < value }
        val equal = values.count { it == value }
        val raw = (below + equal / 2.0) / values.size * 100
        val oriented = if (label in LOWER_IS_BETTER) 100 - raw else raw
        return Math.round(oriented).toInt().coerceIn(1, 100)
    }

    // MARK: Window

    private val onSideLogs: List<PlayerGameLog> get() = logs.filter { side.includes(it.playerType) }

    /**
     * The club's last N games, not its last N log rows. The window is anchored
     * to the last date present in the data, never to today: the NFL plays
     * weekly, so anchoring to now would silently shrink the window to nothing
     * for six days out of seven. And it counts distinct game dates rather than
     * rows, because there is one row per player per game.
     */
    private val sideLogs: List<PlayerGameLog>
        get() {
            val kept = onSideLogs.map { it.gameDate }.distinct().sortedDescending().take(windowGames).toSet()
            return onSideLogs.filter { it.gameDate in kept }
        }

    /**
     * True when the club has played no more games than the window, so "last
     * five" and "the season" are the same games. In week one that turned every
     * rate row into "73.5% to 73.5%": the comparison goes away, the window stays.
     */
    val windowIsWholeSeason: Boolean get() = onSideLogs.map { it.gameDate }.distinct().size <= windowGames

    /** What the window heading says. "LAST 5 GAMES" over a club that has played one is a claim about games that do not exist yet. */
    val windowTitle: String get() = if (windowIsWholeSeason) "SEASON TO DATE" else "LAST $windowGames GAMES"

    /** Summed counting stats for the window, keyed by display label, plus the denominators the rates are rebuilt from. */
    val windowTotals: Map<String, Double> by lazy {
        val window = sideLogs
        val totals = HashMap<String, Double>()
        for (log in window) {
            for ((key, label) in countingWindowKeys(side)) {
                log.metrics[key]?.let { totals[label] = (totals[label] ?: 0.0) + it }
            }
            for (key in listOf("attempts", "completions", "interceptions", "carries", "targets")) {
                log.metrics[key]?.let { totals[key] = (totals[key] ?: 0.0) + it }
            }
            if (side == TeamSide.DEFENSE) {
                log.metrics["def_tackle_assists"]?.let { totals["TACKLES"] = (totals["TACKLES"] ?: 0.0) + it }
            }
        }
        totals
    }

    /** The rate line rebuilt from the window's sums, the same identity the backend rollup uses, so the numbers agree with the Trends board. */
    val windowRates: Map<String, Double> by lazy {
        val totals = windowTotals
        val out = HashMap<String, Double>()
        val att = totals["attempts"] ?: 0.0
        val cmp = totals["completions"] ?: 0.0
        val ints = totals["interceptions"] ?: 0.0
        val car = totals["carries"] ?: 0.0
        val tgt = totals["targets"] ?: 0.0
        val rec = totals["REC"] ?: 0.0
        if (att > 0) {
            out["CMP%"] = cmp / att * 100
            totals["PASS YDS"]?.let { out["Y/A"] = it / att }
            out["INT%"] = ints / att * 100
        }
        if (car > 0) totals["RUSH YDS"]?.let { out["Y/C"] = it / car }
        if (tgt > 0) out["CATCH%"] = rec / tgt * 100
        if (rec > 0) totals["REC YDS"]?.let { out["Y/R"] = it / rec }
        out
    }

    fun sortedByOrder(labels: Collection<String>): List<String> =
        labels.sortedBy { order.indexOf(it).let { index -> if (index < 0) Int.MAX_VALUE else index } }

    // MARK: Teaser

    /**
     * Season line to an invented window, per side and per window length. Built
     * from this club's real season rates so the preview is the team the user is
     * looking at, and so moving the Offense/Defense or 3/5/8 pickers visibly
     * redraws it. Only the window column is fictional, and it stays behind the blur.
     */
    val teaserRows: List<Triple<String, Double, Double>>
        get() {
            val labels = rateLabels.filter { seasonLine[it] != null }
            val fallback = if (side == TeamSide.DEFENSE) {
                listOf("TACKLES" to 62.0, "SACKS" to 2.4, "DEF INT" to 0.8)
            } else {
                listOf("CMP%" to 64.2, "Y/A" to 6.9, "Y/C" to 4.3, "CATCH%" to 65.1)
            }
            val base = if (labels.isEmpty()) fallback else labels.take(4).map { it to (seasonLine[it] ?: 0.0) }
            return base.map { (label, season) ->
                val seed = stableSeed("$label-${side.raw}-$windowGames-$team")
                // Plus or minus 12% of the season figure, the size of a real five-game swing.
                val swing = season * (seed % 25 - 12) / 100
                Triple(label, season, season + swing)
            }
        }

    val teaserTotals: List<Pair<String, Int>>
        get() {
            val scale = windowGames / 5.0
            return listOf("PASS YDS" to 1_180, "RUSH YDS" to 545, "PASS TD" to 8, "REC" to 96).map { (label, value) ->
                label to Math.round(value * scale).toInt()
            }
        }

    /** Deterministic across launches, unlike `hashCode`. */
    private fun stableSeed(text: String): Int =
        Math.abs(text.codePoints().toArray().fold(7) { acc, scalar -> (acc * 31 + scalar) % 100_003 })

    fun format(label: String, value: Double): String = when (label) {
        "CMP%", "INT%", "CATCH%" -> String.format(Locale.US, "%.1f%%", value)
        "Y/A", "Y/C", "Y/R", "SACKS" -> String.format(Locale.US, "%.1f", value)
        else -> NumberFormat.getIntegerInstance(Locale.US).format(Math.round(value))
    }
}

// MARK: - Season

@Composable
private fun SeasonContent(lines: StandardLines) {
    val line = lines.seasonLine
    if (line.isEmpty()) {
        EmptyState("No standard stats for this roster")
        return
    }
    BarGroup("RATE", lines.rateLabels, lines, startIndex = 0)
    BarGroup("VOLUME", lines.countingLabels, lines, startIndex = lines.rateLabels.size)
    Text(
        "Totals add up the current roster's season lines, so a player traded at the deadline brings his whole year with him.",
        style = GridironType.micro,
        color = GridironPalette.inkTertiary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = GridironGeo.padCard, vertical = 10.dp),
    )
}

@Composable
private fun BarGroup(title: String, labels: List<String>, lines: StandardLines, startIndex: Int) {
    val present = labels.filter { lines.seasonLine[it] != null }
    if (present.isEmpty()) return
    GridironSubSectionBar(title)
    present.forEachIndexed { offset, label ->
        val value = lines.seasonLine[label] ?: 0.0
        val metric = Metric(
            label = label,
            value = lines.format(label, value),
            percentile = lines.percentile(label, value),
            category = if (lines.side == TeamSide.DEFENSE) MetricCategory.DEFENSE else MetricCategory.PASSING,
            id = "team-std-$label",
        )
        Box(
            Modifier.fillMaxWidth()
                .background(if ((startIndex + offset) % 2 == 0) GridironPalette.surface else GridironPalette.surfaceAlt)
                .bottomHairline(GridironPalette.divider)
                .padding(horizontal = GridironGeo.padCard, vertical = 12.dp),
        ) { MetricBar(metric) }
    }
}

// MARK: - Recent

@Composable
private fun RecentContent(lines: StandardLines, isPro: Boolean, loading: Boolean, loadError: String?, retry: () -> Unit) {
    when {
        !isPro -> Box(Modifier.fillMaxWidth()) {
            // The blurred sample is hidden from screen readers: it is illustrative, not real.
            Box(Modifier.blur(8.dp).clearAndSetSemantics { }) { Teaser(lines) }
            BlurGateUnlock("See every club's last 3 / 5 / 8 games", PaywallTrigger.TeamView, Modifier.align(Alignment.BottomCenter))
        }
        loading -> GamesTeamsUi.InlineSpinner("Loading recent games…")
        loadError != null -> InlineLoadError(loadError, retry)
        else -> RecentTotals(lines)
    }
}

@Composable
private fun RecentTotals(lines: StandardLines) {
    val totals = lines.windowTotals
    if (totals.isEmpty()) {
        EmptyState("No ${lines.side.label.lowercase()} games in the last ${lines.windowGames}")
        return
    }
    val rates = lines.windowRates
    val seasonLine = lines.seasonLine

    // Season to window, not a percentile bar. Five games of team yards per
    // attempt sits outside the whole spread of thirty-two season figures more
    // often than not, so a bar drawn on that ruler pins to 1 or 100 and says
    // nothing. The move against the club's own season number is the real
    // information, and it's the same framing the Trends board uses.
    if (rates.isNotEmpty()) {
        GridironSubSectionBar("RATE · ${lines.windowTitle}")
        lines.sortedByOrder(rates.keys).forEachIndexed { index, label ->
            val now = rates[label] ?: 0.0
            val then = seasonLine[label]
            val showCompare = then != null && !lines.windowIsWholeSeason
            Row(
                Modifier.fillMaxWidth().height(GridironGeo.rowHeight)
                    .background(if (index % 2 == 0) GridironPalette.surface else GridironPalette.surfaceAlt)
                    .bottomHairline(GridironPalette.divider)
                    .padding(horizontal = GridironGeo.padCard),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = GridironType.bodyBold, color = GridironPalette.ink, modifier = Modifier.width(68.dp))
                Text(
                    if (showCompare) "${lines.format(label, then!!)} → ${lines.format(label, now)}" else lines.format(label, now),
                    style = GridironType.small.copy(fontFeatureSettings = "tnum"),
                    color = GridironPalette.inkSecondary,
                )
                Spacer(Modifier.weight(1f))
                if (showCompare) TrendArrow(now - then!!, 1, lowerIsBetter = label in LOWER_IS_BETTER)
            }
        }
        Text(
            if (lines.windowIsWholeSeason) "Every game this club has played so far." else "Compared with the same club's season line.",
            style = GridironType.micro,
            color = GridironPalette.inkTertiary,
            modifier = Modifier.fillMaxWidth().padding(horizontal = GridironGeo.padCard, vertical = 8.dp),
        )
    }

    // Counting stats get no bar. Five games of touchdowns against thirty-two
    // season totals would sit at the first percentile for every club in the
    // league, which says nothing.
    GridironSubSectionBar("TOTALS · ${lines.windowTitle}")
    countingWindowKeys(lines.side).map { it.second }.filter { totals[it] != null }.forEachIndexed { index, label ->
        TotalRow(label, String.format(Locale.US, "%.0f", totals[label] ?: 0.0), index)
    }
}

@Composable
private fun TotalRow(label: String, value: String, index: Int) {
    Row(
        Modifier.fillMaxWidth().height(GridironGeo.rowHeight)
            .background(if (index % 2 == 0) GridironPalette.surface else GridironPalette.surfaceAlt)
            .bottomHairline(GridironPalette.divider)
            .padding(horizontal = GridironGeo.padCard),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = GridironType.bodyBold, color = GridironPalette.ink, modifier = Modifier.weight(1f))
        Text(value, style = GridironType.statSmall, color = GridironPalette.ink, textAlign = TextAlign.End)
    }
}

/**
 * Invented numbers in the real layout, so a free user can see what the window
 * actually reports rather than a padlock. It tracks the window picker, because
 * a preview that ignores the control above it looks broken.
 */
@Composable
private fun Teaser(lines: StandardLines) {
    Column {
        GridironSubSectionBar("RATE · LAST ${lines.windowGames} GAMES")
        lines.teaserRows.forEachIndexed { index, (label, season, window) ->
            Row(
                Modifier.fillMaxWidth().height(GridironGeo.rowHeight)
                    .background(if (index % 2 == 0) GridironPalette.surface else GridironPalette.surfaceAlt)
                    .padding(horizontal = GridironGeo.padCard),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = GridironType.bodyBold, color = GridironPalette.ink, modifier = Modifier.width(68.dp))
                Text(
                    "${lines.format(label, season)} → ${lines.format(label, window)}",
                    style = GridironType.small.copy(fontFeatureSettings = "tnum"),
                    color = GridironPalette.inkSecondary,
                )
                Spacer(Modifier.weight(1f))
                TrendArrow(window - season, 1)
            }
        }
        GridironSubSectionBar("TOTALS · LAST ${lines.windowGames} GAMES")
        lines.teaserTotals.forEachIndexed { index, (label, value) ->
            Row(
                Modifier.fillMaxWidth().height(GridironGeo.rowHeight)
                    .background(if (index % 2 == 0) GridironPalette.surface else GridironPalette.surfaceAlt)
                    .padding(horizontal = GridironGeo.padCard),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = GridironType.bodyBold, color = GridironPalette.ink, modifier = Modifier.weight(1f))
                Text("$value", style = GridironType.statSmall, color = GridironPalette.ink)
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SfIcon("list.bullet.rectangle", 26.dp, GridironPalette.inkTertiary)
        Text(message, style = GridironType.small, color = GridironPalette.inkSecondary, textAlign = TextAlign.Center)
    }
}
