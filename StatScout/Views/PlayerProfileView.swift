import SwiftUI

struct PlayerProfileView: View {
    @EnvironmentObject private var store: StoreService
    let player: Player
    let history: [Player]
    var allPlayers: [Player] = []
    var isHistoricalLoading = false
    var hasLoadedHistorical = true
    var historicalLoadingMessage = "Loading past seasons…"
    var historicalLoadingProgress = 0.12
    var loadHistorical: (() async -> Void)?
    var fetchGameLogs: ((Int, Int) async throws -> [PlayerGameLog])?
    /// Pre-aggregated rolling window for this player, when one is loaded.
    var recentFormLookup: ((Int, RecentWindow) -> RecentForm?)?
    var loadRecentForm: ((RecentWindow) async -> Void)?
    var comparisonCatalog: ComparisonCatalog?
    @State private var showPercentileInfo = false
    @State private var selectedTab: PlayerStatTab = .statcast
    @State private var selectedPercentileSeason: Int? = nil
    @State private var paywallTrigger: PaywallTrigger?
    @State private var showingPlayerPicker = false
    @State private var comparisonRoute: ComparisonRoute?
    // Contextual trial pitches (compare, recent form, year compare, first-open)
    // all route through the low-friction TrialPitchSheet - its CTA starts the
    // yearly trial directly. PaywallView stays for the deliberate upsell card.
    @State private var trialPitchTrigger: PaywallTrigger?
    @State private var formDisplayMode: FormDisplayMode = .season
    @State private var recentWindowGames: Int = 5
    @State private var recentLogs: [PlayerGameLog] = []
    @State private var recentLoading = false
    @State private var recentLoadError: String?
    @State private var recentCurves: LeaguePercentileCurves?
    @State private var standardMode: FormDisplayMode = .season
    @State private var standardWindow: RecentWindow = .five
    @State private var favorites = FavoritesStore.shared

    private let profileOpenCountKey = "profileOpenCount"

    enum FormDisplayMode: String, CaseIterable {
        case season = "Season"
        case recent = "Recent"
        case both = "Both"
    }

    enum PlayerStatTab: String, CaseIterable {
        case statcast = "Percentiles"
        case standard = "Standard Stats"
        case yearCompare = "Year Compare"
    }

    private var availablePercentileSeasons: [Int] {
        let fromHistory = phaseHistory.compactMap(\.season)
        var set = Set(fromHistory)
        if let s = player.season { set.insert(s) }
        return Array(set).sorted(by: >)
    }

    private var activeSeason: Int? {
        selectedPercentileSeason ?? player.season
    }

    private var displayedPlayer: Player {
        guard let season = activeSeason else { return player }
        return phaseHistory.first { $0.season == season } ?? player
    }

    private var phaseHistory: [Player] {
        history.filter { $0.seasonPhase == player.seasonPhase }
    }

    private var seasonLabel: String {
        activeSeason.map(String.init) ?? "-"
    }

    private var profileMetricKind: MetricKind {
        displayedPlayer.positionGroup == .defense ? .traditional : .advanced
    }

    private var groupedMetrics: [(family: MetricFamily, metrics: [Metric])] {
        let eligible = displayedPlayer.metrics(kind: profileMetricKind)
        let grouped = Dictionary(grouping: eligible) { metric in
            FootballMetricRegistry.definition(for: metric.label, category: metric.category)?.family ?? .production
        }
        return MetricFamily.allCases.compactMap { family in
            guard let metrics = grouped[family], !metrics.isEmpty else { return nil }
            return (family: family, metrics: FootballMetricRegistry.sorted(metrics))
        }
    }

    private var profileHeadline: Metric? {
        displayedPlayer.preferredHeadlineMetric(kind: profileMetricKind)
    }

