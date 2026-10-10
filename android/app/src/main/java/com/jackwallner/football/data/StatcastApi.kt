package com.jackwallner.football.data

import com.jackwallner.football.model.DataCoverage
import com.jackwallner.football.model.DataFreshness
import com.jackwallner.football.model.Game
import com.jackwallner.football.model.GameDetail
import com.jackwallner.football.model.GameProjection
import com.jackwallner.football.model.Player
import com.jackwallner.football.model.PlayerGameLog
import com.jackwallner.football.model.PlayerProfile
import com.jackwallner.football.model.RecentForm
import com.jackwallner.football.model.SeasonPhase
import com.jackwallner.football.model.StatScoutSeason
import com.jackwallner.football.model.TeamRating
import com.jackwallner.football.model.int
import com.jackwallner.football.model.lenientList
import com.jackwallner.football.model.parseDay
import com.jackwallner.football.model.string
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** A response the app cannot use: a non-2xx status or a body that isn't the expected JSON. */
class BadServerResponse(val status: Int) : IOException("Bad server response ($status)")

/** Every row of a non-empty page failed to decode: the schema moved under the app. */
class DataFormatException(message: String) : Exception(message)

interface StatcastProviding {
    suspend fun fetchHistoricalPlayers(): List<Player>
    suspend fun fetchCurrentPlayers(): List<Player>
    suspend fun fetchGameLogs(playerId: Int, season: Int, seasonPhase: SeasonPhase): List<PlayerGameLog>
    suspend fun fetchTeamGameLogs(team: String, season: Int, seasonPhase: SeasonPhase, sinceDate: Instant): List<PlayerGameLog>
    suspend fun fetchRecentForm(season: Int, seasonPhase: SeasonPhase, windowWeeks: Int): List<RecentForm>
    suspend fun fetchDataCoverage(season: Int): DataCoverage?
    suspend fun fetchDataFreshness(season: Int): DataFreshness? = null
    suspend fun fetchGames(season: Int): List<Game> = emptyList()
    suspend fun fetchGameLogs(gameId: String): List<PlayerGameLog> = emptyList()
    suspend fun fetchGameIdsWithStats(season: Int): Set<String> = emptySet()
    suspend fun fetchGameDetail(gameId: String): GameDetail? = null
    suspend fun fetchPlayerProfiles(season: Int): List<PlayerProfile> = emptyList()
    suspend fun fetchTeamRatings(season: Int): List<TeamRating> = emptyList()
    suspend fun fetchGameProjections(season: Int): List<GameProjection> = emptyList()
}

/** PostgREST over HTTPS, the same queries the iOS `StatcastAPI` makes. */
class StatcastApi(private val baseUrl: String, private val apiKey: String) : StatcastProviding {
    private val json = Json { ignoreUnknownKeys = true }

    /** Career rollup (season 0) plus every season before the live one. */
    override suspend fun fetchHistoricalPlayers(): List<Player> = fetchPlayers(
        listOf(
            "or" to "(season.eq.${StatScoutSeason.ALL_TIME},and(season.gte.${StatScoutSeason.EARLIEST},season.lt.${StatScoutSeason.current}))",
        ),
    )

    override suspend fun fetchCurrentPlayers(): List<Player> = fetchPlayers(listOf("season" to "eq.${StatScoutSeason.current}"))

    /** One player's games for a single season and phase; playoffs never pad a regular-season window. */
    override suspend fun fetchGameLogs(playerId: Int, season: Int, seasonPhase: SeasonPhase): List<PlayerGameLog> =
        get(
            "player_game_logs",
            listOf(
                "select" to "*",
                "player_id" to "eq.$playerId",
                "season" to "eq.$season",
                "season_type" to "eq.${seasonPhase.raw}",
                "order" to "game_date.desc",
            ),
        )!!.lenientList(PlayerGameLog::fromJson)

