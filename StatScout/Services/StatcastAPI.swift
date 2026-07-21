import Foundation

protocol StatcastProviding: Sendable {
    func fetchPlayers() async throws -> [Player]
    func fetchHistoricalPlayers() async throws -> [Player]
    func fetchCurrentPlayers() async throws -> [Player]
    func fetchGameLogs(playerId: Int, season: Int) async throws -> [PlayerGameLog]
    func fetchTeamGameLogs(team: String, season: Int, sinceDate: Date) async throws -> [PlayerGameLog]
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

    func fetchHistoricalPlayers() async throws -> [Player] {
        try await fetchPlayers(seasonFilter: "lt.\(StatScoutSeason.current)")
    }

    func fetchCurrentPlayers() async throws -> [Player] {
        try await fetchPlayers(seasonFilter: "eq.\(StatScoutSeason.current)")
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

    private func fetchPlayers(seasonFilter: String) async throws -> [Player] {
        var all: [Player] = []
        let pageSize = 1000
        var offset = 0
        while true {
            let endpoint = baseURL
                .appending(path: "rest/v1/player_snapshots")
                .appending(queryItems: [
                    URLQueryItem(name: "select", value: "*"),
                    URLQueryItem(name: "season", value: seasonFilter),
                    // Stable key so offset paging can't skip/duplicate rows when
                    // updated_at changes mid-fetch.
                    URLQueryItem(name: "order", value: "id.asc"),
                    URLQueryItem(name: "limit", value: String(pageSize)),
                    URLQueryItem(name: "offset", value: String(offset))
                ])
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
            // changed under us — surface it instead of silently going blank.
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

/// Decodes an element if possible, otherwise yields nil instead of throwing —
/// so one malformed row can't fail the entire page.
private struct Lenient<T: Decodable>: Decodable {
    let value: T?

    init(from decoder: Decoder) throws {
        value = try? T(from: decoder)
    }
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
            // fractional part at all — so a 5-digit fraction fails BOTH above
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
