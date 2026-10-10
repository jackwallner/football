package com.jackwallner.football

import com.jackwallner.football.data.PriceCopy
import com.jackwallner.football.data.PriceEmphasis
import com.jackwallner.football.data.StoreLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the copy App Review rejected iOS 1.0 (19) over, Guideline 3.1.2(c): the
 * free trial was promoted more conspicuously than the billed amount. The same
 * ordering holds on Android: the price leads, the trial is demoted to a
 * subordinate line, and the old trial-first ordering is never the live default.
 *
 * Deliberate platform difference: the renewal sentence names Google Play and has
 * no "24 hours" clause (Play has no such rule), so these tests pin the Play copy.
 */
class PriceDisclosureTest {
    private val renew = "Auto-renews unless cancelled before the end of the current period. " +
        "Manage or cancel in Google Play › Payments & subscriptions."
    private val yearly = "$9.99 / year"
    private val trial = "7-day free trial"

    // Every purchase surface: billed amount first

    @Test
    fun renewalCopyIsThePlaySentence() {
        assertEquals(renew, PriceCopy.RENEW)
    }

    @Test
    fun citedSheetCTANamesTheBilledAmountAndNotTheTrial() {
        val label = PriceCopy.directCTALabel(yearly, trial, false, PriceEmphasis.BILLED_AMOUNT_FIRST)
        assertEquals("Subscribe for $yearly", label)
        assertFalse("The cited CTA must not promote the trial: $label", label.lowercase().contains("trial"))
        assertFalse("The cited CTA must not promote the trial: $label", label.lowercase().contains("free"))
    }

    /** The trial is still pitched on the cited sheet, one rank down. */
    @Test
    fun citedSheetStillSellsTheTrialOnASubordinateLine() {
        assertEquals("Starts with a 7-day free trial", PriceCopy.directCTATrialSubline(trial, false, PriceEmphasis.BILLED_AMOUNT_FIRST))
    }

    /** The subordinate line belongs to the price-first emphasis alone. */
    @Test
    fun trialSublineOnlyExistsWhereThePriceLeads() {
        assertNull(PriceCopy.directCTATrialSubline(trial, false, PriceEmphasis.TRIAL_FIRST))
        assertNull("Nothing to name when there is no trial", PriceCopy.directCTATrialSubline(null, false, PriceEmphasis.BILLED_AMOUNT_FIRST))
        assertNull("A win-back has already used the trial", PriceCopy.directCTATrialSubline(trial, true, PriceEmphasis.BILLED_AMOUNT_FIRST))
    }

    /** The price is on the primary line, the trial only on the secondary one. */
    @Test
    fun theCitedButtonRanksThePriceAboveTheTrial() {
        val label = PriceCopy.directCTALabel(yearly, trial, false, PriceEmphasis.BILLED_AMOUNT_FIRST)
        val subline = PriceCopy.directCTATrialSubline(trial, false, PriceEmphasis.BILLED_AMOUNT_FIRST)
        assertTrue(label.contains(yearly))
        assertFalse("The price belongs on the primary line only", subline?.contains("$9.99") ?: false)
        assertTrue(subline?.lowercase()?.contains("free trial") ?: false)
    }

    @Test
    fun citedSheetDisclosureLeadsWithTheBilledAmount() {
        val text = PriceCopy.disclosureText(yearly, true, trial, PriceEmphasis.BILLED_AMOUNT_FIRST)
        assertEquals("$yearly, billed after a $trial. $renew", text)
        assertTrue("The trial must be subordinate in position", text.indexOf(yearly) < text.indexOf(trial))
    }

    /** No branch of the price-first emphasis may omit the price or advertise a trial. */
    @Test
    fun billedAmountFirstNeverPromotesTheTrial() {
        for (price in listOf(yearly, "$1.99 / month")) {
            for (trialText in listOf(null, trial)) {
                for (isWinback in listOf(false, true)) {
                    val label = PriceCopy.directCTALabel(price, trialText, isWinback, PriceEmphasis.BILLED_AMOUNT_FIRST)
                    assertTrue("\"$label\" omits the billed amount", label.contains(price))
                    assertFalse(
                        "\"$label\" promotes the trial in the most prominent element on screen",
                        label.lowercase().contains("free") || label.lowercase().contains("trial"),
                    )
                }
            }
        }
    }

    // The plan picker's own button

