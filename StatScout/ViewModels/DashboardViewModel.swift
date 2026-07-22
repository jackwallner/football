import Foundation
import Observation

@MainActor
@Observable
final class DashboardViewModel {
    private let provider: StatcastProviding
    private let cache: PlayerCaching?

    var players: [Player] = []
    var playerHistories: [Int: [Player]] = [:]
    var searchText = ""
    var selectedPosition: PlayerPositionGroup = .qb {
        didSet {
            guard oldValue != selectedPosition else { return }
            selectedMetricKind = selectedPosition == .defense ? .traditional : .advanced
            selectedFamily = nil
            userSortMetric = nil
            applyDefaultSortDirection()
        }
    }
    var selectedMetricKind: MetricKind = .advanced {
        didSet {
            guard oldValue != selectedMetricKind else { return }
            selectedFamily = nil
            userSortMetric = nil
            applyDefaultSortDirection()
        }
    }
    var selectedFamily: MetricFamily? {
        didSet {
            guard oldValue != selectedFamily else { return }
            userSortMetric = nil
            applyDefaultSortDirection()
        }
    }
    // Compatibility bridge for callers that still speak in wire-format categories.
    var selectedCategory: MetricCategory? {
        get { selectedPosition.primaryCategory }
        set {
            switch newValue {
            case .passing: selectedPosition = .qb
            case .rushing: selectedPosition = .rb
            case .receiving: selectedPosition = .wr
            case .defense: selectedPosition = .defense
            case nil: break
            }
        }
    }

    private var userSortMetric: String?
    var sortDescending = true
    var selectedSeason: Int = Calendar.current.component(.year, from: Date())

    var sortLabel: String { currentSortMetric ?? "Top Metric" }

    var currentSortMetric: String? {
        if let userSortMetric, availableSortMetrics.contains(userSortMetric) {
            return userSortMetric
        }
        return determineSortMetricLabel()
    }

    var availableFamilies: [MetricFamily] {
        let present = Set(eligibleMetrics.compactMap {
            FootballMetricRegistry.definition(for: $0.label, category: $0.category)?.family
        })
        return MetricFamily.allCases.filter(present.contains)
    }

    var availableSortMetrics: [String] {
        var seen = Set<String>()
        return FootballMetricRegistry.sorted(eligibleMetrics)
            .filter { seen.insert($0.label).inserted }
            .map(\.label)
    }

    func setUserSortMetric(_ label: String?) {
        userSortMetric = label
        applyDefaultSortDirection()
    }

    /// Call when the user explicitly flips direction (header tap / menu item)
    /// so the auto-default doesn't stomp their preference until they change
    /// the active metric or category.
    func toggleSortDirection() {
        sortDescending.toggle()
    }

    /// Reset direction to "best first" for the active metric. Triggered by
    /// category changes and by picking a new sort metric — but only when the
    /// user hasn't manually pinned a direction in this session.
    private func applyDefaultSortDirection() {
        guard let label = currentSortMetric,
              let metric = eligibleMetrics.first(where: { $0.label == label }) else {
            sortDescending = true
            return
        }
        sortDescending = FootballMetricRegistry.definition(for: label, category: metric.category)?.higherIsBetter ?? true
    }
    // Mirrors StoreService.isPro. Set by the view layer so season gating and
    // selectedSeason clamping stay consistent without the VM depending on the store.
    var isPro: Bool = false

    func isSeasonLocked(_ season: Int) -> Bool {
        !isPro && season != StatScoutSeason.free
    }

    /// Push Pro state in from the view and re-clamp the selected season so a free
    /// user can never land on (and silently render) a locked past season.
    func applyProState(_ pro: Bool) {
        isPro = pro
        clampSelectedSeason()
    }

    private func clampSelectedSeason() {
        guard isSeasonLocked(selectedSeason) else { return }
        let target = availableSeasons.first(where: { !isSeasonLocked($0) }) ?? StatScoutSeason.free
        if selectedSeason != target { selectedSeason = target }
    }

    // Start true so the very first frame shows a spinner, not a "No data for 2026" empty state
    // before saved players or the network feed resolves.
    var isLoading = true
    var isHistoricalLoading = false
    var hasLoadedHistorical = false
    var loadingMessage = "Starting up…"
    var loadingProgress = 0.05
    var errorMessage: String?
    var lastFetchFailed = false
    private var hasStartedLoading = false

    var isReady: Bool { !players.isEmpty }

