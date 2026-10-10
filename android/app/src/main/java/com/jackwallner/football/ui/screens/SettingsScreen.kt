package com.jackwallner.football.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jackwallner.football.BuildConfig
import com.jackwallner.football.data.StoreLinks
import com.jackwallner.football.model.DateText
import com.jackwallner.football.model.FootballMetricRegistry
import com.jackwallner.football.model.SeasonPhase
import com.jackwallner.football.ui.LocalAppActions
import com.jackwallner.football.ui.LocalGraph
import com.jackwallner.football.ui.components.CrownYellow
import com.jackwallner.football.ui.components.EmptyState
import com.jackwallner.football.ui.components.GridironSectionBar
import com.jackwallner.football.ui.components.GridironTopBar
import com.jackwallner.football.ui.components.Hairline
import com.jackwallner.football.ui.components.PushedScreen
import com.jackwallner.football.ui.components.SearchField
import com.jackwallner.football.ui.components.SfIcon
import com.jackwallner.football.ui.components.TextAction
import com.jackwallner.football.ui.components.bottomBarPadding
import com.jackwallner.football.ui.components.bottomHairline
import com.jackwallner.football.ui.components.gridironCard
import com.jackwallner.football.ui.nav.LocalNavigator
import com.jackwallner.football.ui.nav.Route
import com.jackwallner.football.ui.openMail
import com.jackwallner.football.ui.openPlayListing
import com.jackwallner.football.ui.openUrl
import com.jackwallner.football.ui.theme.GridironGeo
import com.jackwallner.football.ui.theme.GridironPalette
import com.jackwallner.football.ui.theme.GridironType
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private val LONG_DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a", Locale.US)

