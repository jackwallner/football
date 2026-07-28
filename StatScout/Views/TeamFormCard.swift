import SwiftUI

/// Team "percentile rankings" card — the team-level analogue of the player
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
    /// The roster for this team/season.
    let players: [Player]
    /// League pool used to build the value→percentile curve so the team bar sits
    /// on the same ruler as individual players' bars. Filtered per side at
    /// curve-build time.
    let leaguePlayers: [Player]
    let fetchTeamGameLogs: ((String, Int, Date) async throws -> [PlayerGameLog])?
    let onUpgradeTap: () -> Void

    @State private var side: Side = .offense
    @State private var mode: Mode = .season
    @State private var windowGames: Int = 5
    @State private var logs: [PlayerGameLog] = []
    @State private var loading = false
    @State private var loadError: String?
    @State private var offenseCurves: LeaguePercentileCurves?
    @State private var defenseCurves: LeaguePercentileCurves?

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
        case season = "Season", recent = "Recent"
        var id: String { rawValue }
    }

    /// Smallest team-window play count we'll treat as trustworthy — below this we
    /// flag the window as a small sample.
    private let smallSamplePlaysThreshold = 40

    private var curves: LeaguePercentileCurves? {
        side == .defense ? defenseCurves : offenseCurves
    }

    var body: some View {
        VStack(spacing: 0) {
            GridironSectionBar(
                title: "TEAM PERCENTILE RANKINGS",
                trailing: store.isPro ? nil : AnyView(proBadge)
            )

            sidePicker
                .padding(.horizontal, GridironGeo.padInline)
                .padding(.vertical, 8)
                .background(GridironPalette.surfaceAlt)

            modePicker

            if mode == .recent {
                windowPicker
            }

            if mode == .season {
                seasonBars
            } else {
                recentSection
            }
        }
        .background(GridironPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
        .task(id: "\(team)-\(season)-\(mode.rawValue)-\(store.isPro)") {
            if mode == .recent, store.isPro { await load() }
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
        HStack(spacing: 0) {
            ForEach(Side.allCases) { s in
                Button {
                    side = s
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                } label: {
                    Text(s.label)
                        .font(GridironType.smallBold)
                        .foregroundStyle(side == s ? GridironPalette.ink : GridironPalette.inkSecondary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .overlay(
                            Rectangle()
                                .fill(side == s ? GridironPalette.turf : Color.clear)
                                .frame(height: 2)
                                .padding(.top, 32),
                            alignment: .bottom
                        )
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var modePicker: some View {
        HStack(spacing: 6) {
            ForEach(Mode.allCases) { m in
                Button {
                    mode = m
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                } label: {
                    Text(m.rawValue)
                        .font(GridironType.smallBold)
                        .foregroundStyle(mode == m ? .white : GridironPalette.inkSecondary)
                        .frame(maxWidth: .infinity)
                        .frame(height: 30)
                        .background(mode == m ? GridironPalette.turf : GridironPalette.surface)
                        .clipShape(Capsule())
                        .overlay(Capsule().stroke(GridironPalette.hairline, lineWidth: 0.5))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, GridironGeo.padInline)
        .padding(.vertical, 10)
        .background(GridironPalette.surfaceAlt)
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
                    NavigationLink(value: MetricRoute(label: metric.label, category: metric.category)) {
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

    /// One bar per roster metric — roster mean placed on the league curve. Each
    /// bar keeps its own metric category (Passing / Rushing / Receiving) so the
    /// leaderboard link routes correctly.
    private func aggregateSeasonRows() -> [Metric] {
        let cats = side.categories
        let pool = players.filter { p in cats.contains { p.matchesPlayerType(for: $0) } }
        guard !pool.isEmpty, let curves else { return [] }

        // Ordered (label, category) pairs present on the roster.
        var pairs: [(label: String, category: MetricCategory)] = []
        var seen = Set<String>()
        for cat in cats {
            let present = Set(pool.flatMap { p in p.metrics.filter { $0.category == cat }.map(\.label) })
            for label in cat.metricPriorityOrder where present.contains(label) {
                if seen.insert(label).inserted { pairs.append((label, cat)) }
            }
        }

        return pairs.compactMap { pair -> Metric? in
            var sum = 0.0
            var count = 0.0
            for player in pool {
                guard let m = player.metrics.first(where: { $0.label == pair.label && $0.category == pair.category }),
                      let v = DashboardViewModel.rawNumeric(m.value) else { continue }
                sum += v
                count += 1
            }
            guard count > 0 else { return nil }
            let avg = sum / count
            guard let pct = curves.curve(for: pair.label)?.percentile(for: avg) else { return nil }
            return Metric(
                id: "teamavg-\(pair.label)",
                label: pair.label,
                value: formattedValue(avg, label: pair.label),
                percentile: pct,
                category: pair.category
            )
        }
    }

    private func formattedValue(_ v: Double, label: String) -> String {
        if label.hasSuffix("%") { return String(format: "%.1f%%", v) }
        if abs(v) >= 100 { return String(format: "%.0f", v) }
        return String(format: "%.1f", v)
    }

    // MARK: - Recent

    private var sideLogs: [PlayerGameLog] {
        let onSide = logs.filter { side.includes(playerType: $0.playerType) }
        // Most-recent N game dates across the roster define the window.
        let recentDates = Set(onSide.map(\.gameDate).sorted(by: >).prefix(windowGames))
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

    /// Static, non-fetching preview for free users — illustrative team bars in
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
            HStack(spacing: 10) {
                ProgressView().progressViewStyle(.circular).scaleEffect(0.75)
                Text("Loading recent games…")
                    .font(GridironType.small)
                    .foregroundStyle(GridironPalette.inkSecondary)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 24)
        } else if let err = loadError {
            Text(err)
                .font(GridironType.small)
                .foregroundStyle(GridironPalette.inkSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, GridironGeo.padInline)
                .padding(.vertical, 24)
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
                Text("No \(side.label.lowercased()) data in the last \(windowGames) games")
                    .font(GridironType.small)
                    .foregroundStyle(GridironPalette.inkSecondary)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 22)
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
        let existing = Set(seasonRows.map(\.label))
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
        return (seasonRows + stubs).map { recentMetric(forSeasonLabel: $0.label, window: w) ?? $0 }
    }

    /// The recent-window bar for a given season label, or nil if the window has
    /// no game-log data for it (caller falls back to the season aggregate bar).
    private func recentMetric(forSeasonLabel label: String, window w: RecentFormWindow) -> Metric? {
        guard let spec = recentSpecs.first(where: { $0.seasonLabel == label }),
              let v = w.metrics[spec.key],
              let pct = curves?.curve(for: label)?.percentile(for: v) else { return nil }
        return Metric(
            id: "team-recent-\(spec.key)",
            label: label,
            value: String(format: spec.format, v),
            percentile: pct,
            category: category(forLabel: label)
        )
    }

    private func load() async {
        // Free users see a static teaser — never fetch real team game logs.
        guard store.isPro, let fetch = fetchTeamGameLogs else { return }
        loading = true
        loadError = nil
        do {
            // Pull a wide window (last ~120 days of the season) so the client can
            // slice the most recent 1 / 3 / 5 games out of it.
            let since = Calendar.current.date(byAdding: .day, value: -120, to: .now) ?? .now
            logs = try await fetch(team, season, since)
        } catch {
            loadError = "Couldn't load team form. Pull to refresh."
        }
        loading = false
    }

    // MARK: - Shared bits

    private var emptyAggregate: some View {
        VStack(spacing: 6) {
            Image(systemName: "chart.bar.xaxis")
                .font(.system(size: 22))
                .foregroundStyle(GridironPalette.inkTertiary)
            Text("Not enough \(side.label.lowercased()) data to aggregate")
                .font(GridironType.small)
                .foregroundStyle(GridironPalette.inkSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 22)
    }

    private var weightedCaption: some View {
        Text("Averaged across the \(side.label.lowercased()) roster")
            .font(GridironType.micro)
            .foregroundStyle(GridironPalette.inkTertiary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, GridironGeo.padCard)
            .padding(.vertical, 10)
    }

    private func rebuildCurves() {
        let rosterLabels = players.flatMap { $0.metrics.map(\.label) }
        let recentLabels = MetricCategory.allCases.flatMap { $0.metricPriorityOrder }
        let labels = Array(Set(rosterLabels + recentLabels))
        offenseCurves = LeaguePercentileCurves(players: leaguePlayers, categories: [.passing, .rushing, .receiving], labels: labels)
        defenseCurves = LeaguePercentileCurves(players: leaguePlayers, categories: [.defense], labels: labels)
    }
}
