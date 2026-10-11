import SwiftUI

/// Team "percentile rankings" card - the team-level analogue of the player
/// profile's percentile card. Aggregates the roster into one synthetic
/// "team-as-a-player" and renders its profile as percentile bars on the league
/// ruler, with a Season / Recent toggle that mirrors the player page:
///
/// - **Season**: PA/IP-weighted mean of every roster metric for the active side,
///   placed on the league curve. Tapping a bar opens that metric's leaderboard.
/// - **Recent**: the same aggregation over the last 7 / 15 / 30 days of game
///   logs. Pro-gated with the standard blur + CTA, identical to the player card.
///
/// This replaces the old split between a season "team average" card and a
/// separate "team recent form" card, which read as two disconnected modules.
struct TeamRankingsCard: View {
    @EnvironmentObject private var store: StoreService
    let team: String
    let season: Int
    /// Which half of the year the roster's numbers come from. The rolling window
    /// is built from game logs, and those have to be filtered to the same phase
    /// the season bars are showing - a playoff club's five most recent games are
    /// its playoff games, so an unfiltered window put January football under a
    /// "Regular Season" heading.
    var seasonPhase: SeasonPhase = .regular
    /// The roster for this team/season.
    let players: [Player]
    /// League pool used to build the value→percentile curve so the team bar sits
    /// on the same ruler as individual players' bars. Filtered per side at
    /// curve-build time.
    let leaguePlayers: [Player]
    /// (team, season, phase, since).
    let fetchTeamGameLogs: ((String, Int, SeasonPhase, Date) async throws -> [PlayerGameLog])?
    /// Changes after a validated publisher revision, so a retained team page
    /// cannot keep showing logs from the previous game set.
    var freshnessRevision: String? = nil
    var freshnessStatus: DataFreshnessStatus? = nil
    /// False on a historical season: rolling windows are built from per-game
    /// logs, and those are only kept for the live season now, so Recent/Both
    /// are hidden rather than offered and left to come back empty.
    var supportsRecent: Bool = true
    let onUpgradeTap: () -> Void

    @State private var side: Side = .offense
    @State private var mode: Mode = .season
    @State private var windowGames: Int = 5
    @State private var logs: [PlayerGameLog] = []
    @State private var loading = false
    @State private var loadError: String?
    @State private var offenseTable: TeamLeagueTable?
    @State private var defenseTable: TeamLeagueTable?

    enum Side: String, CaseIterable, Identifiable {
        case offense, defense
        var id: String { rawValue }
        var label: String { self == .offense ? "Offense" : "Defense" }
        /// Metric categories this side aggregates.
        var categories: [MetricCategory] {
            self == .offense ? [.passing, .rushing, .receiving] : [.defense]
        }
        /// True when a game-log row (player_type ∈ qb/rb/wr/te/def) belongs to this side.
        func includes(playerType: String) -> Bool {
            self == .defense ? playerType.lowercased() == "def" : playerType.lowercased() != "def"
        }
    }

    enum Mode: String, CaseIterable, Identifiable {
        case season = "Season", recent = "Recent", both = "Both"
        var id: String { rawValue }
        var usesRecent: Bool { self != .season }
    }

    /// The mode actually rendered. `mode` is view state and the season is a
    /// parameter, so a user who picked Recent on the live season and then walked
    /// back to 2018 would otherwise sit in front of a permanently empty window.
    private var effectiveMode: Mode { supportsRecent ? mode : .season }

    /// Smallest team-window play count we'll treat as trustworthy - below this we
    /// flag the window as a small sample.
    private let smallSamplePlaysThreshold = 40

    private var table: TeamLeagueTable? {
        side == .defense ? defenseTable : offenseTable
    }