    private var _teamScores: [String: Double] = [:]
    private var _teamsWithData: [String] = []
    private var _teamCacheSeason: Int?

    var teamScores: [String: Double] {
        if _teamCacheSeason != selectedSeason { recomputeTeamCache() }
        return _teamScores
    }

    var teamsWithData: [String] {
        if _teamCacheSeason != selectedSeason { recomputeTeamCache() }
        return _teamsWithData
    }

    var teamCounts: [String: Int] {
        Dictionary(grouping: seasonPlayers) { normalizedTeamAbbreviation($0.team) }
            .mapValues(\.count)
    }

    var lastUpdated: Date? {
        players.map(\.updatedAt).max()
    }

    var freshnessText: String? {
        guard let lastUpdated else { return nil }
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return "Updated \(formatter.string(from: lastUpdated))"
    }

    /// Fetch per-game logs for a single player. Powers the Recent Form card —
    /// the VM is just a passthrough so the card can stay UI-only and we don't
    /// have to thread the provider through every PlayerProfileView caller.
    func fetchGameLogs(playerId: Int, season: Int) async throws -> [PlayerGameLog] {
        try await provider.fetchGameLogs(playerId: playerId, season: season)
    }

    /// Team-scoped game logs since `sinceDate`. The TeamRankingsCard caps at 30
    /// days so we don't pull the whole season for an aggregate we only ever
    /// slice into 7/15/30 day windows.
    func fetchTeamGameLogs(team: String, season: Int, sinceDate: Date) async throws -> [PlayerGameLog] {
        try await provider.fetchTeamGameLogs(team: team, season: season, sinceDate: sinceDate)
    }

    init(provider: StatcastProviding, cache: PlayerCaching? = nil) {
        self.provider = provider
        self.cache = cache
    }

    #if DEBUG
    convenience init() {
        self.init(provider: PreviewStatcastAPI())
    }
    #endif

    // Seasons present in fetched data, descending. Falls back to the current year while loading.
    var availableSeasons: [Int] {
        let seasons = Set(playerHistories.values.flatMap { $0 }.compactMap(\.season))
        guard !seasons.isEmpty else {
            return [Calendar.current.component(.year, from: Date())]
        }
        return seasons.sorted(by: >)
    }

    // Players filtered by selected season - pull from histories to get all years.
    // Returns empty when the selected season has no data so callers render an empty state
    // instead of falling back to a stale "latest snapshot" set.
    var seasonPlayers: [Player] {
        let allSeasonPlayers = playerHistories.values.flatMap { $0 }.filter { $0.season == selectedSeason }
        var seenIds = Set<Int>()
        return allSeasonPlayers.filter { seenIds.insert($0.playerId).inserted }
    }

    private var eligibleMetrics: [Metric] {
        seasonPlayers
            .filter { $0.positionGroup == selectedPosition }
            .flatMap(\.metrics)
            .filter { metric in
                guard FootballMetricRegistry.kind(for: metric) == selectedMetricKind,
                      FootballMetricRegistry.isSupported(metric, by: selectedPosition) else { return false }
                guard let selectedFamily else { return true }
                return FootballMetricRegistry.definition(for: metric.label, category: metric.category)?.family == selectedFamily
            }
    }

    var filteredPlayers: [Player] {
        seasonPlayers.filter { player in
            let matchesSearch = searchText.isEmpty
                || player.name.localizedCaseInsensitiveContains(searchText)
                || player.team.localizedCaseInsensitiveContains(searchText)
                || teamFullName(player.team).localizedCaseInsensitiveContains(searchText)
            let matchesPosition = player.positionGroup == selectedPosition
            let matchingMetrics = player.metrics.filter { metric in
                guard FootballMetricRegistry.kind(for: metric) == selectedMetricKind,
                      FootballMetricRegistry.isSupported(metric, by: selectedPosition) else { return false }
                guard let selectedFamily else { return true }
                return FootballMetricRegistry.definition(for: metric.label, category: metric.category)?.family == selectedFamily
            }
            let qualifies = matchingMetrics.contains { isQualified(player, for: $0.category) }
            return matchesSearch && matchesPosition && !matchingMetrics.isEmpty && qualifies
        }
    }

    enum QualifierLevel: String, CaseIterable, Identifiable {
        case all = "All"
        case any = "Played"
        case qualified = "Qualified"

        var id: String { rawValue }

        var minGames: Int {
            switch self {
            case .all: return 0
            case .any: return 1
            case .qualified: return 4
            }
        }

