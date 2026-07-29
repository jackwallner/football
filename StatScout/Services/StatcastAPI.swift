import Foundation

/// Returns true when a fetch ended only because its Swift task was superseded.
/// URLSession reports task cancellation as `URLError.cancelled`.
func isTaskCancellation(_ error: Error) -> Bool {
    if error is CancellationError { return true }
    if let urlError = error as? URLError, urlError.code == .cancelled { return true }
    return false
}

protocol StatcastProviding: Sendable {
    func fetchPlayers() async throws -> [Player]
    func fetchHistoricalPlayers() async throws -> [Player]
    func fetchCurrentPlayers() async throws -> [Player]
    func fetchGameLogs(playerId: Int, season: Int) async throws -> [PlayerGameLog]
    func fetchTeamGameLogs(team: String, season: Int, sinceDate: Date) async throws -> [PlayerGameLog]
    func fetchRecentForm(
        season: Int,
        seasonPhase: SeasonPhase,
        windowWeeks: Int
    ) async throws -> [RecentForm]
}

struct StatcastAPI: StatcastProviding {
    private let baseURL: URL
    private let apiKey: String

    init(baseURL: URL, apiKey: String) {
        self.baseURL = baseURL
        self.apiKey = apiKey
    }

    func fetchPlayers() async throws -> [Player] {
        let historical = try await fetchHistoricalPlayers()
        let current = try await fetchCurrentPlayers()
        return historical + current
    }

    /// Everything older than the live season, plus the career rollup.
    ///
    /// The rollup is stored under `season = 0`, which is *below* `earliest`, so
    /// a plain `season.gte.2000` range quietly excluded it and All Time came
    /// back empty. Hence the `or`: the sentinel row or the real historical
    /// range. It still sorts into the historical cache partition, since 0 is
    /// less than the current season.
    func fetchHistoricalPlayers() async throws -> [Player] {
        try await fetchPlayers(queryItems: [
            URLQueryItem(
                name: "or",
                value: "(season.eq.\(StatScoutSeason.allTime),"
                    + "and(season.gte.\(StatScoutSeason.earliest),"
                    + "season.lt.\(StatScoutSeason.current)))"
            ),
        ])
    }

    func fetchCurrentPlayers() async throws -> [Player] {
        try await fetchPlayers(queryItems: [
            URLQueryItem(name: "season", value: "eq.\(StatScoutSeason.current)"),
        ])
    }

