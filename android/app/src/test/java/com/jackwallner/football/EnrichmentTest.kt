package com.jackwallner.football

import com.jackwallner.football.data.DashboardViewModel.QualifierLevel
import com.jackwallner.football.model.ContractValue
import com.jackwallner.football.model.Game
import com.jackwallner.football.model.GameProjection
import com.jackwallner.football.model.InjuryReport
import com.jackwallner.football.model.Metric
import com.jackwallner.football.model.MetricCategory
import com.jackwallner.football.model.PlayerPositionGroup
import com.jackwallner.football.model.PlayerProfile
import com.jackwallner.football.model.StandardStat
import com.jackwallner.football.model.StandingsRow
import com.jackwallner.football.model.StatScoutSeason
import com.jackwallner.football.model.TeamRating
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnrichmentTest {
    private val current = StatScoutSeason.current

    private fun player(id: Int, type: String = "wr", team: String = "SEA", metrics: List<Metric>, stats: List<StandardStat> = emptyList()) =
        testPlayer(id, "P$id", team, type.uppercase(), current, type = type, metrics = metrics, standard = stats)

    private fun m(label: String, value: String, pct: Int, category: MetricCategory, qualified: Boolean? = null, rankable: Boolean? = null) =
        metric(label, value, pct, category, qualified, rankable, id = "$label-$pct-$value")

    private fun array(json: String) = (Json.parseToJsonElement(json) as JsonArray).map { it as JsonObject }

    // Unranked zero counts

    @Test
    fun zeroCountingStatIsUnrankedButRatesAndNonZeroCountsAreNot() {
        assertTrue(m("INT", "0", 47, MetricCategory.DEFENSE).isUnranked)
        assertTrue(m("Sacks", "0.0", 41, MetricCategory.DEFENSE).isUnranked)
        assertFalse(m("Sacks", "1.5", 80, MetricCategory.DEFENSE).isUnranked)
        assertFalse("a rate at zero is a real rank", m("EPA/Rush", "0.00", 50, MetricCategory.RUSHING).isUnranked)
        assertFalse("advanced totals keep their rank", m("Rush EPA", "0.0", 50, MetricCategory.RUSHING).isUnranked)
        assertTrue(m("DEF INT", "0", 47, MetricCategory.DEFENSE, rankable = false).isUnranked)
    }

    @Test
    fun overallPercentileLeavesUnrankedZerosOut() {
        val defender = player(
            1, type = "def",
            metrics = listOf(
                m("Tackles", "4", 20, MetricCategory.DEFENSE),
                m("INT", "0", 47, MetricCategory.DEFENSE),
                m("Sacks", "0.0", 41, MetricCategory.DEFENSE),
            ),
        )
        assertEquals(20, defender.overallPercentile)
        assertEquals("Tackles", defender.headlineMetric?.label)
    }

    // Contract value

    @Test
    fun contractValueIsProductionMinusPayWithinThePosition() {
        // Five receivers: pay ascending by id, production descending.
        val players = (1..5).map { id -> player(id, metrics = listOf(m("EPA/Tgt", "0.$id", 100 - id * 15, MetricCategory.RECEIVING, qualified = true))) }
        val profiles = (1..5).associateWith { id ->
            PlayerProfile(playerId = id, season = current, contractCapShare = id / 100.0, contractAPY = id * 5.0)
        }
        val values = ContractValue.compute(players, profiles) { true }
        assertEquals(5, values.size)
        assertEquals(10, values[1]?.payPercentile)
        assertEquals(90, values[1]?.productionPercentile)
        assertEquals(80, values[1]?.score)
        assertEquals(ContractValue.Verdict.BARGAIN, values[1]?.verdict)
        assertEquals(ContractValue.Verdict.OVERPAID, values[5]?.verdict)
        assertEquals(ContractValue.Verdict.FAIR, values[3]?.verdict)
        assertEquals(5, values[1]?.poolSize)
    }

    @Test
    fun contractValueNeedsFiveQualifiedPlayersAndSkipsDefense() {
        val players = (1..4).map { id -> player(id, metrics = listOf(m("EPA/Tgt", "0.1", 50, MetricCategory.RECEIVING, qualified = true))) } +
            (5..10).map { id -> player(id, type = "def", metrics = listOf(m("Tackles", "$id", id * 9, MetricCategory.DEFENSE))) }
        val profiles = (1..10).associateWith { id -> PlayerProfile(playerId = id, season = current, contractCapShare = 0.01 * id) }
        assertTrue(ContractValue.compute(players, profiles) { true }.isEmpty())
    }

    @Test
    fun productionIgnoresSmallSamplesAndUnrankedZeros() {
        val receiver = player(
            1,
            metrics = listOf(
                m("EPA/Tgt", "0.5", 90, MetricCategory.RECEIVING, qualified = true),
                m("Separation", "3.1", 20, MetricCategory.RECEIVING, qualified = false),
                m("Rec TD", "0", 30, MetricCategory.RECEIVING, qualified = true),
                m("Rush Yds", "40", 99, MetricCategory.RUSHING, qualified = true),
            ),
        )
        assertEquals(90.0, ContractValue.production(receiver)!!, 0.0)
    }

    // Standings

    @Test
    fun standingsCountRecordsDifferentialAndStreak() {
        fun final(id: String, week: Int, away: String, home: String, a: Int, h: Int) = Game(
            id = id, season = 2026, week = week, kickoff = Instant.ofEpochSecond(1_789_000_000L + week * 604_800L),
            awayTeam = away, homeTeam = home, awayScore = a, homeScore = h,
        )
        val games = listOf(
            final("1", 1, "SEA", "SF", 20, 17),
            final("2", 2, "LA", "SEA", 10, 10),
            final("3", 3, "SEA", "ARI", 7, 21),
            Game(id = "4", season = 2026, week = 4, kickoff = null, awayTeam = "SF", homeTeam = "SEA"),
        )
        val table = StandingsRow.build(games, listOf("SEA", "SF", "LA", "ARI"))
        val seattle = table.getValue("SEA")
        assertEquals("1-1-1", seattle.record)
        assertEquals(37 - 48, seattle.differential)
        assertEquals("L1", seattle.streak)
        assertEquals("1-0", table.getValue("ARI").record)
        assertEquals("W1", table.getValue("ARI").streak)
        assertEquals(listOf("ARI", "LA", "SEA", "SF"), StandingsRow.ordered(table.values.toList()).map { it.team })
    }

    // Profiles, injuries, projections

    @Test
    fun profileDecodesTheFeedRow() {
        val json = """
            [{"player_id":38543,"season":2026,"jersey":11,"birth_date":"2002-02-14","height_in":73,"weight_lb":196,
              "college":"Ohio State","years_exp":3,"draft_year":2023,"draft_round":1,"draft_pick":20,"draft_team":"SEA",
              "contract_apy":42.15,"contract_cap_pct":0.14,"contract_years":4,"contract_year_signed":2026,
              "off_snaps":150,"def_snaps":0,"off_snap_pct":0.91,"def_snap_pct":null,
              "injury_week":3,"injury_status":"Questionable","injury":"Ankle","practice_status":"Limited",
              "updated_at":"2026-09-26T18:54:32.1+00:00","unknown_column":1}]
        """.trimIndent()
        val profile = PlayerProfile.fromJson(array(json).first())
        assertEquals("6-1, 196", profile.sizeLabel)
        assertEquals("2023 R1 #20", profile.draftLabel)
        assertEquals("$42.1M/yr", profile.contractLabel)
        assertEquals(24, profile.age(LocalDate.of(2026, 9, 26)))
        assertNull(profile.defenseSnapShare)
    }

    @Test
    fun injuryBadgeOnlyForAReportAboutAnUnplayedWeek() {
        var profile = PlayerProfile(playerId = 1, season = 2026, injuryWeek = 3, injuryStatus = "Out", injury = "Hamstring")
        assertEquals("Out", InjuryReport.current(profile, 3)?.status)
        assertNull("last week's report", InjuryReport.current(profile, 4))
        profile = profile.copy(injuryStatus = null)
        assertNull("practice-only line", InjuryReport.current(profile, 3))
        profile = profile.copy(injuryStatus = "Questionable")
        assertEquals("Q", InjuryReport.current(profile, 3)?.shortStatus)
    }

    @Test
    fun projectionLabelsTheFavourite() {
        val rows = array("""[{"game_id":"g","home_margin":-10.8,"home_win_prob":0.211},{"game_id":"h","home_margin":0.4,"home_win_prob":0.51}]""")
            .map { GameProjection.fromJson(it) }
        assertEquals("SEA by 11.0", rows[0].label("WAS", "SEA"))
        assertEquals(0.789, rows[0].winProbability("SEA", "WAS"), 0.0001)
        assertEquals("Toss-up", rows[1].label("WAS", "SEA"))
    }

    @Test
    fun teamRatingDecodesAndSigns() {
        val json = """[{"season":2026,"team":"SEA","rank":1,"games":2,"through_week":3,"rating":8.71,"offense":3.09,"defense":5.62,"schedule":-0.08,"prior_weight":0.714,"wins":2,"losses":0,"ties":0,"points_for":44,"points_against":17,"updated_at":"2026-09-26T18:54:32+00:00"}]"""
        val rating = TeamRating.fromJson(array(json).first())
        assertEquals("+8.7", TeamRating.signed(rating.rating))
        assertEquals("0.0", TeamRating.signed(-0.04))
        assertEquals("-2.4", TeamRating.signed(-2.37))
    }

    // View model wiring

    @Test
    fun defendersQualifyOnSnapShareAndBoardsCarryVolume() = runTest {
        val regular = player(1, type = "def", metrics = listOf(m("Tackles", "12", 90, MetricCategory.DEFENSE, qualified = true)), stats = listOf(StandardStat("G", "3", "g")))
        val gunner = player(2, type = "def", metrics = listOf(m("Tackles", "2", 30, MetricCategory.DEFENSE, qualified = true)))
        val profiles = listOf(
            PlayerProfile(playerId = 1, season = current, defenseSnapShare = 0.94, defenseSnaps = 180),
            PlayerProfile(playerId = 2, season = current, defenseSnapShare = 0.05, defenseSnaps = 9),
        )
        val provider = object : MockProvider(listOf(regular, gunner)) {
            override suspend fun fetchPlayerProfiles(season: Int) = profiles
        }
        val vm = dashboard(provider)
        vm.load()
        vm.selectedPosition = PlayerPositionGroup.DEFENSE
        vm.qualifierLevel = QualifierLevel.QUALIFIED

        assertEquals(listOf(1), vm.leaderboard.map { it.playerId })
        assertEquals("180 snaps", vm.volumeCaption(regular, MetricCategory.DEFENSE))
        vm.qualifierLevel = QualifierLevel.ALL
        assertEquals(listOf(1, 2), vm.leaderboard.map { it.playerId })
    }

    @Test
    fun qualificationIsPerMetric() = runTest {
        val receiver = player(
            1,
            metrics = listOf(
                m("EPA/Tgt", "0.5", 90, MetricCategory.RECEIVING, qualified = true),
                m("Separation", "3.1", 99, MetricCategory.RECEIVING, qualified = false),
            ),
        )
        val vm = dashboard(MockProvider(listOf(receiver)))
        vm.load()
        vm.selectedPosition = PlayerPositionGroup.WR
        vm.qualifierLevel = QualifierLevel.QUALIFIED
        vm.setUserSortMetric("Separation")
        assertTrue("qualified for EPA/Tgt is not qualified for Separation", vm.leaderboard.isEmpty())
        vm.setUserSortMetric("EPA/Tgt")
        assertEquals(listOf(1), vm.leaderboard.map { it.playerId })
    }
}
