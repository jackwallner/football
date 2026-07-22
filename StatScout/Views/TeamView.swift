import SwiftUI

struct TeamView: View {
    @EnvironmentObject private var store: StoreService
    let team: String
    let players: [Player]
    var season: Int? = nil
    var viewModel: DashboardViewModel? = nil
    var fetchTeamGameLogs: ((String, Int, Date) async throws -> [PlayerGameLog])? = nil
    @State private var selectedTab: TeamTab = .percentiles
    @State private var searchText = ""
    @State private var isSearching = false
    // Default to Passing so the roster always shows a meaningful sort metric.
    @State private var selectedCategory: MetricCategory? = .passing
    @State private var sortDescending = true
    @State private var lastDefaultedSortKey: String? = nil
    @State private var showingTrial = false

    enum TeamTab: String, CaseIterable {
        case percentiles = "Percentiles"
        case roster = "Roster"
    }

    private var displaySeason: Int {
        season ?? players.compactMap(\.season).max() ?? Calendar.current.component(.year, from: Date())
    }

    private var leaguePlayers: [Player] {
        viewModel?.seasonPlayers ?? []
    }

    private var sortMetric: (label: String, category: MetricCategory)? {
        guard let category = selectedCategory else { return nil }
        for label in priorityMetrics(for: category) {
            if players.contains(where: { p in p.metrics.contains { $0.label == label && $0.category == category } }) {
                return (label, category)
            }
        }
        return nil
    }

    private var sortLabel: String {
        if selectedCategory == nil { return "Overall" }
        return sortMetric?.label ?? "Top Category"
    }

    private var rowDisplayMetric: (label: String?, category: MetricCategory?) {
        if let m = sortMetric { return (m.label, m.category) }
        if selectedCategory == nil { return ("Pass Yds", .passing) }
        return (nil, nil)
    }

    private func rawValue(_ player: Player) -> Double? {
        guard let m = sortMetric,
              let metric = player.metrics.first(where: { $0.label == m.label && $0.category == m.category })
        else { return nil }
        return DashboardViewModel.rawNumeric(metric.value)
    }

    private func fallbackPercentile(_ player: Player) -> Int {
        if let category = selectedCategory, let p = player.percentile(for: category) { return p }
        return player.overallPercentile
    }

    private var filteredPlayers: [Player] {
        let bySearch = searchText.isEmpty ? players : players.filter {
            $0.name.localizedCaseInsensitiveContains(searchText)
        }
        let byType = bySearch.filter { $0.matchesPlayerType(for: selectedCategory) }
        let byCategory = selectedCategory == nil
            ? byType
            : byType.filter { p in p.metrics.contains { $0.category == selectedCategory } }
        guard sortMetric != nil else {
            return byCategory.sorted {
                sortDescending ? fallbackPercentile($0) > fallbackPercentile($1) : fallbackPercentile($0) < fallbackPercentile($1)
            }
        }
        let ranked = byCategory.filter { rawValue($0) != nil }.sorted {
            let v1 = rawValue($0) ?? 0
            let v2 = rawValue($1) ?? 0
            return sortDescending ? v1 > v2 : v1 < v2
        }
        let tail = byCategory.filter { rawValue($0) == nil }.sorted {
            fallbackPercentile($0) > fallbackPercentile($1)
        }
        return ranked + tail
    }

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                TeamIdentityStrip(team: team, season: displaySeason)

                tabSelector
                    .padding(.horizontal, 12)
                    .padding(.top, 12)

                switch selectedTab {
                case .percentiles:
                    percentilesContent
                case .roster:
                    rosterContent
                }

