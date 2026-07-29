import Foundation

struct Player: Identifiable, Codable, Hashable, Sendable {
    var id: String { "\(playerId)-\(season ?? 0)" }
    let playerId: Int
    let name: String
    let team: String
    let position: String
    let handedness: String
    let updatedAt: Date
    let season: Int?
    let playerType: String?
    let source: String?
    let metrics: [Metric]
    let standardStats: [StandardStat]?
    let games: [GameTrend]

    enum CodingKeys: String, CodingKey {
        case playerId = "id"
        case name
        case team
        case position
        case handedness
        case updatedAt = "updated_at"
        case season
        case playerType = "player_type"
        case source
        case metrics
        case standardStats = "standard_stats"
        case games
    }

    init(playerId: Int, name: String, team: String, position: String, handedness: String, updatedAt: Date, season: Int? = nil, playerType: String? = nil, source: String? = nil, metrics: [Metric], standardStats: [StandardStat]?, games: [GameTrend]) {
        self.playerId = playerId
        self.name = name
        self.team = team
        self.position = position
        self.handedness = handedness
        self.updatedAt = updatedAt
        self.season = season
        self.playerType = playerType
        self.source = source
        self.metrics = metrics
        self.standardStats = standardStats
        self.games = games
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        playerId = try container.decode(Int.self, forKey: .playerId)
        name = try container.decode(String.self, forKey: .name)
        team = try container.decode(String.self, forKey: .team)
        position = try container.decode(String.self, forKey: .position)
        handedness = try container.decode(String.self, forKey: .handedness)
        // No headshot field: the app never renders player photos (same as the
        // baseball build), and the league's headshot URLs aren't ours to
        // redistribute. It was also a live decoding hazard - it used to be
        // decoded as `URL`, which only round-trips from a plain string on
        // JSONDecoder. PropertyListDecoder expects URL's keyed
        // {relative, base} form, so it threw typeMismatch on the first row and
        // took the whole `[Player]` array down with it, leaving the bundled
        // current *and* historical datasets unreadable ("Data Error - No
        // players found"). The feed still sends `image_url`; unknown keys are
        // simply ignored.
        updatedAt = try container.decode(Date.self, forKey: .updatedAt)
        season = try container.decodeIfPresent(Int.self, forKey: .season)
        playerType = try container.decodeIfPresent(String.self, forKey: .playerType)
        source = try container.decodeIfPresent(String.self, forKey: .source)
        metrics = try container.decode([Metric].self, forKey: .metrics)
        standardStats = try container.decodeIfPresent([StandardStat].self, forKey: .standardStats)
        games = try container.decodeIfPresent([GameTrend].self, forKey: .games) ?? []
    }

