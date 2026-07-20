import Foundation

struct Player: Identifiable, Codable, Hashable, Sendable {
    var id: String { "\(playerId)-\(season ?? 0)" }
    let playerId: Int
    let name: String
    let team: String
    let position: String
    let handedness: String
    let imageURL: URL?
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
        case imageURL = "image_url"
        case updatedAt = "updated_at"
        case season
        case playerType = "player_type"
        case source
        case metrics
        case standardStats = "standard_stats"
        case games
    }

    init(playerId: Int, name: String, team: String, position: String, handedness: String, imageURL: URL?, updatedAt: Date, season: Int? = nil, playerType: String? = nil, source: String? = nil, metrics: [Metric], standardStats: [StandardStat]?, games: [GameTrend]) {
        self.playerId = playerId
        self.name = name
        self.team = team
        self.position = position
        self.handedness = handedness
        self.imageURL = imageURL
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
        imageURL = try container.decodeIfPresent(URL.self, forKey: .imageURL)
        updatedAt = try container.decode(Date.self, forKey: .updatedAt)
        season = try container.decodeIfPresent(Int.self, forKey: .season)
        playerType = try container.decodeIfPresent(String.self, forKey: .playerType)
        source = try container.decodeIfPresent(String.self, forKey: .source)
        metrics = try container.decode([Metric].self, forKey: .metrics)
        standardStats = try container.decodeIfPresent([StandardStat].self, forKey: .standardStats)
        games = try container.decodeIfPresent([GameTrend].self, forKey: .games) ?? []
    }

    var overallPercentile: Int {
        guard !metrics.isEmpty else { return 0 }
        // Players who span more than one category (e.g. a rushing QB with both
        // Passing and Rushing metrics) shouldn't have their headline number
        // diluted by averaging across unrelated skills — take the best category.
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

    /// The preferred display order of metric labels within this category,
    /// leading with the headline counting stats then the advanced Next Gen metrics.
    var metricPriorityOrder: [String] {
        switch self {
        case .passing:
            return ["Pass Yds", "Pass TD", "Cmp%", "Y/A", "Rating", "EPA/Play",
                    "CPOE", "INT%", "Sack%", "Time to Throw", "Aggressiveness", "Intended Air Yds"]
        case .rushing:
            return ["Rush Yds", "Rush TD", "Y/C", "Rush EPA", "Rush 1D",
                    "Explosive%", "RYOE", "Fumble%"]
        case .receiving:
            return ["Rec", "Rec Yds", "Rec TD", "YAC", "Target Share", "WOPR",
                    "RACR", "Rec EPA", "Catch%", "Separation", "YAC+"]
        case .defense:
            return ["Tackles", "Sacks", "INT", "PD", "FF", "TFL", "QB Hits"]
        }
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

    /// The category a player leads with — drives Recent Form and single-category
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
        if !trimmed.isEmpty && trimmed != "TBD" && trimmed != "—" && trimmed != "-" {
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
