import SwiftUI

/// Year-over-year history destination. Carries just the player id; the
/// destination rebuilds the season history from the view model so the route
/// stays cheap and Hashable.
struct YearCompareRoute: Hashable {
    let playerId: Int
    let playerName: String
}

/// Dedicated "Compare" tab, and the home of the players you follow.
///
/// The comparison flows also live inside the player profile (Year Compare tab +
/// the compare toolbar button); this tab doesn't replace those, it puts both
/// head-to-head and year-over-year one tap away, and blurs them behind a trial
/// pitch for non-Pro users.
///
/// Following used to have nowhere to live at all. It sat briefly on the Trends
/// board, which made Trends do two jobs, a league leaderboard and a personal
/// list, and left the tab you'd look for your own players in with no mention of
/// them. The list lives here now, above the comparison cards it feeds, and
/// stays free: following is what makes the app feel like yours, and what's paid
/// is the payoff.
struct CompareView: View {
    @EnvironmentObject private var store: StoreService
    let viewModel: DashboardViewModel

    private enum PickerTarget: Identifiable {
        case playerA, playerB, yearPlayer
        var id: Int { hashValue }
    }

    @State private var playerA: Player?
    @State private var playerB: Player?
    @State private var picker: PickerTarget?
    @State private var comparisonRoute: ComparisonRoute?
    @State private var yearRoute: YearCompareRoute?
    @State private var showingTrial = false
    @State private var showingFollowSheet = false
    @State private var favorites = FavoritesStore.shared
    @State private var seasonA: Int?
    @State private var seasonB: Int?

    private var activeSeasonA: Int { seasonA ?? viewModel.selectedSeason }
    private var activeSeasonB: Int { seasonB ?? viewModel.selectedSeason }

    private func players(forSeason season: Int) -> [Player] {
        viewModel.players(forSeason: season).sorted { $0.name < $1.name }
    }

    private func resolved(_ player: Player?, season: Int) -> Player? {
        guard let player else { return nil }
        if player.season == season { return player }
        return viewModel.playerHistories[player.playerId]?.first { $0.season == season }
    }

    private var resolvedA: Player? { resolved(playerA, season: activeSeasonA) }
    private var resolvedB: Player? { resolved(playerB, season: activeSeasonB) }

    private var slotWarning: String? {
        if viewModel.isHistoricalLoading, resolvedA == nil || resolvedB == nil {
            return "Loading past seasons…"
        }
        if let playerA, resolvedA == nil {
            return "No \(activeSeasonA) data for \(playerA.name)."
        }
        if let playerB, resolvedB == nil {
            return "No \(activeSeasonB) data for \(playerB.name)."
        }
        return nil
    }