/**
 * Settings, a place in the app rather than a modal. Rate and Send Feedback are
 * separate rows: Google forbids gating the Play review behind a question.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen() {
    val graph = LocalGraph.current
    val vm = graph.dashboard
    val store = graph.subscriptions
    val actions = LocalAppActions.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showReviewerCode by remember { mutableStateOf(false) }
    val freshness = vm.freshnessForDisplay
    val coverage = freshness?.coverage ?: vm.dataCoverage
    val gamesThrough = coverage?.let { c ->
        val stamp = DateText.gameDay(c.asOf)
        c.week?.let { "Week $it${if (c.phase == SeasonPhase.PLAYOFFS) " (playoffs)" else ""} · $stamp" } ?: stamp
    } ?: "-"
    val checked = (freshness?.checkedAt ?: vm.lastUpdated)?.let { LONG_DATE.format(it.atZone(ZoneId.systemDefault())) } ?: "-"
    val sourcePublished = freshness?.sourcePublishedAt?.let { LONG_DATE.format(it.atZone(ZoneId.systemDefault())) }

    PushedScreen("Settings") {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(top = 12.dp).padding(bottomBarPadding()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card("STATSCOUT+") {
                Row(Modifier.padding(GridironGeo.padCard), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    SfIcon("crown.fill", 26.dp, if (store.isPro) CrownYellow else GridironPalette.inkTertiary)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (store.isPro) "StatScout+ Unlocked" else "Free Version", style = GridironType.bodyBold, color = GridironPalette.ink)
                        Text(
                            if (store.isPro) "All StatScout+ features are active." else "Unlock Trends, recent form, head-to-head and every season back to 2000.",
                            style = GridironType.small,
                            color = GridironPalette.inkSecondary,
                        )
                    }
                    if (!store.isPro) {
                        Box(
                            Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(8.dp)).background(GridironPalette.turf)
                                .clickable { actions.openPaywall(store.defaultUpgradeTrigger) }.padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("settingsUpgrade"),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (store.isLapsed) "Renew" else store.upgradeCTALabel, style = GridironType.smallBold, color = Color.White) }
                    }
                }
                Hairline()
                LinkRow("arrow.clockwise", "Restore Purchases") { scope.launch { store.restorePurchases() } }
                store.activeProductId?.let { productId ->
                    Hairline()
                    LinkRow("slider.horizontal.3", "Manage Subscription") { context.openUrl(StoreLinks.manageSubscriptionURL(productId)) }
                }
                store.lastError?.let {
                    Hairline()
                    Text(it, style = GridironType.small, color = GridironPalette.turf, modifier = Modifier.fillMaxWidth().padding(GridironGeo.padCard))
                }
            }
            Card("REFERENCE") {
                Row(Modifier.clickable { navigator.push(Route.Glossary) }) {
                    InfoRow("text.book.closed.fill", "Stat Glossary", "Definitions and formulas for every stat in StatScout.")
                }
            }
            Card("SUPPORT & PRIVACY") {
                Row(Modifier.clickable {
                    graph.review.markOpenedWriteReview()
                    context.openPlayListing()
                }) { InfoRow("star.fill", "Rate on Google Play", "Help StatScout grow with an honest review.") }
                Hairline()
                Row(Modifier.clickable { actions.openFeedback() }) { InfoRow("square.and.pencil", "Send Feedback", "Tell us what to improve.") }
                Hairline()
                Row(Modifier.clickable { context.openUrl(StoreLinks.SUPPORT_URL) }) { InfoRow("envelope.fill", "Contact Support", StoreLinks.FEEDBACK_EMAIL) }
                Hairline()
                Row(Modifier.clickable { context.openUrl(StoreLinks.PRIVACY_URL) }) { InfoRow("shield.lefthalf.filled", "Privacy Policy", "No ads or tracking.") }
            }
            Card("DATA") {
                InfoRow("arrow.triangle.2.circlepath", "Data Updates", "Checks for new NFL player data throughout the season.")
                Hairline()
                InfoRow("calendar.badge.clock", "Games Through", gamesThrough)
                Hairline()
                InfoRow("clock.arrow.circlepath", "Last Checked", checked)
                sourcePublished?.let {
                    Hairline()
                    InfoRow("cloud.sun.fill", "Source Published", it)
                }
            }
            Card("STATSCOUT") {
                Row(Modifier.padding(GridironGeo.padCard), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    SfIcon("football.fill", 26.dp, GridironPalette.turf)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Percentile Rankings", style = GridironType.cardTitle, color = GridironPalette.ink)
                        Text("Mobile-first percentile rankings and leaderboards for fans and media.", style = GridironType.small, color = GridironPalette.inkSecondary)
                    }
                }
            }
            Card("VERSION") {
                // Long press opens the private reviewer access for Google Play's review team.
                Row(
                    Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { showReviewerCode = true }).padding(GridironGeo.padCard)
                        .testTag("versionRow"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("App Version", style = GridironType.bodyBold, color = GridironPalette.ink, modifier = Modifier.weight(1f))
                    Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = GridironType.statSmall, color = GridironPalette.inkSecondary)
                }
            }
            Card("DISCLAIMER") {
                Text(
                    "Not affiliated with, endorsed by, or sponsored by the National Football League, its teams, or the NFLPA. Team names and abbreviations are used for identification only. All trademarks are property of their respective owners.",
                    style = GridironType.small,
                    color = GridironPalette.inkSecondary,
                    modifier = Modifier.fillMaxWidth().padding(GridironGeo.padCard),
                )
            }
        }
    }
    if (showReviewerCode) ReviewerCodeDialog { showReviewerCode = false }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().gridironCard()) {
        GridironSectionBar(title)
        content()
    }
}

@Composable
private fun InfoRow(icon: String, title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(GridironGeo.padCard), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) { SfIcon(icon, 24.dp, GridironPalette.turf) }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = GridironType.bodyBold, color = GridironPalette.ink)
            Text(subtitle, style = GridironType.small, color = GridironPalette.inkSecondary)
        }
    }
}

@Composable
private fun LinkRow(icon: String, title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(GridironGeo.padCard),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SfIcon(icon, 14.dp, GridironPalette.linkBlue)
        Text(title, style = GridironType.smallBold, color = GridironPalette.linkBlue)
    }
}

/** Google Play reviewers reach StatScout+ without paying; only a hash of the code is in the app. */
@Composable
private fun ReviewerCodeDialog(onDismiss: () -> Unit) {
    val store = LocalGraph.current.subscriptions
    var code by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GridironPalette.surface,
        title = { Text("Reviewer Access", style = GridironType.cardTitle, color = GridironPalette.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter the reviewer code provided with this app's review notes.", style = GridironType.small, color = GridironPalette.inkSecondary)
                OutlinedTextField(
                    code,
                    { code = it; failed = false },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GridironPalette.turf, cursorColor = GridironPalette.turf),
                    modifier = Modifier.testTag("reviewerCode"),
                )
                if (failed) Text("That code isn't valid.", style = GridironType.small, color = GridironPalette.performanceLow)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (store.activateReviewAccess(code)) onDismiss() else failed = true }) {
                Text("Unlock", style = GridironType.bodyBold, color = GridironPalette.turf)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = GridironType.body, color = GridironPalette.inkSecondary) } },
    )
}