    override suspend fun fetchTeamGameLogs(team: String, season: Int, seasonPhase: SeasonPhase, sinceDate: Instant): List<PlayerGameLog> {
        val since = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(sinceDate.atZone(com.jackwallner.football.model.EASTERN))
        return paged { offset ->
            get(
                "player_game_logs",
                listOf(
                    "select" to "*",
                    "team" to "eq.$team",
                    "season" to "eq.$season",
                    "season_type" to "eq.${seasonPhase.raw}",
                    "game_date" to "gte.$since",
                    "order" to "game_date.desc",
                    "limit" to PAGE.toString(),
                    "offset" to offset.toString(),
                ),
            )!!
        }.flatMap { it.lenientList(PlayerGameLog::fromJson) }
    }

    /** The whole league's rolling window for one length, paged. */
    override suspend fun fetchRecentForm(season: Int, seasonPhase: SeasonPhase, windowWeeks: Int): List<RecentForm> = paged { offset ->
        get(
            "player_recent_form",
            listOf(
                "select" to "*",
                "season" to "eq.$season",
                "season_type" to "eq.${seasonPhase.raw}",
                "window_weeks" to "eq.$windowWeeks",
                "order" to "player_id.asc",
                "limit" to PAGE.toString(),
                "offset" to offset.toString(),
            ),
        )!!
    }.flatMap { it.lenientList(RecentForm::fromJson) }

    /** One row, read for its coverage columns; ordered by week so a playoff row wins. */
    override suspend fun fetchDataCoverage(season: Int): DataCoverage? {
        val rows = get(
            "player_recent_form",
            listOf(
                "select" to "as_of,end_week,season_type",
                "season" to "eq.$season",
                "as_of" to "not.is.null",
                "order" to "end_week.desc.nullslast,as_of.desc",
                "limit" to "1",
            ),
            allowPartial = false,
        ) as? JsonArray
        val row = rows?.firstOrNull() as? JsonObject ?: return null
        val asOf = row.string("as_of")?.let { parseDay(it) } ?: return null
        return DataCoverage(asOf, row.int("end_week"), row.string("season_type")?.let { SeasonPhase.from(it) } ?: SeasonPhase.REGULAR)
    }