    /// Explicit mirror of `init(from:)` so the on-disk plist cache is written
    /// in exactly the shape the decoder above reads back. Leaving this
    /// synthesized is what let the encode and decode sides drift apart in the
    /// first place.
    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(playerId, forKey: .playerId)
        try container.encode(name, forKey: .name)
        try container.encode(team, forKey: .team)
        try container.encode(position, forKey: .position)
        try container.encode(handedness, forKey: .handedness)
        try container.encode(updatedAt, forKey: .updatedAt)
        try container.encodeIfPresent(season, forKey: .season)
        try container.encodeIfPresent(playerType, forKey: .playerType)
        try container.encodeIfPresent(source, forKey: .source)
        try container.encode(metrics, forKey: .metrics)
        try container.encodeIfPresent(standardStats, forKey: .standardStats)
        try container.encode(games, forKey: .games)
    }

    var overallPercentile: Int {
        guard !metrics.isEmpty else { return 0 }
        // Players who span more than one category (e.g. a rushing QB with both
        // Passing and Rushing metrics) shouldn't have their headline number
        // diluted by averaging across unrelated skills - take the best category.
        let categories = Set(metrics.map(\.category))
        if categories.count > 1 {
            let categoryAverages = Dictionary(grouping: metrics) { $0.category }
                .values
                .map { group in
                    Double(group.map(\.percentile).reduce(0, +)) / Double(group.count)
                }
            return Int(round(categoryAverages.max() ?? 0))
        }
        let total = metrics.map(\.percentile).reduce(0, +)
        return Int(round(Double(total) / Double(metrics.count)))
    }

    var headlineMetric: Metric? {
        metrics.sorted { $0.percentile > $1.percentile }.first
    }

    var latestGame: GameTrend? {
        games.sorted { $0.date > $1.date }.first
    }

    var latestPercentileDelta: Int {
        latestGame?.percentileDelta ?? 0
    }

    var weeklyDelta: Int {
        let cutoff = Date().addingTimeInterval(-7 * 24 * 3600)
        return games.filter { $0.date >= cutoff }
            .map(\.percentileDelta)
            .reduce(0, +)
    }

    var shareSummary: String {
        let headline = headlineMetric.map { metric in
            let valueText = metric.value.isEmpty ? "\(metric.percentile.ordinal) percentile" : "\(metric.value), \(metric.percentile.ordinal) percentile"
            return "\(metric.label) \(valueText)"
        } ?? "\(overallPercentile.ordinal) overall percentile"
        return "\(name) · \(team) \(displayPosition)\nOverall: \(overallPercentile.ordinal) percentile\nTop stat: \(headline)\nGridiron StatScout"
    }

    func percentile(for category: MetricCategory) -> Int? {
        let categoryMetrics = metrics.filter { $0.category == category }
        guard !categoryMetrics.isEmpty else { return nil }
        let total = categoryMetrics.map(\.percentile).reduce(0, +)
        return Int(round(Double(total) / Double(categoryMetrics.count)))
    }
}

struct Metric: Identifiable, Codable, Hashable, Sendable {
    let id: String
    let label: String
    let value: String
    let percentile: Int
    let category: MetricCategory
}

struct StandardStat: Identifiable, Codable, Hashable, Sendable {
    let id: String
    let label: String
    let value: String
}

enum MetricDirection: String, Codable, Hashable, Sendable {
    case up
    case flat
    case down
}

enum MetricCategory: String, Codable, CaseIterable, Hashable, Sendable {
    case passing = "Passing"
    case rushing = "Rushing"
    case receiving = "Receiving"
    case defense = "Defense"

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        let value = try container.decode(String.self)
        guard let category = Self.allCases.first(where: {
            $0.rawValue.caseInsensitiveCompare(value) == .orderedSame
        }) else {
            throw DecodingError.dataCorruptedError(
                in: container,
                debugDescription: "Unknown metric category: \(value)"
            )
        }
        self = category
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(rawValue)
    }

    /// Registry-driven display order. Advanced metrics lead within each category,
    /// followed by traditional production metrics.
    var metricPriorityOrder: [String] {
        FootballMetricRegistry.definitions
            .filter { $0.category == self }
            .sorted { $0.priority < $1.priority }
            .map(\.label)
    }

    /// Returns a comparator for sorting metric labels within this category.
    func sortMetrics(_ a: String, _ b: String) -> Bool {
        let order = metricPriorityOrder
        let ia = order.firstIndex(of: a) ?? order.count
        let ib = order.firstIndex(of: b) ?? order.count
        return ia < ib
    }
}

struct TeamRoute: Hashable {
    let abbr: String
    let players: [Player]
}

extension Player {
    /// Known NFL position groups the snapshot feed assigns.
    private static let knownTypes: Set<String> = ["qb", "rb", "wr", "te", "def"]

    func matchesPlayerType(for category: MetricCategory?) -> Bool {
        guard let category else { return true }
        // Only filter on a recognized position group. An unknown / missing
        // player_type falls through to "include" so we never drop a player who
        // lost their role label upstream but still carries real metrics.
        guard let type = playerType?.lowercased(), Self.knownTypes.contains(type) else { return true }
        switch category {
        case .passing:
            return type == "qb"
        case .rushing:
            return ["qb", "rb", "wr", "te"].contains(type)
        case .receiving:
            return ["rb", "wr", "te"].contains(type)
        case .defense:
            return type == "def"
        }
    }