private data class GlossaryEntry(val id: String, val label: String, val category: String, val description: String)

private val SUPPLEMENTAL = listOf(
    GlossaryEntry("general-games", "G", "General", "Games in which the player recorded a tracked statistic."),
    GlossaryEntry("general-value", "Contract Value", "General", "Production percentile minus pay percentile, both ranked among this season's qualified players at the position who have an active contract. Pay is the deal's yearly average as a share of the salary cap when it was signed. +20 means producing like a player paid far more. Offense only until advanced defensive stats publish. Contracts: OverTheCap via nflverse."),
    GlossaryEntry("general-power", "Power Rating", "General", "Points per game better or worse than an average team on a neutral field, from EPA per dropback, EPA per run and points, for minus against, adjusted for schedule. Early in the season last year's rating counts as five games of evidence. Two ratings read like a point spread, with about two points for home field. Modeled on Hawk Blogger's HB Power Rankings."),
    GlossaryEntry("general-small-sample", "Small sample", "General", "Below the playing-time minimum for that stat, prorated by how much of the season the typical team has played. Defenders need a quarter of their team's defensive snaps."),
    GlossaryEntry("general-not-ranked", "Not ranked", "General", "A counting stat at zero. When most of the league has none of something, a tie at zero has no honest percentile, so the value shows and the bar does not."),
    GlossaryEntry("passing-cmp-att", "Cmp/Att", "Passing", "Pass completions and attempts."),
    GlossaryEntry("passing-cmp", "Cmp", "Passing", "Completed forward passes."),
    GlossaryEntry("passing-att", "Att", "Passing", "Forward pass attempts."),
    GlossaryEntry("rushing-car", "Car", "Rushing", "Rushing attempts, also called carries."),
    GlossaryEntry("receiving-rec-tgt", "Rec/Tgt", "Receiving", "Receptions and targets."),
    GlossaryEntry("receiving-tgt", "Tgt", "Receiving", "Pass attempts directed at the receiver."),
    GlossaryEntry("percentile", "Percentile", "General", "A 1–100 rank among players in the same season, season type, and stat category. Higher is always better after lower-is-better stats are inverted."),
)

