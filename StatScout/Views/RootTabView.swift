import StoreKit
import SwiftUI

struct TeamDestination: Hashable {
    let abbr: String
}

struct MetricRoute: Hashable {
    let label: String
    let category: MetricCategory
    /// Which season's leaderboard to open. The player profile has its own
    /// season selector, so a route from a 2022 profile has to carry 2022,
    /// otherwise tapping Cmp% there opened the current-season leaderboard.
    var season: Int? = nil
}

/// Drill-down from a traditional stat row to its league leaderboard.
struct StandardStatRoute: Hashable {
    let stat: String
    let category: StandardStatCategory
    var season: Int? = nil
}

struct RootTabView: View {
    @AppStorage("hasCompletedOnboarding") private var hasCompletedOnboarding = false
    @EnvironmentObject private var store: StoreService
    @Environment(\.requestReview) private var requestReview
    @StateObject private var reviewPromptCoordinator = ReviewPromptCoordinator.shared
    @State private var viewModel: DashboardViewModel
    @State private var selection = 0
    @State private var showReviewPrompt = false
    @State private var reviewPromptInitialStep: ReviewPromptSheet.Step = .enjoyment
    @State private var reviewPromptShownThisSession = false
    @State private var pendingNativeReviewAfterDismiss = false
    // Owned here so TeamsView can auto-push the favorite team and the user can
    // still pop back to the list.
    @State private var teamsPath = NavigationPath()

    init(viewModel: DashboardViewModel) {
        _viewModel = State(initialValue: viewModel)
    }

    var body: some View {
        tabView
            .tint(GridironPalette.turf)
            .sheet(isPresented: $showReviewPrompt, onDismiss: {
            // "Maybe later" already recorded a soft defer; calling markShown
            // here would clear it and apply the full 120-day cooldown to a
            // user who most likely never saw Apple's prompt at all.
            if pendingNativeReviewAfterDismiss {
                pendingNativeReviewAfterDismiss = false
                ReviewPromptTracker.markSoftDeferred()
                requestReview()
            } else if !ReviewPromptTracker.isSoftDeferred {
                ReviewPromptTracker.markShown()
            }
        }) {
            ReviewPromptSheet(initialStep: reviewPromptInitialStep, onFinish: handleReviewPromptFinish)
        }
        .onReceive(NotificationCenter.default.publisher(for: .statscoutPositiveMomentForReview)) { _ in
            scheduleReviewPromptAfterPositiveMoment()
        }
        .onChange(of: reviewPromptCoordinator.pendingPresentation) { _, presentation in
            guard let presentation else { return }
            defer { reviewPromptCoordinator.clear() }
            guard !showReviewPrompt else { return }
            switch presentation {
            case .enjoymentPrompt:
                presentReviewPrompt(step: .enjoyment)
            case .feedbackOnly:
                presentReviewPrompt(step: .feedback)
            }
        }
    }

