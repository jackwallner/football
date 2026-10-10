package com.jackwallner.football.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jackwallner.football.data.PaywallTrigger
import com.jackwallner.football.data.PurchaseOutcome
import com.jackwallner.football.ui.LocalAppActions
import com.jackwallner.football.ui.LocalGraph
import com.jackwallner.football.ui.components.DarkStatusIcons
import com.jackwallner.football.ui.components.SfIcon
import com.jackwallner.football.ui.findActivity
import com.jackwallner.football.ui.theme.GridironPalette
import com.jackwallner.football.ui.theme.GridironType
import kotlinx.coroutines.launch

private data class Bullet(val text: String, val icon: String = "checkmark.circle.fill")
private data class OnboardingPage(val icon: String, val title: String, val description: String, val bullets: List<Bullet>)

private val PAGES = listOf(
    OnboardingPage(
        "football.fill", "Your Pocket\nScout",
        "NFL percentile rankings built for a fast mobile view. Every player, every metric, updated soon after games finish.",
        listOf(
            Bullet("Every player ranked from week one"),
            Bullet("EPA, CPOE, YAC, RYOE, and more"),
            Bullet("Fresh stats soon after every game"),
            Bullet("No account or sign-up"),
        ),
    ),
    OnboardingPage(
        "chart.bar.fill", "Find Insights\nFast",
        "Scores, leaders and movement across the league in seconds, with the numbers behind every game.",
        listOf(
            Bullet("Games: scores and box scores"),
            Bullet("Stats: leaders, best and worst"),
            Bullet("Trends: heating up, cooling off"),
            Bullet("Teams: any roster, any season"),
            Bullet("Compare: two players side by side"),
        ),
    ),
    OnboardingPage(
        "crown.fill", "Go Deeper\nwith StatScout+",
        "Season numbers tell you who's good. StatScout+ tells you who's good right now, and lets you prove it.",
        listOf(
            Bullet("Trends: the league ranked by form", "flame.fill"),
            Bullet("Last 3 / 5 / 8 games, any player", "chart.bar.fill"),
            Bullet("Head-to-head on every percentile", "person.2.fill"),
            Bullet("Seasons back to 2000, year over year", "calendar.badge.clock"),
        ),
    ),
)

