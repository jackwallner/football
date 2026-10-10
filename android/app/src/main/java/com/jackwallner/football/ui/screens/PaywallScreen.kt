package com.jackwallner.football.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Close
import com.jackwallner.football.data.PackageKind
import com.jackwallner.football.data.PaywallTrigger
import com.jackwallner.football.data.Plan
import com.jackwallner.football.data.PriceEmphasis
import com.jackwallner.football.data.PurchaseOutcome
import com.jackwallner.football.data.StoreLinks
import com.jackwallner.football.data.capitalizeWords
import com.jackwallner.football.data.yearlySavingsPercent
import com.jackwallner.football.ui.LocalGraph
import com.jackwallner.football.ui.components.SfIcon
import com.jackwallner.football.ui.components.TextAction
import com.jackwallner.football.ui.findActivity
import com.jackwallner.football.ui.openUrl
import com.jackwallner.football.ui.theme.GridironGeo
import com.jackwallner.football.ui.theme.GridironPalette
import com.jackwallner.football.ui.theme.GridironType
import kotlinx.coroutines.launch

/** Native StatScout+ plan picker. Purchases go through RevenueCat unchanged. */
@Composable
fun PaywallScreen(trigger: PaywallTrigger, onDismiss: () -> Unit) {
    val graph = LocalGraph.current
    val store = graph.subscriptions
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedId by remember { mutableStateOf<String?>(null) }
    var isPurchasing by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var restoreMessage by remember { mutableStateOf<String?>(null) }
    val plans = store.plans

    LaunchedEffect(trigger) {
        graph.paywallGate.markPresented(trigger)
        store.trackPaywallImpression(trigger.paywallImpressionId)
        if (store.plans.isEmpty()) store.fetchProducts()
    }
    LaunchedEffect(plans) {
        if (selectedId == null && plans.isNotEmpty()) {
            selectedId = (plans.firstOrNull { it.kind == PackageKind.YEARLY } ?: plans.first()).packageId
        }
    }
    LaunchedEffect(store.isPro) { if (store.isPro) onDismiss() }
    val selected = plans.firstOrNull { it.packageId == selectedId }

    Box(Modifier.fillMaxSize().background(GridironPalette.canvas)) {
        when {
            store.isLoadingProducts && plans.isEmpty() -> Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CircularProgressIndicator(color = GridironPalette.turf)
                Text("Loading plans…", style = GridironType.small, color = GridironPalette.inkTertiary)
            }
            plans.isEmpty() -> Column(
                Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SfIcon("wifi.exclamationmark", 40.dp, GridironPalette.inkTertiary)
                Text("Couldn't Load Plans", style = GridironType.cardTitle, color = GridironPalette.inkSecondary)
                Text(
                    store.lastError ?: "Check your connection and try again.",
                    style = GridironType.small,
                    color = GridironPalette.inkTertiary,
                    textAlign = TextAlign.Center,
                )
                TextAction("Try Again", GridironType.bodyBold, GridironPalette.turf, { scope.launch { store.fetchProducts() } })
            }
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Hero(trigger)
                Column(
                    Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    trigger.features.forEach { (icon, title) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) { SfIcon(icon, 18.dp, GridironPalette.turf) }
                            Text(title, style = GridironType.body, color = GridironPalette.ink)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                            SfIcon("bolt.fill", 13.dp, GridironPalette.inkTertiary)
                            Text("Next Gen-grade data", style = GridironType.smallBold, color = GridironPalette.inkTertiary)
                        }
                        Text("·", style = GridironType.smallBold, color = GridironPalette.inkTertiary)
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                            SfIcon("checkmark.shield.fill", 13.dp, GridironPalette.inkTertiary)
                            Text("Cancel anytime", style = GridironType.smallBold, color = GridironPalette.inkTertiary)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val monthly = plans.firstOrNull { it.kind == PackageKind.MONTHLY }
                        plans.forEach { plan ->
                            val yearly = plan.kind == PackageKind.YEARLY
                            PlanCard(
                                plan = plan,
                                isSelected = plan.packageId == selectedId,
                                showsTrial = store.isEligibleForIntroOffer(plan),
                                isMostPopular = yearly,
                                savingsPercent = if (yearly) yearlySavingsPercent(plan, monthly) else null,
                                perMonthLabel = if (yearly) plan.monthlyEquivalentLabel else null,
                                monthlyAnchorLabel = if (yearly) store.monthlyAnchorPriceLabel else null,
                            ) { selectedId = plan.packageId }
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(12.dp))
                                .background(GridironPalette.turf.copy(alpha = if (selected == null) 0.5f else 1f))
                                .clickable(enabled = !isPurchasing && selected != null) {
                                    val plan = selected ?: return@clickable
                                    val activity = context.findActivity() ?: return@clickable
                                    errorMessage = null
                                    restoreMessage = null
                                    isPurchasing = true
                                    scope.launch {
                                        try {
                                            when (store.purchase(activity, plan)) {
                                                PurchaseOutcome.PURCHASED -> Unit
                                                PurchaseOutcome.PENDING -> restoreMessage = "Purchase pending approval. StatScout+ unlocks automatically once it's approved."
                                                PurchaseOutcome.CANCELLED -> errorMessage = "Purchase cancelled. Tap again to continue."
                                            }
                                        } catch (error: Exception) {
                                            errorMessage = store.lastError ?: error.message ?: "Couldn't complete the purchase. Please try again."
                                        }
                                        isPurchasing = false
                                    }
                                }
                                .testTag("paywallPurchase"),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isPurchasing) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            else Text(selected?.let { store.planPickerCTALabel(it) } ?: "Continue", style = GridironType.bodyBold, color = Color.White)
                        }
                        selected?.let {
                            Text(
                                store.disclosureText(it, PriceEmphasis.BILLED_AMOUNT_FIRST),
                                style = GridironType.micro,
                                color = GridironPalette.inkTertiary,
                                textAlign = TextAlign.Center,
                            )
                        }
                        errorMessage?.let { Text(it, style = GridironType.small, color = GridironPalette.turf, textAlign = TextAlign.Center) }
                        restoreMessage?.let { Text(it, style = GridironType.small, color = GridironPalette.inkSecondary, textAlign = TextAlign.Center) }
                        TextAction(
                            if (isRestoring) "Restoring…" else "Restore Purchases",
                            GridironType.smallBold,
                            GridironPalette.inkSecondary,
                            {
                                errorMessage = null
                                restoreMessage = null
                                isRestoring = true
                                scope.launch {
                                    store.restorePurchases()
                                    if (!store.isPro) restoreMessage = store.lastError ?: "No active StatScout+ purchase was found for this Google Play account."
                                    isRestoring = false
                                }
                            },
                            enabled = !isRestoring && !isPurchasing,
                        )
                        LegalLinks()
                    }
                }
            }
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(8.dp).size(48.dp).clip(CircleShape).clickable(onClick = onDismiss)
                .semantics { contentDescription = "Close" }.testTag("paywallClose"),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.28f)), contentAlignment = Alignment.Center) {
                SfIcon(androidx.compose.material.icons.Icons.Filled.Close, 18.dp, Color.White)
            }
        }
    }
}