@Composable
fun StatGlossaryScreen() {
    val context = LocalContext.current
    var searchText by remember { mutableStateOf("") }
    val all = (SUPPLEMENTAL + FootballMetricRegistry.definitions.map { GlossaryEntry("${it.category.raw}-${it.label}", it.label, it.category.raw, it.description) })
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    val entries = if (searchText.isEmpty()) all else all.filter {
        it.label.contains(searchText, true) || it.category.contains(searchText, true) || it.description.contains(searchText, true)
    }
    val categories = listOf("General", "Passing", "Rushing", "Receiving", "Defense").filter { c -> entries.any { it.category == c } }
    PushedScreen("Stat Glossary") {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(top = 12.dp).padding(bottomBarPadding()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SearchField(searchText, { searchText = it }, prompt = "Search stats")
            Text(
                "Values come from nflverse player statistics and NFL Next Gen Stats. Percentiles are calculated separately for each season and season type. The current season ranks everyone who has played; past seasons rank qualifying players.",
                style = GridironType.small,
                color = GridironPalette.inkSecondary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            if (entries.isEmpty()) {
                EmptyState("No stats found", "magnifyingglass", "Nothing matches \"$searchText\". Try a stat's abbreviation, like YAC or EPA.", Modifier.padding(vertical = 24.dp))
            } else categories.forEach { category ->
                Column(Modifier.fillMaxWidth().gridironCard()) {
                    GridironSectionBar(category.uppercase())
                    entries.filter { it.category == category }.forEachIndexed { index, entry ->
                        Column(
                            Modifier.fillMaxWidth().background(if (index % 2 == 0) GridironPalette.surface else GridironPalette.surfaceAlt).bottomHairline()
                                .padding(GridironGeo.padCard),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(entry.label, style = GridironType.bodyBold, color = GridironPalette.ink)
                            Text(entry.description, style = GridironType.small, color = GridironPalette.inkSecondary)
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().gridironCard()) {
                GridironSectionBar("SOURCES")
                listOf(
                    "NFL Next Gen Stats Glossary" to "https://nextgenstats.nfl.com/glossary",
                    "nflreadpy Player Stats" to "https://nflreadpy.nflverse.com/api/load_functions/#nflreadpy.load_player_stats",
                ).forEachIndexed { index, (title, url) ->
                    if (index > 0) Hairline()
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { context.openUrl(url) }.padding(GridironGeo.padCard),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(title, style = GridironType.small, color = GridironPalette.turf, modifier = Modifier.weight(1f))
                        SfIcon("arrow.up.right", 13.dp, GridironPalette.inkTertiary)
                    }
                }
            }
        }
    }
}

/** "Help us improve": the feedback half of the iOS review sheet, as its own row. */
@Composable
fun FeedbackSheet(onDismiss: () -> Unit) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    val trimmed = text.trim()
    Column(Modifier.fillMaxSize().background(GridironPalette.canvas)) {
        GridironTopBar(title = "Help us improve", leading = { TextAction("Not now", GridironType.body, Color.White, onDismiss) })
        Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("What would make StatScout work better for you?", style = GridironType.body, color = GridironPalette.inkSecondary)
            Box(
                Modifier.fillMaxWidth().heightIn(min = 160.dp).clip(RoundedCornerShape(GridironGeo.radiusCard)).background(GridironPalette.surface)
                    .border(0.5.dp, GridironPalette.hairline, RoundedCornerShape(GridironGeo.radiusCard)).padding(10.dp),
            ) {
                BasicTextField(
                    text,
                    { text = it },
                    textStyle = GridironType.body.copy(color = GridironPalette.ink),
                    cursorBrush = SolidColor(GridironPalette.turf),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp).testTag("feedbackText"),
                )
            }
            Text("Opens your mail app with a draft to the developer. No analytics, just your words.", style = GridironType.small, color = GridironPalette.inkTertiary)
            Box(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(CircleShape).background(GridironPalette.turf).alpha(if (trimmed.isEmpty()) 0.5f else 1f)
                    .clickable(enabled = trimmed.isNotEmpty()) {
                        if (context.openMail(StoreLinks.feedbackMail("StatScout feedback", trimmed))) {
                            graph.review.markFeedbackSubmitted()
                            onDismiss()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { Text("Send feedback", style = GridironType.bodyBold, color = Color.White) }
        }
    }
}

/** A build without its feed configuration says so instead of showing an empty league. */
@Composable
fun ConfigMissingScreen() {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().background(GridironPalette.canvas).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        SfIcon("exclamationmark.triangle.fill", 44.dp, GridironPalette.turf)
        Text("StatScout can't load", style = GridironType.playerName, color = GridironPalette.ink)
        Text(
            "This build is missing its data-feed configuration. Please install the latest version from Google Play or contact support.",
            style = GridironType.body,
            color = GridironPalette.inkSecondary,
            textAlign = TextAlign.Center,
        )
        Box(
            Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).background(GridironPalette.turf).clickable { context.openUrl(StoreLinks.SUPPORT_URL) }
                .padding(horizontal = 18.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Contact Support", style = GridironType.bodyBold, color = Color.White) }
        Spacer(Modifier.height(8.dp))
    }
}
