package com.jackwallner.football

import com.jackwallner.football.data.PURCHASE_FAILED
import com.jackwallner.football.data.PurchaseException
import com.jackwallner.football.data.PurchaseOutcome
import com.jackwallner.football.data.directFailureMessage
import com.jackwallner.football.data.purchaseOutcome
import com.revenuecat.purchases.PurchasesErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Overnight audit 2026-10-30 (F4): a cancelled sheet and a pending payment are outcomes, not failures. */
class PurchaseErrorMappingTest {
    @Test
    fun cancelAndPendingAreNotFailures() {
        assertEquals(PurchaseOutcome.CANCELLED, purchaseOutcome(PurchasesErrorCode.PurchaseCancelledError, false))
        assertEquals(PurchaseOutcome.CANCELLED, purchaseOutcome(PurchasesErrorCode.UnknownError, true))
        assertEquals(PurchaseOutcome.PENDING, purchaseOutcome(PurchasesErrorCode.PaymentPendingError, false))
    }

    @Test
    fun realFailuresStillThrow() {
        assertNull(purchaseOutcome(PurchasesErrorCode.NetworkError, false))
        assertNull(purchaseOutcome(PurchasesErrorCode.StoreProblemError, false))
    }

    @Test
    fun aFailedBuyShowsItsOwnMessage() {
        assertEquals("Play is down", directFailureMessage(PurchaseException("Play is down")))
        assertEquals(PURCHASE_FAILED, directFailureMessage(IllegalStateException("boom")))
    }
}
