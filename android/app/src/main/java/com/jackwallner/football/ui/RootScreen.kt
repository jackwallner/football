package com.jackwallner.football.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.play.core.review.ReviewManagerFactory
import com.jackwallner.football.AppGraph
import com.jackwallner.football.DebugLaunchOptions
import com.jackwallner.football.data.PaywallTrigger
import com.jackwallner.football.model.SeasonLabel
import com.jackwallner.football.ui.components.FitText
import com.jackwallner.football.ui.components.GridironNavPill
import com.jackwallner.football.ui.components.GridironTopBar
import com.jackwallner.football.ui.components.SeasonPhasePicker
import com.jackwallner.football.ui.components.SfIcon
import com.jackwallner.football.ui.components.StatScoutSheet
import com.jackwallner.football.ui.components.rememberHaptic
import com.jackwallner.football.ui.nav.LocalNavigator
import com.jackwallner.football.ui.nav.NavStack
import com.jackwallner.football.ui.nav.Navigator
import com.jackwallner.football.ui.nav.Route
import com.jackwallner.football.ui.screens.ConfigMissingScreen
import com.jackwallner.football.ui.screens.Destination
import com.jackwallner.football.ui.screens.FeedbackSheet
import com.jackwallner.football.ui.screens.OnboardingScreen
import com.jackwallner.football.ui.screens.PaywallScreen
import com.jackwallner.football.ui.screens.SEEN_KEY
import com.jackwallner.football.ui.screens.TabRoot
import com.jackwallner.football.ui.screens.TrialPitchSheet
import com.jackwallner.football.ui.theme.GridironPalette
import com.jackwallner.football.ui.theme.GridironType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AppTab(val title: String, val icon: String) {
    STATS("Stats", "chart.bar.fill"),
    GAMES("Games", "sportscourt.fill"),
    TRENDS("Trends", "flame.fill"),
    TEAMS("Teams", "shield.lefthalf.filled"),
    COMPARE("Compare", "arrow.left.arrow.right"),
}

