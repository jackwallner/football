import SwiftUI

struct DashboardView: View {
    @EnvironmentObject private var store: StoreService
    @Bindable var viewModel: DashboardViewModel
    @State private var showingAbout = false
    @State private var paywallTrigger: PaywallTrigger?
    @State private var isSearching = false

    var body: some View {
        ZStack {
            ScrollView {
                LazyVStack(spacing: 0, pinnedViews: []) {
                    unifiedControlBar
                    if !viewModel.players.isEmpty {
                        activeSortChip
                        if isSearching || !viewModel.searchText.isEmpty {
                            searchRow
                            teamResults
                        }
                        if viewModel.showingRecent {
                            recentWindowRow
                        }
                    }
                    leaderboardSection
                    if !viewModel.players.isEmpty {
                        aboutFooter
                    }
                    // Lets the leaderboard scroll through the floating tab bar instead of
                    // stopping above it with canvas gray showing underneath the glass pill.
                    Color.clear.frame(height: 88)
                }
            }
            .scrollBounceBehavior(.basedOnSize)
            .scrollDismissesKeyboard(.interactively)
            .scrollClipDisabled()
            .refreshable {
                await viewModel.load()
            }
            if viewModel.isLoading && viewModel.players.isEmpty {
                loadingCard
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(GridironPalette.canvas)
        .sheet(isPresented: $showingAbout) {
            NavigationStack {
                AboutView(
                    lastUpdated: viewModel.lastUpdated,
                    onRequestReview: {
                        showingAbout = false
                        Task { @MainActor in
                            try? await Task.sleep(nanoseconds: 400_000_000)
                            ReviewPromptCoordinator.shared.requestEnjoymentPrompt()
                        }
                    }
                )
                    .navigationTitle("About")
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .topBarTrailing) {
                            Button("Done") { showingAbout = false }
                        }
                    }
            }
            .presentationDragIndicator(.visible)
        }
        .sheet(item: $paywallTrigger) { trigger in
            PaywallView(trigger: trigger)
        }
    }

    private var aboutFooter: some View {
        VStack(spacing: 8) {
            if !store.isPro {
                Button(action: { paywallTrigger = store.defaultUpgradeTrigger }) {
                    HStack(spacing: 6) {
                        Image(systemName: "crown.fill")
                            .font(.system(size: 10))
                        Text("Unlock StatScout+")
                            .font(GridironType.micro)
                    }
                    .foregroundStyle(.white)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(GridironPalette.turf)
                    .clipShape(Capsule())
                }
                .buttonStyle(.plain)
            }
            Button(action: { showingAbout = true }) {
                Text("About StatScout")
                    .font(GridironType.micro)
                    .foregroundStyle(GridironPalette.inkTertiary)
                    .padding(.vertical, 8)
            }
            .buttonStyle(.plain)
        }
        .padding(.vertical, 8)
    }

    private var unifiedControlBar: some View {
        VStack(spacing: 10) {
            if (viewModel.isLoading || viewModel.isHistoricalLoading) && !viewModel.players.isEmpty {
                loadingStatusBar
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .padding(.horizontal, 12)
            }

            positionSelector
        }
        .padding(.top, 8)
    }

    /// Position group is this app's metric-category control, the same tier as
    /// baseball's Hitting / Pitching / Fielding / Running header, so it uses the
    /// same underlined tabs. It used to be a row of filled rounded-rects 36pt
    /// tall, which is the page-level-switch shape at a fifth distinct control
    /// height, so the one control that decides what the whole screen is about
    /// looked like nothing else in the app.
    private var positionSelector: some View {
        GridironTabs(
            tabs: PlayerPositionGroup.allCases.map(\.rawValue),
            selected: Binding(
                get: { viewModel.selectedPosition.rawValue },
                set: { raw in
                    guard let next = PlayerPositionGroup.allCases.first(where: { $0.rawValue == raw }),
                          next != viewModel.selectedPosition else { return }
                    viewModel.selectedPosition = next
                }
            )
        )
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Position")
    }

    // Qualification is the only secondary filter left in the toolbar. Metric
    // selection and sort direction live in the leaderboard column header.
    private var filtersMenu: some View {
        Menu {
            Section("Qualifier") {
                ForEach(DashboardViewModel.QualifierLevel.allCases) { level in
                    Button {
                        viewModel.qualifierLevel = level
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    } label: {
                        if level == viewModel.qualifierLevel {
                            Label("\(level.rawValue) · \(level.description)", systemImage: "checkmark")
                        } else {
                            Text("\(level.rawValue) · \(level.description)")
                        }
                    }
                }
            }
        } label: {
            HStack(spacing: 4) {
                Image(systemName: "line.3.horizontal.decrease.circle")
                    .font(.system(size: 11, weight: .semibold))
                Text(viewModel.qualifierLevel.rawValue)
                    .font(GridironType.micro)
                Image(systemName: "chevron.down")
                    .font(.system(size: 9, weight: .bold))
            }
            .foregroundStyle(GridironPalette.inkSecondary)
            .padding(.horizontal, 10)
            .frame(height: 30)
            .background(GridironPalette.surface)
            .clipShape(Capsule())
            .overlay(Capsule().stroke(GridironPalette.hairline, lineWidth: 0.5))
        }
        .menuOrder(.fixed)
        .accessibilityLabel("Qualifier")
        .accessibilityValue(viewModel.qualifierLevel.rawValue)
    }

