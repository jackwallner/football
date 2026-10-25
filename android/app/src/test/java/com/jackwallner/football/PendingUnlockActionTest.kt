package com.jackwallner.football

import com.jackwallner.football.data.PendingUnlockAction
import com.jackwallner.football.ui.screens.FormDisplayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingUnlockActionTest {
    @Test
    fun purchaseResumesTheChosenSeasonOnce() {
        val pending = PendingUnlockAction()
        val seasons = mutableListOf<Int>()
        pending.prepare { seasons.add(2023) }
        assertTrue(seasons.isEmpty())
        pending.finish(true)
        pending.finish(true)
        assertEquals(listOf(2023), seasons)
    }

    @Test
    fun restoreResumesTheChosenMode() {
        for (mode in listOf(FormDisplayMode.RECENT, FormDisplayMode.BOTH)) {
            val pending = PendingUnlockAction()
            var selection = FormDisplayMode.SEASON
            pending.prepare { selection = mode }
            pending.finish(true)
            assertEquals(mode, selection)
        }
    }

    @Test
    fun deferredApprovalResumesComparisonWhenOfferClosesWithAccess() {
        val pending = PendingUnlockAction()
        var showingPicker = false
        pending.prepare { showingPicker = true }
        // A pending purchase leaves the offer open and the action untouched.
        assertFalse(showingPicker)
        pending.finish(true)
        assertTrue(showingPicker)
    }

    @Test
    fun dismissalWithoutAccessClearsTheActionEvenIfApprovalArrivesLater() {
        val pending = PendingUnlockAction()
        var showingPicker = false
        pending.prepare { showingPicker = true }
        pending.finish(false)
        pending.finish(true)
        assertFalse(showingPicker)
    }

    @Test
    fun cancelledPurchaseCanRetryWithANewDestination() {
        val pending = PendingUnlockAction()
        val seasons = mutableListOf<Int>()
        pending.prepare { seasons.add(2023) }
        pending.finish(false)
        pending.prepare { seasons.add(2024) }
        pending.finish(true)
        assertEquals(listOf(2024), seasons)
    }

    @Test
    fun latestActionReplacesThePreviousDestination() {
        val pending = PendingUnlockAction()
        val seasons = mutableListOf<Int>()
        pending.prepare { seasons.add(2023) }
        pending.prepare { seasons.add(2024) }
        pending.finish(true)
        assertEquals(listOf(2024), seasons)
    }
}