    private func scheduleReviewPromptAfterPositiveMoment() {
        guard ReviewPromptTracker.shouldShowAfterPositiveMoment(hasCompletedOnboarding: hasCompletedOnboarding),
              !reviewPromptShownThisSession,
              !showReviewPrompt
        else { return }

        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 3_500_000_000)
            guard !showReviewPrompt,
                  ReviewPromptTracker.shouldShowAfterPositiveMoment(hasCompletedOnboarding: hasCompletedOnboarding)
            else { return }
            ReviewPromptTracker.consumePendingPositiveMoment()
            reviewPromptInitialStep = .enjoyment
            reviewPromptShownThisSession = true
            showReviewPrompt = true
        }
    }

    private func handleReviewPromptFinish(_ outcome: ReviewPromptDismissOutcome) {
        showReviewPrompt = false
        if outcome == .enjoyedMaybeLater {
            pendingNativeReviewAfterDismiss = true
        }
    }

    private func presentReviewPrompt(step: ReviewPromptSheet.Step) {
        reviewPromptInitialStep = step
        reviewPromptShownThisSession = true
        showReviewPrompt = true
    }

    /// Hand-rolled tab bar rather than a `TabView`.
    ///
    /// On iOS 26 a `TabView` always draws its own Liquid Glass platter, and
    /// `.toolbarBackground(.hidden, for: .tabBar)` is a no-op against it, which
    /// is what made the bar read as a grey box sitting on the canvas. Owning the
    /// bar means there is no system background to fight.
    ///
    /// Tabs live in a `ZStack` and toggle visibility rather than being swapped,
    /// so each one's navigation stack and scroll position survive switching
    /// away and back. Inactive tabs are hidden from VoiceOver too: at
    /// `opacity(0)` they are still perfectly reachable via the rotor.
    private var tabView: some View {
        ZStack(alignment: .bottom) {
            ForEach(Tab.allCases) { tab in
                tabContent(tab)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .opacity(selection == tab.rawValue ? 1 : 0)
                    .allowsHitTesting(selection == tab.rawValue)
                    .accessibilityHidden(selection != tab.rawValue)
            }

            floatingTabBar
        }
        .ignoresSafeArea(edges: .bottom)
    }

    private enum Tab: Int, CaseIterable, Identifiable {
        case stats, trends, teams, compare

        var id: Int { rawValue }

        var title: String {
            switch self {
            case .stats: return "Stats"
            case .trends: return "Trends"
            case .teams: return "Teams"
            case .compare: return "Compare"
            }
        }

        var icon: String {
            switch self {
            case .stats: return "chart.bar.fill"
            case .trends: return "flame.fill"
            case .teams: return "shield.lefthalf.filled"
            case .compare: return "arrow.left.arrow.right"
            }
        }
    }

    @ViewBuilder
    private func tabContent(_ tab: Tab) -> some View {
        switch tab {
        case .stats: statsTab
        case .trends: trendsTab
        case .teams: teamsTab
        case .compare: compareTab
        }
    }

    private var floatingTabBar: some View {
        HStack(spacing: 0) {
            ForEach(Tab.allCases) { tab in
                TabBarButton(
                    icon: tab.icon,
                    label: tab.title,
                    isSelected: selection == tab.rawValue
                ) {
                    guard selection != tab.rawValue else { return }
                    selection = tab.rawValue
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                }
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 7)
        .background(.ultraThinMaterial.opacity(0.8), in: Capsule())
        .overlay(Capsule().stroke(GridironPalette.hairline, lineWidth: 0.5))
        .shadow(color: .black.opacity(0.10), radius: 12, y: 4)
        .padding(.bottom, 12)
    }

    private var statsTab: some View {
        NavigationStack {
            StatsView(viewModel: viewModel)
                .navigationTitle("Stats")
                .navigationBarTitleDisplayMode(.inline)
                .modifier(GridironNavBar())
                .modifier(HomeTabToolbar(lastUpdated: viewModel.lastUpdated))
                .modifier(StandardDestinations(viewModel: viewModel))
        }
    }

    private var trendsTab: some View {
        NavigationStack {
            HotColdView(
                viewModel: viewModel,
                isActive: selection == Tab.trends.rawValue
            )
                .navigationTitle("Trends")
                .navigationBarTitleDisplayMode(.inline)
                .modifier(GridironNavBar())
                .modifier(HomeTabToolbar(lastUpdated: viewModel.lastUpdated))
                .modifier(StandardDestinations(viewModel: viewModel))
        }
    }

    private var teamsTab: some View {
        NavigationStack(path: $teamsPath) {
            TeamsView(viewModel: viewModel, path: $teamsPath)
                .navigationTitle("Teams")
                .navigationBarTitleDisplayMode(.inline)
                .modifier(GridironNavBar())
                .modifier(HomeTabToolbar(lastUpdated: viewModel.lastUpdated))
                .modifier(StandardDestinations(viewModel: viewModel))
        }
    }

    private var compareTab: some View {
        NavigationStack {
            // CompareView declares its own ComparisonRoute / YearCompareRoute
            // destinations, so StandardDestinations is intentionally omitted
            // here to avoid a duplicate navigationDestination for the same type.
            CompareView(viewModel: viewModel)
                .navigationTitle("Compare")
                .navigationBarTitleDisplayMode(.inline)
                .modifier(GridironNavBar())
                .modifier(HomeTabToolbar(lastUpdated: viewModel.lastUpdated))
                .modifier(PlayerProfileDestination(viewModel: viewModel))
        }
    }

}

