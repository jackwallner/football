package com.jackwallner.football

import com.jackwallner.football.data.PlayerSnapshotValidator
import com.jackwallner.football.data.TwoTierPlayerCache
import com.jackwallner.football.model.MetricCategory
import com.jackwallner.football.model.Player
import com.jackwallner.football.model.StatScoutSeason
import java.io.File
import java.nio.file.Files
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpeningWeekCacheTest {
    private val directory: File = Files.createTempDirectory("opening-week").toFile()

    @After
    fun cleanUp() {
        directory.deleteRecursively()
    }

    private fun cache() = TwoTierPlayerCache(directory) { null }

    private fun players(oneTeam: Boolean = false, season: Int = StatScoutSeason.current): List<Player> = (0 until 30).map { index ->
        testPlayer(
            id = index, name = "Player $index", team = if (oneTeam || index < 15) "SEA" else "NE",
            season = season, type = listOf("qb", "rb", "wr", "te", "def")[index % 5],
            updatedAt = Instant.EPOCH,
            metrics = listOf(
                metric("EPA/Play", "0.1", 50, com.jackwallner.football.model.MetricCategory.PASSING, id = "pass"),
                metric("EPA/Rush", "0.1", 50, com.jackwallner.football.model.MetricCategory.RUSHING, id = "rush"),
                metric("EPA/Tgt", "0.1", 50, com.jackwallner.football.model.MetricCategory.RECEIVING, id = "rec"),
            ),
        )
    }

    @Test
    fun completeOpeningGameIsCacheableBeforeFullSlate() {
        assertTrue(PlayerSnapshotValidator.isCompleteCurrent(players()))
    }

    @Test
    fun onlyOneTeamIsNotACompleteOpeningGame() {
        assertFalse(PlayerSnapshotValidator.isCompleteCurrent(players(oneTeam = true)))
    }

    @Test
    fun openingGameStillRequiresEveryPositionGroup() {
        assertFalse(PlayerSnapshotValidator.isCompleteCurrent(players().filter { it.playerType != "def" }))
    }

    @Test
    fun lastYearsOpeningGameIsNotCurrentData() {
        assertFalse(PlayerSnapshotValidator.isCompleteCurrent(players(season = StatScoutSeason.current - 1)))
    }

    @Test
    fun expiredSavedSnapshotIsKeptAndNotRestamped() {
        val cache = cache()
        cache.saveCurrentPlayers(players())
        val file = File(directory, "players-current.json")
        val weekAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        assertTrue(file.setLastModified(weekAgo))

        assertEquals(30, cache.loadCurrentPlayers().size)
        assertEquals(weekAgo.toDouble(), file.lastModified().toDouble(), 1000.0)
    }

    @Test
    fun noSavedSnapshotServesNoCurrentPlayers() {
        assertTrue(cache().loadCurrentPlayers().isEmpty())
    }
}
