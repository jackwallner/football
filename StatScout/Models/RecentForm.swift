import Foundation

/// One player's rolling window, as stored in `public.player_recent_form`.
///
/// Mirrors the rolling-leaderboard shape the baseball app uses: the current
/// window, the equal-length window immediately before it, and the change
/// between them. The delta is the interesting column, a 9.1 Y/A means more when
/// you can see it was 6.2 over the three games before that.
///
/// The one structural difference from baseball is the window unit. MLB plays
/// daily, so a calendar window is the natural slice; the NFL plays weekly, with
/// byes, and a postseason where only two clubs play the final game. A window of
/// days would either be empty or misdated for most of the league, so a window
/// here is the player's own last N games, and `asOf` / `startWeek` / `endWeek`
/// report which games those actually were.
struct RecentForm: Codable, Hashable, Sendable, Identifiable {
    let playerId: Int
    let season: Int
    let playerType: String
    let windowGames: Int
    /// Date of the last game in the window. Lets the UI say "through Feb 8"
    /// rather than implying the window runs to today.
    let asOf: Date?
    /// NFL week numbers of the first and last game in the window, so a label
    /// can read "Weeks 15-17" instead of a date range.
    let startWeek: Int?
    let endWeek: Int?
    let team: String?
    let games: Int
    /// Offensive involvement: pass attempts + carries + targets.
    let plays: Int
    /// Ball touches: completions + carries + receptions.
    let touches: Int
    let metrics: [String: Double]
    let priorMetrics: [String: Double]
    let delta: [String: Double]

    var id: String { "\(playerId)-\(playerType)-\(windowGames)" }

    enum CodingKeys: String, CodingKey {
        case playerId = "player_id"
        case season
        case playerType = "player_type"
        case windowGames = "window_games"
        case asOf = "as_of"
        case startWeek = "start_week"
        case endWeek = "end_week"
        case team
        case games
        case plays
        case touches
        case metrics
        case priorMetrics = "prior_metrics"
        case delta
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        playerId = try c.decode(Int.self, forKey: .playerId)
        season = try c.decode(Int.self, forKey: .season)
        playerType = try c.decode(String.self, forKey: .playerType)
        windowGames = try c.decode(Int.self, forKey: .windowGames)
        team = try c.decodeIfPresent(String.self, forKey: .team)
        games = try c.decodeIfPresent(Int.self, forKey: .games) ?? 0
        plays = try c.decodeIfPresent(Int.self, forKey: .plays) ?? 0
        touches = try c.decodeIfPresent(Int.self, forKey: .touches) ?? 0
        startWeek = try c.decodeIfPresent(Int.self, forKey: .startWeek)
        endWeek = try c.decodeIfPresent(Int.self, forKey: .endWeek)

        // as_of is a Postgres `date`, so it arrives as "YYYY-MM-DD" and won't
        // parse with the ISO8601 strategy the rest of the payload uses.
        if let raw = try c.decodeIfPresent(String.self, forKey: .asOf) {
            var parts = DateComponents()
            let bits = raw.split(separator: "-").compactMap { Int($0) }
            if bits.count == 3 {
                parts.year = bits[0]; parts.month = bits[1]; parts.day = bits[2]
                asOf = Calendar.current.date(from: parts)
            } else {
                asOf = nil
            }
        } else {
            asOf = nil
        }

        // Null metric values mean "no data in this window" (see the rollup's
        // omit-rather-than-zero rule), so they're dropped rather than coerced.
        func numbers(_ key: CodingKeys) -> [String: Double] {
            guard let raw = try? c.decodeIfPresent([String: Double?].self, forKey: key) else { return [:] }
            return raw.compactMapValues { $0 }
        }
        metrics = numbers(.metrics)
        priorMetrics = numbers(.priorMetrics)
        delta = numbers(.delta)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(playerId, forKey: .playerId)
        try c.encode(season, forKey: .season)
        try c.encode(playerType, forKey: .playerType)
        try c.encode(windowGames, forKey: .windowGames)
        try c.encodeIfPresent(team, forKey: .team)
        try c.encode(games, forKey: .games)
        try c.encode(plays, forKey: .plays)
        try c.encode(touches, forKey: .touches)
        try c.encodeIfPresent(startWeek, forKey: .startWeek)
        try c.encodeIfPresent(endWeek, forKey: .endWeek)
        try c.encode(metrics, forKey: .metrics)
        try c.encode(priorMetrics, forKey: .priorMetrics)
        try c.encode(delta, forKey: .delta)
    }

    /// Weeks covered, e.g. "Weeks 15-17" or "Week 17". Nil when the rollup
    /// predates the week columns.
    var weekRangeLabel: String? {
        guard let startWeek, let endWeek else { return nil }
        return startWeek == endWeek ? "Week \(startWeek)" : "Weeks \(startWeek)-\(endWeek)"
    }