    /** The plan picker states the action, not the offer, so there is no ordering to get wrong. */
    @Test
    fun planPickerCTANamesTheActionNotTheOffer() {
        assertEquals("Start Free Trial", PriceCopy.planPickerCTALabel(isLifetime = false, trialEligible = true))
        assertEquals("Subscribe", PriceCopy.planPickerCTALabel(isLifetime = false, trialEligible = false))
        assertEquals("Unlock Lifetime", PriceCopy.planPickerCTALabel(isLifetime = true, trialEligible = false))
    }

    /** A non-consumable has no introductory offer, so eligibility never leaks a trial onto the lifetime button. */
    @Test
    fun lifetimePlanNeverOffersATrial() {
        assertEquals("Unlock Lifetime", PriceCopy.planPickerCTALabel(isLifetime = true, trialEligible = true))
    }

    @Test
    fun planPickerCTANeverCarriesAPrice() {
        for (isLifetime in listOf(true, false)) {
            for (trialEligible in listOf(true, false)) {
                val label = PriceCopy.planPickerCTALabel(isLifetime, trialEligible)
                assertFalse("\"$label\" states an amount the plan card already ranks above it", label.any { it.isDigit() } || label.contains("$"))
            }
        }
    }

    // Trial-first formatting is never the live default

    @Test
    fun defaultCopyLeadsWithTheBilledAmountEverywhere() {
        assertEquals("Subscribe for $yearly", PriceCopy.directCTALabel(yearly, trial, false))
        assertEquals("Subscribe for $yearly", PriceCopy.directCTALabel(yearly, null, false))
        assertEquals("$yearly, billed after a $trial. $renew", PriceCopy.disclosureText(yearly, true, trial))
    }

    @Test
    fun trialFirstFormattingRequiresAnExplicitOptIn() {
        assertEquals("Start $trial", PriceCopy.directCTALabel(yearly, trial, false, PriceEmphasis.TRIAL_FIRST))
    }

    /** A lapsed subscriber has already used the trial, so a win-back never offers it again. */
    @Test
    fun winbackNeverOffersTheTrial() {
        for (emphasis in PriceEmphasis.entries) {
            val label = PriceCopy.directCTALabel(yearly, trial, true, emphasis)
            assertTrue(label, label.contains(yearly))
            assertFalse(label, label.lowercase().contains("free trial"))
        }
    }

    // True of both orderings

    @Test
    fun subscriptionWithoutATrialAlwaysLeadsWithThePrice() {
        for (emphasis in PriceEmphasis.entries) {
            val text = PriceCopy.disclosureText("$1.99 / month", true, null, emphasis)
            assertTrue(text, text.startsWith("$1.99 / month."))
            assertTrue(text, text.contains(renew))
        }
    }

    /** A non-consumable must not claim to auto-renew, either way. */
    @Test
    fun lifetimeIsNeverDescribedAsASubscription() {
        for (emphasis in PriceEmphasis.entries) {
            val text = PriceCopy.disclosureText("$19.99", false, null, emphasis)
            assertEquals("$19.99. One-time purchase. Lifetime access, no subscription.", text)
            assertFalse(text, text.contains("Auto-renews"))
        }
    }

    @Test
    fun everyDisclosureStatesThePriceAndTheTerms() {
        data class Case(val price: String, val isSubscription: Boolean, val trial: String?)
        val cases = listOf(
            Case(yearly, true, trial),
            Case(yearly, true, null),
            Case("$1.99 / month", true, "3-day free trial"),
            Case("$19.99", false, null),
        )
        for (c in cases) {
            for (emphasis in PriceEmphasis.entries) {
                val text = PriceCopy.disclosureText(c.price, c.isSubscription, c.trial, emphasis)
                assertTrue("\"$text\" omits the price", text.contains(c.price))
                if (c.isSubscription) assertTrue("\"$text\" omits the renewal and cancel terms", text.contains(renew))
                c.trial?.let { assertTrue("\"$text\" omits the trial", text.lowercase().contains(it.lowercase())) }
            }
        }
    }

    // Legal links (Android points at its own hosted pages, not Apple's EULA)

    @Test
    fun legalLinksResolve() {
        assertEquals("https://jackwallner.github.io/football/android-terms.html", StoreLinks.TERMS_URL)
        assertTrue(StoreLinks.PRIVACY_URL.startsWith("https://"))
        assertTrue(StoreLinks.PRIVACY_URL.endsWith("android-privacy.html"))
    }
}