    var body: some View {
        VStack(spacing: 0) {
            GridironSectionBar(
                title: "TEAM ADVANCED STATS",
                trailing: store.isPro ? nil : AnyView(proBadge)
            )

            GridironPickerRow {
                sidePicker.segmentCount(Side.allCases.count)
                modePicker.segmentCount(Mode.allCases.count)
            }
            .padding(.horizontal, GridironGeo.padInline)
            .padding(.vertical, 8)
            .background(GridironPalette.surfaceAlt)

            if effectiveMode.usesRecent {
                windowPicker
            }

            switch effectiveMode {
            case .season: seasonBars
            case .recent: recentSection
            case .both: bothSection
            }
        }
        .background(GridironPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
        .task(id: "\(team)-\(season)-\(seasonPhase.rawValue)-\(effectiveMode.rawValue)-\(store.isPro)-\(freshnessRevision ?? "none")") {
            if effectiveMode.usesRecent, store.isPro { await load() }
        }
        .onAppear { rebuildCurves() }
        .onChange(of: leaguePlayers.count) { _, _ in rebuildCurves() }
    }

    private var proBadge: some View {
        HStack(spacing: 3) {
            Image(systemName: "crown.fill")
                .font(.system(size: 9, weight: .bold))
            Text("STATSCOUT+")
                .font(GridironType.micro)
                .fontWeight(.bold)
        }
        .foregroundStyle(GridironPalette.midnight)
        .padding(.horizontal, 7)
        .padding(.vertical, 3)
        .background(Color.yellow)
        .clipShape(Capsule())
    }

    // MARK: - Pickers

    private var sidePicker: some View {
        GridironSegmented(
            segments: Side.allCases.map { .init(value: $0, label: $0.label) },
            selection: $side
        )
    }

    @ViewBuilder
    private var modePicker: some View {
        if supportsRecent {
            GridironSegmented(
                segments: Mode.allCases.map {
                    .init(
                        value: $0,
                        label: $0.rawValue,
                        isLocked: !store.isPro && $0 != .season
                    )
                },
                selection: $mode,
                onLockedTap: { _ in onUpgradeTap() }
            )
        }
    }

    private var bothSection: some View {
        let seasonRows = aggregateSeasonRows()
        let recentRows: [String: Metric] = {
            guard let window = recentWindow else { return [:] }
            let pairs = seasonRows.compactMap { row -> (String, Metric)? in
                guard let recent = recentMetric(
                    forSeasonLabel: TeamLeagueTable.baseLabel(row.label),
                    window: window
                ) else { return nil }
                return (row.label, recent)
            }
            return Dictionary(pairs, uniquingKeysWith: { first, _ in first })
        }()

        return VStack(spacing: 0) {
            if loading {
                loadingRow
            } else if let loadError {
                InlineLoadError(message: loadError) { await load() }
            } else {
                ForEach(Array(seasonRows.enumerated()), id: \.element.id) { index, metric in
                    DualMetricBar(
                        season: metric,
                        recent: recentRows[metric.label],
                        recentCaption: "Last \(windowGames)G"
                    )
                    .padding(.horizontal, GridironGeo.padCard)
                    .padding(.vertical, 10)
                    .background(index % 2 == 0 ? GridironPalette.surface : GridironPalette.surfaceAlt)
                    .overlay(
                        Rectangle().fill(GridironPalette.divider).frame(height: GridironGeo.hairline),
                        alignment: .bottom
                    )
                }
            }
        }
    }

    private var loadingRow: some View {
        HStack(spacing: 10) {
            ProgressView().scaleEffect(0.75)
            Text("Loading recent games…")
                .font(GridironType.small)
                .foregroundStyle(GridironPalette.inkSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 24)
    }

    private var windowPicker: some View {
        GridironSegmented(
            segments: RecentWindow.allCases.map { .init(value: $0, label: $0.segmentLabel) },
            selection: Binding(
                get: { RecentWindow(rawValue: windowGames) ?? .three },
                set: { windowGames = $0.rawValue }
            )
        )
        .padding(.horizontal, GridironGeo.padInline)
        .padding(.bottom, 8)
        .background(GridironPalette.surfaceAlt)
    }

    // MARK: - Season

    @ViewBuilder
    private var seasonBars: some View {
        let rows = aggregateSeasonRows()
        if rows.isEmpty {
            emptyAggregate
        } else {
            GridironSubSectionBar(title: side.label.uppercased())

            VStack(spacing: 0) {
                ForEach(Array(rows.enumerated()), id: \.element.id) { index, metric in
                    NavigationLink(value: MetricRoute(label: TeamLeagueTable.baseLabel(metric.label), category: metric.category)) {
                        MetricBar(metric: metric)
                            .padding(.horizontal, GridironGeo.padCard)
                            .padding(.vertical, 12)
                            .background(index % 2 == 0 ? GridironPalette.surface : GridironPalette.surfaceAlt)
                            .overlay(
                                Rectangle().fill(GridironPalette.divider).frame(height: GridironGeo.hairline),
                                alignment: .bottom
                            )
                    }
                    .buttonStyle(.plain)
                    .accessibilityHint("See the league leaderboard for \(metric.label)")
                }
            }

            weightedCaption
        }
    }

    /// One bar per roster metric: the club's pooled value (totals per game,
    /// rates weighted by volume) ranked against the other 31 clubs. Each bar
    /// keeps its own metric category (Passing / Rushing / Receiving) so the
    /// leaderboard link routes correctly.
    ///
    /// It used to average the roster's raw values and place that on the
    /// individual-player curve, so a club's "Pass Yds" was its QBs' mean and
    /// "Rush TD 0.2" sat next to a 44th percentile.
    private func aggregateSeasonRows() -> [Metric] {
        let cats = side.categories
        let pool = players.filter { p in cats.contains { p.matchesPlayerType(for: $0) } }
        guard !pool.isEmpty, let table else { return [] }

        // Ordered (label, category) pairs present on the roster.
        var pairs: [(label: String, category: MetricCategory)] = []
        var seen = Set<String>()
        for cat in cats {
            let present = Set(pool.flatMap { p in p.metrics.filter { $0.category == cat }.map(\.label) })
            for label in cat.metricPriorityOrder where present.contains(label) {
                guard !RosterAggregate.descriptiveOnlyLabels.contains(label) else { continue }
                if seen.insert(label).inserted { pairs.append((label, cat)) }
            }
        }

        return pairs.compactMap { pair -> Metric? in
            guard let value = TeamLeagueTable.teamValue(label: pair.label, category: pair.category, roster: pool),
                  let pct = table.percentile(for: value, label: pair.label, category: pair.category)
            else { return nil }
            return Metric(
                id: "teamavg-\(pair.label)",
                label: TeamLeagueTable.displayLabel(pair.label, category: pair.category),
                value: formattedValue(value, label: pair.label),
                percentile: pct,
                category: pair.category
            )
        }
    }

    private func formattedValue(_ v: Double, label: String) -> String {
        if label.hasSuffix("%") { return String(format: "%.1f%%", v) }
        if ["EPA/Play", "EPA/Rush", "EPA/Tgt", "RACR"].contains(label) { return String(format: "%.2f", v) }
        if abs(v) >= 100 { return String(format: "%.0f", v) }
        return String(format: "%.1f", v)
    }

    // MARK: - Recent

    private var sideLogs: [PlayerGameLog] {
        let onSide = logs.filter { side.includes(playerType: $0.playerType) }
        // Most-recent N game dates across the roster define the window. Dedupe
        // first: every player has a row per game, so the raw list's first five
        // entries were five copies of the last game.
        let recentDates = Set(Set(onSide.map(\.gameDate)).sorted(by: >).prefix(windowGames))
        return onSide.filter { recentDates.contains($0.gameDate) }
    }

    private var recentWindow: RecentFormWindow? {
        guard !sideLogs.isEmpty else { return nil }
        return RecentFormWindow.build(label: "Last \(windowGames)", span: windowGames, logs: sideLogs)
    }

    @ViewBuilder
    private var recentSection: some View {
        if store.isPro {
            recentBars
        } else {
            ZStack(alignment: .bottom) {
                recentTeaser
                    .blur(radius: 8)
                    .disabled(true)
                    .allowsHitTesting(false)
                BlurGateUnlock(
                    headline: "See every team's last 3 / 5 / 8 game form",
                    trigger: .teamView
                )
            }
        }
    }

    /// Static, non-fetching preview for free users - illustrative team bars in
    /// the recent-form layout. No game logs are fetched (no network/battery cost)
    /// and no real team data is shown, so the blur can't be read through to leak
    /// the actual recent numbers.
    private var recentTeaser: some View {
        let sample: [Metric] = side == .offense
            ? [
                Metric(id: "tt_passyd", label: "Pass Yds", value: "3,980", percentile: 84, category: .passing),
                Metric(id: "tt_rushyd", label: "Rush Yds", value: "1,720", percentile: 77, category: .rushing),
                Metric(id: "tt_recyd",  label: "Rec Yds",  value: "3,910", percentile: 71, category: .receiving),
                Metric(id: "tt_yac",    label: "YAC",      value: "5.4",   percentile: 66, category: .receiving),
            ]
            : [
                Metric(id: "tt_tackles", label: "Tackles", value: "78",  percentile: 81, category: .defense),
                Metric(id: "tt_sacks",   label: "Sacks",   value: "11",  percentile: 76, category: .defense),
                Metric(id: "tt_int",     label: "INT",     value: "4",   percentile: 70, category: .defense),
                Metric(id: "tt_pd",      label: "PD",      value: "9",   percentile: 73, category: .defense),
            ]
        return VStack(spacing: 0) {
            HStack(spacing: 12) {
                summaryStat(label: "G", value: "14")
                summaryStat(label: "Plays", value: "521")
                if side == .offense { summaryStat(label: "Touches", value: "318") }
                Spacer(minLength: 0)
            }
            .padding(GridironGeo.padInline)

            GridironSubSectionBar(title: side.label.uppercased())
            VStack(spacing: 0) {
                ForEach(Array(sample.enumerated()), id: \.element.id) { index, metric in
                    MetricBar(metric: metric)
                        .padding(.horizontal, GridironGeo.padCard)
                        .padding(.vertical, 12)
                        .background(index % 2 == 0 ? GridironPalette.surface : GridironPalette.surfaceAlt)
                        .overlay(
                            Rectangle().fill(GridironPalette.divider).frame(height: GridironGeo.hairline),
                            alignment: .bottom
                        )
                }
            }
        }
    }

    @ViewBuilder
    private var recentBars: some View {
        if loading {
            loadingRow
        } else if let err = loadError {
            InlineLoadError(message: err) { await load() }
        } else if let w = recentWindow {
            recentSummaryRow(w)
            let rows = recentDisplayRows(window: w)
            if !rows.isEmpty {
                GridironSubSectionBar(title: side.label.uppercased())
                VStack(spacing: 0) {
                    ForEach(Array(rows.enumerated()), id: \.element.id) { index, metric in
                        MetricBar(metric: metric)
                            .padding(.horizontal, GridironGeo.padCard)
                            .padding(.vertical, 12)
                            .background(index % 2 == 0 ? GridironPalette.surface : GridironPalette.surfaceAlt)
                            .overlay(
                                Rectangle().fill(GridironPalette.divider).frame(height: GridironGeo.hairline),
                                alignment: .bottom
                            )
                    }
                }
            }
        } else {
            VStack(spacing: 6) {
                Image(systemName: "calendar.badge.exclamationmark")
                    .font(.system(size: 22))
                    .foregroundStyle(GridironPalette.inkTertiary)
                Text(emptyStateText)
                    .font(GridironType.small)
                    .foregroundStyle(GridironPalette.inkSecondary)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 22)
        }
    }

    private var emptyStateText: String {
        switch freshnessStatus {
        case .pending, .partial, .checking:
            return "Recent team data is still arriving"
        case .offline, .failed:
            return "Recent team data is unavailable right now"
        default:
            return "No \(side.label.lowercased()) data in the last \(windowGames) games"
        }
    }

    private func recentSummaryRow(_ w: RecentFormWindow) -> some View {
        HStack(spacing: 12) {
            summaryStat(label: "G", value: "\(w.games)")
            summaryStat(label: "Plays", value: "\(w.plays)")
            if side == .offense {
                summaryStat(label: "Touches", value: "\(w.touches)")
            }
            Spacer(minLength: 0)
            if w.plays < smallSamplePlaysThreshold {
                Text("SMALL SAMPLE")
                    .font(GridironType.micro)
                    .foregroundStyle(.white)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(GridironPalette.inkTertiary)
                    .clipShape(Capsule())
            }
        }
        .padding(GridironGeo.padInline)
    }

    private func summaryStat(label: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(label)
                .font(GridironType.micro)
                .foregroundStyle(GridironPalette.inkTertiary)
            Text(value)
                .font(GridironType.bodyBold)
                .foregroundStyle(GridironPalette.ink)
        }
    }

    /// Maps a recent game-log metric key onto the matching season aggregate label
    /// so a recent value can overlay the season bar it corresponds to.
    private var recentSpecs: [(key: String, seasonLabel: String, format: String)] {
        side == .defense
            ? [
                ("tackles",           "Tackles", "%.0f"),
                ("def_sacks",         "Sacks",   "%.1f"),
                ("def_interceptions", "INT",     "%.0f"),
            ]
            : [
                ("passing_yards",   "Pass Yds", "%.0f"),
                ("passing_tds",     "Pass TD",  "%.0f"),
                ("rushing_yards",   "Rush Yds", "%.0f"),
                ("rushing_tds",     "Rush TD",  "%.0f"),
                ("receiving_yards", "Rec Yds",  "%.0f"),
                ("receptions",      "Rec",      "%.0f"),
            ]
    }

    /// The metric category a season label belongs to.
    private func category(forLabel label: String) -> MetricCategory {
        for cat in side.categories where cat.metricPriorityOrder.contains(label) {
            return cat
        }
        return side.categories.first ?? .passing
    }

    /// Recent mode mirrors the season list: every season aggregate bar is shown.
    /// Metrics with game-log data in the window render the recent value (re-placed
    /// on the league curve); the rest fall back to their season aggregate bar.
    private func recentDisplayRows(window w: RecentFormWindow) -> [Metric] {
        let seasonRows = aggregateSeasonRows()
        let existing = Set(seasonRows.map { TeamLeagueTable.baseLabel($0.label) })
        let stubs: [Metric] = recentSpecs.compactMap { spec in
            guard !existing.contains(spec.seasonLabel),
                  recentMetric(forSeasonLabel: spec.seasonLabel, window: w) != nil else { return nil }
            return Metric(
                id: "team-recent-stub-\(spec.key)",
                label: spec.seasonLabel,
                value: "",
                percentile: 0,
                category: category(forLabel: spec.seasonLabel)
            )
        }
        return (seasonRows + stubs).map {
            recentMetric(forSeasonLabel: TeamLeagueTable.baseLabel($0.label), window: w) ?? $0
        }
    }

    /// The recent-window bar for a given season label, or nil if the window has
    /// no game-log data for it (caller falls back to the season aggregate bar).
    /// Window totals are divided by the games in the window and ranked against
    /// every club's season per-game figure, so three games are never measured
    /// on a ruler of full-season totals.
    private func recentMetric(forSeasonLabel label: String, window w: RecentFormWindow) -> Metric? {
        let category = category(forLabel: label)
        guard let spec = recentSpecs.first(where: { $0.seasonLabel == label }),
              let total = w.metrics[spec.key], w.games > 0,
              let pct = table?.percentile(for: total / Double(w.games), label: label, category: category)
        else { return nil }
        return Metric(
            id: "team-recent-\(spec.key)",
            label: TeamLeagueTable.displayLabel(label, category: category),
            value: formattedValue(total / Double(w.games), label: label),
            percentile: pct,
            category: category
        )
    }

    private func load() async {
        // Free users see a static teaser - never fetch real team game logs.
        guard store.isPro, let fetch = fetchTeamGameLogs else { return }
        loading = true
        loadError = nil
        do {
            // Pull a wide window (the last ~120 days of the season) so the client
            // can slice the most recent 3 / 5 / 8 games out of it. Anchored to the
            // season's own end, not to today - see `gameLogWindowStart`.
            let since = StatScoutSeason.gameLogWindowStart(season: season)
            logs = try await fetch(team, season, seasonPhase, since)
        } catch {
            if !isTaskCancellation(error) {
                loadError = "Couldn't load team form. Check your connection and try again."
            }
        }
        loading = false
    }

    // MARK: - Shared bits

    private var emptyAggregate: some View {
        VStack(spacing: 6) {
            Image(systemName: "chart.bar.xaxis")
                .font(.system(size: 22))
                .foregroundStyle(GridironPalette.inkTertiary)
            Text(players.isEmpty
                 ? "No games played yet this season"
                 : "Not enough \(side.label.lowercased()) data to aggregate")
                .font(GridironType.small)
                .foregroundStyle(GridironPalette.inkSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 22)
    }

    private var weightedCaption: some View {
        Text("Season to date: totals per game, rates weighted by volume. Bars rank each club against the other 31.")
            .font(GridironType.micro)
            .foregroundStyle(GridironPalette.inkTertiary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, GridironGeo.padCard)
            .padding(.vertical, 10)
    }

    private func rebuildCurves() {
        offenseTable = TeamLeagueTable(players: leaguePlayers, categories: Side.offense.categories)
        defenseTable = TeamLeagueTable(players: leaguePlayers, categories: Side.defense.categories)
    }
}

/// Every club's pooled value for each metric, the ruler a team bar sits on.
/// Counting stats are per game so a club with a bye is not ranked on one game
/// fewer; rates use the registry's volume weighting.
struct TeamLeagueTable {
    private let valuesByKey: [String: [Double]]

    init(players: [Player], categories: [MetricCategory]) {
        let pool = players.filter { p in categories.contains { p.matchesPlayerType(for: $0) } }
        let byTeam = Dictionary(grouping: pool) { normalizedTeamAbbreviation($0.team) }
        var result: [String: [Double]] = [:]
        for category in categories {
            for label in category.metricPriorityOrder {
                let values = byTeam.values.compactMap {
                    Self.teamValue(label: label, category: category, roster: $0)
                }
                if values.count >= 2 { result[Self.key(label, category)] = values }
            }
        }
        valuesByKey = result
    }

    static func key(_ label: String, _ category: MetricCategory) -> String {
        category.rawValue + "|" + label
    }

    /// A club's value: summed totals divided by its games, or the weighted rate.
    static func teamValue(label: String, category: MetricCategory, roster: [Player]) -> Double? {
        guard let pooled = RosterAggregate.value(label: label, category: category, roster: roster) else { return nil }
        guard isPerGame(label, category: category) else { return pooled }
        guard let games = RosterAggregate.teamGames(roster: roster) else { return nil }
        return pooled / games
    }

    static func isPerGame(_ label: String, category: MetricCategory) -> Bool {
        if case .sum = FootballMetricRegistry.aggregation(for: label, category: category) { return true }
        return false
    }

    static func displayLabel(_ label: String, category: MetricCategory) -> String {
        isPerGame(label, category: category) ? label + "/G" : label
    }

    static func baseLabel(_ displayLabel: String) -> String {
        displayLabel.hasSuffix("/G") ? String(displayLabel.dropLast(2)) : displayLabel
    }

    /// Share of the other clubs this value beats, 1 to 99, direction-aware.
    func percentile(for value: Double, label: String, category: MetricCategory) -> Int? {
        guard let values = valuesByKey[Self.key(label, category)] else { return nil }
        let higherIsBetter = FootballMetricRegistry.definition(for: label, category: category)?.higherIsBetter ?? true
        let worse = values.filter { higherIsBetter ? $0 < value : $0 > value }.count
        let ties = values.filter { $0 == value }.count
        // The club itself is one of the ties; it neither beats nor loses to itself.
        let others = max(1, values.count - 1)
        let score = (Double(worse) + Double(max(0, ties - 1)) / 2) / Double(others) * 100
        return min(99, max(1, Int(score.rounded())))
    }
}