    /// Small samples make wild deltas. Volume means different things by
    /// position, so the floor does too: a quarterback throws thirty times in a
    /// bad game, a tight end can have a real week on four targets. Defenders
    /// have no play count in the weekly feed at all, so they gate on games.
    var isSmallSample: Bool {
        if games < 2 { return true }
        switch playerType {
        case "qb":  return plays < 30
        case "rb":  return plays < 18
        case "wr":  return plays < 12
        case "te":  return plays < 10
        default:    return false
        }
    }
}

/// Season metric label to the rolling rollup's column for it, plus the one
/// formatter for a window value.
///
/// The leaderboard, the team roster and the team cards all need to ask "what is
/// this player's Y/A over the last five games"; each had grown its own private
/// copy of the mapping, which is how a metric ends up trending on one screen
/// and blank on the next.
///
/// Returns nil for the season metrics the rollup has no column for. Those come
/// from Next Gen Stats aggregates with no per-game denominator (Aggressiveness,
/// Intended Air Yds, Target Share, WOPR) or from play-by-play the weekly feed
/// doesn't carry (Explosive%). A metric with no rollup key simply gets no
/// recent bar, which is the same rule baseball uses for the metrics Savant
/// publishes no season percentile for.
enum RecentMetricKey {
    static func key(for label: String) -> String? {
        switch label {
        // Passing.
        case "Pass Yds": return "pass_yards"
        case "Pass TD": return "pass_tds"
        case "Cmp%": return "cmp_pct"
        case "Y/A": return "ypa"
        case "INT%": return "int_rate"
        case "Rating": return "passer_rating"
        case "EPA/Play": return "passing_epa"
        case "CPOE": return "cpoe"
        case "Time to Throw": return "avg_time_to_throw"
        case "Sack%": return "sack_rate"
        // Rushing.
        case "Rush Yds": return "rush_yards"
        case "Rush TD": return "rush_tds"
        case "Y/C": return "ypc"
        case "Rush EPA": return "rushing_epa"
        case "Rush 1D": return "rush_first_downs"
        case "Fumble%": return "fumble_rate"
        case "RYOE": return "rush_yoe"
        // Receiving.
        case "Rec": return "receptions"
        case "Rec Yds": return "rec_yards"
        case "Rec TD": return "rec_tds"
        case "YAC": return "yac"
        case "RACR": return "racr"
        case "Rec EPA": return "receiving_epa"
        case "Catch%": return "catch_pct"
        case "Separation": return "avg_separation"
        case "YAC+": return "avg_yac_above_expectation"
        // Defense.
        case "Tackles": return "tackles"
        case "Sacks": return "sacks"
        case "INT": return "def_ints"
        case "PD": return "passes_defended"
        case "FF": return "forced_fumbles"
        case "TFL": return "tfl"
        case "QB Hits": return "qb_hits"
        default: return nil
        }
    }

    /// True where a falling number is the improvement.
    static func lowerIsBetter(_ label: String) -> Bool {
        label == "INT%" || label == "Sack%" || label == "Fumble%"
    }

    /// How many decimals the metric's delta moves in. Yardage and counting
    /// stats are whole, percentages and per-attempt rates are tenths, EPA per
    /// play is hundredths.
    static func decimals(for label: String) -> Int {
        switch label {
        case "EPA/Play", "RACR", "Time to Throw": return 2
        case "Cmp%", "INT%", "Sack%", "Fumble%", "Catch%", "Y/A", "Y/C",
             "Rating", "CPOE", "Rush EPA", "Rec EPA", "RYOE",
             "Separation", "YAC+": return 1
        default: return 0
        }
    }

    /// Matches the player page's conventions: a percentage carries its sign, a
    /// rate its decimals, and a yardage total its thousands separator.
    static func format(_ value: Double, label: String) -> String {
        let places = decimals(for: label)
        if label.hasSuffix("%") { return String(format: "%.1f%%", value) }
        if places == 0 {
            let whole = Int(value.rounded())
            return whole.formatted(.number.grouping(.automatic))
        }
        return String(format: "%.\(places)f", value)
    }
}

/// Which rolling window is on screen. Mirrors `RecentFormWindow.windows` so the
/// per-player card and the league leaderboard offer the same choices.
///
/// Games, not days. Eight is about half a regular season, three is "the last
/// few weeks", and five sits where most people's sense of "lately" does.
enum RecentWindow: Int, CaseIterable, Identifiable, Sendable {
    case three = 3
    case five = 5
    case eight = 8

    var id: Int { rawValue }
    /// Always says "games". A bare "Last 5" beside a row that also reports a
    /// week range reads as five *weeks* to everyone who wasn't the person who
    /// wrote it.
    var label: String { "Last \(rawValue) games" }
    /// Segment-width version of the same wording: still explicit about the
    /// unit, short enough for three of them across a phone.
    var segmentLabel: String { "\(rawValue) games" }
    var shortLabel: String { "\(rawValue)G" }
}