        /// Short caption explaining the active threshold. Shown next to the picker
        /// so "All" and "Played" don't look like synonyms.
        var description: String {
            switch self {
            case .all: return "No minimum"
            case .any: return "1+ game"
            case .qualified: return "Next Gen qualifier"
            }
        }
    }

    var qualifierLevel: QualifierLevel = .qualified

    func isQualified(_ player: Player, for category: MetricCategory?) -> Bool {
        switch qualifierLevel {
        case .all:
            return true
        case .qualified:
            // The pipeline only assigns percentiles to players who meet the
            // per-category qualification thresholds, so "has a percentile in this
            // category" is the authoritative signal.
            if let category {
                return player.metrics.contains { $0.category == category }
            }
            return !player.metrics.isEmpty
        case .any:
            return gamesValue(in: player.standardStats ?? []) >= qualifierLevel.minGames
        }
    }

    private func gamesValue(in stats: [StandardStat]) -> Int {
        guard let stat = stats.first(where: { $0.label.uppercased() == "G" }) else { return 0 }
        return Int(stat.value.filter { $0.isNumber }) ?? 0
    }

    // Sort by the raw stat value, not the percentile — percentile-sorting
    // produced ties (two players at 95) and made the "PCTL" header look
    // disconnected from the xwOBA values shown per row. Players missing the
    // exact metric are partitioned to the end so blank-value rows never
    // interleave above genuinely-ranked players.
    var leaderboard: [Player] {
        guard let label = currentSortMetric,
              let referenceMetric = eligibleMetrics.first(where: { $0.label == label }) else {
            return filteredPlayers.sorted { $0.name < $1.name }
        }
        let category = referenceMetric.category

        func rawValue(_ p: Player) -> Double? {
            guard let m = p.metrics.first(where: { $0.label == label && $0.category == category }) else { return nil }
            return Self.rawNumeric(m.value)
        }

        let ranked = filteredPlayers.filter { rawValue($0) != nil }
            .sorted { p1, p2 in
                let v1 = rawValue(p1) ?? 0
                let v2 = rawValue(p2) ?? 0
                return sortDescending ? v1 > v2 : v1 < v2
            }
        let tail = filteredPlayers.filter { rawValue($0) == nil }
            .sorted { (p1, p2) in
                (p1.percentile(for: category) ?? 0) > (p2.percentile(for: category) ?? 0)
            }
        return ranked + tail
    }

    /// Parse a leading numeric value from a metric's display string.
    /// Handles ".345", "8.2%", "98.5 mph", "28.5 ft/s", "25.3°", "-1.2".
    static func rawNumeric(_ value: String) -> Double? {
        var s = value.trimmingCharacters(in: .whitespaces)
        // Strip thousands separators — NFL yardage ships as "3,322".
        s = s.replacingOccurrences(of: ",", with: "")
        if s.hasPrefix(".") { s = "0" + s }
        if s.hasPrefix("-.") { s = "-0" + s.dropFirst() }
        let scanner = Scanner(string: s)
        scanner.charactersToBeSkipped = nil
        return scanner.scanDouble()
    }

    static func lowerIsBetter(label: String, category: MetricCategory) -> Bool {
        guard let definition = FootballMetricRegistry.definition(for: label, category: category) else { return false }
        return !definition.higherIsBetter
    }

    /// Default sort direction for a metric — descending (highest first) unless
    /// the metric reads better when lower. Used to keep "best player first" as
    /// the initial ordering even after switching to raw-value sorting.
    static func defaultSortDescending(label: String?, category: MetricCategory?) -> Bool {
        guard let label, let category else { return true }
        return !lowerIsBetter(label: label, category: category)
    }

    private func determineSortMetricLabel() -> String? {
        let preferred = selectedMetricKind == .advanced
            ? selectedPosition.preferredAdvancedMetrics
            : selectedPosition.preferredTraditionalMetrics
        for label in preferred where eligibleMetrics.contains(where: { $0.label == label }) {
            return label
        }
        return availableSortMetrics.first
    }

    // Expose the current sort metric for row display. When no category is
    // active the leaderboard sorts by raw xwOBA; surface that label (with
    // nil category) so LeaderboardTableRow matches by label alone and shows
    // each player's xwOBA value instead of a percentile fallback.
    var currentSortMetricForDisplay: (label: String?, category: MetricCategory?) {
        guard let label = currentSortMetric,
              let metric = eligibleMetrics.first(where: { $0.label == label }) else {
            return (nil, nil)
        }
        return (label, metric.category)
    }