    /** The optional status endpoint; a 404 means "not deployed", never a player-data error. */
    override suspend fun fetchDataFreshness(season: Int): DataFreshness? {
        val rows = get(
            "data_refresh_status",
            listOf(
                "season" to "eq.$season",
                "order" to "published_at.desc.nullslast,last_checked_at.desc",
                "limit" to "1",
            ),
            missingIsNull = true,
            allowPartial = false,
        ) ?: return null
        val first = (rows as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        return DataFreshness.fromJson(first)
    }

    override suspend fun fetchGames(season: Int): List<Game> = get(
        "games",
        listOf("select" to "*", "season" to "eq.$season", "order" to "kickoff_at.asc,game_id.asc", "limit" to "400"),
    )!!.lenientList(Game::fromJson)

    override suspend fun fetchGameLogs(gameId: String): List<PlayerGameLog> = get(
        "player_game_logs",
        listOf("select" to "*", "game_id" to "eq.$gameId", "limit" to "200"),
    )!!.lenientList(PlayerGameLog::fromJson)

    /** Every game has a passer, so quarterback rows alone answer "has stats". */
    override suspend fun fetchGameIdsWithStats(season: Int): Set<String> =
        (get(
            "player_game_logs",
            listOf("select" to "game_id", "season" to "eq.$season", "player_type" to "eq.qb", "limit" to "2000"),
        ) as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.string("game_id") }.toSet()

    override suspend fun fetchGameDetail(gameId: String): GameDetail? {
        val rows = get("game_details", listOf("select" to "*", "game_id" to "eq.$gameId", "limit" to "1")) as? JsonArray
        val first = rows?.firstOrNull() as? JsonObject ?: return null
        return GameDetail.fromJson(first)
    }

    /** Optional context: a missing table reads as no profiles. */
    override suspend fun fetchPlayerProfiles(season: Int): List<PlayerProfile> {
        val all = mutableListOf<PlayerProfile>()
        var offset = 0
        while (true) {
            val page = get(
                "player_profiles",
                listOf(
                    "select" to "*",
                    "season" to "eq.$season",
                    "order" to "player_id.asc",
                    "limit" to PAGE.toString(),
                    "offset" to offset.toString(),
                ),
                missingIsNull = true,
            ) as? JsonArray ?: return emptyList()
            all += page.lenientList(PlayerProfile::fromJson)
            if (page.size < PAGE) return all
            offset += PAGE
        }
    }

    override suspend fun fetchTeamRatings(season: Int): List<TeamRating> =
        get("team_ratings", listOf("select" to "*", "season" to "eq.$season", "order" to "rank.asc"), missingIsNull = true)
            ?.lenientList(TeamRating::fromJson).orEmpty()

    override suspend fun fetchGameProjections(season: Int): List<GameProjection> =
        get(
            "game_projections",
            listOf("select" to "game_id,home_margin,home_win_prob", "season" to "eq.$season", "limit" to "400"),
            missingIsNull = true,
        )?.lenientList(GameProjection::fromJson).orEmpty()

    private suspend fun fetchPlayers(filters: List<Pair<String, String>>): List<Player> {
        val all = mutableListOf<Player>()
        var offset = 0
        while (true) {
            val rows = get(
                "player_snapshots",
                listOf(
                    "select" to "*",
                    // Stable key so offset paging can't skip or repeat rows mid-refresh.
                    "order" to "season.asc,season_type.asc,id.asc",
                    "limit" to PAGE.toString(),
                    "offset" to offset.toString(),
                ) + filters,
            ) as? JsonArray ?: throw DataFormatException("Player page was not an array")
            val page = rows.lenientList(Player::fromJson)
            if (rows.isNotEmpty() && page.isEmpty()) throw DataFormatException("All player rows failed to decode")
            all += page
            if (rows.size < PAGE) break
            offset += PAGE
        }
        return all
    }

    private suspend fun paged(fetch: suspend (Int) -> JsonElement): List<JsonElement> {
        val pages = mutableListOf<JsonElement>()
        var offset = 0
        while (true) {
            val page = fetch(offset)
            pages += page
            if ((page as? JsonArray)?.size ?: 0 < PAGE) break
            offset += PAGE
        }
        return pages
    }

    /**
     * One GET against `rest/v1/<table>`. Bypasses caches: the pipeline rewrites
     * rows in place and a stale page is worse than a slow one.
     */
    private suspend fun get(
        table: String,
        query: List<Pair<String, String>>,
        missingIsNull: Boolean = false,
        allowPartial: Boolean = true,
    ): JsonElement? = withContext(Dispatchers.IO) {
        val qs = query.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
        val connection = URL("${baseUrl.trimEnd('/')}/rest/v1/$table?$qs").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.useCaches = false
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("apikey", apiKey)
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            if (status == 404 && missingIsNull) return@withContext null
            val ok = status in 200..299 || (allowPartial && status == 206)
            if (!ok) throw BadServerResponse(status)
            val body = connection.inputStream.use { it.readBytes().decodeToString() }
            try {
                json.parseToJsonElement(body)
            } catch (error: kotlinx.serialization.SerializationException) {
                throw DataFormatException(error.message ?: "Bad JSON")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private companion object {
        const val PAGE = 1000
    }
}

/** No configuration: the app shows its "can't load" screen rather than an empty league. */
object OfflineStatcastApi : StatcastProviding {
    override suspend fun fetchHistoricalPlayers(): List<Player> = emptyList()
    override suspend fun fetchCurrentPlayers(): List<Player> = emptyList()
    override suspend fun fetchGameLogs(playerId: Int, season: Int, seasonPhase: SeasonPhase): List<PlayerGameLog> = emptyList()
    override suspend fun fetchTeamGameLogs(team: String, season: Int, seasonPhase: SeasonPhase, sinceDate: Instant): List<PlayerGameLog> = emptyList()
    override suspend fun fetchRecentForm(season: Int, seasonPhase: SeasonPhase, windowWeeks: Int): List<RecentForm> = emptyList()
    override suspend fun fetchDataCoverage(season: Int): DataCoverage? = null
}