    /// This player's rolling-window figure for the sorted metric, when the
    /// board is in Recent mode and the rollup has one. Metrics the rollup has
    /// no column for (the Next Gen aggregates) simply keep their season number.
    private func recentValue(for player: Player, metric label: String?) -> String? {
        guard viewModel.showingRecent, store.isPro,
              let label,
              let key = RecentMetricKey.key(for: label),
              let value = viewModel.recentForm(for: player.playerId)?.metrics[key]
        else { return nil }
        return RecentMetricKey.format(value, label: label)
    }

    private var activeSortChip: some View {
        HStack(spacing: 8) {
            Text("LEAGUE LEADERS")
                .font(GridironType.sectionTitle)
                .foregroundStyle(GridironPalette.ink)
            Spacer(minLength: 0)
            recentChip
            searchToggle
            filtersMenu
        }
        .padding(.horizontal, 12)
        .padding(.top, 8)
    }

    /// Swaps the board from season totals to the rolling window.
    ///
    /// Locked rather than hidden, so a free user can see the feature exists.
    /// The label carries the window's own wording ("Last 5 games"), the same
    /// phrase the picker below it and every other screen use.
    private var recentChip: some View {
        Button {
            if store.isPro {
                withAnimation(.easeInOut(duration: 0.2)) {
                    viewModel.showingRecent.toggle()
                }
                if viewModel.showingRecent {
                    Task { await viewModel.loadRecentFormIfNeeded() }
                }
            } else {
                paywallTrigger = .recentForm
            }
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        } label: {
            GridironChip(
                title: viewModel.showingRecent ? viewModel.recentWindow.label : "Recent",
                systemImage: "flame.fill",
                isActive: viewModel.showingRecent,
                isLocked: !store.isPro
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Recent form")
        .accessibilityValue(viewModel.showingRecent ? viewModel.recentWindow.label : "off")
    }

    /// Window lengths, shown only while Recent is on: a picker for a mode
    /// you're not in is just noise in the header.
    private var recentWindowRow: some View {
        HStack(spacing: 6) {
            GridironSegmented(
                segments: RecentWindow.allCases.map { .init(value: $0, label: $0.segmentLabel) },
                selection: $viewModel.recentWindow
            )
            if viewModel.isRecentFormLoading {
                ProgressView().scaleEffect(0.6).frame(width: 20)
            }
        }
        .padding(.horizontal, 12)
        .padding(.top, 8)
        .onChange(of: viewModel.recentWindow) { _, _ in
            Task { await viewModel.loadRecentFormIfNeeded() }
        }
    }

    /// True while the search row is open or a query is still applied, so the
    /// chip stays visually "on" and the user knows results are filtered.
    private var isActiveSearch: Bool { isSearching || !viewModel.searchText.isEmpty }

    // Magnifier that expands the inline search row. When a query is active it
    // stays visually "on" so the user knows results are filtered.
    private var searchToggle: some View {
        Button {
            withAnimation(.easeInOut(duration: 0.2)) { isSearching.toggle() }
            if !isSearching { viewModel.searchText = "" }
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        } label: {
            GridironChip(systemImage: "magnifyingglass", isActive: isActiveSearch)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Search players or teams")
    }

    private var searchRow: some View {
        HStack(spacing: 8) {
            SearchField(text: $viewModel.searchText, focusOnAppear: true)
            Button("Cancel") {
                withAnimation(.easeInOut(duration: 0.2)) {
                    isSearching = false
                    viewModel.searchText = ""
                }
            }
            .font(GridironType.small)
            .foregroundStyle(GridironPalette.turf)
        }
        .padding(.horizontal, 12)
        .padding(.top, 8)
        .transition(.move(edge: .top).combined(with: .opacity))
    }

    private var sortHeader: some View {
        LeaderboardTableHeader(
            sortDescending: viewModel.sortDescending,
            sortLabel: viewModel.currentSortMetric ?? viewModel.sortLabel,
            metrics: viewModel.availableSortMetrics,
            onSelectMetric: { metric in
                viewModel.setUserSortMetric(metric)
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
            },
            onToggleDirection: {
                viewModel.toggleSortDirection()
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
            }
        )
    }

    /// Clubs matching the search, above the filtered player rows.
    ///
    /// Searching used to only ever narrow the list of players. Someone typing
    /// "chiefs" is usually after Kansas City, so the club itself is a result:
    /// one tap to the team page, with the roster still filtered underneath if
    /// that's what they wanted.
    @ViewBuilder
    private var teamResults: some View {
        let teams = viewModel.searchedTeams
        if !teams.isEmpty {
            VStack(spacing: 0) {
                ForEach(Array(teams.prefix(3).enumerated()), id: \.element) { index, team in
                    if index > 0 {
                        Rectangle()
                            .fill(GridironPalette.divider)
                            .frame(height: GridironGeo.hairline)
                    }
                    NavigationLink(value: TeamDestination(abbr: team)) {
                        HStack(spacing: 10) {
                            ZStack {
                                Circle()
                                    .fill(NFLTeamColor.color(team))
                                    .frame(width: 28, height: 28)
                                Text(displayTeamAbbr(team))
                                    .font(GridironType.micro)
                                    .foregroundStyle(.white)
                                    .lineLimit(1)
                                    .minimumScaleFactor(0.6)
                            }
                            Text(teamFullName(team))
                                .font(GridironType.bodyBold)
                                .foregroundStyle(GridironPalette.ink)
                            Text("TEAM PAGE")
                                .font(GridironType.micro)
                                .foregroundStyle(GridironPalette.inkTertiary)
                            Spacer(minLength: 0)
                            Image(systemName: "chevron.right")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundStyle(GridironPalette.inkTertiary)
                        }
                        .padding(.horizontal, GridironGeo.padCard)
                        .frame(height: GridironGeo.rowHeight)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            .background(GridironPalette.surface)
            .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
            .overlay(
                RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                    .stroke(GridironPalette.hairline, lineWidth: 0.5)
            )
            .padding(.horizontal, 12)
            .padding(.bottom, 8)
        }
    }

    private var leaderboardSection: some View {
        VStack(spacing: 0) {
            if viewModel.leaderboard.isEmpty && !viewModel.searchText.isEmpty {
                ContentUnavailableView {
                    Label("No players found", systemImage: "magnifyingglass")
                } description: {
                    Text("Try a different search term.")
                }
                .padding(.vertical, 24)
                .frame(minHeight: 200)
            } else if let errorMessage = viewModel.errorMessage, viewModel.leaderboard.isEmpty {
                ContentUnavailableView {
                    Label("Data Error", systemImage: "exclamationmark.triangle")
                } description: {
                    Text(errorMessage)
                } actions: {
                    Button("Retry") {
                        Task { await viewModel.load() }
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(GridironPalette.inkTertiary)
                }
                .padding(.vertical, 24)
                .frame(minHeight: 200)
            } else if viewModel.leaderboard.isEmpty && !viewModel.isLoading {
                let hasSeasonData = !viewModel.seasonPlayers.isEmpty
                ContentUnavailableView {
                    Label(hasSeasonData ? "No matching metrics" : "No players yet", systemImage: "football")
                } description: {
                    Text(hasSeasonData
                         ? "No metrics are available for \(viewModel.selectedPosition.rawValue) in \(String(viewModel.selectedSeason))."
                         : "No player data is available for the \(String(viewModel.selectedSeason)) season.")
                }
                .padding(.vertical, 24)
                .frame(minHeight: 200)
            } else {
                sortHeader
                let sortMetric = viewModel.currentSortMetricForDisplay
                LazyVStack(spacing: 0) {
                    ForEach(Array(viewModel.leaderboard.enumerated()), id: \.element.id) { index, player in
                        NavigationLink(value: player) {
                            LeaderboardTableRow(
                                rank: index + 1,
                                player: player,
                                metricLabel: sortMetric.label,
                                metricCategory: sortMetric.category,
                                valueOverride: recentValue(for: player, metric: sortMetric.label)
                            )
                        }
                        .buttonStyle(.plain)
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
        .padding(.horizontal, 12)
        .padding(.top, 8)
        .padding(.bottom, 12)
    }

    private var loadingCard: some View {
        VStack(spacing: 14) {
            ProgressView(value: min(max(viewModel.loadingProgress, 0), 1), total: 1)
                .progressViewStyle(.linear)
                .tint(GridironPalette.turf)
            Text(viewModel.loadingMessage)
                .font(GridironType.bodyBold)
                .foregroundStyle(GridironPalette.ink)
            Text("\(Int(min(max(viewModel.loadingProgress, 0), 1) * 100))%")
                .font(GridironType.micro)
                .foregroundStyle(GridironPalette.inkTertiary)
        }
        .padding(22)
        .frame(maxWidth: 300)
        .background(GridironPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
        .shadow(color: .black.opacity(0.08), radius: 16, y: 8)
        .padding(.horizontal, 24)
    }

    private var loadingStatusBar: some View {
        HStack(spacing: 10) {
            ProgressView(value: min(max(viewModel.loadingProgress, 0), 1), total: 1)
                .progressViewStyle(.linear)
                .tint(GridironPalette.turf)
                .frame(maxWidth: .infinity)
            Text(viewModel.loadingMessage)
                .font(GridironType.micro)
                .foregroundStyle(GridironPalette.inkSecondary)
                .lineLimit(1)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(GridironPalette.surface)
        .clipShape(Capsule())
        .overlay(
            Capsule()
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
        .padding(.horizontal, 12)
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        DashboardView(viewModel: DashboardViewModel())
            .environmentObject(StoreService.shared)
    }
}
#endif
