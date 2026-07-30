import SwiftUI

enum StandardStatCategory: String, CaseIterable {
    case passing = "Passing"
    case rushing = "Rushing"
    case receiving = "Receiving"
    case defense = "Defense"

    var metricCategory: MetricCategory {
        switch self {
        case .passing: return .passing
        case .rushing: return .rushing
        case .receiving: return .receiving
        case .defense: return .defense
        }
    }

    var defaultPosition: PlayerPositionGroup {
        switch self {
        case .passing: return .qb
        case .rushing: return .rb
        case .receiving: return .wr
        case .defense: return .defense
        }
    }
}

/// Traditional leaderboard with the same position tabs and control vocabulary
/// as the Advanced board.
struct StandardStatsLeadersView: View {
    let players: [Player]
    @Binding var selectedStat: String
    @Binding var selectedPosition: PlayerPositionGroup
    @Binding var sortDescending: Bool
    var season: Int? = nil
    var boardBindings: StatsBoardBindings? = nil
    var viewModel: DashboardViewModel? = nil

    private var availableStats: [String] {
        StandardStatCatalog.stats(for: selectedPosition)
    }

    private var filteredPlayers: [Player] {
        players.filter { player in
            player.positionGroup == selectedPosition
                && numericStat(for: player) != nil
        }
    }

    private var sortedPlayers: [Player] {
        filteredPlayers.sorted { first, second in
            let firstValue = numericStat(for: first) ?? 0
            let secondValue = numericStat(for: second) ?? 0
            if firstValue != secondValue {
                return sortDescending
                    ? firstValue > secondValue
                    : firstValue < secondValue
            }
            return games(for: first) > games(for: second)
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            positionSelector
            controlRow

            ScrollView {
                VStack(spacing: 0) {
                leadersList
                    .padding(.horizontal, 12)
                    .padding(.top, 8)
                    .padding(.bottom, 12)
                Color.clear.frame(height: 88)
                }
            }
            .scrollBounceBehavior(.basedOnSize)
        }
        .background(GridironPalette.canvas.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .onChange(of: selectedPosition) { _, next in
            guard !StandardStatCatalog.stats(for: next).contains(selectedStat) else {
                return
            }
            selectedStat = StandardStatCatalog.defaultStat(for: next)
            sortDescending = StandardStatCatalog.defaultDescending(
                for: selectedStat,
                position: next
            )
        }
    }

    private var positionSelector: some View {
        GridironTabs(
            tabs: PlayerPositionGroup.allCases.map(\.rawValue),
            selected: Binding(
                get: { selectedPosition.rawValue },
                set: { rawValue in
                    guard let position = PlayerPositionGroup.allCases.first(where: {
                        $0.rawValue == rawValue
                    }) else { return }
                    selectedPosition = position
                }
            )
        )
        .padding(.top, 8)
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Position")
    }

    private var controlRow: some View {
        HStack(spacing: 8) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    if let boardBindings, let viewModel {
                        StatsBoardStatPicker(
                            viewModel: viewModel,
                            bindings: boardBindings
                        )
                    } else {
                        statMenu
                    }

                    SortDirectionButton(
                        descending: sortDescending,
                        statLabel: selectedStat
                    ) {
                        sortDescending.toggle()
                    }
                }
                .padding(.leading, 12)
                .padding(.trailing, 2)
                .padding(.vertical, 1)
            }
            .scrollBounceBehavior(.basedOnSize)
            .mask(
                LinearGradient(
                    stops: [
                        .init(color: .black, location: 0),
                        .init(color: .black, location: 0.88),
                        .init(color: .clear, location: 1),
                    ],
                    startPoint: .leading,
                    endPoint: .trailing
                )
            )