    func players(forTeam team: String) -> [Player] {
        // Sort the roster by overall percentile so the standout players surface
        // first regardless of position group.
        let normalized = normalizedTeamAbbreviation(team)
        return seasonPlayers.filter { normalizedTeamAbbreviation($0.team) == normalized }
            .sorted { $0.overallPercentile > $1.overallPercentile }
    }

    func teamScore(_ abbr: String) -> Double {
        _teamScores[normalizedTeamAbbreviation(abbr)] ?? 0
    }

    /// Players who meet the active qualifier for at least one category they appear in.
    /// Used to filter the StatScout leaders and Box Score so unqualified samples don't pollute results.
    var qualifiedSeasonPlayers: [Player] {
        seasonPlayers.filter { player in
            let categories = Set(player.metrics.map(\.category))
            if categories.isEmpty { return isQualified(player, for: nil) }
            return categories.contains { isQualified(player, for: $0) }
        }
    }

    var allMetrics: [(label: String, category: MetricCategory, best: (player: Player, percentile: Int, actualValue: String)?, worst: (player: Player, percentile: Int, actualValue: String)?)] {
        var metricMap: [String: (category: MetricCategory, values: [(player: Player, percentile: Int, actualValue: String)])] = [:]
        for player in seasonPlayers {
            for metric in player.metrics {
                guard isQualified(player, for: metric.category) else { continue }
                let compositeKey = "\(metric.label)|\(metric.category.rawValue)"
                if metricMap[compositeKey] == nil {
                    metricMap[compositeKey] = (category: metric.category, values: [])
                }
                metricMap[compositeKey]?.values.append((player: player, percentile: metric.percentile, actualValue: metric.value))
            }
        }
        return metricMap.compactMap { (key, data) -> MetricLeaderEntry? in
            let label = key.split(separator: "|").first.map(String.init) ?? key
            // Rank Best/Worst by Savant percentile, NOT by parsing the value
            // string. Roughly half of xISO / xOBP / Hard-Hit% (and 100% of
            // Arm Strength / Squared-Up%) ship a valid percentile but a blank
            // value; rawNumeric("") collapsed them all to 0, every player tied,
            // and the sort returned the same player (e.g. Ohtani) for both
            // ends with empty cells. Percentile is Savant's normalized
            // goodness — already direction-correct (it inverts for pitchers),
            // so highest = best, lowest = worst with no per-metric polarity
            // table needed.
            let byPercentile = data.values.sorted { $0.percentile < $1.percentile }
            guard let best = byPercentile.last else { return nil }
            let worst = byPercentile.first
            // Single qualifier (or every qualifier tied): the same player can't
            // be both Best and Worst — drop the duplicate so the row reads
            // "Best: X / Only qualifier" instead of "X is also the worst".
            let dedupedWorst = (worst?.player.id == best.player.id) ? nil : worst
            return (
                label: label,
                category: data.category,
                best: best,
                worst: dedupedWorst
            )
        }.sorted { $0.label < $1.label }
    }

    func loadIfNeeded() async {
        guard !hasStartedLoading else { return }
        hasStartedLoading = true
        await load()
    }

    func load() async {
        hasStartedLoading = true
        isLoading = players.isEmpty
        loadingMessage = players.isEmpty ? "Loading saved players…" : "Refreshing player data…"
        loadingProgress = players.isEmpty ? 0.12 : 0.2

        let cached: [Player] = await Task.detached { [cache] in
            if let cache = cache as? TwoTierPlayerCache {
                return (try? cache.loadCurrentPlayers()) ?? []
            }
            return (try? cache?.loadPlayers()) ?? []
        }.value

        if players.isEmpty, !cached.isEmpty {
            ingestPlayers(cached)
        }

        loadingMessage = "Checking for updates…"
        loadingProgress = 0.45
        isLoading = players.isEmpty
        errorMessage = nil
        lastFetchFailed = false

        do {
            let current = try await provider.fetchCurrentPlayers()
            let fallbackPlayers = cached.isEmpty ? playerHistories.values.flatMap { $0 } : cached
            let allPlayers = current.isEmpty ? fallbackPlayers : mergePlayers(replacing: current)

            guard !allPlayers.isEmpty else {
                // No current data (offseason / cold cache / offline). Fall back to
                // bundled historical so the app is usable instead of trapped on an
                // empty state; season gating still applies via isSeasonLocked.
                let historicalFallback: [Player] = await Task.detached { [cache] in
                    if let cache = cache as? TwoTierPlayerCache {
                        return cache.loadHistoricalPlayers()
                    }
                    return (try? cache?.loadPlayers()) ?? []
                }.value
                if !historicalFallback.isEmpty {
                    ingestPlayers(historicalFallback)
                } else {
                    errorMessage = "No players found."
                    lastFetchFailed = true
                }
                isLoading = false
                loadingProgress = 1
                return
            }

            loadingMessage = "Preparing leaderboard…"
            loadingProgress = 0.85
            ingestPlayers(allPlayers)
            try? cache?.savePlayers(current)

        } catch is DecodingError {
            errorMessage = "Data format changed — app may need an update."
            lastFetchFailed = true
        } catch _ as URLError {
            errorMessage = players.isEmpty ? "Can't reach data feed. Check your connection." : "Showing saved data. Pull to refresh when your connection improves."
            lastFetchFailed = true
        } catch {
            errorMessage = players.isEmpty ? "Something went wrong loading player data." : "Showing saved data. Pull to refresh to try again."
            lastFetchFailed = true
        }
        isLoading = false
        loadingProgress = 1
    }