/// One item in the hand-rolled floating tab bar. The selected pill uses the
/// turf green at low opacity rather than a filled capsule so the bar stays
/// light over whatever content scrolls beneath it.
private struct TabBarButton: View {
    let icon: String
    let label: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Image(systemName: icon)
                    .font(.system(size: 20, weight: .semibold))
                Text(label)
                    .font(GridironType.smallBold)
            }
            .foregroundStyle(isSelected ? GridironPalette.turf : GridironPalette.inkSecondary)
            .frame(width: 78, height: 52)
            .background(
                isSelected ? GridironPalette.turf.opacity(0.12) : .clear,
                in: Capsule()
            )
        }
        .buttonStyle(.plain)
        .animation(.easeInOut(duration: 0.2), value: isSelected)
        .accessibilityLabel(label)
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : [.isButton])
    }
}

private struct GridironNavBar: ViewModifier {
    func body(content: Content) -> some View {
        content
            .toolbarBackground(GridironPalette.midnight, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
    }
}

/// Trailing toolbar group shared by the four home tabs: a settings gear, then
/// the upgrade CTA when the user isn't subscribed.
///
/// The gear is the only entry point to Settings that isn't buried; it used to
/// live only in a link under the bottom of the leaderboard, which nobody
/// scrolls to. Trailing rather than leading because Stats already owns the
/// leading slot with its season pill, and a control that moves between tabs
/// isn't an anchor.
private struct HomeTabToolbar: ViewModifier {
    @EnvironmentObject private var store: StoreService
    let lastUpdated: Date?
    /// Owned per tab, not shared. All four tabs stay alive in the ZStack, so a
    /// single shared flag would push Settings onto all four stacks at once.
    @State private var showingSettings = false
    @State private var paywallTrigger: PaywallTrigger?

    /// Yellow crown + short action verb on a filled pill. The old version was
    /// a bare yellow "Pro" label that read as a status badge rather than a
    /// button, tap-through rates were correspondingly weak. The verb makes
    /// the CTA unambiguous, and the trial-aware label appears when an intro
    /// offer is available.
    private var ctaLabel: String { store.upgradeCTALabel }

    private var upgradeButton: some View {
        Button {
            paywallTrigger = store.defaultUpgradeTrigger
        } label: {
            HStack(spacing: 4) {
                Image(systemName: "crown.fill")
                    .font(.system(size: 10, weight: .bold))
                Text(ctaLabel)
                    .font(GridironType.micro)
                    .fontWeight(.bold)
            }
            .foregroundStyle(GridironPalette.midnight)
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(Color.yellow)
            .clipShape(Capsule())
        }
        .accessibilityLabel("\(ctaLabel), unlock all features")
    }

    /// Outline cog, no filled circle behind it. The Liquid Glass container
    /// gave it a pale disc that made a secondary control louder than the
    /// content it sits above.
    private var settingsButton: some View {
        Button {
            showingSettings = true
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        } label: {
            Image(systemName: "gearshape")
                .font(.system(size: 16, weight: .regular))
                .foregroundStyle(.white.opacity(0.85))
        }
        .accessibilityLabel("Settings")
    }

    func body(content: Content) -> some View {
        content
            // A push rather than a bottom sheet: Settings is a place in the
            // app, not a modal interruption over what you were reading.
            .navigationDestination(isPresented: $showingSettings) {
                AboutView(
                    lastUpdated: lastUpdated,
                    onRequestReview: {
                        showingSettings = false
                        Task { @MainActor in
                            try? await Task.sleep(nanoseconds: 400_000_000)
                            ReviewPromptCoordinator.shared.requestEnjoymentPrompt()
                        }
                    }
                )
                .navigationTitle("Settings")
                .navigationBarTitleDisplayMode(.inline)
                .modifier(GridironNavBar())
            }
            .toolbar {
                // Declared before the CTA so the gear sits to its left.
                if #available(iOS 26.0, *) {
                    ToolbarItem(placement: .topBarTrailing) { settingsButton }
                        .sharedBackgroundVisibility(.hidden)
                } else {
                    ToolbarItem(placement: .topBarTrailing) { settingsButton }
                }
                if !store.isPro {
                    // The yellow pill is its own capsule; suppress the iOS 26
                    // Liquid Glass container so it doesn't read as a double-pill.
                    if #available(iOS 26.0, *) {
                        ToolbarItem(placement: .topBarTrailing) { upgradeButton }
                            .sharedBackgroundVisibility(.hidden)
                    } else {
                        ToolbarItem(placement: .topBarTrailing) { upgradeButton }
                    }
                }
            }
            // The toolbar CTA pitches; it doesn't hand over a price list. Same
            // sheet as every other in-app offer, so "Try Free" leads to the
            // trial in one more tap rather than to a plan comparison.
            .sheet(item: $paywallTrigger) { trigger in
                TrialPitchSheet(trigger: trigger)
            }
    }
}