            if let boardBindings, let viewModel {
                StatsViewMenu(
                    viewModel: viewModel,
                    board: boardBindings.$board
                )
                .fixedSize()
            }
        }
        .padding(.trailing, 12)
        .frame(height: GridironControl.height + 2)
        .padding(.top, GridironGeo.controlRowGap)
    }

    private var statMenu: some View {
        StatPickerMenu(
            standard: availableStats.map {
                .init(id: $0, label: $0, isSelected: $0 == selectedStat)
            },
            activeLabel: selectedStat,
            onSelectStandard: { option in
                selectedStat = option.id
                sortDescending = StandardStatCatalog.defaultDescending(
                    for: option.id,
                    position: selectedPosition
                )
            }
        )
    }

    private var leadersList: some View {
        VStack(spacing: 0) {
            Button {
                sortDescending.toggle()
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
            } label: {
                HStack(spacing: 0) {
                    Text("RANK")
                        .font(GridironType.micro)
                        .foregroundStyle(GridironPalette.inkTertiary)
                        .frame(width: 42, alignment: .leading)
                    Text("PLAYER")
                        .font(GridironType.micro)
                        .foregroundStyle(GridironPalette.inkTertiary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Text("TEAM")
                        .font(GridironType.micro)
                        .foregroundStyle(GridironPalette.inkTertiary)
                        .frame(width: 44, alignment: .leading)
                    HStack(spacing: 4) {
                        Text(selectedStat.uppercased())
                            .font(GridironType.micro)
                            .foregroundStyle(GridironPalette.turf)
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                        Image(systemName: sortDescending ? "arrow.down" : "arrow.up")
                            .font(.system(size: 8, weight: .bold))
                            .foregroundStyle(GridironPalette.turf)
                    }
                    .frame(width: 80, alignment: .trailing)
                }
                .frame(height: GridironGeo.rowHeightHeader)
                .padding(.horizontal, GridironGeo.padInline)
                .background(GridironPalette.surfaceAlt)
                .overlay(
                    Rectangle()
                        .fill(GridironPalette.divider)
                        .frame(height: GridironGeo.hairline),
                    alignment: .bottom
                )
            }
            .buttonStyle(.plain)

            if sortedPlayers.isEmpty {
                ContentUnavailableView {
                    Label("No data available", systemImage: "chart.bar")
                } description: {
                    Text("No \(selectedPosition.rawValue) players have \(selectedStat) data for this season.")
                }
                .padding(.vertical, 48)
                .background(GridironPalette.surface)
            } else {
                ForEach(
                    Array(sortedPlayers.prefix(50).enumerated()),
                    id: \.element.id
                ) { index, player in
                    playerRow(rank: index + 1, player: player)
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

    private func playerRow(rank: Int, player: Player) -> some View {
        NavigationLink(value: player) {
            HStack(spacing: 0) {
                Text("\(rank)")
                    .font(GridironType.statSmall)
                    .foregroundStyle(GridironPalette.inkSecondary)
                    .frame(width: 36, alignment: .leading)

                HStack(spacing: 10) {
                    PlayerHeadshot(
                        team: player.team,
                        initials: player.initials,
                        size: 36
                    )
                    VStack(alignment: .leading, spacing: 2) {
                        Text(player.name)
                            .font(GridironType.bodyBold)
                            .foregroundStyle(GridironPalette.ink)
                            .lineLimit(1)
                            .minimumScaleFactor(0.85)
                            .truncationMode(.tail)
                        Text(player.displayPosition)
                            .font(GridironType.micro)
                            .foregroundStyle(GridironPalette.inkTertiary)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                HStack(spacing: 4) {
                    TeamColorDot(abbr: player.team, size: 6)
                    Text(displayTeamAbbr(player.team))
                        .font(GridironType.small)
                        .foregroundStyle(GridironPalette.inkSecondary)
                }
                .frame(width: 44, alignment: .leading)

                Text(statDisplay(for: player))
                    .font(GridironType.statMed)
                    .foregroundStyle(GridironPalette.turf)
                    .frame(width: 70, alignment: .trailing)
                    .monospacedDigit()
            }
            .frame(height: GridironGeo.rowHeight)
            .padding(.horizontal, GridironGeo.padInline)
            .background(
                rank.isMultiple(of: 2)
                    ? GridironPalette.surfaceAlt
                    : GridironPalette.surface
            )
            .overlay(
                Rectangle()
                    .fill(GridironPalette.divider)
                    .frame(height: GridironGeo.hairline),
                alignment: .bottom
            )
        }
        .buttonStyle(.plain)
    }

    /// Case-insensitive on purpose.
    ///
    /// Callers reach this board from several places and one of them displays its
    /// stat names in caps. An exact match meant a casing difference emptied the
    /// whole board and reported it as "no data for this season", which reads as
    /// a fact about the league rather than a mismatch between two strings. The
    /// route now passes the data's own spelling, and this makes a future one
    /// harmless instead of silent.
    private func standardStat(for player: Player) -> StandardStat? {
        player.standardStats?.first {
            $0.label.compare(selectedStat, options: .caseInsensitive) == .orderedSame
        }
    }

    private func numericStat(for player: Player) -> Double? {
        guard let stat = standardStat(for: player) else { return nil }
        return DashboardViewModel.rawNumeric(stat.value)
    }

    private func statDisplay(for player: Player) -> String {
        standardStat(for: player)?.value ?? "-"
    }

    private func games(for player: Player) -> Double {
        guard let value = player.standardStats?.first(where: {
            $0.label == "G"
        })?.value else { return 0 }
        return DashboardViewModel.rawNumeric(value) ?? 0
    }
}

/// Standalone traditional-stat drill-down reached from a player or team page.
struct StandardStatsLeaderboardScreen: View {
    let players: [Player]
    var season: Int? = nil
    @State private var stat: String
    @State private var position: PlayerPositionGroup
    @State private var sortDescending: Bool

    init(
        players: [Player],
        initialStat: String = "Pass Yds",
        initialPosition: PlayerPositionGroup = .qb,
        season: Int? = nil
    ) {
        self.players = players
        self.season = season
        _stat = State(initialValue: initialStat)
        _position = State(initialValue: initialPosition)
        _sortDescending = State(
            initialValue: StandardStatCatalog.defaultDescending(
                for: initialStat,
                position: initialPosition
            )
        )
    }

    init(
        players: [Player],
        initialStat: String,
        initialCategory: StandardStatCategory,
        season: Int? = nil
    ) {
        self.init(
            players: players,
            initialStat: initialStat,
            initialPosition: initialCategory.defaultPosition,
            season: season
        )
    }

    var body: some View {
        StandardStatsLeadersView(
            players: players,
            selectedStat: $stat,
            selectedPosition: $position,
            sortDescending: $sortDescending,
            season: season
        )
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        StandardStatsLeaderboardScreen(players: SampleData.players)
            .environmentObject(StoreService.shared)
            .navigationTitle("Standard Stats")
    }
}
#endif