    /// Players eligible for comparison: same position group, sorted by overall
    /// percentile proximity to the current player so the closest match is first.
    private var comparablePlayers: [Player] {
        let myType = player.playerType?.lowercased()
        let pool = allPlayers.filter { other in
            guard other.playerId != player.playerId else { return false }
            guard let myType else { return true }
            return other.playerType?.lowercased() == myType
        }
        let mine = player.overallPercentile
        return pool.sorted { a, b in
            abs(a.overallPercentile - mine) < abs(b.overallPercentile - mine)
        }
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                PlayerIdentityStrip(player: player)

                tabSelector
                    .padding(.horizontal, 12)
                    .padding(.top, 12)

                switch selectedTab {
                case .statcast:
                    statcastContent
                case .standard:
                    standardContent
                case .yearCompare:
                    yearCompareContent
                }

                Color.clear.frame(height: 88)
            }
        }
        .scrollBounceBehavior(.basedOnSize)
        .background(GridironPalette.canvas.ignoresSafeArea())
        // First-tap activation: profile renders immediately (no full-screen
        // paywall blocking it), and a native half-sheet TrialPitchSheet
        // floats on top with a "Maybe later" dismiss. PaywallGate caps this
        // at 2 per session so repeat taps don't re-prompt. The old full-page
        // .activation PaywallView was removed for being too intrusive - this
        // is the Vitals-style soft pitch that replaced it.
        .navigationTitle(player.name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if #available(iOS 26.0, *) {
                ToolbarItem(placement: .topBarTrailing) { favoriteButton }
                    .sharedBackgroundVisibility(.hidden)
                ToolbarItem(placement: .topBarTrailing) { compareButton }
                    .sharedBackgroundVisibility(.hidden)
            } else {
                ToolbarItem(placement: .topBarTrailing) { favoriteButton }
                ToolbarItem(placement: .topBarTrailing) { compareButton }
            }
        }
        .sheet(isPresented: $showPercentileInfo) {
            PercentileInfoSheet()
        }
        .sheet(item: $paywallTrigger) { trigger in
            TrialPitchSheet(trigger: trigger)
        }
        .sheet(isPresented: $showingPlayerPicker) {
            PlayerPickerSheet(players: comparablePlayers) { selected in
                comparisonRoute = ComparisonRoute(playerA: player, playerB: selected)
            }
        }
        .sheet(item: $trialPitchTrigger) { trigger in
            TrialPitchSheet(trigger: trigger)
        }
        .navigationDestination(item: $comparisonRoute) { route in
            PlayerComparisonView(
                playerA: route.playerA,
                playerB: route.playerB,
                catalog: comparisonCatalog
            )
        }
        .onAppear {
            // Defer the first-impression pitch: a user verifying one stat from a
            // group chat shouldn't hit a subscription story before scrolling a
            // single row. Show it from the *second* profile open onward (Pro-only
            // controls - Recent Form, past seasons, Compare - still pitch on tap).
            let opens = UserDefaults.standard.integer(forKey: profileOpenCountKey) + 1
            UserDefaults.standard.set(opens, forKey: profileOpenCountKey)
            if !store.isPro, opens >= 2, PaywallGate.shared.shouldPresent(.playerScouting) {
                trialPitchTrigger = .playerScouting
            }
            // Third+ profile visit = engaged browsing; never on open 2 (trial pitch).
            if opens >= 3 {
                ReviewPromptTracker.recordPositiveMoment()
            }
        }
    }

    /// Following a player is free. It's the signal the Trends tab and the
    /// review funnel read from, so gating it would suppress the thing we most
    /// want people to do.
    private var favoriteButton: some View {
        let isFavorite = favorites.isFavorite(playerId: player.playerId)
        return Button {
            favorites.toggleFavorite(playerId: player.playerId)
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        } label: {
            Image(systemName: isFavorite ? "star.fill" : "star")
                .foregroundStyle(isFavorite ? Color.yellow : .white)
        }
        .accessibilityLabel(isFavorite ? "Unfollow \(player.name)" : "Follow \(player.name)")
    }

    private var compareButton: some View {
        Button {
            if store.isPro {
                showingPlayerPicker = true
            } else {
                trialPitchTrigger = .playerComparison
            }
        } label: {
            Image(systemName: "person.2.fill")
                .foregroundStyle(.white)
        }
        .accessibilityLabel("Compare with another player")
    }

    private var tabSelector: some View {
        HStack(spacing: 8) {
            statcastTabButton
            standardTabButton
            yearCompareTabButton
        }
    }

    private var statcastTabButton: some View {
        let isSelected = selectedTab == .statcast
        return Button(action: {
            withAnimation(.easeInOut(duration: 0.2)) {
                selectedTab = .statcast
            }
            let generator = UIImpactFeedbackGenerator(style: .light)
            generator.impactOccurred()
        }) {
            Text(PlayerStatTab.statcast.rawValue)
                .font(GridironType.bodyBold)
                .foregroundStyle(isSelected ? .white : GridironPalette.ink)
                .frame(maxWidth: .infinity)
                .frame(height: 44)
                .background(isSelected ? GridironPalette.turf : GridironPalette.surface)
                .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        }
        .buttonStyle(.plain)
    }

    private var standardTabButton: some View {
        let isSelected = selectedTab == .standard
        return Button(action: {
            withAnimation(.easeInOut(duration: 0.2)) {
                selectedTab = .standard
            }
            let generator = UIImpactFeedbackGenerator(style: .light)
            generator.impactOccurred()
        }) {
            Text(PlayerStatTab.standard.rawValue)
                .font(GridironType.bodyBold)
                .foregroundStyle(isSelected ? .white : GridironPalette.ink)
                .frame(maxWidth: .infinity)
                .frame(height: 44)
                .background(isSelected ? GridironPalette.turf : GridironPalette.surface)
                .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        }
        .buttonStyle(.plain)
    }

    private var yearCompareTabButton: some View {
        let isSelected = selectedTab == .yearCompare
        return Button(action: {
            withAnimation(.easeInOut(duration: 0.2)) {
                selectedTab = .yearCompare
            }
            if store.isPro, !hasLoadedHistorical, !isHistoricalLoading {
                Task { await loadHistorical?() }
            }
            let generator = UIImpactFeedbackGenerator(style: .light)
            generator.impactOccurred()
        }) {
            Text(PlayerStatTab.yearCompare.rawValue)
                .font(GridironType.bodyBold)
                .foregroundStyle(isSelected ? .white : GridironPalette.ink)
                .frame(maxWidth: .infinity)
                .frame(height: 44)
                .background(isSelected ? GridironPalette.turf : GridironPalette.surface)
                .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        }
        .buttonStyle(.plain)
    }

    private var statcastContent: some View {
        VStack(spacing: 12) {
            headlineCard
            percentileRankingsCard

            if !store.isPro {
                RecentFormCard(
                    player: player,
                    season: activeSeason ?? player.season ?? Calendar.current.component(.year, from: .now),
                    leaguePlayers: allPlayers,
                    fetchGameLogs: fetchGameLogs,
                    onUpgradeTap: { trialPitchTrigger = .recentForm }
                )
                proUpsellCard
            }
        }
        .padding(.horizontal, 12)
        .padding(.top, 12)
    }

    private var headlineCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(displayedPlayer.positionGroup == .defense ? "PRODUCTION PROFILE" : "ADVANCED PROFILE")
                    .font(GridironType.micro)
                    .foregroundStyle(GridironPalette.inkTertiary)
                Spacer()
                seasonMenu
            }
            if let metric = profileHeadline {
                HStack(alignment: .lastTextBaseline) {
                    VStack(alignment: .leading, spacing: 3) {
                        Text(metric.label)
                            .font(GridironType.bodyBold)
                            .foregroundStyle(GridironPalette.ink)
                        Text(displayedPlayer.positionGroup.cohortDescription)
                            .font(GridironType.small)
                            .foregroundStyle(GridironPalette.inkSecondary)
                    }
                    Spacer()
                    Text(metric.value.isEmpty ? "-" : metric.value)
                        .font(GridironType.statMed)
                        .foregroundStyle(GridironPalette.color(forPercentile: metric.percentile))
                    Text("\(metric.percentile.ordinal)")
                        .font(GridironType.smallBold)
                        .foregroundStyle(GridironPalette.color(forPercentile: metric.percentile))
                }
            }
        }
        .padding(16)
        .background(GridironPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(RoundedRectangle(cornerRadius: GridironGeo.radiusCard).stroke(GridironPalette.hairline, lineWidth: 0.5))
    }

    private var yearCompareSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text("YEAR-OVER-YEAR")
                        .font(GridironType.micro)
                        .foregroundStyle(GridironPalette.inkTertiary)
                    Text("Compare how this profile changed by season.")
                        .font(GridironType.small)
                        .foregroundStyle(GridironPalette.inkSecondary)
                }
                Spacer()
                Button("Compare") {
                    selectedTab = .yearCompare
                    if store.isPro, !hasLoadedHistorical, !isHistoricalLoading {
                        Task { await loadHistorical?() }
                    }
                    if !store.isPro { trialPitchTrigger = .yearCompare }
                }
                .font(GridironType.smallBold)
                .buttonStyle(.bordered)
                .tint(GridironPalette.midnight)
            }
            if selectedTab == .yearCompare, store.isPro {
                yearCompareContent
            }
        }
        .padding(16)
        .background(GridironPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(RoundedRectangle(cornerRadius: GridironGeo.radiusCard).stroke(GridironPalette.hairline, lineWidth: 0.5))
    }

    private var proUpsellCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                Image(systemName: "crown.fill")
                    .font(.system(size: 14))
                    .foregroundStyle(Color.yellow)
                Text("StatScout+")
                    .font(GridironType.smallBold)
                    .foregroundStyle(GridironPalette.ink)
            }

            Text("Get the full scouting picture on \(player.name).")
                .font(GridironType.body)
                .foregroundStyle(GridironPalette.ink)
                .fixedSize(horizontal: false, vertical: true)

            VStack(alignment: .leading, spacing: 6) {
                proPerk("chart.line.uptrend.xyaxis", "Year-over-year trends across every metric")
                proPerk("person.2.fill", "Head-to-head comparisons vs any player")
                proPerk("calendar.badge.clock", "Every past season, not just this one")
                proPerk("arrow.down.circle.fill", "Saved offline - works on the road")
            }

            Button {
                paywallTrigger = store.defaultUpgradeTrigger
            } label: {
                HStack(spacing: 6) {
                    Text(store.paywallBlurCTA)
                        .font(GridironType.bodyBold)
                    Image(systemName: "arrow.right")
                        .font(.system(size: 12, weight: .bold))
                }
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 46)
                .background(GridironPalette.turf)
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
            .buttonStyle(.plain)

            if let subtext = store.paywallBlurSubtext {
                Text(subtext)
                    .font(GridironType.micro)
                    .foregroundStyle(GridironPalette.inkTertiary)
                    .frame(maxWidth: .infinity, alignment: .center)
            }
        }
        .padding(16)
        .background(GridironPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
    }

    private func proPerk(_ icon: String, _ text: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(GridironPalette.turf)
                .frame(width: 16)
            Text(text)
                .font(GridironType.small)
                .foregroundStyle(GridironPalette.inkSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var standardContent: some View {
        VStack(spacing: 12) {
            standardStatsGridCard
        }
        .padding(.horizontal, 12)
        .padding(.top, 12)
    }

    @ViewBuilder
    private var yearCompareContent: some View {
        if store.isPro {
            if isHistoricalLoading {
                historicalLoadingCard
            } else if !hasLoadedHistorical, loadHistorical != nil {
                loadHistoricalCard
            } else if history.count < 2 {
                ContentUnavailableView {
                    Label("Not enough history", systemImage: "calendar.badge.clock")
                } description: {
                    Text("\(player.name) doesn't have multiple seasons of data to compare.")
                }
                .padding(.vertical, 48)
            } else {
                YearComparisonView(history: phaseHistory)
            }
        } else {
            YearComparePreview(playerName: player.name) {
                trialPitchTrigger = .yearCompare
            }
        }
    }

    private var historicalLoadingCard: some View {
        VStack(spacing: 14) {
            ProgressView(value: min(max(historicalLoadingProgress, 0), 1), total: 1)
                .progressViewStyle(.linear)
                .tint(GridironPalette.turf)
            Text(historicalLoadingMessage)
                .font(GridironType.bodyBold)
                .foregroundStyle(GridironPalette.ink)
            Text("\(Int(min(max(historicalLoadingProgress, 0), 1) * 100))%")
                .font(GridironType.micro)
                .foregroundStyle(GridironPalette.inkTertiary)
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        .background(GridironPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
        .padding(.horizontal, 12)
        .padding(.top, 12)
    }

    private var loadHistoricalCard: some View {
        VStack(spacing: 14) {
            Image(systemName: "clock.arrow.circlepath")
                .font(.system(size: 36))
                .foregroundStyle(GridironPalette.turf)
            Text("Load past seasons")
                .font(GridironType.bodyBold)
                .foregroundStyle(GridironPalette.ink)
            Text("Year Compare loads historical data only when you need it.")
                .font(GridironType.body)
                .foregroundStyle(GridironPalette.inkSecondary)
                .multilineTextAlignment(.center)
            Button("Load History") {
                Task { await loadHistorical?() }
            }
            .buttonStyle(.borderedProminent)
            .tint(GridironPalette.turf)
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        .background(GridironPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
        .padding(.horizontal, 12)
        .padding(.top, 12)
    }

    private func emptyStateCard(icon: String, title: String, description: String) -> some View {
        VStack(spacing: 12) {
            ContentUnavailableView {
                Label(title, systemImage: icon)
            } description: {
                Text(description)
            }
        }
        .padding(.vertical, 48)
        .frame(maxWidth: .infinity)
        .background(GridironPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
    }

    // MARK: - Cards

    private var seasonMenu: some View {
        let seasons = availablePercentileSeasons
        return Group {
            if seasons.count > 1 {
                Menu {
                    ForEach(seasons, id: \.self) { season in
                        let isLocked = season != StatScoutSeason.free && !store.isPro
                        Button {
                            if isLocked {
                                // Explicit tap on a locked season - always answer it.
                                // PaywallGate only caps automatic pop-ups.
                                trialPitchTrigger = .pastSeason
                            } else {
                                selectedPercentileSeason = season
                            }
                        } label: {
                            HStack {
                                Text(String(season))
                                if isLocked {
                                    Image(systemName: "crown.fill")
                                        .font(.system(size: 10))
                                        .foregroundStyle(Color.yellow)
                                }
                            }
                        }
                    }
                } label: {
                    HStack(spacing: 4) {
                        Text(seasonLabel)
                            .font(GridironType.micro)
                            .foregroundStyle(GridironPalette.inkSecondary)
                        Image(systemName: "chevron.down")
                            .font(.system(size: 9, weight: .bold))
                            .foregroundStyle(GridironPalette.inkSecondary)
                    }
                }
                .menuOrder(.fixed)
            } else {
                Text(seasonLabel)
                    .font(GridironType.micro)
                    .foregroundStyle(GridironPalette.inkSecondary)
            }
        }
    }

    private var formModePicker: some View {
        HStack(spacing: 6) {
            ForEach(FormDisplayMode.allCases, id: \.self) { mode in
                Button {
                    formDisplayMode = mode
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                } label: {
                    Text(mode.rawValue)
                        .font(GridironType.smallBold)
                        .foregroundStyle(formDisplayMode == mode ? .white : GridironPalette.inkSecondary)
                        .frame(maxWidth: .infinity)
                        .frame(height: 30)
                        .background(formDisplayMode == mode ? GridironPalette.turf : GridironPalette.surface)
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

    private var percentileRankingsCard: some View {
        VStack(spacing: 0) {
            GridironSectionBar(
                title: displayedPlayer.positionGroup == .defense ? "PRODUCTION PERCENTILES" : "ADVANCED PERCENTILES",
                trailing: AnyView(
                    HStack(spacing: 4) {
                        seasonMenu
                        Button(action: { showPercentileInfo = true }) {
                            Text("ⓘ")
                                .font(GridironType.micro)
                                .foregroundStyle(GridironPalette.linkBlue)
                        }
                        .buttonStyle(.plain)
                    }
                )
            )

            // Recent mode only makes sense for the live season - game logs are
            // only fetched for the current season, so on a historical season it
            // would always read "No games".
            if store.isPro, isCurrentSeasonActive {
                formModePicker
            }

            if formDisplayMode != .season, store.isPro, isCurrentSeasonActive {
                recentWindowPicker
                if recentLoading {
                    HStack(spacing: 10) {
                        ProgressView().scaleEffect(0.75)
                        Text("Loading recent games…")
                            .font(GridironType.small)
                            .foregroundStyle(GridironPalette.inkSecondary)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                } else if let recentLoadError {
                    Text(recentLoadError)
                        .font(GridironType.small)
                        .foregroundStyle(GridironPalette.inkSecondary)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                } else if recentWindow == nil {
                    Text("No games in the last \(recentWindowGames) games")
                        .font(GridironType.small)
                        .foregroundStyle(GridironPalette.inkSecondary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                }
            }

            if groupedMetrics.isEmpty {
                emptyStateCard(
                    icon: "chart.bar",
                    title: "No metrics available",
                    description: "Percentile rankings are not available for this player in the \(seasonLabel) season."
                )
                .padding(.vertical, 24)
            } else {
                ForEach(groupedMetrics, id: \.family) { group in
                    let rows = displayedMetrics(in: group.metrics)
                    if !rows.isEmpty {
                        GridironSubSectionBar(
                            title: group.family.rawValue.uppercased()
                        )

                        ForEach(Array(rows.enumerated()), id: \.element.id) { index, metric in
                            percentileMetricRow(metric: metric, index: index)
                        }
                    }
                }
            }
        }
        .background(GridironPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
        .task(id: "\(formDisplayMode)-\(recentWindowGames)-\(player.playerId)-\(activeSeason ?? 0)-\(store.isPro)") {
            guard store.isPro, effectiveFormDisplayMode != .season else { return }
            rebuildRecentCurves()
            await loadRecentLogs()
        }
        .onAppear { rebuildRecentCurves() }
        .onChange(of: allPlayers.count) { _, _ in rebuildRecentCurves() }
    }

    private var category: MetricCategory { player.primaryCategory }

    /// Recent-form is anchored to the current season's game logs, so it's only
    /// meaningful while viewing the current season. Historical seasons render
    /// season bars only.
    private var isCurrentSeasonActive: Bool {
        (activeSeason ?? StatScoutSeason.current) == StatScoutSeason.current
    }

    /// The mode rows actually render in - forced back to `.season` on a
    /// historical season so a user who toggled Recent/Both doesn't see stale
    /// current-season windows against past-season bars.
    private var effectiveFormDisplayMode: FormDisplayMode {
        isCurrentSeasonActive ? formDisplayMode : .season
    }

    private var recentWindow: RecentFormWindow? {
        let windowLogs = Array(recentLogs.sorted { $0.gameDate > $1.gameDate }.prefix(recentWindowGames))
        guard !windowLogs.isEmpty else { return nil }
        return RecentFormWindow.build(label: "Last \(recentWindowGames)", span: recentWindowGames, logs: windowLogs)
    }

    private var recentWindowPicker: some View {
        GridironSegmented(
            segments: RecentWindow.allCases.map { .init(value: $0, label: $0.segmentLabel) },
            selection: Binding(
                get: { RecentWindow(rawValue: recentWindowGames) ?? .three },
                set: { recentWindowGames = $0.rawValue }
            )
        )
    }

    private func displayedMetrics(in metrics: [Metric]) -> [Metric] {
        guard effectiveFormDisplayMode == .recent, store.isPro else { return metrics }
        // Recent mode: show every season bar - metrics with window data render the
        // recent value, the rest fall back to the season bar (handled in
        // `percentileMetricRow`). Additionally inject a stub for any game-log spec
        // the season snapshot omits (Gridiron sometimes drops e.g. Hard-Hit%) so its
        // recent bar still appears even with no season row to hang it on.
        let targetCategory: MetricCategory = category
        guard metrics.first?.category == targetCategory else { return metrics }
        let existing = Set(metrics.map { $0.label })
        let stubs: [Metric] = recentSpecs.compactMap { spec in
            guard !existing.contains(spec.label) else { return nil }
            let stub = Metric(
                id: "recent-stub-\(spec.key)",
                label: spec.label,
                value: "",
                percentile: 0,
                category: targetCategory
            )
            return recentMetric(for: stub) != nil ? stub : nil
        }
        return metrics + stubs
    }

    private var recentSpecs: [(key: String, label: String, seasonLabel: String, format: String)] {
        switch category {
        case .passing:
            return [
                ("passing_yards", "Pass Yds", "Pass Yds", "%.0f"),
                ("passing_tds",   "Pass TD",  "Pass TD",  "%.0f"),
            ]
        case .rushing:
            return [
                ("rushing_yards", "Rush Yds", "Rush Yds", "%.0f"),
                ("rushing_tds",   "Rush TD",  "Rush TD",  "%.0f"),
            ]
        case .receiving:
            return [
                ("receiving_yards", "Rec Yds", "Rec Yds", "%.0f"),
                ("receptions",      "Rec",     "Rec",     "%.0f"),
                ("receiving_tds",   "Rec TD",  "Rec TD",  "%.0f"),
            ]
        case .defense:
            return [
                ("tackles",           "Tackles", "Tackles", "%.0f"),
                ("def_sacks",         "Sacks",   "Sacks",   "%.1f"),
                ("def_interceptions", "INT",     "INT",     "%.0f"),
            ]
        }
    }

    @ViewBuilder
    private func percentileMetricRow(metric: Metric, index: Int) -> some View {
        let recentMetric = recentMetric(for: metric)
        let rowBackground = index % 2 == 0 ? GridironPalette.surface : GridironPalette.surfaceAlt

        switch effectiveFormDisplayMode {
        case .season:
            NavigationLink(value: MetricRoute(label: metric.label, category: metric.category, season: activeSeason)) {
                MetricBar(metric: metric)
                    .padding(.horizontal, GridironGeo.padCard)
                    .padding(.vertical, 12)
                    .background(rowBackground)
                    .overlay(
                        Rectangle().fill(GridironPalette.divider).frame(height: GridironGeo.hairline),
                        alignment: .bottom
                    )
            }
            .buttonStyle(.plain)
        case .recent:
            if let recentMetric {
                MetricBar(metric: recentMetric)
                    .padding(.horizontal, GridironGeo.padCard)
                    .padding(.vertical, 12)
                    .background(rowBackground)
                    .overlay(
                        Rectangle().fill(GridironPalette.divider).frame(height: GridironGeo.hairline),
                        alignment: .bottom
                    )
            } else if !metric.id.hasPrefix("recent-stub-") {
                // No game-log data for this metric - fall back to the season bar
                // so the recent view still shows every percentile bar.
                NavigationLink(value: MetricRoute(label: metric.label, category: metric.category, season: activeSeason)) {
                    MetricBar(metric: metric)
                        .padding(.horizontal, GridironGeo.padCard)
                        .padding(.vertical, 12)
                        .background(rowBackground)
                        .overlay(
                            Rectangle().fill(GridironPalette.divider).frame(height: GridironGeo.hairline),
                            alignment: .bottom
                        )
                }
                .buttonStyle(.plain)
            }
        case .both:
            NavigationLink(value: MetricRoute(label: metric.label, category: metric.category, season: activeSeason)) {
                DualMetricBar(
                    season: metric,
                    recent: recentMetric,
                    recentCaption: "Last \(recentWindowGames)"
                )
                .padding(.horizontal, GridironGeo.padCard)
                .padding(.vertical, 12)
                .background(rowBackground)
                .overlay(
                    Rectangle().fill(GridironPalette.divider).frame(height: GridironGeo.hairline),
                    alignment: .bottom
                )
            }
            .buttonStyle(.plain)
        }
    }

    private func recentMetric(for seasonMetric: Metric) -> Metric? {
        guard let w = recentWindow else { return nil }
        guard let spec = recentSpecs.first(where: { $0.label == seasonMetric.label || $0.seasonLabel == seasonMetric.label }),
              let v = w.metrics[spec.key],
              let pct = recentCurves?.curve(for: spec.seasonLabel)?.percentile(for: v) else { return nil }
        return Metric(
            id: "recent-\(spec.key)",
            label: seasonMetric.label,
            value: String(format: spec.format, v),
            percentile: pct,
            category: seasonMetric.category
        )
    }

    private func rebuildRecentCurves() {
        recentCurves = LeaguePercentileCurves(
            players: allPlayers,
            categories: [category],
            labels: category.metricPriorityOrder
        )
    }

    private func loadRecentLogs() async {
        guard store.isPro, let fetch = fetchGameLogs,
              let season = activeSeason ?? player.season else { return }
        recentLoading = true
        recentLoadError = nil
        do {
            recentLogs = try await fetch(player.playerId, season)
        } catch {
            // Distinguish "no games" from "fetch failed" - otherwise a network
            // error renders as an honest-looking "No games in the last N days".
            recentLogs = []
            recentLoadError = "Couldn't load recent games. Check your connection and try again."
        }
        recentLoading = false
    }

    /// Which of the four boards a traditional stat belongs to, so tapping a row
    /// opens the leaderboard that actually lists it. Without this a running
    /// back's Rec Yds row pushed the Passing board, which has no Rec Yds column.
    private static func standardCategory(for label: String, fallback: StandardStatCategory) -> StandardStatCategory {
        switch label.uppercased() {
        case "PASS YDS", "PASS TD", "INT", "CMP/ATT": return .passing
        case "CAR", "RUSH YDS", "RUSH TD":            return .rushing
        case "REC/TGT", "REC YDS", "REC TD":          return .receiving
        case "TACKLES", "SACKS", "DEF INT":           return .defense
        default:                                      return fallback
        }
    }

    private var standardFallbackCategory: StandardStatCategory {
        switch player.playerType?.lowercased() {
        case "qb":  return .passing
        case "rb":  return .rushing
        case "wr", "te": return .receiving
        default:    return .defense
        }
    }

    /// Stats where a lower number is the better outcome. Only one on a football
    /// line: interceptions thrown. A defender's takeaways are their own label
    /// ("Def INT") and read the normal way round.
    private static let lowerIsBetterStandard: Set<String> = ["INT"]

    /// Counting stats. Ranking these is honest but playing-time driven, a
    /// backup's 2 rushing touchdowns isn't a talent signal, so they're grouped
    /// separately from the rate stats and captioned as volume.
    ///
    /// Football's traditional line is almost entirely counting stats, unlike
    /// baseball's, so in practice the RATE group is usually empty and the
    /// sub-section bars are only drawn when both groups have something in them.
    private static let countingStats: Set<String> = [
        "G", "PASS YDS", "PASS TD", "INT", "CAR", "RUSH YDS", "RUSH TD",
        "REC", "REC YDS", "REC TD", "TACKLES", "SACKS", "DEF INT",
    ]

    /// Percentile rank for a traditional stat against the league.
    ///
    /// The pipeline publishes percentiles for the advanced metrics but not for
    /// the traditional line, so these are computed here: a player's position in
    /// the distribution of every same-position player who has the stat. Returns
    /// nil below a usable pool size rather than drawing a bar off five samples.
    private func standardStatPercentile(label: String, value: Double) -> Int? {
        let key = label.uppercased()
        let myType = player.playerType?.lowercased()
        let values: [Double] = allPlayers.compactMap { other in
            guard other.playerType?.lowercased() == myType else { return nil }
            guard let stat = other.standardStats?.first(where: { $0.label.uppercased() == key })
            else { return nil }
            return DashboardViewModel.rawNumeric(stat.value)
        }
        guard values.count >= 20 else { return nil }

        // Midpoint rank, so a cluster of identical values lands mid-band
        // instead of all sharing the top of it.
        let below = values.reduce(0) { $0 + ($1 < value ? 1 : 0) }
        let equal = values.reduce(0) { $0 + ($1 == value ? 1 : 0) }
        let raw = (Double(below) + Double(equal) / 2) / Double(values.count) * 100
        let oriented = Self.lowerIsBetterStandard.contains(key) ? 100 - raw : raw
        return max(1, min(100, Int(oriented.rounded())))
    }

    /// Standard stats rendered as the same `Metric` the percentile card uses, so
    /// both tabs read on one ruler. Stats with too small a league pool, and the
    /// composite ones like Cmp/Att that aren't a single number, keep their value
    /// but get no bar.
    private func standardMetrics(counting: Bool) -> [Metric] {
        (displayedPlayer.standardStats ?? [])
            .filter { Self.countingStats.contains($0.label.uppercased()) == counting }
            .map { stat in
                let pct = DashboardViewModel.rawNumeric(stat.value)
                    .flatMap { standardStatPercentile(label: stat.label, value: $0) }
                return Metric(
                    id: "std-\(stat.label)",
                    label: stat.label.uppercased(),
                    value: stat.value,
                    percentile: pct ?? 0,
                    category: Self.standardCategory(for: stat.label, fallback: standardFallbackCategory).metricCategory
                )
            }
    }

    /// Traditional stat label to the rollup's key. Games played and the
    /// composite Cmp/Att and Rec/Tgt lines have no single-number window
    /// counterpart, so they keep their season row alone.
    private static let standardRecentKeys: [String: String] = [
        "PASS YDS": "pass_yards", "PASS TD": "pass_tds", "INT": "interceptions",
        "CAR": "carries", "RUSH YDS": "rush_yards", "RUSH TD": "rush_tds",
        "REC": "receptions", "REC YDS": "rec_yards", "REC TD": "rec_tds",
        "TACKLES": "tackles", "SACKS": "sacks", "DEF INT": "def_ints",
    ]

    private var standardRecentForm: RecentForm? {
        recentFormLookup?(player.playerId, standardWindow)
    }

    /// The recent-window version of one traditional stat, or nil when the window
    /// has no figure for it. Percentile comes from the same league season
    /// distribution the season row uses, so both sit on one ruler.
    private func recentStandardMetric(for seasonMetric: Metric) -> Metric? {
        guard let key = Self.standardRecentKeys[seasonMetric.label],
              let value = standardRecentForm?.metrics[key] else { return nil }
        let text = seasonMetric.label == "SACKS"
            ? String(format: "%.1f", value)
            : Int(value.rounded()).formatted(.number.grouping(.automatic))
        return Metric(
            id: "std-recent-\(seasonMetric.label)",
            label: seasonMetric.label,
            value: text,
            percentile: standardStatPercentile(label: seasonMetric.label, value: value) ?? 0,
            category: seasonMetric.category
        )
    }

    private var standardStatsGridCard: some View {
        VStack(spacing: 0) {
            // The season already appears in the picker on the right; printing
            // it in the title too was saying it twice.
            GridironSectionBar(
                title: "STANDARD STATS",
                trailing: AnyView(seasonMenu)
            )

            // Recent only means something on the season the rollup covers; on a
            // past season the window would always be empty.
            if isCurrentSeasonActive {
                standardModePicker
                if effectiveStandardMode != .season {
                    standardWindowPicker
                }
            }

            if (displayedPlayer.standardStats ?? []).isEmpty {
                emptyStateCard(
                    icon: "chart.bar",
                    title: "Standard stats unavailable",
                    description: "Traditional stats are not available for this player."
                )
                .padding(.vertical, 24)
            } else {
                let rates = standardMetrics(counting: false)
                let counts = standardMetrics(counting: true)
                // Only worth labelling the two groups when there are two of
                // them. A football line is usually all volume, and a lone
                // "VOLUME" bar above every row says nothing.
                let labelled = !rates.isEmpty && !counts.isEmpty

                if !rates.isEmpty {
                    if labelled { GridironSubSectionBar(title: "RATE") }
                    standardRows(rates)
                }
                if !counts.isEmpty {
                    if labelled { GridironSubSectionBar(title: "VOLUME") }
                    standardRows(counts, startingIndex: rates.count)
                }
            }
        }
        .background(GridironPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
    }

    private var effectiveStandardMode: FormDisplayMode {
        (store.isPro && isCurrentSeasonActive) ? standardMode : .season
    }

    private var standardModePicker: some View {
        GridironSegmented(
            segments: FormDisplayMode.allCases.map {
                .init(value: $0, label: $0.rawValue, isLocked: !store.isPro && $0 != .season)
            },
            selection: $standardMode,
            onLockedTap: { _ in trialPitchTrigger = .recentForm }
        )
        .padding(.horizontal, GridironGeo.padInline)
        .padding(.vertical, 10)
        .background(GridironPalette.surfaceAlt)
        .onChange(of: standardMode) { _, mode in
            if mode != .season { Task { await loadRecentForm?(standardWindow) } }
        }
    }

    private var standardWindowPicker: some View {
        GridironSegmented(
            segments: RecentWindow.allCases.map { .init(value: $0, label: $0.segmentLabel) },
            selection: $standardWindow
        )
        .padding(.horizontal, GridironGeo.padInline)
        .padding(.bottom, 8)
        .background(GridironPalette.surfaceAlt)
        .onChange(of: standardWindow) { _, window in
            Task { await loadRecentForm?(window) }
        }
    }

    @ViewBuilder
    private func standardRows(_ metrics: [Metric], startingIndex: Int = 0) -> some View {
        ForEach(Array(metrics.enumerated()), id: \.element.id) { offset, metric in
            let recent = recentStandardMetric(for: metric)
            let background = (startingIndex + offset) % 2 == 0 ? GridironPalette.surface : GridironPalette.surfaceAlt

            NavigationLink(value: StandardStatRoute(
                stat: metric.label,
                category: Self.standardCategory(for: metric.label, fallback: standardFallbackCategory),
                season: activeSeason
            )) {
                Group {
                    switch effectiveStandardMode {
                    case .season:
                        MetricBar(metric: metric)
                    case .recent:
                        // No recent figure for this stat, fall back to season
                        // rather than dropping the row, matching the percentile
                        // card's behaviour.
                        MetricBar(metric: recent ?? metric)
                    case .both:
                        DualMetricBar(
                            season: metric,
                            recent: recent,
                            recentCaption: standardWindow.segmentLabel
                        )
                    }
                }
                .padding(.horizontal, GridironGeo.padCard)
                .padding(.vertical, 12)
                .background(background)
                .overlay(
                    Rectangle().fill(GridironPalette.divider).frame(height: GridironGeo.hairline),
                    alignment: .bottom
                )
            }
            .buttonStyle(.plain)
        }
    }

}

struct PercentileInfoSheet: View {
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Percentile Rankings")
                        .font(GridironType.playerName)
                        .foregroundStyle(GridironPalette.ink)

                    Text("Percentile rankings compare a player to others at the same position. A 90th percentile means the player ranks in the top 10% of the league for that metric.")
                        .font(GridironType.body)
                        .foregroundStyle(GridironPalette.inkSecondary)

                    VStack(alignment: .leading, spacing: 8) {
                        Label("Elite (75-100): Green bars", systemImage: "flame.fill")
                            .font(GridironType.bodyBold)
                            .foregroundStyle(GridironPalette.performanceHigh)
                        Label("Average (25-75): Charcoal bars", systemImage: "minus")
                            .font(GridironType.bodyBold)
                            .foregroundStyle(GridironPalette.inkSecondary)
                        Label("Below Average (0-25): Rust bars", systemImage: "snowflake")
                            .font(GridironType.bodyBold)
                            .foregroundStyle(GridironPalette.performanceLow)
                    }
                    .padding(.vertical, 8)

                    Text("Data refreshes nightly from public NFL advanced-stats leaderboards. Not all metrics are available for every player due to qualifying thresholds.")
                        .font(GridironType.small)
                        .foregroundStyle(GridironPalette.inkTertiary)
                }
                .padding(24)
            }
            .background(GridironPalette.canvas.ignoresSafeArea())
            .navigationTitle("About Percentiles")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

private struct PlayerPickerSheet: View {
    let players: [Player]
    var onSelect: (Player) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var searchText = ""

    var filteredPlayers: [Player] {
        guard !searchText.isEmpty else { return players }
        return players.filter {
            $0.name.localizedCaseInsensitiveContains(searchText) ||
            $0.team.localizedCaseInsensitiveContains(searchText)
        }
    }

    var body: some View {
        NavigationStack {
            List(filteredPlayers) { player in
                Button {
                    dismiss()
                    onSelect(player)
                } label: {
                    HStack(spacing: 12) {
                        PlayerHeadshot(team: player.team, initials: player.initials, size: 36)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(player.name)
                                .font(GridironType.bodyBold)
                                .foregroundStyle(GridironPalette.ink)
                            Text("\(player.team) · \(player.displayPosition)")
                                .font(GridironType.small)
                                .foregroundStyle(GridironPalette.inkTertiary)
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
            .searchable(text: $searchText, placement: .navigationBarDrawer(displayMode: .always), prompt: "Search players")
            .navigationTitle("Compare With")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        PlayerProfileView(player: SampleData.players[0], history: [SampleData.players[0]])
            .environmentObject(StoreService.shared)
    }
}
#endif
