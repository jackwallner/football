package com.jackwallner.football

import com.jackwallner.football.data.DashboardViewModel
import com.jackwallner.football.data.DashboardViewModel.QualifierLevel
import com.jackwallner.football.data.InMemoryStore
import com.jackwallner.football.data.PlayerSnapshotValidator
import com.jackwallner.football.model.Metric
import com.jackwallner.football.model.MetricCategory
import com.jackwallner.football.model.NFLConference
import com.jackwallner.football.model.SeasonLabel
import com.jackwallner.football.model.SeasonPhase
import com.jackwallner.football.model.StatScoutSeason
import com.jackwallner.football.model.teamFullName
import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardViewModelTest {
    private val current = StatScoutSeason.current

    private fun historicalPlayers() = (StatScoutSeason.EARLIEST until current).flatMap { completeSeasonPlayers(it, "Historical") }

    private val expectedSeasons: List<Int>
        get() = listOf(StatScoutSeason.ALL_TIME) + (StatScoutSeason.EARLIEST..current).toList().reversed()

    @Test
    fun allMetricsKeyCollision() = runTest {
        val players = listOf(
            testPlayer(1, "A", "KC", "QB", current, type = "qb", metrics = listOf(metric("TD", "26", 90, MetricCategory.PASSING, id = "m1"))),
            testPlayer(2, "B", "PHI", "RB", current, type = "rb", metrics = listOf(metric("TD", "13", 85, MetricCategory.RUSHING, id = "m2"))),
        )
        val vm = dashboard(MockProvider(players))
        vm.load()
        assertEquals("Same label in different categories should produce 2 entries", 2, vm.allMetrics.size)
    }

    @Test
    fun loadDistinguishesErrors() = runTest {
        val format = dashboard(MockProvider(error = SerializationException("bad row")))
        format.load()
        assertTrue(format.errorMessage?.contains("format changed") == true)

        val offline = dashboard(MockProvider(error = IOException("offline")))
        offline.load()
        assertTrue(offline.errorMessage?.contains("connection") == true)
    }

    @Test
    fun lastUpdatedReturnsNullWhenEmpty() = runTest {
        assertNull(dashboard(MockProvider()).lastUpdated)
    }

    @Test
    fun teamFullNameReturnsCorrectFullName() {
        assertEquals("Kansas City Chiefs", teamFullName("KC"))
        assertEquals("San Francisco 49ers", teamFullName("SF"))
        assertEquals("Philadelphia Eagles", teamFullName("PHI"))
        assertEquals("Unknown", teamFullName("Unknown"))
    }

    @Test
    fun playersForTeamMatchesAliases() = runTest {
        val players = listOf(
            testPlayer(1, "A", "Kansas City Chiefs", "QB", 2025),
            testPlayer(2, "B", "OAK", "WR", 2025),
        )
        val vm = dashboard(MockProvider(players))
        vm.selectedSeason = 2025
        vm.load()

        assertEquals(listOf(1), vm.players("KC").map { it.playerId })
        assertEquals(listOf(2), vm.players("LV").map { it.playerId })
    }

    @Test
    fun conferenceFilterScopesPlayersTeamsAndMetrics() = runTest {
        val players = listOf(
            testPlayer(1, "AFC Player", "KC", "QB", 2025, type = "qb", metrics = listOf(metric("Pass Yds", "4,000", 90, id = "afc"))),
            testPlayer(2, "NFC Player", "PHI", "QB", 2025, type = "qb", metrics = listOf(metric("CPOE", "5.1", 80, id = "nfc"))),
        )
        val vm = dashboard(MockProvider(players))
        vm.selectedSeason = 2025
        vm.load()

        vm.selectedConference = NFLConference.AFC
        vm.searchText = "Kansas"
        assertEquals(listOf("AFC Player"), vm.filteredPlayers.map { it.name })
        assertEquals(listOf("KC"), vm.searchedTeams)
        assertEquals(listOf("Pass Yds"), vm.allMetrics.map { it.label })

        vm.selectedConference = NFLConference.NFC
        vm.searchText = "Philadelphia"
        assertEquals(listOf("NFC Player"), vm.filteredPlayers.map { it.name })
        assertEquals(listOf("PHI"), vm.searchedTeams)
        assertEquals(listOf("CPOE"), vm.allMetrics.map { it.label })
    }

    @Test
    fun teamCountsPopulatedAfterLoad() = runTest {
        val players = listOf(
            testPlayer(1, "A", "KC", "QB", 2025),
            testPlayer(2, "B", "KC", "RB", 2025),
            testPlayer(3, "C", "SF", "WR", 2025),
        )
        val vm = dashboard(MockProvider(players))
        vm.selectedSeason = 2025
        vm.load()
        assertEquals(2, vm.teamCounts["KC"])
        assertEquals(1, vm.teamCounts["SF"])
    }

    @Test
    fun partialRefreshPreservesCompleteCache() = runTest {
        val cached = completeCurrentPlayers()
        val cache = InMemoryPlayerCache(cached)
        val vm = dashboard(MockProvider(cached.take(5)), cache)

        vm.load()

        assertEquals(cached.size, vm.seasonPlayers.size)
        assertEquals(32, vm.teamsWithData.size)
        assertTrue(vm.lastFetchFailed)
        assertEquals(cached.size, cache.savedPlayers.size)
    }

    @Test
    fun completeRefreshReplacesCurrentCache() = runTest {
        val refreshed = completeCurrentPlayers("Fresh")
        val cache = InMemoryPlayerCache(completeCurrentPlayers())
        val vm = dashboard(MockProvider(refreshed), cache)

        vm.load()

        assertEquals(refreshed.size, vm.seasonPlayers.size)
        assertTrue(vm.seasonPlayers.all { it.name.startsWith("Fresh") })
        assertEquals(refreshed.size, cache.savedPlayers.size)
    }

    @Test
    fun cacheHydratesPlayersBeforeFetch() = runTest {
        val cached = listOf(testPlayer(99, "Cached", "KC", "QB", season = null))
        val vm = dashboard(MockProvider(error = IOException("offline")), InMemoryPlayerCache(cached))
        vm.load()
        assertEquals("Cached players should be shown even when refresh fails", listOf("99-0-REG"), vm.players.map { it.id })
    }

    @Test
    fun sortLabelReflectsCategory() = runTest {
        val passers = listOf(
            testPlayer(1, "A", "KC", "QB", current, type = "qb", metrics = listOf(metric("Pass Yds", "4,000", 90, id = "m1"))),
        )
        val vm = dashboard(MockProvider(passers))
        vm.load()
        // Default category is passing, should find Pass Yds in data.
        assertEquals("Pass Yds", vm.sortLabel)

        val rushers = listOf(
            testPlayer(2, "B", "PHI", "RB", current, type = "rb", metrics = listOf(metric("Rush Yds", "1,500", 85, MetricCategory.RUSHING, id = "m1"))),
        )
        val rushing = dashboard(MockProvider(rushers))
        rushing.load()
        rushing.selectedCategory = MetricCategory.RUSHING
        assertEquals("Rush Yds", rushing.sortLabel)

        // Empty data falls back to the default label.
        val empty = dashboard(MockProvider())
        empty.load()
        empty.selectedCategory = MetricCategory.PASSING
        assertEquals("Top Metric", empty.sortLabel)

        // A null category leaves the default position, so the passer's label shows.
        val unset = dashboard(MockProvider(passers))
        unset.load()
        unset.selectedCategory = null
        assertEquals("Pass Yds", unset.sortLabel)
    }

    @Test
    fun rushingSortUsesAvailableMetrics() = runTest {
        val back = testPlayer(
            1, "Test RB", "PHI", "RB", current, type = "rb",
            metrics = listOf(
                metric("Rush Yds", "1,500", 85, MetricCategory.RUSHING, id = "m1"),
                metric("Y/C", "5.2", 70, MetricCategory.RUSHING, id = "m2"),
            ),
        )
        val vm = dashboard(MockProvider(listOf(back)))
        vm.load()
        vm.selectedCategory = MetricCategory.RUSHING

        assertEquals(1, vm.filteredPlayers.size)
        assertEquals(1, vm.leaderboard.first().playerId)
    }

    @Test
    fun historicalArchiveRequiresEverySupportedSeason() {
        val complete = historicalPlayers()
        assertTrue(PlayerSnapshotValidator.isCompleteHistorical(complete))

        val missingEarliest = complete.filter { it.season != StatScoutSeason.EARLIEST }
        assertFalse(PlayerSnapshotValidator.isCompleteHistorical(missingEarliest))
    }

    @Test
    fun availableSeasonsIncludes2000ThroughCurrentPlusAllTime() = runTest {
        val vm = dashboard(MockProvider(historicalPlayers() + completeCurrentPlayers()))
        vm.applyProState(true)

        vm.load()

        assertEquals(expectedSeasons, vm.availableSeasons)
        assertEquals(StatScoutSeason.ALL_TIME, vm.availableSeasons.first())
    }

    @Test
    fun availableSeasonsIncludesLockedHistoryBeforeHistoryLoads() = runTest {
        val vm = dashboard(MockProvider(completeCurrentPlayers()))

        vm.load()

        assertEquals(expectedSeasons, vm.availableSeasons)
        assertTrue(vm.isSeasonLocked(current - 1))
    }

    /** All Time is Pro, like every season other than the current one. */
    @Test
    fun allTimeIsLockedForFreeUsers() = runTest {
        val vm = dashboard(MockProvider(completeCurrentPlayers()))
        vm.applyProState(false)
        assertTrue(vm.isSeasonLocked(StatScoutSeason.ALL_TIME))
        vm.applyProState(true)
        assertFalse(vm.isSeasonLocked(StatScoutSeason.ALL_TIME))
    }

    /** The free season is the calendar's season, even before its data lands. */
    @Test
    fun freeSeasonIsTheCalendarSeasonEvenBeforeItsDataLands() = runTest {
        val vm = dashboard(MockProvider(completeSeasonPlayers(current - 1, "LastYear")))

        vm.load()

        assertEquals(current, vm.freeSeason)
        assertEquals(current, vm.selectedSeason)
        assertTrue(vm.isSeasonLocked(current - 1))
        assertTrue(vm.availableSeasons.contains(current))
    }

    /** Week 1: two teams have played. The live season is still the default, for Pro too. */
    @Test
    fun aThinLiveSeasonIsStillTheDefault() = runTest {
        val lastSeason = completeSeasonPlayers(current - 1, "LastYear")
        val opener = completeCurrentPlayers("Opener").filter { it.team in listOf("NE", "SEA") }
        val vm = dashboard(MockProvider(lastSeason + opener))
        vm.applyProState(true)

        vm.load()

        assertEquals(current, vm.selectedSeason)
        assertFalse(vm.isSeasonLocked(current))
        assertTrue(vm.players.any { it.season == current })
    }

    /** All Players (the default) keeps small samples dimmed below qualified ones; Qualified hides them. */
    @Test
    fun qualifiedFilterHonoursTheLiveSeasonFlag() = runTest {
        val store = InMemoryStore()
        val starter = testPlayer(
            1, "Starter", "NE", "QB", current, type = "qb",
            metrics = listOf(metric("EPA/Play", "0.10", 60, qualified = true, id = "s")),
        )
        val backup = testPlayer(
            2, "Backup", "SEA", "QB", current, type = "qb",
            metrics = listOf(metric("EPA/Play", "0.90", 99, qualified = false, id = "b")),
        )
        val vm = dashboard(MockProvider(listOf(starter, backup)), store = store)
        vm.load()

        assertEquals(QualifierLevel.ALL, vm.qualifierLevel)
        // The backup's 99th percentile outranks the starter, but a small sample never tops a board.
        assertEquals(listOf("Starter", "Backup"), vm.leaderboard.map { it.name })
        vm.qualifierLevel = QualifierLevel.QUALIFIED
        assertEquals(listOf("Starter"), vm.leaderboard.map { it.name })
        assertEquals("the choice persists", QualifierLevel.QUALIFIED, dashboard(MockProvider(), store = store).qualifierLevel)
    }

    @Test
    fun metricDecodesWithAndWithoutTheQualifiedFlag() {
        val json = """
            [{"id":"a","label":"EPA/Play","value":"0.1","percentile":50,"category":"Passing","qualified":false},
             {"id":"b","label":"EPA/Play","value":"0.2","percentile":60,"category":"Passing"}]
        """.trimIndent()
        val metrics = (Json.parseToJsonElement(json) as JsonArray).map { Metric.fromJson(it as JsonObject) }
        assertEquals(listOf(false, null), metrics.map { it.qualified })
    }

    /** Recent form covers the live season and the one before it, as rules rather than literal years. */
    @Test
    fun recentFormCoversTheLiveSeasonAndTheOneBefore() = runTest {
        val vm = dashboard(MockProvider(completeCurrentPlayers()))

        vm.load()

        assertEquals(current, vm.recentFormSeason)
        assertEquals("Newest first", vm.freeSeason, vm.recentFormSeasons.first())
        assertTrue(vm.recentFormSeasons.size <= 2)
        assertEquals(vm.recentFormSeasons.sortedDescending(), vm.recentFormSeasons)
        assertTrue(vm.supportsRecentForm(vm.freeSeason))
        vm.recentFormSeasons.forEach { assertTrue(vm.supportsRecentForm(it)) }
        assertFalse(vm.supportsRecentForm(StatScoutSeason.ALL_TIME))
    }

    /** Never offer a season the rollup table no longer holds. */
    @Test
    fun recentFormNeverOffersAPurgedSeason() = runTest {
        val vm = dashboard(MockProvider(completeCurrentPlayers()))

        vm.load()

        vm.recentFormSeasons.forEach { assertTrue(it >= StatScoutSeason.EARLIEST_RECENT_FORM) }
        assertFalse(vm.supportsRecentForm(StatScoutSeason.EARLIEST_RECENT_FORM - 1))
    }

    /** The floor is a fact about the database: moving it down without re-ingesting puts an empty year in Trends. */
    @Test
    fun recentFormFloorMatchesTheSeasonsTheRollupStillHolds() {
        assertEquals(2025, StatScoutSeason.EARLIEST_RECENT_FORM)
        assertTrue(
            "The floor can never sit above the live season, or Recent Form vanishes entirely",
            StatScoutSeason.EARLIEST_RECENT_FORM <= current,
        )
    }

    /** Once the new season's rows land, it is the free season and last season is Pro. */
    @Test
    fun theAppMovesToTheNewSeasonTheDayItsDataLands() = runTest {
        val lastSeason = current - 1
        val postKickoff = dashboard(MockProvider(completeSeasonPlayers(lastSeason, "LastYear") + completeCurrentPlayers()))
        postKickoff.load()

        assertEquals("and moves on by itself once rows land", current, postKickoff.freeSeason)
        assertFalse(postKickoff.isSeasonLocked(current))
        assertTrue("last season becomes Pro, not gone", postKickoff.isSeasonLocked(lastSeason))
        assertTrue(postKickoff.availableSeasons.contains(lastSeason))

        assertEquals(current, postKickoff.recentFormSeasons.first())
        assertEquals(listOf(current, lastSeason).filter { it >= StatScoutSeason.EARLIEST_RECENT_FORM }, postKickoff.recentFormSeasons)
    }

    @Test
    fun freeSeasonIsTheCurrentSeasonOnceItHasData() = runTest {
        val vm = dashboard(MockProvider(completeCurrentPlayers()))

        vm.load()

        assertEquals(current, vm.freeSeason)
        assertFalse(vm.isSeasonLocked(current))
    }

    /** History is loaded on demand, so the first tap on a past season has to start that load. */
    @Test
    fun selectingAPastSeasonLoadsTheHistoryItNeeds() = runTest {
        val vm = dashboard(MockProvider(completeCurrentPlayers()))
        vm.applyProState(true)
        vm.load()
        assertFalse("History should still be unfetched after a plain load", vm.hasLoadedHistorical)

        vm.selectSeason(StatScoutSeason.ALL_TIME)

        // The header moves on the tap; the rows arrive behind it.
        assertEquals(StatScoutSeason.ALL_TIME, vm.selectedSeason)
        assertNotNull("Choosing a past season should start the history load", vm.seasonLoadJob)
        vm.seasonLoadJob?.join()
    }

    /** The live season needs no extra fetch, so it must not start one. */
    @Test
    fun selectingTheFreeSeasonDoesNotRefetchHistory() = runTest {
        val vm = dashboard(MockProvider(completeCurrentPlayers()))
        vm.load()

        vm.selectSeason(vm.freeSeason)

        assertNull(vm.seasonLoadJob)
    }

    /** Trends ranks rolling week windows, so a career has nothing to rank. */
    @Test
    fun trendsSeasonListExcludesAllTime() = runTest {
        val vm = dashboard(MockProvider(completeCurrentPlayers()))

        vm.load()

        assertFalse(vm.seasonsExcludingAllTime.contains(StatScoutSeason.ALL_TIME))
        assertEquals(vm.availableSeasons.size - 1, vm.seasonsExcludingAllTime.size)
    }

    /** The sentinel must never reach the UI as "0". */
    @Test
    fun seasonLabelRendersSentinelAsAllSinceEarliest() {
        assertEquals("All since 2000", SeasonLabel.text(StatScoutSeason.ALL_TIME))
        assertEquals("2024", SeasonLabel.text(2024))
        assertEquals("All since 2000 · Regular Season", SeasonLabel.text(StatScoutSeason.ALL_TIME, SeasonPhase.REGULAR))
        assertEquals("2024 Playoffs", SeasonLabel.text(2024, SeasonPhase.PLAYOFFS))
    }

    @Test
    fun seasonPhaseAlwaysReadsAsAFullName() {
        assertEquals("Regular Season", SeasonPhase.REGULAR.label)
        assertEquals("Playoffs", SeasonPhase.PLAYOFFS.label)
    }

    @Test
    fun seasonPlayersReturnsPlayersForSelectedSeason() = runTest {
        val vm = dashboard(MockProvider(listOf(testPlayer(1, "Player 2025", "NYY", "RF", 2025), testPlayer(2, "Player 2024", "BOS", "1B", 2024))))
        vm.load()

        vm.selectedSeason = 2025
        assertEquals(1, vm.seasonPlayers.size)
        assertEquals(1, vm.seasonPlayers.first().playerId)

        vm.selectedSeason = 2024
        assertEquals(1, vm.seasonPlayers.size)
        assertEquals(2, vm.seasonPlayers.first().playerId)
    }

    @Test
    fun seasonPlayersIsEmptyWhenSeasonHasNoData() = runTest {
        val vm = dashboard(MockProvider(listOf(testPlayer(1, "Player 2025", "NYY", "RF", 2025))))
        vm.load()

        // 2024 has no data, so it reports empty with no stale fallback.
        vm.selectedSeason = 2024
        assertTrue(vm.seasonPlayers.isEmpty())
    }

    @Test
    fun loadNeverSnapsAwayFromTheLiveSeason() = runTest {
        val vm = dashboard(MockProvider(listOf(testPlayer(1, "Last Season", "KC", "QB", current - 1))))
        vm.applyProState(true)
        vm.load()
        assertEquals(current, vm.selectedSeason)
    }
}