/// Just the player-profile route. Split out of `StandardDestinations` so the
/// Compare tab, which owns its own comparison routes and so can't take the
/// whole bundle, can still push a player page. Following a player is free, and
/// a followed name that can't be tapped through to its own numbers is a dead
/// row on the one screen the user curated themselves.
struct PlayerProfileDestination: ViewModifier {
    let viewModel: DashboardViewModel

    func body(content: Content) -> some View {
        content
            .navigationDestination(for: Player.self) { player in
                let history = viewModel.playerHistories[player.playerId] ?? []
                let seasonPlayer = history.first {
                    $0.season == player.season
                        && $0.seasonPhase == player.seasonPhase
                } ?? player
                let profileSeason = seasonPlayer.season ?? viewModel.selectedSeason
                let profilePhase = seasonPlayer.seasonPhase
                PlayerProfileView(
                    player: seasonPlayer,
                    history: history,
                    allPlayers: viewModel.players(
                        forSeason: profileSeason,
                        phase: profilePhase
                    ),
                    isHistoricalLoading: viewModel.isHistoricalLoading,
                    hasLoadedHistorical: viewModel.hasLoadedHistorical,
                    historicalLoadingMessage: viewModel.loadingMessage,
                    historicalLoadingProgress: viewModel.loadingProgress,
                    loadHistorical: { await viewModel.loadHistoricalIfNeeded() },
                    fetchGameLogs: { id, season in
                        try await viewModel.fetchGameLogs(playerId: id, season: season)
                    },
                    recentFormLookup: { id, window in
                        viewModel.recentForm(
                            for: id,
                            window: window,
                            season: profileSeason,
                            phase: profilePhase
                        )
                    },
                    loadRecentForm: { window in
                        await viewModel.loadRecentFormIfNeeded(
                            window: window,
                            season: profileSeason,
                            phase: profilePhase
                        )
                    },
                    comparisonCatalog: ComparisonCatalog(
                        viewModel: viewModel,
                        defaultPhase: profilePhase
                    )
                )
                    .modifier(GridironNavBar())
            }
    }
}

private struct StandardDestinations: ViewModifier {
    let viewModel: DashboardViewModel

    func body(content: Content) -> some View {
        content
            .modifier(PlayerProfileDestination(viewModel: viewModel))
            .navigationDestination(for: TeamDestination.self) { dest in
                TeamView(
                    team: dest.abbr,
                    players: viewModel.players(forTeam: dest.abbr),
                    season: viewModel.selectedSeason,
                    viewModel: viewModel,
                    fetchTeamGameLogs: { team, season, since in
                        try await viewModel.fetchTeamGameLogs(team: team, season: season, sinceDate: since)
                    }
                )
                    .modifier(GridironNavBar())
            }
            .navigationDestination(for: MetricRoute.self) { route in
                let season = route.season ?? viewModel.selectedSeason
                MetricRankingView(
                    metricLabel: route.label,
                    metricCategory: route.category,
                    players: viewModel.players(forSeason: season),
                    season: season
                )
                    .modifier(GridironNavBar())
            }
            .navigationDestination(for: StandardStatRoute.self) { route in
                let season = route.season ?? viewModel.selectedSeason
                StandardStatsLeaderboardScreen(
                    players: viewModel.players(forSeason: season),
                    initialStat: route.stat,
                    initialCategory: route.category,
                    season: season
                )
                    .navigationTitle(route.stat + " · " + SeasonLabel.text(season))
                    .navigationBarTitleDisplayMode(.inline)
                    .modifier(GridironNavBar())
            }
            .navigationDestination(for: ComparisonRoute.self) { route in
                PlayerComparisonView(
                    playerA: route.playerA,
                    playerB: route.playerB,
                    catalog: ComparisonCatalog(
                        viewModel: viewModel,
                        defaultPhase: route.playerA.seasonPhase
                    )
                )
                    .modifier(GridironNavBar())
            }
    }
}
