package com.jackwallner.football

import com.jackwallner.football.ui.screens.stableSeed
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The blurred board behind the Trends paywall has to redraw when the controls
 * move. Row one is the real league leader; everything under it is invented and
 * seeded. A seed that fails to reshuffle shows eighteen identical rows after a
 * window change, which advertises that they are fake. These pin both halves of
 * the fix: the seed's position in the string and the hash's avalanche.
 */
class TrendsTeaserTest {
    /** Stand-ins for the roster the teaser draws from; nflverse ids are close to sequential. */
    private val playerIds = (0 until 60).map { "00-00${34000 + it * 37}" }

    private fun teaserOrder(seed: String, count: Int = 18): List<String> =
        playerIds.map { it to stableSeed("$seed-$it") }.sortedBy { it.second }.take(count).map { it.first }

    /** The window is the last component of the seed and the one that reproduced the bug. */
    @Test
    fun changingTheWindowRedrawsTheBoardUnderTheBlur() {
        val orders = listOf("3", "5", "8").map { teaserOrder("epa_per_play-false-$it-2025-REG") }
        for ((a, b) in listOf(0 to 1, 1 to 2, 0 to 2)) {
            assertNotEquals("The window changed and the teaser did not", orders[a], orders[b])
            val shared = orders[a].take(5).toSet().intersect(orders[b].take(5).toSet())
            assertTrue("The top of the teaser barely moved between windows: $shared", shared.size <= 2)
        }
    }

    /** Every other control on the screen feeds the same seed. */
    @Test
    fun everyTrendsControlRedrawsTheBoard() {
        val baseline = teaserOrder("epa_per_play-false-3-2025-REG")
        val variants = listOf(
            "cpoe-false-3-2025-REG", // metric
            "epa_per_play-true-3-2025-REG", // cooling off
            "epa_per_play-false-3-2024-REG", // season
            "epa_per_play-false-3-2025-POST", // phase
        )
        for (variant in variants) assertNotEquals("$variant left the teaser unchanged", baseline, teaserOrder(variant))
    }

    /** A one-character change anywhere in the string has to move the hash, not nudge it. */
    @Test
    fun oneCharacterChangesTheWholeSeed() {
        val a = stableSeed("epa_per_play-false-3-2025-REG-00-0034796")
        val b = stableSeed("epa_per_play-false-5-2025-REG-00-0034796")
        assertNotEquals(a, b)
        assertTrue("Seeds $a and $b are adjacent, so sorting by them preserves the old order", abs(a - b) > 1_000)
    }

    @Test
    fun theSameSeedAlwaysProducesTheSameBoard() {
        val seed = "epa_per_play-false-3-2025-REG"
        assertEquals(teaserOrder(seed), teaserOrder(seed))
        assertEquals(stableSeed("00-0034796"), stableSeed("00-0034796"))
    }

    /** Used as an array index and a modulus, so it can never be negative. */
    @Test
    fun seedsAreAlwaysUsableAsAnIndex() {
        for (id in playerIds) {
            val seed = stableSeed("epa_per_play-false-3-2025-REG-$id")
            assertTrue(seed >= 0)
            assertTrue(seed < 100_003)
        }
    }

    /** Identical to iOS (FNV-1a plus a murmur3 finalizer), so the same controls draw the same teaser. Expected values computed from the Swift algorithm. */
    @Test
    fun seedMatchesTheIosHash() {
        assertEquals(35031, stableSeed("00-0034796"))
        assertEquals(44573, stableSeed("epa_per_play-false-3-2025-REG"))
        assertEquals(44524, stableSeed("epa_per_play-false-3-2025-REG-00-0034796"))
    }
}