/**
 * Three cards, then the one-tap monthly offer on the last. The primary button
 * and the footer slot below it are identical on every page, so the thumb
 * target never moves; everything else grows upward above the button.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit, startPage: Int = 0) {
    val graph = LocalGraph.current
    val store = graph.subscriptions
    val vm = graph.dashboard
    val actions = LocalAppActions.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = startPage) { PAGES.size }
    val isLastPage = pager.currentPage == PAGES.size - 1
    var isStartingTrial by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var trialError by remember { mutableStateOf<String?>(null) }
    val showsUpsell = isLastPage && !store.isPro
    // The label and its disclosure come from the same loaded plan, so a paid button never appears without a price.
    val monthly = store.monthlyPlan
    val disclosure = if (monthly != null) store.onboardingMonthlyDisclosureText else null
    val ctaLabel = if (monthly != null && disclosure != null) store.onboardingMonthlyCTALabel else "Upgrade to StatScout+"

    LaunchedEffect(Unit) { if (store.offeringId == null) store.fetchProducts() }
    DarkStatusIcons()
    // Becoming Pro here (purchase or restore) finishes onboarding, as on iOS.
    var wasPro by remember { mutableStateOf(store.isPro) }
    LaunchedEffect(store.isPro) {
        if (store.isPro && !wasPro) onFinish()
        wasPro = store.isPro
    }

    fun buyMonthly() {
        val plan = store.monthlyPlan
        if (plan == null) {
            actions.openPaywall(PaywallTrigger.Onboarding)
            return
        }
        val activity = context.findActivity() ?: return
        trialError = null
        isStartingTrial = true
        scope.launch {
            try {
                when (store.purchase(activity, plan)) {
                    PurchaseOutcome.PURCHASED -> Unit
                    PurchaseOutcome.PENDING -> trialError = "Purchase pending approval. StatScout+ unlocks automatically once it's approved."
                    PurchaseOutcome.CANCELLED -> trialError = "Purchase cancelled. Tap again to continue."
                }
            } catch (error: Exception) {
                trialError = store.lastError ?: "Couldn't complete the purchase. Please try again."
            }
            isStartingTrial = false
        }
    }

    Box(Modifier.fillMaxSize().background(GridironPalette.canvas).windowInsetsPadding(WindowInsets.safeDrawing).testTag("onboarding")) {
        Column(Modifier.fillMaxSize().widthIn(max = 700.dp).align(Alignment.TopCenter)) {
            Box(Modifier.fillMaxWidth().height(40.dp)) {
                if (!isLastPage) {
                    Text(
                        "Skip",
                        style = GridironType.bodyBold,
                        color = GridironPalette.turf,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onFinish).padding(horizontal = 12.dp, vertical = 10.dp).testTag("onboardingSkip"),
                    )
                }
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { index -> OnboardingCard(PAGES[index]) }
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                repeat(PAGES.size) { i ->
                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (i == pager.currentPage) GridironPalette.ink else GridironPalette.hairline))
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.fillMaxWidth().alpha(if (showsUpsell) 1f else 0f), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    PrimaryButton("Get Started", prominent = false, enabled = showsUpsell, onClick = onFinish)
                    Text(
                        trialError ?: " ",
                        style = GridironType.micro,
                        color = GridironPalette.performanceLow,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth().height(30.dp),
                    )
                    if (disclosure != null) {
                        Text(disclosure, style = GridironType.micro, color = GridironPalette.inkTertiary, textAlign = TextAlign.Center)
                    }
                    if (showsUpsell) LegalLinks()
                }
                if (isLastPage) {
                    if (store.isPro) PrimaryButton("Get Started", prominent = true, onClick = onFinish)
                    else PrimaryButton(ctaLabel, prominent = true, loading = isStartingTrial, enabled = !isStartingTrial, tag = "onboardingBuy") { buyMonthly() }
                } else {
                    PrimaryButton("Continue", prominent = true, tag = "onboardingContinue") {
                        scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    }
                }
                Box(Modifier.fillMaxWidth().height(32.dp), contentAlignment = Alignment.Center) {
                    when {
                        isLastPage && !vm.isReady -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(14.dp), color = GridironPalette.inkSecondary, strokeWidth = 2.dp)
                            Text(vm.loadingMessage, style = GridironType.micro, color = GridironPalette.inkSecondary)
                        }
                        isLastPage && !store.isPro -> Text(
                            if (isRestoring) "Restoring…" else "Restore Purchases",
                            style = GridironType.micro,
                            color = GridironPalette.inkTertiary,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = !isRestoring) {
                                isRestoring = true
                                scope.launch {
                                    store.restorePurchases()
                                    if (!store.isPro) trialError = store.lastError ?: "No active StatScout+ purchase was found for this Google Play account."
                                    isRestoring = false
                                }
                            }.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrimaryButton(
    title: String,
    prominent: Boolean,
    enabled: Boolean = true,
    loading: Boolean = false,
    tag: String? = null,
    onClick: () -> Unit,
) {
    Box(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp))
            .background(if (prominent) GridironPalette.turf else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .semantics { contentDescription = title },
        contentAlignment = Alignment.Center,
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        else Text(title, style = GridironType.bodyBold, color = if (prominent) Color.White else GridironPalette.ink, textAlign = TextAlign.Center)
    }
}

@Composable
private fun OnboardingCard(page: OnboardingPage) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Short phones get a smaller illustration so the benefits never clip under the dots.
        val compact = maxHeight < 440.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp, Alignment.CenterVertically),
        ) {
            Box(Modifier.width(200.dp).height(if (compact) 72.dp else 104.dp), contentAlignment = Alignment.Center) {
                BarBackdrop(
                    listOf(92, 78, 65, 48, 32, 88, 71, 55, 42, 80),
                    { GridironPalette.color(it).copy(alpha = 0.55f) },
                    barWidth = 14,
                    spacing = 6,
                    scale = if (compact) 0.72f else 1.1f,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                Box(
                    Modifier.size(if (compact) 64.dp else 84.dp).shadow(12.dp, CircleShape, ambientColor = GridironPalette.midnight, spotColor = GridironPalette.midnight)
                        .clip(CircleShape).background(GridironPalette.midnight),
                    contentAlignment = Alignment.Center,
                ) { SfIcon(page.icon, if (compact) 28.dp else 36.dp, Color.White) }
            }
            Text(page.title, style = GridironType.playerName, color = GridironPalette.ink, textAlign = TextAlign.Center)
            Text(
                page.description,
                style = GridironType.body,
                color = GridironPalette.inkSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                page.bullets.forEach { bullet ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        SfIcon(bullet.icon, 16.dp, GridironPalette.turf, Modifier.padding(top = 2.dp))
                        Text(bullet.text, style = GridironType.body, color = GridironPalette.ink)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

const val SEEN_KEY = "hasCompletedOnboarding"
