import Foundation

protocol PlayerCaching: Sendable {
    func loadPlayers() throws -> [Player]
    func savePlayers(_ players: [Player]) throws
}

struct DiskPlayerCache: PlayerCaching {
    let fileURL: URL
    private let maxAge: TimeInterval?

    /// Pass `nil` for maxAge to disable expiration (permanent cache).
    init(fileManager: FileManager = .default, maxAge: TimeInterval? = 48 * 60 * 60) {
        let directory = fileManager.urls(for: .cachesDirectory, in: .userDomainMask).first ?? fileManager.temporaryDirectory
        self.fileURL = directory.appending(path: "players-cache.json")
        self.maxAge = maxAge
    }

    init(fileURL: URL, maxAge: TimeInterval? = 48 * 60 * 60) {
        self.fileURL = fileURL
        self.maxAge = maxAge
    }

    func loadPlayers() throws -> [Player] {
        let attributes = try FileManager.default.attributesOfItem(atPath: fileURL.path)
        if let maxAge = maxAge,
           let modified = attributes[.modificationDate] as? Date,
           Date().timeIntervalSince(modified) > maxAge {
            throw URLError(.resourceUnavailable)
        }
        let data = try Data(contentsOf: fileURL)
        return try JSONDecoder.statScout.decode([Player].self, from: data)
    }

    func savePlayers(_ players: [Player]) throws {
        let data = try JSONEncoder.statScout.encode(players)
        try FileManager.default.createDirectory(at: fileURL.deletingLastPathComponent(), withIntermediateDirectories: true)
        try data.write(to: fileURL, options: [.atomic])
    }
}

/// Binary-plist-backed cache for the heavyweight historical dataset.
/// Plist decode is ~2-3× faster than JSON on the same payload, and the file is ~30% smaller.
struct PlistPlayerCache: PlayerCaching {
    private let fileURL: URL

    init(fileURL: URL) {
        self.fileURL = fileURL
    }

    func loadPlayers() throws -> [Player] {
        let data = try Data(contentsOf: fileURL)
        return try PropertyListDecoder.statScout.decode([Player].self, from: data)
    }

    func savePlayers(_ players: [Player]) throws {
        let data = try PropertyListEncoder.statScout.encode(players)
        try FileManager.default.createDirectory(at: fileURL.deletingLastPathComponent(), withIntermediateDirectories: true)
        try data.write(to: fileURL, options: [.atomic])
    }
}

/// Two-tier cache: permanent for historical data, expiring for current season.
struct TwoTierPlayerCache: PlayerCaching {
    private let historical: PlistPlayerCache
    private let legacyHistorical: DiskPlayerCache
    private let current: DiskPlayerCache
    private let bundle: Bundle
    private let historicalBundleResourceName: String
    private let currentBundleResourceName: String

    init(
        fileManager: FileManager = .default,
        bundle: Bundle = .main,
        historicalBundleResourceName: String = "players-historical",
        currentBundleResourceName: String = "players-current"
    ) {
        let directory = fileManager.urls(for: .cachesDirectory, in: .userDomainMask).first ?? fileManager.temporaryDirectory
        self.historical = PlistPlayerCache(fileURL: directory.appending(path: "players-historical.plist"))
        self.legacyHistorical = DiskPlayerCache(fileURL: directory.appending(path: "players-historical.json"), maxAge: nil)
        self.current = DiskPlayerCache(fileURL: directory.appending(path: "players-current.json"), maxAge: 48 * 60 * 60)
        self.bundle = bundle
        self.historicalBundleResourceName = historicalBundleResourceName
        self.currentBundleResourceName = currentBundleResourceName
    }

    func loadPlayers() throws -> [Player] {
        let historicalPlayers = loadHistoricalPlayers()
        let currentPlayers = (try? loadCurrentPlayers()) ?? []
        return historicalPlayers + currentPlayers
    }

    func loadCurrentPlayers() throws -> [Player] {
        if let cached = try? current.loadPlayers(), PlayerSnapshotValidator.isCompleteCurrent(cached) {
            return cached
        }
        if let players = loadBundledPlayers(named: currentBundleResourceName),
           PlayerSnapshotValidator.isCompleteCurrent(players) {
            try? current.savePlayers(players)
            return players
        }
        return []
    }

