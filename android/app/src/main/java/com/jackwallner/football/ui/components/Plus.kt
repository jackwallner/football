package com.jackwallner.football.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jackwallner.football.data.DirectPurchaseOutcome
import com.jackwallner.football.data.PaywallTrigger
import com.jackwallner.football.data.PriceEmphasis
import com.jackwallner.football.ui.LocalAppActions
import com.jackwallner.football.ui.LocalGraph
import com.jackwallner.football.ui.findActivity
import com.jackwallner.football.ui.nav.LocalNavigator
import com.jackwallner.football.ui.nav.Route
import com.jackwallner.football.ui.theme.GridironGeo
import com.jackwallner.football.ui.theme.GridironPalette
import com.jackwallner.football.ui.theme.GridironType
import kotlinx.coroutines.launch

enum class PlusCTAStyle { CAPSULE, BAR }

/**
 * The app's in-place conversion control: tap it and Google Play's purchase
 * sheet is next. The plan picker stays behind a quiet "See all plans" link.
 */
@Composable
fun PlusDirectCTA(
    trigger: PaywallTrigger,
    style: PlusCTAStyle = PlusCTAStyle.BAR,
    showsAllPlansLink: Boolean = true,
    emphasis: PriceEmphasis = PriceEmphasis.BILLED_AMOUNT_FIRST,
) {
    val graph = LocalGraph.current
    val store = graph.subscriptions
    val actions = LocalAppActions.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isPurchasing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(trigger) {
        store.trackPaywallImpression(trigger.paywallImpressionId, oncePerSession = true)
        if (store.offeringId == null) store.fetchProducts()
    }

    val label = store.directCTALabel(trigger, emphasis)
    val subline = store.directCTATrialSubline(trigger, emphasis)
    fun buy() {
        val activity = context.findActivity() ?: return
        status = null
        isPurchasing = true
        scope.launch {
            when (val outcome = store.purchaseYearlyDirect(activity)) {
                DirectPurchaseOutcome.Unlocked -> Unit
                DirectPurchaseOutcome.Pending -> status = "Purchase pending approval. StatScout+ unlocks automatically once it's approved."
                DirectPurchaseOutcome.Cancelled -> status = "Purchase cancelled. Tap again to continue."
                is DirectPurchaseOutcome.Failed -> status = outcome.message
                DirectPurchaseOutcome.NeedsPlanPicker -> actions.openPaywall(trigger)
            }
            isPurchasing = false
        }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val a11y = listOfNotNull(label, subline).joinToString(". ")
        when (style) {
            PlusCTAStyle.CAPSULE -> Row(
                Modifier.heightIn(min = 44.dp).clip(CircleShape).background(GridironPalette.turf)
                    .clickable(enabled = !isPurchasing) { buy() }.semantics { contentDescription = a11y }.testTag("plusCTA")
                    .padding(horizontal = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isPurchasing) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                else {
                    SfIcon("crown.fill", 13.dp, Color.White)
                    Text(label, style = GridironType.bodyBold, color = Color.White)
                }
            }
            PlusCTAStyle.BAR -> Box(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(12.dp)).background(GridironPalette.turf)
                    .clickable(enabled = !isPurchasing) { buy() }.semantics { contentDescription = a11y }.testTag("plusCTA")
                    .padding(vertical = if (subline == null) 0.dp else 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isPurchasing) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(label, style = GridironType.bodyBold, color = Color.White, textAlign = TextAlign.Center)
                    subline?.let { Text(it, style = GridironType.micro, color = Color.White.copy(alpha = 0.85f)) }
                }
            }
        }
        val disclosure = store.yearlyPlan?.let { store.disclosureText(it, emphasis) } ?: store.paywallBlurSubtext
        disclosure?.let {
            Text(it, style = GridironType.micro.copy(letterSpacing = 0.3.sp), color = GridironPalette.inkTertiary, textAlign = TextAlign.Center)
        }
        status?.let { Text(it, style = GridironType.micro, color = GridironPalette.turf, textAlign = TextAlign.Center) }
        if (showsAllPlansLink) {
            TextAction("See all plans", GridironType.micro.copy(letterSpacing = 0.3.sp), GridironPalette.inkSecondary, { actions.openPaywall(trigger) })
        }
    }
}

/** The unlock affordance over a blurred teaser: fades the preview into the card, then the CTA. */
@Composable
fun BlurGateUnlock(headline: String, trigger: PaywallTrigger, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, GridironPalette.surface.copy(alpha = 0.95f), GridironPalette.surface)))
            .padding(start = 20.dp, end = 20.dp, top = 52.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(headline, style = GridironType.smallBold, color = GridironPalette.ink, textAlign = TextAlign.Center)
        PlusDirectCTA(trigger, PlusCTAStyle.CAPSULE)
    }
}

/** "What do these stats mean?": the glossary, from the bottom of any stat-heavy page. */
@Composable
fun StatGlossaryLink() {
    val navigator = LocalNavigator.current
    Row(
        Modifier.fillMaxWidth().gridironCard().clickable { navigator.push(Route.Glossary) }
            .semantics { contentDescription = "Stat glossary. Definitions for every stat in StatScout" }
            .padding(GridironGeo.padCard),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SfIcon("info.circle", 16.dp, GridironPalette.turf)
        Text("What do these stats mean?", style = GridironType.small, color = GridironPalette.turf, modifier = Modifier.weight(1f))
        SfIcon("chevron.right", 16.dp, GridironPalette.inkTertiary)
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = GridironPalette.divider) {
    Spacer(modifier.fillMaxWidth().height(GridironGeo.hairline).background(color))
}
