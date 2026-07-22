import SwiftUI

@Observable
final class TeamsViewModel {
    private static let favoritesKey = "favoriteTeam"

    var favoriteTeam: String? {
        didSet {
            if let team = favoriteTeam {
                UserDefaults.standard.set(team, forKey: Self.favoritesKey)
            } else {
                UserDefaults.standard.removeObject(forKey: Self.favoritesKey)
            }
        }
    }

    init() {
        self.favoriteTeam = UserDefaults.standard.string(forKey: Self.favoritesKey)
    }

    func isFavorite(_ team: String) -> Bool {
        favoriteTeam == team
    }

    func setFavorite(_ team: String) {
        favoriteTeam = team
    }

    func removeFavorite() {
        favoriteTeam = nil
    }
}

struct TeamsView: View {
    @EnvironmentObject private var store: StoreService
    let viewModel: DashboardViewModel
    @Binding var path: NavigationPath
    @State private var teamsViewModel = TeamsViewModel()
    @State private var searchText = ""
    @State private var showingTrial = false
    // Auto-enter the favorite team once per launch; popping back must not
    // re-push it, or the user can never reach the list.
    @State private var didAutoEnterFavorite = false

    private static let allTeams: [String] = nflTeamAbbreviations

    private var filteredTeams: [String] {
        let teams = searchText.isEmpty ? viewModel.teamsWithData : viewModel.teamsWithData.filter {
            teamFullName($0).localizedCaseInsensitiveContains(searchText) ||
            $0.localizedCaseInsensitiveContains(searchText)
        }
        // Plain alphabetical by full team name — the old score-based ordering was
        // confusing and the score itself was a mislabeled percentile. The
        // favorite is lifted into its own pinned section above the grid.
        return teams.sorted { teamFullName($0).localizedCompare(teamFullName($1)) == .orderedAscending }
    }

    /// The favorite, shown only when not actively searching so search results
    /// stay a single uninterrupted list.
    private var pinnedFavorite: String? {
        guard searchText.isEmpty, let fav = teamsViewModel.favoriteTeam else { return nil }
        return fav
    }

    /// All-teams grid with the pinned favorite removed so it isn't listed twice.
    private var gridTeams: [String] {
        guard let fav = pinnedFavorite else { return filteredTeams }
        return filteredTeams.filter { $0 != fav }
    }