    func fetchGameLogs(playerId: Int, season: Int) async throws -> [PlayerGameLog] {
        let endpoint = baseURL
            .appending(path: "rest/v1/player_game_logs")
            .appending(queryItems: [
                URLQueryItem(name: "select", value: "*"),
                URLQueryItem(name: "player_id", value: "eq.\(playerId)"),
                URLQueryItem(name: "season", value: "eq.\(season)"),
                URLQueryItem(name: "order", value: "game_date.desc"),
            ])
        var request = URLRequest(url: endpoint, cachePolicy: .reloadIgnoringLocalCacheData)
        request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
        request.setValue(apiKey, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse,
              200..<300 ~= httpResponse.statusCode || httpResponse.statusCode == 206 else {
            throw URLError(.badServerResponse)
        }

        let rows = try JSONDecoder.statScout.decode([Lenient<PlayerGameLog>].self, from: data)
        return rows.compactMap(\.value)
    }

    func fetchTeamGameLogs(team: String, season: Int, sinceDate: Date) async throws -> [PlayerGameLog] {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.timeZone = TimeZone(identifier: "America/New_York")
        let sinceString = formatter.string(from: sinceDate)

        var all: [PlayerGameLog] = []
        let pageSize = 1000
        var offset = 0
        while true {
            let endpoint = baseURL
                .appending(path: "rest/v1/player_game_logs")
                .appending(queryItems: [
                    URLQueryItem(name: "select", value: "*"),
                    URLQueryItem(name: "team", value: "eq.\(team)"),
                    URLQueryItem(name: "season", value: "eq.\(season)"),
                    URLQueryItem(name: "game_date", value: "gte.\(sinceString)"),
                    URLQueryItem(name: "order", value: "game_date.desc"),
                    URLQueryItem(name: "limit", value: String(pageSize)),
                    URLQueryItem(name: "offset", value: String(offset)),
                ])
            var request = URLRequest(url: endpoint, cachePolicy: .reloadIgnoringLocalCacheData)
            request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
            request.setValue(apiKey, forHTTPHeaderField: "apikey")
            request.setValue("application/json", forHTTPHeaderField: "Accept")

            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse,
                  200..<300 ~= httpResponse.statusCode || httpResponse.statusCode == 206 else {
                throw URLError(.badServerResponse)
            }

            let rows = try JSONDecoder.statScout.decode([Lenient<PlayerGameLog>].self, from: data)
            let page = rows.compactMap(\.value)
            all.append(contentsOf: page)
            if rows.count < pageSize { break }
            offset += pageSize
        }
        return all
    }

    /// The whole league's rolling window for one window length, in one paged
    /// fetch. The board ranks by delta, so it needs every qualifying player at
    /// once; there is no useful partial sort.
    ///
    /// Cache-bypassing for the same reason the game-log fetches are: the
    /// nightly rewrites these rows in place, and a stale window is worse than a
    /// slow one. The element decoder is lossy so a single malformed row can't
    /// empty the board.
    func fetchRecentForm(
        season: Int,
        seasonPhase: SeasonPhase,
        windowWeeks: Int
    ) async throws -> [RecentForm] {
        var all: [RecentForm] = []
        let pageSize = 1000
        var offset = 0
        while true {
            let endpoint = baseURL
                .appending(path: "rest/v1/player_recent_form")
                .appending(queryItems: [
                    URLQueryItem(name: "select", value: "*"),
                    URLQueryItem(name: "season", value: "eq.\(season)"),
                    URLQueryItem(name: "season_type", value: "eq.\(seasonPhase.rawValue)"),
                    URLQueryItem(name: "window_weeks", value: "eq.\(windowWeeks)"),
                    URLQueryItem(name: "order", value: "player_id.asc"),
                    URLQueryItem(name: "limit", value: String(pageSize)),
                    URLQueryItem(name: "offset", value: String(offset)),
                ])
            var request = URLRequest(url: endpoint, cachePolicy: .reloadIgnoringLocalCacheData)
            request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
            request.setValue(apiKey, forHTTPHeaderField: "apikey")
            request.setValue("application/json", forHTTPHeaderField: "Accept")

            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse,
                  200..<300 ~= httpResponse.statusCode || httpResponse.statusCode == 206 else {
                throw URLError(.badServerResponse)
            }

            let rows = try JSONDecoder.statScout.decode([Lenient<RecentForm>].self, from: data)
            all.append(contentsOf: rows.compactMap(\.value))
            if rows.count < pageSize { break }
            offset += pageSize
        }
        return all
    }

    private func fetchPlayers(queryItems filters: [URLQueryItem]) async throws -> [Player] {
        var all: [Player] = []
        let pageSize = 1000
        var offset = 0
        while true {
            let queryItems = [
                URLQueryItem(name: "select", value: "*"),
                // Stable key so offset paging can't skip/duplicate rows when
                // updated_at changes mid-fetch.
                URLQueryItem(
                    name: "order",
                    value: "season.asc,season_type.asc,id.asc"
                ),
                URLQueryItem(name: "limit", value: String(pageSize)),
                URLQueryItem(name: "offset", value: String(offset)),
            ] + filters
            let endpoint = baseURL
                .appending(path: "rest/v1/player_snapshots")
                .appending(queryItems: queryItems)
            // Bypass URLCache so the shared headshot cache can't serve a stale page.
            var request = URLRequest(url: endpoint, cachePolicy: .reloadIgnoringLocalCacheData)
            request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
            request.setValue(apiKey, forHTTPHeaderField: "apikey")
            request.setValue("application/json", forHTTPHeaderField: "Accept")

            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse,
                  200..<300 ~= httpResponse.statusCode || httpResponse.statusCode == 206 else {
                throw URLError(.badServerResponse)
            }

            let rows = try JSONDecoder.statScout.decode([Lenient<Player>].self, from: data)
            let page = rows.compactMap(\.value)
            // A non-empty page that decodes to zero players means the schema
            // changed under us - surface it instead of silently going blank.
            if !rows.isEmpty && page.isEmpty {
                throw DecodingError.dataCorrupted(
                    .init(codingPath: [], debugDescription: "All player rows failed to decode")
                )
            }
            all.append(contentsOf: page)
            if rows.count < pageSize { break }
            offset += pageSize
        }
        return all
    }
}

/// Decodes an element if possible, otherwise yields nil instead of throwing,
/// so one malformed row cannot fail the entire page.
private struct Lenient<T: Decodable>: Decodable {
    let value: T?