@Composable
fun LegalLinks(extra: (@Composable () -> Unit)? = null) {
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        TextAction("Terms", GridironType.micro, GridironPalette.inkTertiary, { context.openUrl(StoreLinks.TERMS_URL) })
        TextAction("Privacy", GridironType.micro, GridironPalette.inkTertiary, { context.openUrl(StoreLinks.PRIVACY_URL) })
        extra?.invoke()
    }
}

@Composable
private fun Hero(trigger: PaywallTrigger) {
    Box(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(GridironPalette.midnight, GridironPalette.midnight.copy(alpha = 0.88f)))),
    ) {
        BarBackdrop(
            listOf(94, 81, 67, 52, 38, 88, 73, 60, 45, 83, 70),
            Color.White.copy(alpha = 0.16f),
            barWidth = 13,
            spacing = 7,
            scale = 1.05f,
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 28.dp).padding(bottom = 12.dp),
        )
        Column(
            Modifier.fillMaxWidth().padding(top = 56.dp, bottom = 22.dp, start = 22.dp, end = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(70.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                SfIcon(trigger.icon, 32.dp, Color.White)
            }
            Text("STATSCOUT+", style = GridironType.micro, color = Color.White.copy(alpha = 0.65f))
            Text(trigger.title, style = GridironType.playerName, color = Color.White, textAlign = TextAlign.Center, maxLines = 2)
            Text(trigger.subtitle, style = GridironType.small, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center, maxLines = 3)
        }
    }
}

/** A faint row of percentile bars: the leaderboard's visual language behind a hero. */
@Composable
fun BarBackdrop(percentiles: List<Int>, color: (Int) -> Color, barWidth: Int, spacing: Int, scale: Float, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(spacing.dp), verticalAlignment = Alignment.Bottom) {
        percentiles.forEach { pct ->
            Box(Modifier.width(barWidth.dp).height((pct * scale).dp).clip(RoundedCornerShape(3.dp)).background(color(pct)))
        }
    }
}

@Composable
fun BarBackdrop(percentiles: List<Int>, color: Color, barWidth: Int, spacing: Int, scale: Float, modifier: Modifier = Modifier) =
    BarBackdrop(percentiles, { color }, barWidth, spacing, scale, modifier)

@Composable
private fun PlanCard(
    plan: Plan,
    isSelected: Boolean,
    showsTrial: Boolean,
    isMostPopular: Boolean,
    savingsPercent: Int?,
    perMonthLabel: String?,
    monthlyAnchorLabel: String?,
    onTap: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(GridironGeo.radiusCard)).background(GridironPalette.surface)
            .border(if (isSelected) 2.dp else 0.5.dp, if (isSelected) GridironPalette.turf else GridironPalette.hairline, RoundedCornerShape(GridironGeo.radiusCard))
            .clickable(onClick = onTap)
            .semantics { selected = isSelected }
            .testTag("plan_${plan.displayName}")
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).border(2.dp, if (isSelected) GridironPalette.turf else GridironPalette.hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (isSelected) Box(Modifier.size(12.dp).clip(CircleShape).background(GridironPalette.turf)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(plan.displayName, style = GridironType.bodyBold, color = GridironPalette.ink)
                savingsPercent?.let {
                    Text(
                        "SAVE $it%",
                        style = GridironType.micro,
                        color = Color.White,
                        modifier = Modifier.clip(CircleShape).background(GridironPalette.turf).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            if (isMostPopular) Text("Best value", style = GridironType.micro, color = GridironPalette.inkTertiary)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(plan.priceLabel, style = GridironType.cardTitle, color = GridironPalette.ink)
            if (perMonthLabel != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (monthlyAnchorLabel != null && savingsPercent != null) {
                        Text(monthlyAnchorLabel, style = GridironType.micro.copy(textDecoration = TextDecoration.LineThrough), color = GridironPalette.inkTertiary)
                    }
                    Text("$perMonthLabel/mo", style = GridironType.micro, color = GridironPalette.inkTertiary)
                }
            }
            if (showsTrial) plan.introOfferLabel?.let { Text(it.capitalizeWords(), style = GridironType.micro, color = GridironPalette.inkTertiary) }
        }
    }
}