    func loadHistoricalPlayers() -> [Player] {
        let bundled = loadBundledPlayers(named: historicalBundleResourceName)
        let bundledIsComplete = bundled.map(PlayerSnapshotValidator.isCompleteHistorical) ?? false

        // 1. Permanent disk cache, unless the bundled archive has broader coverage.
        if let cached = try? historical.loadPlayers(), !cached.isEmpty {
            if PlayerSnapshotValidator.isCompleteHistorical(cached) || !bundledIsComplete {
                return cached
            }
        }
        // 2. Bundled binary plist (shipped with the app).
        if let bundled, bundledIsComplete {
            try? historical.savePlayers(bundled)
            try? FileManager.default.removeItem(at: legacyHistorical.fileURL)
            return bundled
        }
        // 3. Legacy on-disk JSON cache from older builds - migrate forward.
        if let players = try? legacyHistorical.loadPlayers(), !players.isEmpty {
            try? historical.savePlayers(players)
            try? FileManager.default.removeItem(at: legacyHistorical.fileURL)
            return players
        }
        // 4. Bundled JSON fallback (in case the plist asset is ever missing).
        if let players = loadBundledPlayers(named: historicalBundleResourceName, extension: "json"), !players.isEmpty {
            try? historical.savePlayers(players)
            return players
        }
        return []
    }

    private func loadBundledPlayers(named name: String, extension fileExtension: String = "plist") -> [Player]? {
        guard let url = bundle.url(forResource: name, withExtension: fileExtension),
              let data = try? Data(contentsOf: url) else {
            return nil
        }
        if fileExtension == "plist" {
            return try? PropertyListDecoder.statScout.decode([Player].self, from: data)
        }
        return try? JSONDecoder.statScout.decode([Player].self, from: data)
    }

    func savePlayers(_ players: [Player]) throws {
        let historicalPlayers = players.filter { ($0.season ?? 0) < StatScoutSeason.current }
        let currentPlayers = players.filter { ($0.season ?? 0) >= StatScoutSeason.current }
        if !historicalPlayers.isEmpty {
            try historical.savePlayers(historicalPlayers)
        }
        if !currentPlayers.isEmpty, PlayerSnapshotValidator.isCompleteCurrent(currentPlayers) {
            try current.savePlayers(currentPlayers)
        }
    }
}

enum PlayerSnapshotValidator {
    private static let minimumTeamCount = 30
    private static let requiredTypes: Set<String> = ["qb", "rb", "wr", "te", "def"]

    static func isCompleteHistorical(_ players: [Player]) -> Bool {
        let expectedSeasons = Set(StatScoutSeason.earliest..<StatScoutSeason.current)
        let grouped = Dictionary(grouping: players.filter {
            guard let season = $0.season else { return false }
            return expectedSeasons.contains(season)
        }, by: { $0.season! })

        guard Set(grouped.keys) == expectedSeasons else { return false }
        return grouped.values.allSatisfy { seasonPlayers in
            let teams = Set(seasonPlayers.map { normalizedTeamAbbreviation($0.team) })
            let types = Set(seasonPlayers.compactMap(\.playerType).map { $0.lowercased() })
            return teams.count >= minimumTeamCount
                && requiredTypes.isSubset(of: types)
                && seasonPlayers.allSatisfy { !$0.metrics.isEmpty }
        }
    }

    static func isCompleteCurrent(_ players: [Player]) -> Bool {
        let current = players.filter { $0.season == StatScoutSeason.current }
        let teams = Set(current.map { normalizedTeamAbbreviation($0.team) })
        let types = Set(current.compactMap(\.playerType).map { $0.lowercased() })
        let metricLabels = Set(current.flatMap(\.metrics).map(\.label))
        let requiredMetrics: Set<String> = ["EPA/Play", "EPA/Rush", "EPA/Tgt"]
        return teams.count >= minimumTeamCount
            && requiredTypes.isSubset(of: types)
            && requiredMetrics.isSubset(of: metricLabels)
    }
}

extension JSONEncoder {
    static var statScout: JSONEncoder {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        return encoder
    }
}

extension PropertyListEncoder {
    static var statScout: PropertyListEncoder {
        let encoder = PropertyListEncoder()
        encoder.outputFormat = .binary
        return encoder
    }
}

extension PropertyListDecoder {
    /// Conversion script stores dates as native plist Date values, so default decoding works.
    static var statScout: PropertyListDecoder { PropertyListDecoder() }
}
