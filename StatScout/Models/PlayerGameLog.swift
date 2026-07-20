import Foundation

/// One player's contribution in one game (one row per player_type). Powers the
/// Recent Form card.
struct PlayerGameLog: Codable, Hashable, Sendable {
    let playerId: Int
    let season: Int
    let gameDate: Date
    let playerType: String
    let team: String?
    let opponent: String?
    /// Offensive involvement: pass attempts + carries + targets.
    let plays: Int
    /// Ball touches: completions + carries + receptions.
    let touches: Int
    let metrics: [String: Double?]

    enum CodingKeys: String, CodingKey {
        case playerId = "player_id"
        case season
        case gameDate = "game_date"
        case playerType = "player_type"
        case team
        case opponent
        case plays
        case touches
        case metrics
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        playerId = try c.decode(Int.self, forKey: .playerId)
        season = try c.decode(Int.self, forKey: .season)
        playerType = try c.decode(String.self, forKey: .playerType)
        team = try c.decodeIfPresent(String.self, forKey: .team)
        opponent = try c.decodeIfPresent(String.self, forKey: .opponent)
        plays = try c.decodeIfPresent(Int.self, forKey: .plays) ?? 0
        touches = try c.decodeIfPresent(Int.self, forKey: .touches) ?? 0

        // game_date arrives as "YYYY-MM-DD" from Supabase (date column, not timestamptz).
        let raw = try c.decode(String.self, forKey: .gameDate)
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.timeZone = TimeZone(identifier: "America/New_York")
        guard let parsed = formatter.date(from: raw) else {
            throw DecodingError.dataCorruptedError(forKey: .gameDate, in: c, debugDescription: "Invalid game_date: \(raw)")
        }
        gameDate = parsed

        // metrics is a JSONB object with nullable numeric values.
        if let dict = try? c.decode([String: Double?].self, forKey: .metrics) {
            metrics = dict
        } else {
            metrics = [:]
        }
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(playerId, forKey: .playerId)
        try c.encode(season, forKey: .season)
        try c.encode(playerType, forKey: .playerType)
        try c.encodeIfPresent(team, forKey: .team)
        try c.encodeIfPresent(opponent, forKey: .opponent)
        try c.encode(plays, forKey: .plays)
        try c.encode(touches, forKey: .touches)

        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.timeZone = TimeZone(identifier: "America/New_York")
        try c.encode(formatter.string(from: gameDate), forKey: .gameDate)
        try c.encode(metrics, forKey: .metrics)
    }
}

/// Aggregated stats over the last N games. NFL cadence is weekly, so the window
/// is measured in games (last 1 / 3 / 5), not days.
struct RecentFormWindow {
    let label: String
    /// Number of games requested for the window.
    let span: Int
    /// Actual number of games in the window.
    let games: Int
    let plays: Int
    let touches: Int
    /// Per-metric totals across the window. NFL box-score stats are counting
    /// stats (yards, TDs, receptions), so the window value is their sum.
    let metrics: [String: Double]

    static let windows: [(label: String, span: Int)] = [
        ("Last 1", 1),
        ("Last 3", 3),
        ("Last 5", 5),
    ]

    /// Build a window by summing each metric across the supplied game logs.
    static func build(label: String, span: Int, logs: [PlayerGameLog]) -> RecentFormWindow {
        let plays = logs.reduce(0) { $0 + $1.plays }
        let touches = logs.reduce(0) { $0 + $1.touches }

        var combined: [String: Double] = [:]
        let allKeys = Set(logs.flatMap { $0.metrics.keys })
        for key in allKeys {
            var total = 0.0
            var any = false
            for log in logs {
                if let value = log.metrics[key] ?? nil {
                    total += value
                    any = true
                }
            }
            if any { combined[key] = total }
        }

        return RecentFormWindow(
            label: label,
            span: span,
            games: logs.count,
            plays: plays,
            touches: touches,
            metrics: combined
        )
    }
}
