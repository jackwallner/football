import SwiftUI

/// Year-over-year history destination. Carries just the player id; the
/// destination rebuilds the season history from the view model so the route
/// stays cheap and Hashable.
struct YearCompareRoute: Hashable {
    let playerId: Int
    let playerName: String
}

/// Dedicated "Compare" tab. The comparison flows already live inside the
/// player profile (Year Compare tab + the compare toolbar button); this tab
/// doesn't replace those — it's a discoverable promo surface that puts both
/// head-to-head and year-over-year one tap away, and blurs behind a trial
/// pitch for non-Pro users.
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

    private var players: [Player] {
        viewModel.seasonPlayers.sorted { $0.name < $1.name }
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                intro
                playerVsPlayerCard
                yearOverYearCard
            }
            .padding(.horizontal, 12)
            .padding(.top, 12)
            .padding(.bottom, 16)
            .blur(radius: store.isPro ? 0 : 5)
            .disabled(!store.isPro)
            // Scroll-under spacer so content can pass behind the floating tab
            // bar — matches the Dashboard / Teams pattern.
            Color.clear.frame(height: 88)
        }
        .scrollBounceBehavior(.basedOnSize)
        .background(SavantPalette.canvas.ignoresSafeArea())
        // Bottom-anchored unlock so the blurred Compare cards stay visible as a
        // teaser above the gate, instead of being fully covered by the box.
        .overlay(alignment: .bottom) {
            if !store.isPro {
                lockedOverlay
                    .padding(.bottom, 100)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            if store.isPro, !viewModel.hasLoadedHistorical, !viewModel.isHistoricalLoading {
                Task { await viewModel.loadHistoricalIfNeeded() }
            }
        }
        .sheet(item: $picker) { target in
            // Hide the other slot's pick so a player can't be compared to themselves.
            ComparePlayerPicker(players: players.filter { candidate in
                switch target {
                case .playerA: return candidate.playerId != playerB?.playerId
                case .playerB: return candidate.playerId != playerA?.playerId
                case .yearPlayer: return true
                }
            }) { selected in
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
        .navigationDestination(item: $comparisonRoute) { route in
            PlayerComparisonView(playerA: route.playerA, playerB: route.playerB)
                .modifier(SavantNavBarPublic())
        }
        .navigationDestination(item: $yearRoute) { route in
            YearCompareDestination(route: route, viewModel: viewModel)
                .modifier(SavantNavBarPublic())
        }
    }

    private var intro: some View {
        VStack(spacing: 6) {
            Text("Compare")
                .font(SavantType.statLarge)
                .foregroundStyle(SavantPalette.ink)
            Text("Stack two players head-to-head, or track one player across seasons.")
                .font(SavantType.small)
                .foregroundStyle(SavantPalette.inkSecondary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
    }

    private var playerVsPlayerCard: some View {
        VStack(spacing: 0) {
            SavantSectionBar(title: "PLAYER VS PLAYER")

            VStack(spacing: 12) {
                HStack(spacing: 10) {
                    playerSlot(player: playerA, placeholder: "Player A") { picker = .playerA }
                    Text("vs")
                        .font(SavantType.smallBold)
                        .foregroundStyle(SavantPalette.inkTertiary)
                    playerSlot(player: playerB, placeholder: "Player B") { picker = .playerB }
                }

                Button {
                    if let a = playerA, let b = playerB {
                        comparisonRoute = ComparisonRoute(playerA: a, playerB: b)
                    }
                } label: {
                    Text("Compare")
                        .font(SavantType.bodyBold)
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 46)
                        .background(playerA != nil && playerB != nil
                                    ? SavantPalette.savantRed
                                    : SavantPalette.inkTertiary)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
                .disabled(playerA == nil || playerB == nil)
            }
            .padding(16)
        }
        .background(SavantPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: SavantGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: SavantGeo.radiusCard)
                .stroke(SavantPalette.hairline, lineWidth: 0.5)
        )
    }

    private var yearOverYearCard: some View {
        VStack(spacing: 0) {
            SavantSectionBar(title: "YEAR OVER YEAR")

            VStack(spacing: 12) {
                Text("Pick a player to see how their percentile rankings moved across every season.")
                    .font(SavantType.small)
                    .foregroundStyle(SavantPalette.inkSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)

                Button {
                    picker = .yearPlayer
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "calendar.badge.clock")
                            .font(.system(size: 14, weight: .semibold))
                        Text("Choose a player")
                            .font(SavantType.bodyBold)
                    }
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 46)
                    .background(SavantPalette.savantRed)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
            }
            .padding(16)
        }
        .background(SavantPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: SavantGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: SavantGeo.radiusCard)
                .stroke(SavantPalette.hairline, lineWidth: 0.5)
        )
    }

    private func playerSlot(player: Player?, placeholder: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 6) {
                if let player {
                    PlayerHeadshot(team: player.team, initials: player.initials, size: 44)
                    Text(player.name)
                        .font(SavantType.smallBold)
                        .foregroundStyle(SavantPalette.ink)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                } else {
                    ZStack {
                        Circle()
                            .fill(SavantPalette.surfaceAlt)
                            .frame(width: 44, height: 44)
                        Image(systemName: "plus")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(SavantPalette.inkTertiary)
                    }
                    Text(placeholder)
                        .font(SavantType.small)
                        .foregroundStyle(SavantPalette.inkTertiary)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(SavantPalette.surfaceAlt)
            .clipShape(RoundedRectangle(cornerRadius: SavantGeo.radiusCard))
        }
        .buttonStyle(.plain)
    }

    private var lockedOverlay: some View {
        VStack(spacing: 12) {
            Image(systemName: "crown.fill")
                .font(.system(size: 22))
                .foregroundStyle(Color.yellow)
            Text("Find the Edge")
                .font(SavantType.cardTitle)
                .foregroundStyle(SavantPalette.ink)
            Text("Compare is a StatScout+ feature.")
                .font(SavantType.small)
                .foregroundStyle(SavantPalette.inkSecondary)
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
                        .font(SavantType.bodyBold)
                    Image(systemName: "arrow.right")
                        .font(.system(size: 12, weight: .bold))
                }
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 48)
                .background(SavantPalette.savantRed)
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
            .buttonStyle(.plain)
            if let subtext = store.paywallBlurSubtext {
                Text(subtext)
                    .font(SavantType.micro)
                    .tracking(0.3)
                    .foregroundStyle(SavantPalette.inkTertiary)
                    .multilineTextAlignment(.center)
            }
        }
        .padding(20)
        .background(
            RoundedRectangle(cornerRadius: SavantGeo.radiusCard)
                .fill(SavantPalette.surface)
                .shadow(color: .black.opacity(0.1), radius: 16, y: 6)
        )
        .overlay(
            RoundedRectangle(cornerRadius: SavantGeo.radiusCard)
                .stroke(SavantPalette.hairline, lineWidth: 0.5)
        )
        .padding(.horizontal, 28)
    }

    private func perkRow(_ icon: String, _ text: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(SavantPalette.savantRed)
                .frame(width: 16)
            Text(text)
                .font(SavantType.small)
                .foregroundStyle(SavantPalette.inkSecondary)
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

    private var history: [Player] {
        viewModel.playerHistories[route.playerId] ?? []
    }

    var body: some View {
        ScrollView {
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
        }
        .scrollBounceBehavior(.basedOnSize)
        .background(SavantPalette.canvas.ignoresSafeArea())
        .navigationTitle(route.playerName)
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// Lightweight, self-contained player picker (the profile screen's picker is
/// private to that file).
private struct ComparePlayerPicker: View {
    let players: [Player]
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
                                .font(SavantType.bodyBold)
                                .foregroundStyle(SavantPalette.ink)
                            Text("\(player.team) · \(player.displayPosition)")
                                .font(SavantType.small)
                                .foregroundStyle(SavantPalette.inkTertiary)
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
            .searchable(text: $searchText, placement: .navigationBarDrawer(displayMode: .always), prompt: "Search players")
            .navigationTitle("Select Player")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }
}

/// Public mirror of RootTabView's private SavantNavBar so destinations pushed
/// from this file keep the same navy bar treatment.
struct SavantNavBarPublic: ViewModifier {
    func body(content: Content) -> some View {
        content
            .toolbarBackground(SavantPalette.savantNavy, for: .navigationBar)
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