    /// The category a player leads with - drives Recent Form and single-category
    /// framing. Derived from the position group, falling back to the most common
    /// metric category when the role label is missing.
    var primaryCategory: MetricCategory {
        switch playerType?.lowercased() {
        case "qb": return .passing
        case "rb": return .rushing
        case "wr", "te": return .receiving
        case "def": return .defense
        default:
            let counts = Dictionary(grouping: metrics, by: \.category).mapValues(\.count)
            return counts.max { $0.value < $1.value }?.key ?? .passing
        }
    }

    /// Position to surface in the UI. When the snapshot has no position (TBD /
    /// empty) but the player has metrics, fall back to the player-type label so
    /// we never show "TBD" next to real stats.
    var displayPosition: String {
        let trimmed = position.trimmingCharacters(in: .whitespaces).uppercased()
        if !trimmed.isEmpty && trimmed != "TBD" && trimmed != "\u{2014}" && trimmed != "-" {
            return position
        }
        return playerType?.uppercased() ?? position
    }

    var initials: String {
        let parts = name.split(separator: " ")
        guard let first = parts.first else { return "" }
        guard parts.count > 1 else { return String(first.prefix(1)) }

        let last = parts.last!
        let suffix = last.trimmingCharacters(in: .punctuationCharacters).uppercased()
        let hasSuffix = ["JR", "SR", "II", "III", "IV", "V"].contains(suffix)

        if hasSuffix && parts.count > 2 {
            // Use part before suffix as last name (e.g., "Bobby Witt Jr." → "BW")
            let lastName = parts[parts.count - 2]
            return String(first.prefix(1)) + String(lastName.prefix(1))
        }

        // Standard case: first initial + last initial
        return String(first.prefix(1)) + String(last.prefix(1))
    }
}

struct GameTrend: Identifiable, Codable, Hashable, Sendable {
    let id: String
    let date: Date
    let opponent: String
    let summary: String
    let percentileDelta: Int
    let keyMetric: String

    enum CodingKeys: String, CodingKey {
        case id
        case date
        case opponent
        case summary
        case percentileDelta = "percentile_delta"
        case keyMetric = "key_metric"
    }
}


enum PlayerPositionGroup: String, CaseIterable, Identifiable, Hashable, Sendable {
    case qb = "QB"
    case rb = "RB"
    case wr = "WR"
    case te = "TE"
    case defense = "DEF"

    var id: String { rawValue }

    var cohortDescription: String {
        switch self {
        case .defense: return "Among qualifying defensive players"
        default: return "Among qualifying \(rawValue)s"
        }
    }

    var primaryCategory: MetricCategory {
        switch self {
        case .qb: return .passing
        case .rb: return .rushing
        case .wr, .te: return .receiving
        case .defense: return .defense
        }
    }

    var preferredAdvancedMetrics: [String] {
        switch self {
        case .qb: return ["EPA/Play", "CPOE", "Rating"]
        case .rb: return ["EPA/Rush", "RYOE", "Explosive%", "Rush EPA"]
        case .wr, .te: return ["EPA/Tgt", "WOPR", "YAC+", "Rec EPA"]
        case .defense: return []
        }
    }

    var preferredTraditionalMetrics: [String] {
        switch self {
        case .qb: return ["Pass Yds", "Pass TD", "Cmp%"]
        case .rb: return ["Rush Yds", "Rush TD", "Car"]
        case .wr, .te: return ["Rec Yds", "Rec TD", "Rec"]
        case .defense: return ["Tackles", "Sacks", "Def INT"]
        }
    }
}

enum MetricKind: String, CaseIterable, Identifiable, Hashable, Sendable {
    case advanced = "Advanced"
    case traditional = "Traditional"

    var id: String { rawValue }
}

enum MetricFamily: String, CaseIterable, Identifiable, Hashable, Sendable {
    case efficiency = "Efficiency"
    case accuracy = "Accuracy"
    case pressure = "Pressure"
    case aggressiveness = "Aggressiveness"
    case rushing = "Rushing"
    case expectedProduction = "Expected"
    case explosiveness = "Explosiveness"
    case usage = "Usage"
    case receiving = "Receiving"
    case separation = "Separation"
    case yac = "YAC"
    case production = "Production"
    case passRush = "Pass Rush"
    case turnovers = "Turnovers"

