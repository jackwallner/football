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
    var selectedSeason: Int = StatScoutSeason.current

    var sortLabel: String { currentSortMetric ?? "Top Metric" }

    var currentSortMetric: String? {
        if let userSortMetric, availableSortMetrics.contains(userSortMetric) {
            return userSortMetric
        }
        return determineSortMetricLabel()
    }

    var availableSortMetrics: [String] {
        var seen = Set<String>()
        return FootballMetricRegistry.sorted(eligibleMetrics)
            .filter { seen.insert($0.label).inserted }
            .map(\.label)
    }

    var availableAdvancedSortMetrics: [String] {
        availableSortMetrics.filter { label in
            eligibleMetrics.contains { metric in
                metric.label == label
                    && FootballMetricRegistry.definition(
                        for: metric.label,
                        category: metric.category
                    )?.kind == .advanced
            }
        }
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
    /// category changes and by picking a new sort metric - but only when the
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

    /// Fetch per-game logs for a single player. Powers the Recent Form card.
    /// The VM is a passthrough so the card stays UI-only and we do not
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

    // Keep the full supported range visible even before historical data loads.
    // Free users can discover older seasons in the menu and see that they are
    // part of StatScout+, rather than seeing a misleading single-year picker.
    var availableSeasons: [Int] {
        var seasons = Set(StatScoutSeason.earliest...StatScoutSeason.current)
        seasons.formUnion(playerHistories.values.flatMap { $0 }.compactMap(\.season))
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

    // MARK: - Recent form

    /// Rolling windows keyed by length, cached per season so flipping between
    /// 3 / 5 / 8 doesn't refetch what's already in hand.
    var recentFormByWindow: [Int: [Int: RecentForm]] = [:]
    var recentFormLoadingWindows: Set<Int> = []
    var recentFormError: String?
    private var recentFormSeason: Int?
    private var recentFormTasks: [Int: Task<Void, Never>] = [:]

    /// The window the Trends board and the trend arrows read from.
    var recentWindow: RecentWindow = .five

    /// True while a board is showing recent form rather than season totals.
    /// Pro-gated at the call site, free users get a blurred teaser.
    var showingRecent = false

    func recentForm(for playerId: Int, window: RecentWindow? = nil) -> RecentForm? {
        recentFormByWindow[(window ?? recentWindow).rawValue]?[playerId]
    }

    var isRecentFormLoading: Bool {
        recentFormLoadingWindows.contains(recentWindow.rawValue)
    }

    /// The last game date covered by the loaded window, for honest labelling.
    var recentFormAsOf: Date? {
        recentFormByWindow[recentWindow.rawValue]?.values.compactMap(\.asOf).max()
    }

    /// The latest week any loaded row reaches, so a board can say "through
    /// Week 18" without every row carrying its own caption.
    var recentFormThroughWeek: Int? {
        recentFormByWindow[recentWindow.rawValue]?.values.compactMap(\.endWeek).max()
    }

    /// Every row for a window, keyed by side of the ball. The Trends board
    /// ranks within one position group, so it needs the rows a per-player
    /// dictionary throws away: a two-way player has one row per player_type.
    func recentFormRows(window: RecentWindow, playerType: String) -> [RecentForm] {
        (recentFormRowsByWindow[window.rawValue] ?? [])
            .filter { $0.playerType == playerType }
    }

    private var recentFormRowsByWindow: [Int: [RecentForm]] = [:]

    func reloadRecentForm(window: RecentWindow? = nil) async {
        let target = window ?? recentWindow
        recentFormTasks[target.rawValue]?.cancel()
        recentFormTasks.removeValue(forKey: target.rawValue)
        recentFormByWindow.removeValue(forKey: target.rawValue)
        recentFormRowsByWindow.removeValue(forKey: target.rawValue)
        recentFormError = nil
        await loadRecentFormIfNeeded(window: target)
    }

    func loadRecentFormIfNeeded(window: RecentWindow? = nil) async {
        let target = window ?? recentWindow
        // Season changed under us, the cache describes a different year.
        if recentFormSeason != selectedSeason {
            for task in recentFormTasks.values { task.cancel() }
            recentFormTasks.removeAll()
            recentFormLoadingWindows.removeAll()
            recentFormByWindow.removeAll()
            recentFormRowsByWindow.removeAll()
            recentFormSeason = selectedSeason
        }
        guard recentFormByWindow[target.rawValue] == nil else { return }

        if let inFlight = recentFormTasks[target.rawValue] {
            await inFlight.value
            return
        }

        recentFormLoadingWindows.insert(target.rawValue)
        recentFormError = nil
        let season = selectedSeason
        let task = Task { @MainActor [weak self] in
            guard let self else { return }
            defer {
                self.recentFormLoadingWindows.remove(target.rawValue)
                self.recentFormTasks.removeValue(forKey: target.rawValue)
            }
            do {
                let rows = try await self.provider.fetchRecentForm(
                    season: season,
                    windowGames: target.rawValue
                )
                guard season == self.selectedSeason else { return }
                var byPlayer: [Int: RecentForm] = [:]
                for row in rows {
                    if let existing = byPlayer[row.playerId],
                       existing.plays >= row.plays { continue }
                    byPlayer[row.playerId] = row
                }
                guard !byPlayer.isEmpty else { return }
                self.recentFormByWindow[target.rawValue] = byPlayer
                self.recentFormRowsByWindow[target.rawValue] = rows
            } catch {
                if !isTaskCancellation(error) {
                    self.recentFormError = "Couldn't load recent form."
                }
            }
        }
        recentFormTasks[target.rawValue] = task
        await task.value
    }

    /// Unique players for an arbitrary season, not just the selected one.
    /// Drill-down leaderboards opened from a player profile need the season
    /// that profile is showing, which can differ from `selectedSeason`.
    func players(forSeason season: Int) -> [Player] {
        let all = playerHistories.values.flatMap { $0 }.filter { $0.season == season }
        var seen = Set<Int>()
        return all.filter { seen.insert($0.playerId).inserted }
    }

    /// Clubs whose name or abbreviation matches the current search.
    ///
    /// Searching used to only ever narrow the list of players. Someone typing
    /// "chiefs" is usually after Kansas City, so the club itself is now a
    /// result: one tap to the team page, with the roster still filtered
    /// underneath if that's what they wanted.
    var searchedTeams: [String] {
        let query = searchText.trimmingCharacters(in: .whitespaces)
        guard !query.isEmpty else { return [] }
        return teamsWithData
            .filter {
                teamFullName($0).localizedCaseInsensitiveContains(query)
                    || $0.localizedCaseInsensitiveContains(query)
            }
            .sorted { teamFullName($0) < teamFullName($1) }
    }

    private var eligibleMetrics: [Metric] {
        seasonPlayers
            .filter { $0.positionGroup == selectedPosition }
            .flatMap(\.metrics)
            .filter { FootballMetricRegistry.isSupported($0, by: selectedPosition) }
    }

    var filteredPlayers: [Player] {
        seasonPlayers.filter { player in
            let matchesSearch = searchText.isEmpty
                || player.name.localizedCaseInsensitiveContains(searchText)
                || player.team.localizedCaseInsensitiveContains(searchText)
                || teamFullName(player.team).localizedCaseInsensitiveContains(searchText)
            let matchesPosition = player.positionGroup == selectedPosition
            let matchingMetrics = player.metrics.filter {
                FootballMetricRegistry.isSupported($0, by: selectedPosition)
            }
            let qualifies = matchingMetrics.contains { isQualified(player, for: $0.category) }
            return matchesSearch && matchesPosition && !matchingMetrics.isEmpty && qualifies
        }
    }

    enum QualifierLevel: String, CaseIterable, Identifiable {
        case all = "All Players"
        case qualified = "Qualified"

        var id: String { rawValue }

        var description: String {
            switch self {
            case .all: return "No playing-time minimum"
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
        }
    }

    var leaderboard: [Player] {
        guard let label = currentSortMetric,
              let referenceMetric = eligibleMetrics.first(where: { $0.label == label }) else {
            return filteredPlayers.sorted { $0.name < $1.name }
        }
        return filteredPlayers.sorted(
            by: Self.metricComparator(
                label: label,
                category: referenceMetric.category,
                descending: sortDescending
            )
        )
    }

    /// Rank by the backend's direction-correct percentile, then use the raw
    /// number only to break a tied percentile bucket. Percentile-only metrics
    /// remain rankable instead of being swept below every printable value.
    static func metricComparator(
        label: String,
        category: MetricCategory,
        descending: Bool
    ) -> (Player, Player) -> Bool {
        let percentileDescending = descending != lowerIsBetter(
            label: label,
            category: category
        )
        return { first, second in
            let firstMetric = first.metrics.first {
                $0.label == label && $0.category == category
            }
            let secondMetric = second.metrics.first {
                $0.label == label && $0.category == category
            }

            switch (firstMetric, secondMetric) {
            case (nil, nil):
                return first.name < second.name
            case (nil, _):
                return false
            case (_, nil):
                return true
            default:
                break
            }

            guard let firstMetric, let secondMetric else { return false }
            if firstMetric.percentile != secondMetric.percentile {
                return percentileDescending
                    ? firstMetric.percentile > secondMetric.percentile
                    : firstMetric.percentile < secondMetric.percentile
            }
            if let firstValue = rawNumeric(firstMetric.value),
               let secondValue = rawNumeric(secondMetric.value),
               firstValue != secondValue {
                return descending
                    ? firstValue > secondValue
                    : firstValue < secondValue
            }
            return first.name < second.name
        }
    }

    /// Parse a leading numeric value from a metric's display string.
    /// Handles ".345", "8.2%", "98.5 mph", "28.5 ft/s", "25.3°", "-1.2".
    static func rawNumeric(_ value: String) -> Double? {
        var s = value.trimmingCharacters(in: .whitespaces)
        // Strip thousands separators - NFL yardage ships as "3,322".
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

    /// Default sort direction for a metric - descending (highest first) unless
    /// the metric reads better when lower. Used to keep "best player first" as
    /// the initial ordering even after switching to raw-value sorting.
    static func defaultSortDescending(label: String?, category: MetricCategory?) -> Bool {
        guard let label, let category else { return true }
        return !lowerIsBetter(label: label, category: category)
    }

    private func determineSortMetricLabel() -> String? {
        let preferred = selectedPosition.preferredAdvancedMetrics + selectedPosition.preferredTraditionalMetrics
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
            // Rank Best/Worst by Gridiron percentile, NOT by parsing the value
            // string. Roughly half of xISO / xOBP / Hard-Hit% (and 100% of
            // Arm Strength / Squared-Up%) ship a valid percentile but a blank
            // value; rawNumeric("") collapsed them all to 0, every player tied,
            // and the sort returned the same player (e.g. Ohtani) for both
            // ends with empty cells. Percentile is Gridiron's normalized
            // goodness - already direction-correct (it inverts for pitchers),
            // so highest = best, lowest = worst with no per-metric polarity
            // table needed.
            let byPercentile = data.values.sorted { $0.percentile < $1.percentile }
            guard let best = byPercentile.last else { return nil }
            let worst = byPercentile.first
            // Single qualifier (or every qualifier tied): the same player can't
            // be both Best and Worst - drop the duplicate so the row reads
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
            let hasCompleteFallback = PlayerSnapshotValidator.isCompleteCurrent(fallbackPlayers)
            let acceptedCurrent = PlayerSnapshotValidator.isCompleteCurrent(current) || !hasCompleteFallback
                ? current
                : []
            let allPlayers = acceptedCurrent.isEmpty ? fallbackPlayers : mergePlayers(replacing: acceptedCurrent)

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
            if !acceptedCurrent.isEmpty {
                try? cache?.savePlayers(acceptedCurrent)
            } else if !current.isEmpty {
                errorMessage = "Showing complete saved data while the live feed finishes updating."
                lastFetchFailed = true
            }

        } catch is DecodingError {
            errorMessage = "Data format changed - app may need an update."
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