    private var isInitiallyLoading: Bool {
        viewModel.isLoading && viewModel.teamsWithData.isEmpty
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                if isInitiallyLoading {
                    teamsLoadingState
                } else {
                    allTeamsSection
                }
                // Scroll-under spacer so the last grid row isn't trapped behind
                // the floating tab bar — matches the Dashboard pattern.
                Color.clear.frame(height: 88)
            }
            .padding(.top, 12)
        }
        .scrollBounceBehavior(.basedOnSize)
        .background(GridironPalette.canvas.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) { seasonMenu }
        }
        .refreshable {
            await viewModel.load()
        }
        .onAppear(perform: autoEnterFavoriteIfNeeded)
        .sheet(isPresented: $showingTrial) {
            TrialPitchSheet(trigger: .teamView)
        }
    }

    /// On first Teams visit, drop the user straight into their favorite team
    /// (the back button returns to the alphabetical list). Guarded so popping
    /// back or revisiting the tab doesn't trap them by re-pushing.
    private func autoEnterFavoriteIfNeeded() {
        guard !didAutoEnterFavorite,
              let favorite = teamsViewModel.favoriteTeam,
              path.isEmpty else { return }
        didAutoEnterFavorite = true
        path.append(TeamDestination(abbr: favorite))
    }

    private var teamsLoadingState: some View {
        VStack(spacing: 12) {
            ForEach(0..<6, id: \.self) { _ in
                HStack(spacing: 12) {
                    Circle()
                        .fill(GridironPalette.surfaceAlt)
                        .frame(width: 36, height: 36)
                    VStack(alignment: .leading, spacing: 6) {
                        RoundedRectangle(cornerRadius: 4)
                            .fill(GridironPalette.surfaceAlt)
                            .frame(width: 140, height: 12)
                        RoundedRectangle(cornerRadius: 4)
                            .fill(GridironPalette.surfaceAlt)
                            .frame(width: 40, height: 10)
                    }
                    Spacer()
                }
                .padding(.horizontal, 12)
                .frame(height: 56)
            }
        }
        .padding(.horizontal, 12)
        .background(GridironPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
        .padding(.horizontal, 12)
        .redacted(reason: .placeholder)
    }

    // Navigation-bar season selector matches the Stats tab: a compact turf pill
    // with calendar and year on the midnight bar. Replaces the old in-content
    // season header card so Teams and Stats read the same.
    private var seasonMenu: some View {
        Menu {
            if viewModel.isHistoricalLoading {
                Label("Loading past seasons…", systemImage: "hourglass")
            } else if !viewModel.hasLoadedHistorical {
                Button {
                    if store.isPro {
                        Task { await viewModel.loadHistoricalIfNeeded() }
                    } else {
                        // Explicit tap — always answer it; the gate only caps
                        // automatic pop-ups.
                        showingTrial = true
                    }
                } label: {
                    Label(store.isPro ? "Load past seasons" : "Past seasons require StatScout+",
                          systemImage: store.isPro ? "clock.arrow.circlepath" : "crown.fill")
                }
            }
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

    /// Logo grid replaces the old single-column list of rows. Each tile is a
    /// big colored disk with the abbreviation + full name, a corner star for
    /// favorites, and a long-press toggle so the row stays one-tap-to-navigate.
    private var allTeamsSection: some View {
        VStack(spacing: 0) {
            SearchField(text: $searchText)
                .padding(.horizontal, 12)
                .padding(.bottom, 12)

            if let fav = pinnedFavorite {
                HStack {
                    Text("FAVORITE TEAM")
                        .font(GridironType.micro)
                        .foregroundStyle(GridironPalette.inkSecondary)
                    Spacer()
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 8)

                FavoriteTeamCard(
                    abbr: fav,
                    destination: TeamDestination(abbr: fav),
                    onRemove: {
                        teamsViewModel.removeFavorite()
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    }
                )
                .padding(.horizontal, 12)
                .padding(.bottom, 16)
            }

            HStack {
                Text("ALL TEAMS")
                    .font(GridironType.micro)
                    .foregroundStyle(GridironPalette.inkSecondary)
                Spacer()
                if searchText.isEmpty {
                    Text("\(filteredTeams.count) teams")
                        .font(GridironType.micro)
                        .foregroundStyle(GridironPalette.inkTertiary)
                } else {
                    Button("Clear") { searchText = "" }
                        .font(GridironType.micro)
                        .foregroundStyle(GridironPalette.inkSecondary)
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 8)

            if filteredTeams.isEmpty {
                let noDataForSeason = searchText.isEmpty && viewModel.teamsWithData.isEmpty
                ContentUnavailableView {
                    Label(noDataForSeason ? "No teams available" : "No teams found", systemImage: "magnifyingglass")
                } description: {
                    Text(noDataForSeason
                         ? "No teams have player data for the \(String(viewModel.selectedSeason)) season. Try selecting a different season from the Leaders tab."
                         : "Try a different search term.")
                }
                .padding(.vertical, 48)
            } else {
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 3), spacing: 10) {
                    ForEach(gridTeams, id: \.self) { abbr in
                        TeamGridTile(
                            abbr: abbr,
                            isFavorite: teamsViewModel.isFavorite(abbr),
                            onFavoriteTap: {
                                if teamsViewModel.isFavorite(abbr) {
                                    teamsViewModel.removeFavorite()
                                } else {
                                    teamsViewModel.setFavorite(abbr)
                                }
                                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                            },
                            destination: TeamDestination(abbr: abbr)
                        )
                        .contextMenu {
                            Button {
                                if teamsViewModel.isFavorite(abbr) {
                                    teamsViewModel.removeFavorite()
                                } else {
                                    teamsViewModel.setFavorite(abbr)
                                }
                                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                            } label: {
                                Label(teamsViewModel.isFavorite(abbr) ? "Remove Favorite" : "Set as Favorite",
                                      systemImage: teamsViewModel.isFavorite(abbr) ? "star.slash" : "star.fill")
                            }
                        }
                    }
                }
                .padding(.horizontal, 12)
                .padding(.bottom, 12)
            }
        }
    }
}

// MARK: - Team Grid Tile

/// Large logo-led tile for the redesigned Teams grid. Tap to navigate; the
/// favorite star sits in the upper-right corner and the full team name reads
/// below the abbreviation disk. Long-press surfaces the favorite toggle.
struct TeamGridTile: View {
    let abbr: String
    let isFavorite: Bool
    var onFavoriteTap: (() -> Void)? = nil
    let destination: TeamDestination

    var body: some View {
        ZStack(alignment: .topTrailing) {
            NavigationLink(value: destination) {
                tileBody
            }
            .buttonStyle(.plain)

            Button {
                onFavoriteTap?()
            } label: {
                Image(systemName: isFavorite ? "star.fill" : "star")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(isFavorite ? Color.yellow : GridironPalette.inkTertiary)
                    .padding(6)
                    .background(Circle().fill(GridironPalette.surface))
                    .overlay(Circle().stroke(GridironPalette.hairline, lineWidth: 0.5))
            }
            .buttonStyle(.borderless)
            .accessibilityLabel(isFavorite ? "Remove favorite" : "Set as favorite")
            .padding(6)
        }
    }

    private var tileBody: some View {
        VStack(spacing: 8) {
            ZStack {
                Circle()
                    .fill(NFLTeamColor.color(abbr))
                    .frame(width: 64, height: 64)
                    .shadow(color: Color.black.opacity(0.08), radius: 4, y: 2)
                Text(abbr)
                    .font(.system(size: 18, weight: .bold, design: .default))
                    .foregroundStyle(.white)
            }
            .frame(height: 64)

            Text(teamFullName(abbr))
                .font(GridironType.smallBold)
                .foregroundStyle(GridironPalette.ink)
                .lineLimit(2)
                .minimumScaleFactor(0.8)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
        }
        .padding(.vertical, 14)
        .padding(.horizontal, 8)
        .frame(maxWidth: .infinity, minHeight: 130)
        .background(isFavorite ? GridironPalette.surfaceAlt : GridironPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(isFavorite ? GridironPalette.turf : GridironPalette.hairline,
                        lineWidth: isFavorite ? 1.5 : 0.5)
        )
    }
}

// MARK: - Favorite Team Card

/// Full-width "hero" row for the pinned favorite team. Reads as a featured item
/// distinct from the grid below — tap anywhere to open the team, tap the star to
/// unpin. This is what makes Favorite do something visible: your team is always
/// one tap away at the top of the list.
struct FavoriteTeamCard: View {
    let abbr: String
    let destination: TeamDestination
    var onRemove: (() -> Void)? = nil

    var body: some View {
        ZStack(alignment: .topTrailing) {
            NavigationLink(value: destination) {
                HStack(spacing: 14) {
                    ZStack {
                        Circle()
                            .fill(NFLTeamColor.color(abbr))
                            .frame(width: 52, height: 52)
                            .shadow(color: Color.black.opacity(0.08), radius: 4, y: 2)
                        Text(abbr)
                            .font(.system(size: 16, weight: .bold, design: .default))
                            .foregroundStyle(.white)
                    }

                    VStack(alignment: .leading, spacing: 3) {
                        Text("YOUR TEAM")
                            .font(GridironType.micro)
                            .foregroundStyle(GridironPalette.turf)
                        Text(teamFullName(abbr))
                            .font(GridironType.bodyBold)
                            .foregroundStyle(GridironPalette.ink)
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                    }

                    Spacer(minLength: 0)

                    Image(systemName: "chevron.right")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(GridironPalette.inkTertiary)
                        .padding(.trailing, 36)
                }
                .padding(14)
                .frame(maxWidth: .infinity)
                .background(GridironPalette.surfaceAlt)
                .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
                .overlay(
                    RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                        .stroke(GridironPalette.turf, lineWidth: 1.5)
                )
            }
            .buttonStyle(.plain)

            Button {
                onRemove?()
            } label: {
                Image(systemName: "star.fill")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(Color.yellow)
                    .padding(8)
                    .background(Circle().fill(GridironPalette.surface))
                    .overlay(Circle().stroke(GridironPalette.hairline, lineWidth: 0.5))
            }
            .buttonStyle(.borderless)
            .accessibilityLabel("Remove favorite")
            .padding(10)
        }
    }
}

// MARK: - Team Row

struct TeamRowContent: View {
    let abbr: String
    let isFavorite: Bool

    var body: some View {
        HStack(spacing: 0) {
            Circle()
                .fill(NFLTeamColor.color(abbr))
                .frame(width: 36, height: 36)
                .overlay(
                    Text(abbr)
                        .font(GridironType.smallBold)
                        .foregroundStyle(.white)
                )
                .padding(.trailing, 12)

            VStack(alignment: .leading, spacing: 2) {
                Text(teamFullName(abbr))
                    .font(GridironType.bodyBold)
                    .foregroundStyle(GridironPalette.ink)
                    .lineLimit(1)

                Text(abbr)
                    .font(GridironType.small)
                    .foregroundStyle(GridironPalette.inkTertiary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            Image(systemName: "chevron.right")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(GridironPalette.inkTertiary)
        }
        .padding(.leading, 12)
        .frame(height: 56)
        .contentShape(Rectangle())
        .background(isFavorite ? GridironPalette.surfaceAlt : GridironPalette.surface)
    }
}

// MARK: - Legacy Team Tile (for reference/previews)

struct TeamTile: View {
    let abbr: String
    var isFavorite: Bool = false

    var body: some View {
        VStack(spacing: 8) {
            ZStack {
                Circle()
                    .fill(NFLTeamColor.color(abbr))
                    .frame(width: 44, height: 44)
                Text(abbr)
                    .font(GridironType.smallBold)
                    .foregroundStyle(.white)

                if isFavorite {
                    // Star badge
                    VStack {
                        HStack {
                            Spacer()
                            Image(systemName: "star.fill")
                                .font(.system(size: 10))
                                .foregroundStyle(.yellow)
                                .shadow(radius: 1)
                                .offset(x: 4, y: -4)
                        }
                        Spacer()
                    }
                    .frame(width: 44, height: 44)
                }
            }

            Text(teamFullName(abbr))
                .font(GridironType.smallBold)
                .foregroundStyle(isFavorite ? GridironPalette.turf : GridironPalette.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
        .padding(.horizontal, 6)
        .background(isFavorite ? GridironPalette.surfaceAlt : GridironPalette.surface)
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(isFavorite ? GridironPalette.turf : GridironPalette.hairline, lineWidth: isFavorite ? 2 : 0.5)
        )
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        TeamsView(viewModel: DashboardViewModel(), path: .constant(NavigationPath()))
            .environmentObject(StoreService.shared)
    }
}
#endif