@Composable
fun RootScreen(graph: AppGraph) {
    if (graph.api == null) {
        ConfigMissingScreen()
        return
    }
    val vm = graph.dashboard
    val store = graph.subscriptions
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasCompletedOnboarding by remember { mutableStateOf(graph.defaults.getBoolean(SEEN_KEY)) }
    var selection by rememberSaveable { mutableIntStateOf(DebugLaunchOptions.launchTab ?: 0) }
    val navigators = remember { AppTab.entries.associateWith { Navigator() } }
    LaunchedEffect(graph.pendingTab) {
        val name = graph.pendingTab ?: return@LaunchedEffect
        AppTab.entries.firstOrNull { it.title.equals(name, ignoreCase = true) }?.let { selection = it.ordinal }
        graph.pendingTab = null
    }
    var paywall by remember { mutableStateOf<PaywallTrigger?>(null) }
    var pitch by remember { mutableStateOf<PaywallTrigger?>(null) }
    var showFeedback by remember { mutableStateOf(false) }
    var reviewShownThisSession by remember { mutableStateOf(false) }

    val actions = remember {
        object : AppActions {
            override fun openPaywall(trigger: PaywallTrigger) {
                pitch = null
                paywall = trigger
            }

            override fun openTrialPitch(trigger: PaywallTrigger) {
                pitch = trigger
            }

            override fun openFeedback() {
                showFeedback = true
            }

            /** Google forbids a question before the Play card: the gates decide, then the card shows itself. */
            override fun positiveMoment() {
                graph.review.recordPositiveMoment()
                val completed = graph.defaults.getBoolean(SEEN_KEY)
                if (reviewShownThisSession || !graph.review.shouldShowAfterPositiveMoment(completed)) return
                scope.launch {
                    delay(3_500)
                    if (!graph.review.shouldShowAfterPositiveMoment(graph.defaults.getBoolean(SEEN_KEY))) return@launch
                    val activity = context.findActivity() ?: return@launch
                    reviewShownThisSession = true
                    graph.review.markShown()
                    val manager = ReviewManagerFactory.create(activity)
                    manager.requestReviewFlow().addOnCompleteListener { request ->
                        if (request.isSuccessful) manager.launchReviewFlow(activity, request.result)
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) { vm.loadIfNeeded() }
    LaunchedEffect(store.isPro) {
        vm.applyProState(store.isPro)
        if (store.isPro) vm.loadHistoricalIfNeeded()
    }
    // While the app is in front, ask the status endpoint every two minutes so boards update in place.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.refreshOnForeground()
            while (true) {
                delay(120_000)
                vm.refreshOnForeground()
            }
        }
    }

    CompositionLocalProvider(LocalAppActions provides actions) {
        Box(Modifier.fillMaxSize().background(GridironPalette.canvas)) {
            // Every tab stays composed so its stack and scroll survive switching; the active one sits on top.
            AppTab.entries.forEachIndexed { index, tab ->
                val active = selection == index
                Box(
                    Modifier.fillMaxSize().zIndex(if (active) 1f else 0f).background(GridironPalette.canvas)
                        .then(if (active) Modifier else Modifier.alpha(0f).clearAndSetSemantics { }.swallowTouches()),
                ) {
                    NavStack(navigators.getValue(tab), enabled = active && hasCompletedOnboarding) { route ->
                        if (route == Route.Root) TabRoot(tab, active) else Destination(route)
                    }
                }
            }
            FloatingTabBar(selection, Modifier.align(Alignment.BottomCenter).zIndex(2f)) { index ->
                if (selection == index) navigators.getValue(AppTab.entries[index]).popToRoot() else selection = index
            }
            if (!hasCompletedOnboarding) Box(Modifier.zIndex(3f)) {
                OnboardingScreen(
                    onFinish = {
                        graph.defaults.putBoolean(SEEN_KEY, true)
                        hasCompletedOnboarding = true
                    },
                    startPage = DebugLaunchOptions.onboardingPage ?: 0,
                )
            }
        }
        StatScoutSheet(visible = pitch != null, onDismiss = { pitch = null }, fullHeight = false) {
            pitch?.let { TrialPitchSheet(it) { pitch = null } }
        }
        StatScoutSheet(visible = paywall != null, onDismiss = { paywall = null }) {
            paywall?.let { PaywallScreen(it) { paywall = null } }
        }
        StatScoutSheet(visible = showFeedback, onDismiss = { showFeedback = false }) {
            FeedbackSheet { showFeedback = false }
        }
    }
}

/** Hand-rolled floating tab bar: each tab keeps its stack and scroll when you switch away. */
@Composable
private fun FloatingTabBar(selection: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val haptic = rememberHaptic()
    Row(
        modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 12.dp)
            .shadow(12.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.1f), spotColor = Color.Black.copy(alpha = 0.1f))
            .clip(CircleShape)
            .background(GridironPalette.surface.copy(alpha = 0.97f))
            .border(0.5.dp, GridironPalette.hairline, CircleShape)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .widthIn(max = 420.dp),
    ) {
        AppTab.entries.forEachIndexed { index, tab ->
            val isSelected = selection == index
            val tint by animateColorAsState(if (isSelected) GridironPalette.turf else GridironPalette.inkSecondary, label = "tabTint")
            Column(
                Modifier.width(66.dp).height(54.dp).clip(CircleShape)
                    .background(if (isSelected) GridironPalette.turf.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable {
                        onSelect(index)
                        haptic()
                    }
                    .semantics { contentDescription = tab.title; selected = isSelected }
                    .testTag("tab_${tab.title}"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
            ) {
                SfIcon(tab.icon, 22.dp, tint)
                FitText(tab.title, GridironType.smallBold, tint, minScale = 0.8f)
            }
        }
    }
}

/** The midnight bar for a home tab: a leading chooser or title, then settings and the upgrade pill. */
@Composable
fun HomeTopBar(title: String? = null, leading: (@Composable RowScope.() -> Unit)? = null) {
    val graph = LocalGraph.current
    val store = graph.subscriptions
    val navigator = LocalNavigator.current
    val actions = LocalAppActions.current
    val haptic = rememberHaptic()
    GridironTopBar(
        title = title,
        leading = leading,
        trailing = {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable {
                    haptic()
                    navigator.push(Route.Settings)
                }.semantics { contentDescription = "Settings" }.testTag("settingsButton"),
                contentAlignment = Alignment.Center,
            ) { SfIcon("gearshape", 22.dp, Color.White.copy(alpha = 0.85f)) }
            if (!store.isPro) {
                val label = store.upgradeCTALabel
                Box(
                    Modifier.height(44.dp).clip(CircleShape).clickable { actions.openPaywall(store.defaultUpgradeTrigger) }
                        .semantics { contentDescription = "$label, unlock all features" }.testTag("upgradeButton"),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        Modifier.clip(CircleShape).background(Color(1f, 0.8f, 0f)).padding(horizontal = 9.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SfIcon("crown.fill", 12.dp, GridironPalette.midnight)
                        Text(label, style = GridironType.micro.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = GridironPalette.midnight, maxLines = 1)
                    }
                }
            }
        },
    )
}

/** The season and phase pill on Stats, Trends and Teams: season type first, then the years. */
@Composable
fun SeasonPhaseNavPill(seasons: List<Int>, onLockedSeason: (Int) -> Unit) {
    val vm = LocalGraph.current.dashboard
    SeasonPhasePicker(
        seasons = seasons,
        selectedSeason = vm.selectedSeason,
        selectedPhase = vm.selectedPhase,
        isSeasonLocked = vm::isSeasonLocked,
        onSelectSeason = { season -> if (vm.isSeasonLocked(season)) onLockedSeason(season) else vm.selectSeason(season) },
        onSelectPhase = { vm.selectedPhase = it },
    ) { open ->
        GridironNavPill(
            SeasonLabel.text(vm.selectedSeason) + " · " + vm.selectedPhase.label,
            open,
            description = "Season and season type, ${SeasonLabel.text(vm.selectedSeason)}, ${vm.selectedPhase.label}",
        )
    }
}

/** A hidden tab must never take a tap meant for the visible one. */
private fun Modifier.swallowTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
    }
}

/** The nav-bar season pill for a screen that keeps its own season and phase (Trends, a team page). */
@Composable
fun SeasonPhaseNavPillFor(
    seasons: List<Int>,
    selectedSeason: Int,
    selectedPhase: com.jackwallner.football.model.SeasonPhase,
    onSelectSeason: (Int) -> Unit,
    onSelectPhase: (com.jackwallner.football.model.SeasonPhase) -> Unit,
) {
    val vm = LocalGraph.current.dashboard
    SeasonPhasePicker(
        seasons = seasons,
        selectedSeason = selectedSeason,
        selectedPhase = selectedPhase,
        isSeasonLocked = vm::isSeasonLocked,
        onSelectSeason = onSelectSeason,
        onSelectPhase = onSelectPhase,
    ) { open ->
        GridironNavPill(
            SeasonLabel.text(selectedSeason) + " · " + selectedPhase.label,
            open,
            description = "Season and season type, ${SeasonLabel.text(selectedSeason)}, ${selectedPhase.label}",
        )
    }
}