    var id: String { rawValue }
}

struct MetricDefinition: Hashable, Sendable {
    let label: String
    let category: MetricCategory
    let kind: MetricKind
    let family: MetricFamily
    let positions: Set<PlayerPositionGroup>
    let higherIsBetter: Bool
    let priority: Int
    let description: String
}

enum FootballMetricRegistry {
    static let definitions: [MetricDefinition] = [
        definition("EPA/Play", .passing, .advanced, .efficiency, [.qb], 10, "Expected points added per passing play."),
        definition("CPOE", .passing, .advanced, .accuracy, [.qb], 20, "Completion percentage over expectation."),
        definition("INT%", .passing, .advanced, .accuracy, [.qb], 30, "Share of attempts intercepted.", higherIsBetter: false),
        definition("Sack%", .passing, .advanced, .pressure, [.qb], 40, "Share of dropbacks ending in a sack.", higherIsBetter: false),
        definition("Time to Throw", .passing, .advanced, .pressure, [.qb], 50, "Average time from snap to throw.", higherIsBetter: false),
        definition("Aggressiveness", .passing, .advanced, .aggressiveness, [.qb], 60, "Share of throws into tight coverage."),
        definition("Intended Air Yds", .passing, .advanced, .aggressiveness, [.qb], 70, "Average intended depth of target."),
        definition("Pass Yds", .passing, .traditional, .production, [.qb], 110, "Total passing yards."),
        definition("Pass TD", .passing, .traditional, .production, [.qb], 120, "Total passing touchdowns."),
        definition("Cmp%", .passing, .traditional, .accuracy, [.qb], 130, "Completion percentage."),
        definition("Y/A", .passing, .traditional, .efficiency, [.qb], 140, "Passing yards per attempt."),
        definition("Rating", .passing, .traditional, .efficiency, [.qb], 150, "NFL passer rating."),

        definition("EPA/Rush", .rushing, .advanced, .efficiency, [.qb, .rb, .wr, .te], 10, "Expected points added per rushing attempt."),
        definition("RYOE", .rushing, .advanced, .expectedProduction, [.qb, .rb], 20, "Rushing yards over expectation."),
        definition("Explosive%", .rushing, .advanced, .explosiveness, [.qb, .rb, .wr, .te], 30, "Rate of explosive rushing plays."),
        definition("Rush EPA", .rushing, .advanced, .production, [.qb, .rb, .wr, .te], 40, "Total expected points added on rushing plays."),
        definition("Fumble%", .rushing, .advanced, .efficiency, [.qb, .rb, .wr, .te], 50, "Fumbles per rushing opportunity.", higherIsBetter: false),
        definition("Rush Yds", .rushing, .traditional, .production, [.qb, .rb, .wr, .te], 110, "Total rushing yards."),
        definition("Rush TD", .rushing, .traditional, .production, [.qb, .rb, .wr, .te], 120, "Total rushing touchdowns."),
        definition("Y/C", .rushing, .traditional, .efficiency, [.qb, .rb, .wr, .te], 130, "Rushing yards per carry."),
        definition("Rush 1D", .rushing, .traditional, .production, [.qb, .rb, .wr, .te], 140, "Rushing first downs."),

        definition("EPA/Tgt", .receiving, .advanced, .efficiency, [.rb, .wr, .te], 10, "Expected points added per target."),
        definition("WOPR", .receiving, .advanced, .usage, [.rb, .wr, .te], 20, "Weighted opportunity rating from targets and air yards."),
        definition("Target Share", .receiving, .advanced, .usage, [.rb, .wr, .te], 30, "Share of team pass attempts targeting the player."),
        definition("RACR", .receiving, .advanced, .efficiency, [.rb, .wr, .te], 40, "Receiving yards per air yard."),
        definition("Separation", .receiving, .advanced, .separation, [.rb, .wr, .te], 50, "Average separation from the nearest defender."),
        definition("YAC+", .receiving, .advanced, .yac, [.rb, .wr, .te], 60, "Yards after catch over expectation."),
        definition("Rec EPA", .receiving, .advanced, .production, [.rb, .wr, .te], 70, "Total expected points added on receiving plays."),
        definition("Rec", .receiving, .traditional, .production, [.rb, .wr, .te], 110, "Total receptions."),
        definition("Rec Yds", .receiving, .traditional, .production, [.rb, .wr, .te], 120, "Total receiving yards."),
        definition("Rec TD", .receiving, .traditional, .production, [.rb, .wr, .te], 130, "Total receiving touchdowns."),
        definition("YAC", .receiving, .traditional, .yac, [.rb, .wr, .te], 140, "Yards after catch."),
        definition("Catch%", .receiving, .traditional, .efficiency, [.rb, .wr, .te], 150, "Share of targets caught."),

        definition("Tackles", .defense, .traditional, .production, [.defense], 110, "Total tackles."),
        definition("TFL", .defense, .traditional, .production, [.defense], 120, "Tackles for loss."),
        definition("PD", .defense, .traditional, .production, [.defense], 130, "Passes defended."),
        definition("Sacks", .defense, .traditional, .passRush, [.defense], 140, "Total sacks."),
        definition("QB Hits", .defense, .traditional, .passRush, [.defense], 150, "Quarterback hits."),
        definition("INT", .defense, .traditional, .turnovers, [.defense], 160, "Defensive interceptions."),
        definition("FF", .defense, .traditional, .turnovers, [.defense], 170, "Forced fumbles.")
    ]