    func loadHistoricalIfNeeded() async {
        guard !hasLoadedHistorical, !isHistoricalLoading else { return }
        isHistoricalLoading = true
        loadingMessage = "Loading past seasons…"
        loadingProgress = 0.12

        let historical: [Player] = await Task.detached { [cache] in
            if let cache = cache as? TwoTierPlayerCache {
                return cache.loadHistoricalPlayers()
            }
            return ((try? cache?.loadPlayers()) ?? []).filter { ($0.season ?? 0) < StatScoutSeason.current }
        }.value

        loadingMessage = "Preparing season history…"
        loadingProgress = 0.78

        if !historical.isEmpty {
            ingestPlayers(mergePlayers(replacing: historical))
            hasLoadedHistorical = true
        }

        isHistoricalLoading = false
        loadingProgress = 1
    }

    private func ingestPlayers(_ players: [Player]) {
        let grouped = Dictionary(grouping: players, by: \.playerId)
        var latestPlayers: [Player] = []
        var histories: [Int: [Player]] = [:]

        for (playerId, history) in grouped {
            let sortedHistory = history.sorted {
                guard let s1 = $0.season, let s2 = $1.season else {
                    if $0.season == nil && $1.season == nil { return false }
                    return $0.season != nil
                }
                return s1 > s2
            }
            histories[playerId] = sortedHistory
            if let latest = sortedHistory.first {
                latestPlayers.append(latest)
            }
        }

        self.playerHistories = histories
        self.players = latestPlayers

        let seasonsWithData = Set(histories.values.flatMap { $0 }.compactMap(\.season))
        if !seasonsWithData.contains(selectedSeason),
           let mostRecentUnlocked = seasonsWithData.sorted(by: >).first(where: { !isSeasonLocked($0) }) {
            // Only auto-jump to a season the user can actually view; a free user
            // with no current-season data stays on the free season (honest empty
            // state) rather than silently rendering locked past-season data.
            selectedSeason = mostRecentUnlocked
        }

        recomputeTeamCache()
    }

    private func recomputeTeamCache() {
        let allSeasonPlayers = playerHistories.values.flatMap { $0 }.filter { $0.season == selectedSeason }
        var seenIds = Set<Int>()
        let uniquePlayers = allSeasonPlayers.filter { seenIds.insert($0.playerId).inserted }

        var teams = Set<String>()
        var teamScoresAccum: [String: (sum: Int, count: Int)] = [:]

        for player in uniquePlayers {
            let abbr = normalizedTeamAbbreviation(player.team)
            teams.insert(abbr)
            let score = player.overallPercentile
            if score > 0 {
                var entry = teamScoresAccum[abbr] ?? (0, 0)
                entry.sum += score
                entry.count += 1
                teamScoresAccum[abbr] = entry
            }
        }

        _teamsWithData = teams.sorted()
        _teamScores = teamScoresAccum.mapValues { Double($0.sum) / Double($0.count) }
        _teamCacheSeason = selectedSeason
    }

    private func mergePlayers(replacing replacements: [Player]) -> [Player] {
        var merged: [String: Player] = [:]
        for player in playerHistories.values.flatMap({ $0 }) {
            merged[player.id] = player
        }
        for player in replacements {
            merged[player.id] = player
        }
        return Array(merged.values)
    }
}