    /// The followed players that exist in the selected season, in the order
    /// they were followed.
    private var followedPlayers: [Player] {
        let roster = viewModel.players(forSeason: viewModel.selectedSeason)
        return favorites.playerIds.compactMap { id in
            roster.first { $0.playerId == id }
        }
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                yourPlayersCard

                // Only the comparison cards blur. Following is free, so putting
                // the whole page behind the gate would have hidden the one
                // thing a free user can actually do here.
                ZStack(alignment: .bottom) {
                    VStack(spacing: 12) {
                        playerVsPlayerCard
                        yearOverYearCard
                    }
                    .blur(radius: store.isPro ? 0 : 5)
                    .disabled(!store.isPro)
                    .allowsHitTesting(store.isPro)

                    if !store.isPro {
                        // The same gate every other locked module uses, rather
                        // than this screen's own floating panel.
                        BlurGateUnlock(
                            headline: "Stack any two players, or any player against his own past seasons",
                            trigger: .playerComparison
                        )
                        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
                    }
                }
            }
            .padding(.horizontal, 12)
            .padding(.top, 12)
            .padding(.bottom, 16)
            // Scroll-under spacer so content can pass behind the floating tab
            // bar, matches the Dashboard / Teams pattern.
            Color.clear.frame(height: 88)
        }
        .scrollBounceBehavior(.basedOnSize)
        .background(GridironPalette.canvas.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showingFollowSheet) {
            FollowPlayersSheet(viewModel: viewModel)
        }
        .onAppear {
            if store.isPro, !viewModel.hasLoadedHistorical, !viewModel.isHistoricalLoading {
                Task { await viewModel.loadHistoricalIfNeeded() }
            }
        }
        .task(id: "\(activeSeasonA)-\(activeSeasonB)") {
            guard store.isPro,
                  activeSeasonA != viewModel.selectedSeason
                    || activeSeasonB != viewModel.selectedSeason
            else { return }
            await viewModel.loadHistoricalIfNeeded()
        }
        .sheet(item: $picker) { target in
            let season: Int = {
                switch target {
                case .playerA: return activeSeasonA
                case .playerB: return activeSeasonB
                case .yearPlayer: return viewModel.selectedSeason
                }
            }()
            ComparePlayerPicker(
                players: players(forSeason: season).filter { candidate in
                    switch target {
                    case .playerA: return candidate.playerId != playerB?.playerId
                    case .playerB: return candidate.playerId != playerA?.playerId
                    case .yearPlayer: return true
                    }
                },
                season: season,
                isLoading: viewModel.isHistoricalLoading
            ) { selected in
                switch target {
                case .playerA: playerA = selected
                case .playerB: playerB = selected
                case .yearPlayer:
                    comparisonRoute = nil
                    yearRoute = YearCompareRoute(playerId: selected.playerId, playerName: selected.name)
                }
            }
        }
        .sheet(isPresented: $showingTrial) {
            TrialPitchSheet(trigger: .playerComparison)
        }
        .modifier(PlayerProfileDestination(viewModel: viewModel))
        .navigationDestination(item: $comparisonRoute) { route in
            PlayerComparisonView(
                playerA: route.playerA,
                playerB: route.playerB,
                catalog: ComparisonCatalog(viewModel: viewModel)
            )
                .modifier(GridironNavBarPublic())
        }
        .navigationDestination(item: $yearRoute) { route in
            YearCompareDestination(
                route: route,
                viewModel: viewModel,
                onChangePlayer: { picker = .yearPlayer }
            )
                .modifier(GridironNavBarPublic())
        }
    }

    private var intro: some View {
        VStack(spacing: 6) {
            Text("Compare")
                .font(GridironType.pageTitle)
                .foregroundStyle(GridironPalette.ink)
            Text("Stack two players head-to-head, or track one player across seasons.")
                .font(GridironType.small)
                .foregroundStyle(GridironPalette.inkSecondary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
    }

    /// The followed list. Free, and the first thing on the tab: it's the only
    /// personal state the app holds, and it feeds the slots below it.
    private var yourPlayersCard: some View {
        VStack(spacing: 0) {
            GridironSectionBar(
                title: "YOUR PLAYERS",
                trailing: AnyView(
                    Button {
                        showingFollowSheet = true
                    } label: {
                        Label(favorites.playerIds.isEmpty ? "Add" : "Edit", systemImage: "star")
                            .font(GridironType.micro)
                            .foregroundStyle(GridironPalette.turf)
                    }
                    .buttonStyle(.plain)
                )
            )

            if followedPlayers.isEmpty {
                VStack(spacing: 8) {
                    Text("Follow players to keep them one tap from a comparison, and to spot them on the Trends board.")
                        .font(GridironType.small)
                        .foregroundStyle(GridironPalette.inkSecondary)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                    Button {
                        showingFollowSheet = true
                    } label: {
                        Text("Follow players")
                            .font(GridironType.smallBold)
                            .foregroundStyle(.white)
                            .padding(.horizontal, 16)
                            .frame(height: GridironControl.height)
                            .background(GridironPalette.turf)
                            .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
                .padding(.vertical, 18)
                .padding(.horizontal, 20)
                .frame(maxWidth: .infinity)
            } else {
                ForEach(Array(followedPlayers.enumerated()), id: \.element.playerId) { index, player in
                    followedRow(player: player, index: index)
                }
                Text(store.isPro
                     ? "Tap a player to load them into a slot below."
                     : "Tap a player to see their stats. Head-to-head needs StatScout+.")
                    .font(GridironType.micro)
                    .foregroundStyle(GridironPalette.inkTertiary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, GridironGeo.padCard)
                    .padding(.vertical, 10)
            }
        }
        .background(GridironPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
    }

    /// A followed player, and what tapping him does.
    ///
    /// For Pro that's "load into a comparison slot". For everyone else it's
    /// "open his page": his own stats are free, so a free tap that threw up the
    /// comparison pitch would give the user nothing, on a list they built
    /// themselves. Head-to-head is what's paid; a player's own numbers never
    /// were. The pitch is still one row further down, on the blurred cards that
    /// actually need it.
    @ViewBuilder
    private func followedRow(player: Player, index: Int) -> some View {
        let inSlot = playerA?.playerId == player.playerId || playerB?.playerId == player.playerId

        HStack(spacing: 0) {
            if store.isPro {
                Button {
                    loadIntoSlot(player)
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                } label: {
                    followedRowLabel(player: player, inSlot: inSlot)
                }
                .buttonStyle(.plain)
                .accessibilityHint("Loads \(player.name) into a comparison slot")

                // A Pro tap is spent on the slot, which left the list you
                // curated yourself with no way through to any of the pages it
                // names. The chevron is its own target and does the obvious
                // thing.
                NavigationLink(value: player) {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(GridironPalette.inkTertiary)
                        .frame(width: 40, height: GridironGeo.rowHeight)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Open \(player.name)'s page")
            } else {
                NavigationLink(value: player) {
                    followedRowLabel(player: player, inSlot: inSlot)
                }
                .buttonStyle(.plain)
                .accessibilityHint("Opens \(player.name)'s stats")
            }
        }
        .padding(.trailing, store.isPro ? 4 : 0)
        .background(index % 2 == 0 ? GridironPalette.surface : GridironPalette.surfaceAlt)
        .overlay(
            Rectangle().fill(GridironPalette.divider).frame(height: GridironGeo.hairline),
            alignment: .bottom
        )
        .contextMenu {
            NavigationLink(value: player) {
                Label("Open player page", systemImage: "person.text.rectangle")
            }
            Button(role: .destructive) {
                favorites.toggleFavorite(playerId: player.playerId)
            } label: {
                Label("Unfollow", systemImage: "star.slash")
            }
        }
    }

    private func followedRowLabel(player: Player, inSlot: Bool) -> some View {
        HStack(spacing: 10) {
            PlayerHeadshot(team: player.team, initials: player.initials, size: 34)
            VStack(alignment: .leading, spacing: 2) {
                Text(player.name)
                    .font(GridironType.bodyBold)
                    .foregroundStyle(GridironPalette.ink)
                    .lineLimit(1)
                Text("\(displayTeamAbbr(player.team)) · \(player.displayPosition)")
                    .font(GridironType.micro)
                    .foregroundStyle(GridironPalette.inkTertiary)
            }
            Spacer(minLength: 0)
            if inSlot {
                Text("IN SLOT")
                    .font(GridironType.micro)
                    .foregroundStyle(.white)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(GridironPalette.turf)
                    .clipShape(Capsule())
            } else {
                // The glyph states the outcome: a slot to fill for Pro (the
                // chevron beside it opens the page), a page to open for
                // everyone else.
                Image(systemName: store.isPro ? "plus.circle" : "chevron.right")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(GridironPalette.inkTertiary)
            }
        }
        .padding(.leading, GridironGeo.padInline)
        .padding(.trailing, store.isPro ? 6 : GridironGeo.padInline)
        .frame(height: GridironGeo.rowHeight)
        .contentShape(Rectangle())
    }

    /// Fills the first empty slot, then replaces the older of the two, so
    /// tapping down a list keeps swapping the challenger against a held player
    /// rather than clearing both.
    private func loadIntoSlot(_ player: Player) {
        if playerA == nil || playerA?.playerId == player.playerId {
            playerA = player
        } else if playerB == nil || playerB?.playerId == player.playerId {
            playerB = player
        } else {
            playerB = player
        }
    }

    private var playerVsPlayerCard: some View {
        VStack(spacing: 0) {
            GridironSectionBar(title: "PLAYER VS PLAYER")

            VStack(spacing: 12) {
                HStack(alignment: .top, spacing: 10) {
                    slotColumn(
                        player: playerA,
                        placeholder: "Player A",
                        season: activeSeasonA,
                        onPickPlayer: { picker = .playerA },
                        onPickSeason: { seasonA = $0 }
                    )
                    Text("vs")
                        .font(GridironType.smallBold)
                        .foregroundStyle(GridironPalette.inkTertiary)
                        .padding(.top, 40)
                    slotColumn(
                        player: playerB,
                        placeholder: "Player B",
                        season: activeSeasonB,
                        onPickPlayer: { picker = .playerB },
                        onPickSeason: { seasonB = $0 }
                    )
                }

                if let slotWarning {
                    Text(slotWarning)
                        .font(GridironType.micro)
                        .foregroundStyle(GridironPalette.inkTertiary)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                }

                Button {
                    if let a = resolvedA, let b = resolvedB {
                        comparisonRoute = ComparisonRoute(playerA: a, playerB: b)
                    }
                } label: {
                    Text("Compare")
                        .font(GridironType.bodyBold)
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 46)
                        .background(resolvedA != nil && resolvedB != nil
                                    ? GridironPalette.turf
                                    : GridironPalette.inkTertiary)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
                .disabled(resolvedA == nil || resolvedB == nil)
            }
            .padding(16)
        }
        .background(GridironPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
    }

    private var yearOverYearCard: some View {
        VStack(spacing: 0) {
            GridironSectionBar(title: "YEAR OVER YEAR")

            VStack(spacing: 12) {
                Text("Pick a player to see how their percentile rankings moved across every season.")
                    .font(GridironType.small)
                    .foregroundStyle(GridironPalette.inkSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)

                Button {
                    picker = .yearPlayer
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "calendar.badge.clock")
                            .font(.system(size: 14, weight: .semibold))
                        Text("Choose a player")
                            .font(GridironType.bodyBold)
                    }
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 46)
                    .background(GridironPalette.turf)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
            }
            .padding(16)
        }
        .background(GridironPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
    }

    private func slotColumn(
        player: Player?,
        placeholder: String,
        season: Int,
        onPickPlayer: @escaping () -> Void,
        onPickSeason: @escaping (Int) -> Void
    ) -> some View {
        VStack(spacing: 6) {
            playerSlot(player: player, placeholder: placeholder, action: onPickPlayer)

            SeasonMenu(
                seasons: viewModel.availableSeasons,
                selected: season,
                isLocked: { viewModel.isSeasonLocked($0) },
                onSelect: { picked in
                    if viewModel.isSeasonLocked(picked) {
                        showingTrial = true
                    } else {
                        onPickSeason(picked)
                    }
                }
            ) {
                GridironInlinePill(systemImage: "calendar", title: String(season))
            }
            .accessibilityLabel("Season for \(player?.name ?? placeholder)")
        }
        .frame(maxWidth: .infinity)
    }

    private func playerSlot(player: Player?, placeholder: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 6) {
                if let player {
                    PlayerHeadshot(team: player.team, initials: player.initials, size: 44)
                    Text(player.name)
                        .font(GridironType.smallBold)
                        .foregroundStyle(GridironPalette.ink)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                } else {
                    ZStack {
                        Circle()
                            .fill(GridironPalette.surfaceAlt)
                            .frame(width: 44, height: 44)
                        Image(systemName: "plus")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(GridironPalette.inkTertiary)
                    }
                    Text(placeholder)
                        .font(GridironType.small)
                        .foregroundStyle(GridironPalette.inkTertiary)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(GridironPalette.surfaceAlt)
            .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        }
        .buttonStyle(.plain)
    }

    private var lockedOverlay: some View {
        VStack(spacing: 12) {
            Image(systemName: "crown.fill")
                .font(.system(size: 22))
                .foregroundStyle(Color.yellow)
            Text("Find the Edge")
                .font(GridironType.cardTitle)
                .foregroundStyle(GridironPalette.ink)
            Text("Compare is a StatScout+ feature.")
                .font(GridironType.small)
                .foregroundStyle(GridironPalette.inkSecondary)
                .multilineTextAlignment(.center)

            VStack(alignment: .leading, spacing: 6) {
                perkRow("person.2.fill", "Head-to-head comparisons across every metric")
                perkRow("chart.line.uptrend.xyaxis", "Year-over-year trends for any player")
                perkRow("calendar.badge.clock", "Every past season unlocked")
                perkRow("arrow.down.circle.fill", "Available offline, even at the stadium")
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.vertical, 4)

            Button {
                showingTrial = true
            } label: {
                HStack(spacing: 6) {
                    Text(store.paywallBlurCTA)
                        .font(GridironType.bodyBold)
                    Image(systemName: "arrow.right")
                        .font(.system(size: 12, weight: .bold))
                }
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 48)
                .background(GridironPalette.turf)
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
            .buttonStyle(.plain)
            if let subtext = store.paywallBlurSubtext {
                Text(subtext)
                    .font(GridironType.micro)
                    .foregroundStyle(GridironPalette.inkTertiary)
                    .multilineTextAlignment(.center)
            }
        }
        .padding(20)
        .background(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .fill(GridironPalette.surface)
                .shadow(color: .black.opacity(0.1), radius: 16, y: 6)
        )
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
        .padding(.horizontal, 28)
    }

    private func perkRow(_ icon: String, _ text: String) -> some View {
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
}

/// Resolves a `YearCompareRoute` into the player's season history and renders
/// the existing year-over-year comparison. Empty-history players surface a
/// graceful message instead of a blank screen.
private struct YearCompareDestination: View {
    let route: YearCompareRoute
    let viewModel: DashboardViewModel
    var onChangePlayer: (() -> Void)? = nil

    private var history: [Player] {
        viewModel.playerHistories[route.playerId] ?? []
    }

    private var latest: Player? {
        history.max { ($0.season ?? 0) < ($1.season ?? 0) }
    }

    @ViewBuilder
    private var playerBar: some View {
        HStack(spacing: 10) {
            if let latest {
                NavigationLink(value: latest) {
                    HStack(spacing: 10) {
                        PlayerHeadshot(team: latest.team, initials: latest.initials, size: 34)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(route.playerName)
                                .font(GridironType.bodyBold)
                                .foregroundStyle(GridironPalette.ink)
                                .lineLimit(1)
                            Text("\(displayTeamAbbr(latest.team)) · \(latest.displayPosition)")
                                .font(GridironType.micro)
                                .foregroundStyle(GridironPalette.inkTertiary)
                        }
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityHint("Opens \(route.playerName)'s page")
            } else {
                Text(route.playerName)
                    .font(GridironType.bodyBold)
                    .foregroundStyle(GridironPalette.ink)
            }

            Spacer(minLength: 0)

            if let onChangePlayer {
                Button(action: onChangePlayer) {
                    GridironInlinePill(
                        systemImage: "arrow.triangle.2.circlepath",
                        title: "Change"
                    )
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Compare a different player's seasons")
            }
        }
        .padding(.horizontal, GridironGeo.padInline)
        .padding(.vertical, 10)
        .background(GridironPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
        .padding(.horizontal, 12)
        .padding(.top, 12)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                playerBar
                if history.count < 2 {
                    ContentUnavailableView {
                        Label("Not enough history", systemImage: "calendar.badge.clock")
                    } description: {
                        Text(viewModel.isHistoricalLoading
                             ? "Loading past seasons for \(route.playerName)…"
                             : "\(route.playerName) doesn't have multiple seasons of data to compare.")
                    }
                    .padding(.vertical, 64)
                } else {
                    YearComparisonView(history: history)
                }
                Color.clear.frame(height: 88)
            }
        }
        .scrollBounceBehavior(.basedOnSize)
        .background(GridironPalette.canvas.ignoresSafeArea())
        .navigationTitle(route.playerName)
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// Lightweight, self-contained player picker (the profile screen's picker is
/// private to that file).
struct ComparePlayerPicker: View {
    let players: [Player]
    var season: Int? = nil
    var isLoading: Bool = false
    var onSelect: (Player) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var searchText = ""

    private var filtered: [Player] {
        guard !searchText.isEmpty else { return players }
        return players.filter {
            $0.name.localizedCaseInsensitiveContains(searchText) ||
            $0.team.localizedCaseInsensitiveContains(searchText)
        }
    }

    var body: some View {
        NavigationStack {
            List(filtered) { player in
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
            .overlay {
                if filtered.isEmpty {
                    ContentUnavailableView {
                        Label(
                            isLoading ? "Loading players…" : "No players",
                            systemImage: "person.slash"
                        )
                    } description: {
                        if isLoading {
                            Text("Pulling the \(season.map(String.init) ?? "") roster.")
                        } else if !searchText.isEmpty {
                            Text("Nobody matches “\(searchText)”.")
                        } else if let season {
                            Text("No player data for the " + String(season) + " season.")
                        }
                    }
                }
            }
            .searchable(text: $searchText, placement: .navigationBarDrawer(displayMode: .always), prompt: "Search players")
            .navigationTitle(season.map { "Select Player · " + String($0) } ?? "Select Player")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }
}

/// Public mirror of RootTabView's private GridironNavBar so destinations pushed
/// from this file keep the same midnight bar treatment.
struct GridironNavBarPublic: ViewModifier {
    func body(content: Content) -> some View {
        content
            .toolbarBackground(GridironPalette.midnight, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        CompareView(viewModel: DashboardViewModel())
            .environmentObject(StoreService.shared)
    }
}
#endif