    init(from decoder: Decoder) throws {
        value = try? T(from: decoder)
    }
}

struct OfflineStatcastAPI: StatcastProviding {
    func fetchPlayers() async throws -> [Player] { [] }
    func fetchHistoricalPlayers() async throws -> [Player] { [] }
    func fetchCurrentPlayers() async throws -> [Player] { [] }
    func fetchGameLogs(playerId: Int, season: Int) async throws -> [PlayerGameLog] { [] }
    func fetchTeamGameLogs(team: String, season: Int, sinceDate: Date) async throws -> [PlayerGameLog] { [] }
    func fetchRecentForm(
        season: Int,
        seasonPhase: SeasonPhase,
        windowWeeks: Int
    ) async throws -> [RecentForm] { [] }
}

#if DEBUG
struct PreviewStatcastAPI: StatcastProviding {
    func fetchPlayers() async throws -> [Player] {
        SampleData.players
    }

    func fetchHistoricalPlayers() async throws -> [Player] {
        SampleData.players.filter { ($0.season ?? 0) < StatScoutSeason.current }
    }

    func fetchCurrentPlayers() async throws -> [Player] {
        SampleData.players.filter { ($0.season ?? 0) >= StatScoutSeason.current }
    }

    func fetchGameLogs(playerId: Int, season: Int) async throws -> [PlayerGameLog] {
        []
    }

    func fetchTeamGameLogs(team: String, season: Int, sinceDate: Date) async throws -> [PlayerGameLog] {
        []
    }

    func fetchRecentForm(
        season: Int,
        seasonPhase: SeasonPhase,
        windowWeeks: Int
    ) async throws -> [RecentForm] {
        []
    }
}
#endif

extension JSONDecoder {
    static var statScout: JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .custom { decoder in
            let container = try decoder.singleValueContainer()
            let dateString = try container.decode(String.self)

            let formatter = ISO8601DateFormatter()
            formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
            if let date = formatter.date(from: dateString) {
                return date
            }

            let fallback = ISO8601DateFormatter()
            fallback.formatOptions = [.withInternetDateTime]
            if let date = fallback.date(from: dateString) {
                return date
            }

            // Postgres/PostgREST emits variable-length fractional seconds
            // (e.g. "…40.95411+00:00"). ISO8601DateFormatter's
            // .withFractionalSeconds only accepts exactly 3 fractional digits
            // on some iOS versions, and the plain formatter rejects any
            // fractional part at all - so a 5-digit fraction fails BOTH above
            // and every row's required updated_at drops, surfacing as a bogus
            // "Data format changed" error. Strip the fraction and retry.
            if let dotRange = dateString.range(of: #"\.\d+"#, options: .regularExpression) {
                var stripped = dateString
                stripped.removeSubrange(dotRange)
                if let date = fallback.date(from: stripped) {
                    return date
                }
            }

            throw DecodingError.dataCorruptedError(in: container, debugDescription: "Invalid date: \(dateString)")
        }
        return decoder
    }
}