    static func definition(for label: String, category: MetricCategory) -> MetricDefinition? {
        definitions.first { $0.label == label && $0.category == category }
    }

    static func kind(for metric: Metric) -> MetricKind {
        definition(for: metric.label, category: metric.category)?.kind ?? .advanced
    }

    static func isSupported(_ metric: Metric, by position: PlayerPositionGroup) -> Bool {
        definition(for: metric.label, category: metric.category)?.positions.contains(position) ?? true
    }

    static func sorted(_ metrics: [Metric]) -> [Metric] {
        metrics.sorted { lhs, rhs in
            let left = definition(for: lhs.label, category: lhs.category)?.priority ?? Int.max
            let right = definition(for: rhs.label, category: rhs.category)?.priority ?? Int.max
            if left == right { return lhs.label < rhs.label }
            return left < right
        }
    }

    private static func definition(
        _ label: String,
        _ category: MetricCategory,
        _ kind: MetricKind,
        _ family: MetricFamily,
        _ positions: Set<PlayerPositionGroup>,
        _ priority: Int,
        _ description: String,
        higherIsBetter: Bool = true
    ) -> MetricDefinition {
        MetricDefinition(
            label: label,
            category: category,
            kind: kind,
            family: family,
            positions: positions,
            higherIsBetter: higherIsBetter,
            priority: priority,
            description: description
        )
    }
}

extension Player {
    var positionGroup: PlayerPositionGroup {
        switch playerType?.lowercased() {
        case "qb": return .qb
        case "rb": return .rb
        case "wr": return .wr
        case "te": return .te
        case "def": return .defense
        default:
            let position = displayPosition.uppercased()
            if position == "QB" { return .qb }
            if position == "RB" || position == "FB" { return .rb }
            if position == "WR" { return .wr }
            if position == "TE" { return .te }
            return primaryCategory == .defense ? .defense : .wr
        }
    }

    func metrics(kind: MetricKind) -> [Metric] {
        FootballMetricRegistry.sorted(metrics.filter { FootballMetricRegistry.kind(for: $0) == kind })
    }

    func preferredHeadlineMetric(kind: MetricKind) -> Metric? {
        let candidates = metrics(kind: kind)
        let preferred = kind == .advanced
            ? positionGroup.preferredAdvancedMetrics
            : positionGroup.preferredTraditionalMetrics
        for label in preferred {
            if let metric = candidates.first(where: { $0.label == label }) { return metric }
        }
        return candidates.first
    }
}
