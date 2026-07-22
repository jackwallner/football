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
                            .tracking(0.4)
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
                    .tracking(0.4)
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
            }

            positionSelector
        }
        .padding(.horizontal, 12)
        .padding(.top, 8)
    }

    private var positionSelector: some View {
        HStack(spacing: 6) {
            ForEach(PlayerPositionGroup.allCases) { position in
                Button {
                    viewModel.selectedPosition = position
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                } label: {
                    Text(position.rawValue)
                        .font(GridironType.smallBold)
                        .foregroundStyle(viewModel.selectedPosition == position ? .white : GridironPalette.inkSecondary)
                        .frame(maxWidth: .infinity)
                        .frame(height: 36)
                        .background(viewModel.selectedPosition == position ? GridironPalette.midnight : GridironPalette.surface)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }
                .buttonStyle(.plain)
            }
        }
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
                    .tracking(0.4)
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

    private var activeSortChip: some View {
        HStack(spacing: 8) {
            Text("LEAGUE LEADERS")
                .font(GridironType.sectionTitle)
                .tracking(0.8)
                .foregroundStyle(GridironPalette.ink)
            Spacer(minLength: 0)
            searchToggle
            filtersMenu
        }
        .padding(.horizontal, 12)
        .padding(.top, 8)
    }

    // Magnifier that expands the inline search row. When a query is active it
    // stays visually "on" so the user knows results are filtered.
    private var searchToggle: some View {
        Button {
            withAnimation(.easeInOut(duration: 0.2)) { isSearching.toggle() }
            if !isSearching { viewModel.searchText = "" }
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        } label: {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(isActiveSearch ? .white : GridironPalette.inkSecondary)
                .frame(width: 30, height: 30)
                .background(isActiveSearch ? GridironPalette.turf : GridironPalette.surface)
                .clipShape(Capsule())
                .overlay(Capsule().stroke(isActiveSearch ? Color.clear : GridironPalette.hairline, lineWidth: 0.5))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Search")
    }

    private var isActiveSearch: Bool { isSearching || !viewModel.searchText.isEmpty }

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
                                metricCategory: sortMetric.category
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
                .tracking(0.5)
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
                .tracking(0.4)
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