                // Lets content scroll under the floating tab bar so the last
                // rows aren't trapped behind it — matches Dashboard.
                Color.clear.frame(height: 88)
            }
        }
        .scrollBounceBehavior(.basedOnSize)
        .scrollDismissesKeyboard(.interactively)
        .background(GridironPalette.canvas.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) { teamSwitcherMenu }
            if let viewModel {
                ToolbarItem(placement: .topBarTrailing) { navSeasonMenu(viewModel: viewModel) }
            }
        }
        .onAppear { applyDefaultDirectionIfMetricChanged() }
        .onChange(of: selectedCategory) { _, _ in
            applyDefaultDirectionIfMetricChanged()
        }
        // Season changes through the nav-bar menu rotate the roster data beneath
        // us; re-default the sort direction so the chip never displays a metric
        // the new season doesn't have.
        .onChange(of: displaySeason) { _, _ in
            applyDefaultDirectionIfMetricChanged()
        }
        .sheet(isPresented: $showingTrial) {
            TrialPitchSheet(trigger: .teamView)
        }
    }

    // MARK: - Tabs

    /// Mirrors the player profile's tab selector with equal-width turf controls
    /// that swap the card content below. Two tabs: the team's percentile profile and
    /// its sortable roster.
    private var tabSelector: some View {
        HStack(spacing: 8) {
            ForEach(TeamTab.allCases, id: \.self) { tab in
                let isSelected = selectedTab == tab
                Button {
                    withAnimation(.easeInOut(duration: 0.2)) { selectedTab = tab }
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                } label: {
                    Text(tab.rawValue)
                        .font(GridironType.bodyBold)
                        .foregroundStyle(isSelected ? .white : GridironPalette.ink)
                        .frame(maxWidth: .infinity)
                        .frame(height: 44)
                        .background(isSelected ? GridironPalette.turf : GridironPalette.surface)
                        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var percentilesContent: some View {
        VStack(spacing: 12) {
            TeamRankingsCard(
                team: team,
                season: displaySeason,
                players: players,
                leaguePlayers: leaguePlayers,
                fetchTeamGameLogs: fetchTeamGameLogs,
                onUpgradeTap: {
                    // Explicit tap — always answer it; the gate only caps
                    // automatic pop-ups.
                    showingTrial = true
                }
            )
        }
        .padding(.horizontal, 12)
        .padding(.top, 12)
    }

    private var rosterContent: some View {
        VStack(spacing: 0) {
            CategoryFilter(selectedCategory: $selectedCategory)
                .padding(.horizontal, 12)
                .padding(.top, 12)

            if !players.isEmpty {
                sortControlsRow
                if isSearching || !searchText.isEmpty {
                    searchRow
                }
            }

            rosterSection
        }
    }

    private func applyDefaultDirectionIfMetricChanged() {
        let key = sortMetric.map { "\($0.category.rawValue)|\($0.label)" } ?? "—"
        guard key != lastDefaultedSortKey else { return }
        lastDefaultedSortKey = key
        sortDescending = DashboardViewModel.defaultSortDescending(
            label: sortMetric?.label,
            category: sortMetric?.category
        )
    }

    /// Mirrors the Stats tab's sort UI — left-side chip showing the active
    /// metric (tap flips direction), and a magnifying-glass toggle on the right
    /// that expands an inline search row.
    private var sortControlsRow: some View {
        HStack(spacing: 8) {
            Button {
                sortDescending.toggle()
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
            } label: {
                HStack(spacing: 6) {
                    Text(sortLabel)
                        .font(GridironType.smallBold)
                        .foregroundStyle(GridironPalette.ink)
                    Image(systemName: sortDescending ? "arrow.down" : "arrow.up")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundStyle(GridironPalette.turf)
                }
                .padding(.horizontal, 12)
                .frame(height: 30)
                .background(GridironPalette.surface)
                .clipShape(Capsule())
                .overlay(Capsule().stroke(GridironPalette.hairline, lineWidth: 0.5))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Sorted by \(sortLabel), \(sortDescending ? "highest first" : "lowest first")")
            .accessibilityHint("Tap to flip sort direction")

            Spacer(minLength: 0)

            Button {
                withAnimation(.easeInOut(duration: 0.2)) { isSearching.toggle() }
                if !isSearching { searchText = "" }
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
            } label: {
                let active = isSearching || !searchText.isEmpty
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(active ? .white : GridironPalette.inkSecondary)
                    .frame(width: 30, height: 30)
                    .background(active ? GridironPalette.turf : GridironPalette.surface)
                    .clipShape(Capsule())
                    .overlay(Capsule().stroke(active ? Color.clear : GridironPalette.hairline, lineWidth: 0.5))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Search")
        }
        .padding(.horizontal, 12)
        .padding(.top, 12)
    }

    private var searchRow: some View {
        HStack(spacing: 8) {
            SearchField(text: $searchText, focusOnAppear: true)
            Button("Cancel") {
                withAnimation(.easeInOut(duration: 0.2)) {
                    isSearching = false
                    searchText = ""
                }
            }
            .font(GridironType.small)
            .foregroundStyle(GridironPalette.turf)
        }
        .padding(.horizontal, 12)
        .padding(.top, 8)
        .transition(.move(edge: .top).combined(with: .opacity))
    }

    private var rosterSection: some View {
        VStack(spacing: 0) {
            if players.isEmpty {
                emptyStateView(
                    icon: "person.2.slash",
                    title: "No players tracked",
                    description: "No players are tracked for \(teamFullName(team)) in the \(String(displaySeason)) season."
                )
            } else if filteredPlayers.isEmpty {
                let noCategoryMatch = searchText.isEmpty && selectedCategory != nil
                emptyStateView(
                    icon: "magnifyingglass",
                    title: "No players found",
                    description: noCategoryMatch
                        ? "No players match the selected category for this team."
                        : "Try a different search term."
                )
            } else {
                Button {
                    sortDescending.toggle()
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                } label: {
                    LeaderboardTableHeader(sortDescending: sortDescending, sortLabel: sortLabel)
                }
                .buttonStyle(.plain)

                ForEach(Array(filteredPlayers.enumerated()), id: \.element.id) { index, player in
                    NavigationLink(value: player) {
                        LeaderboardTableRow(
                            rank: index + 1,
                            player: player,
                            metricLabel: rowDisplayMetric.label,
                            metricCategory: rowDisplayMetric.category
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .background(GridironPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
        .padding(.horizontal, 12)
        .padding(.top, 12)
    }

    private func emptyStateView(icon: String, title: String, description: String) -> some View {
        ContentUnavailableView {
            Label(title, systemImage: icon)
        } description: {
            Text(description)
        }
        .padding(.vertical, 48)
    }

    /// Team name in the nav title doubles as a switcher menu — tap to jump to
    /// any other team without popping back to the Teams list.
    private var teamSwitcherMenu: some View {
        Menu {
            ForEach(allTeams, id: \.self) { abbr in
                NavigationLink(value: TeamDestination(abbr: abbr)) {
                    HStack {
                        Text(teamFullName(abbr))
                        if abbr == team {
                            Image(systemName: "checkmark")
                        }
                    }
                }
            }
        } label: {
            HStack(spacing: 4) {
                Text(teamFullName(team))
                    .font(GridironType.bodyBold)
                    .foregroundStyle(.white)
                Image(systemName: "chevron.down")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(.white.opacity(0.85))
            }
        }
        .menuOrder(.fixed)
        .accessibilityLabel("Team")
        .accessibilityValue(teamFullName(team))
        .accessibilityHint("Switch to another team")
    }

    private static let allTeamAbbrs: [String] = nflTeamAbbreviations

    private var allTeams: [String] {
        Self.allTeamAbbrs.sorted { teamFullName($0).localizedCompare(teamFullName($1)) == .orderedAscending }
    }

    private func navSeasonMenu(viewModel: DashboardViewModel) -> some View {
        Menu {
            ForEach(viewModel.availableSeasons, id: \.self) { season in
                let isLocked = viewModel.isSeasonLocked(season)
                Button {
                    if isLocked {
                        showingTrial = true
                    } else {
                        viewModel.selectedSeason = season
                    }
                } label: {
                    HStack {
                        Text(String(season))
                        if isLocked {
                            Image(systemName: "crown.fill")
                                .font(.system(size: 10))
                                .foregroundStyle(GridironPalette.inkTertiary)
                        }
                    }
                }
            }
        } label: {
            HStack(spacing: 5) {
                Image(systemName: "calendar")
                    .font(.system(size: 11, weight: .semibold))
                Text(String(viewModel.selectedSeason))
                    .font(GridironType.smallBold)
                Image(systemName: "chevron.down")
                    .font(.system(size: 9, weight: .bold))
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(GridironPalette.turf)
            .clipShape(Capsule())
        }
        .menuOrder(.fixed)
        .accessibilityLabel("Season")
        .accessibilityValue(String(viewModel.selectedSeason))
    }

    private func priorityMetrics(for category: MetricCategory) -> [String] {
        switch category {
        case .passing: return ["Pass Yds", "Pass TD", "Rating", "EPA/Play"]
        case .rushing: return ["Rush Yds", "Rush TD", "Y/C", "Rush EPA"]
        case .receiving: return ["Rec Yds", "Rec", "Rec TD", "YAC"]
        case .defense: return ["Tackles", "Sacks", "INT"]
        }
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        TeamView(
            team: "KC",
            players: SampleData.players.filter { $0.team == "KC" },
            season: 2025
        )
    }
}
#endif
