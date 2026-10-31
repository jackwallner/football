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
/// Search on the Standard board reaches every position, not only the tab on
/// screen: a name typed from the QB tab must still find the receiver.
enum StandardBoardSearch {
    static func matches(_ player: Player, query: String) -> Bool {
        query.isEmpty
            || player.name.localizedCaseInsensitiveContains(query)
            || player.team.localizedCaseInsensitiveContains(query)
            || teamFullName(player.team).localizedCaseInsensitiveContains(query)
    }

    /// The selected position first, then the rest, only while searching.
    static func positions(selected: PlayerPositionGroup, query: String) -> [PlayerPositionGroup] {
        guard !query.isEmpty else { return [selected] }
        return [selected] + PlayerPositionGroup.allCases.filter { $0 != selected }
    }
}

struct StandardStatsLeadersView: View {
    let players: [Player]
    @Binding var selectedStat: String
    @Binding var selectedPosition: PlayerPositionGroup
    @Binding var sortDescending: Bool
    var season: Int? = nil
    var boardBindings: StatsBoardBindings? = nil
    var viewModel: DashboardViewModel? = nil
    @State private var isSearching = false
    @State private var searchText = ""

    private var availableStats: [String] {
        StandardStatCatalog.stats(for: selectedPosition)
    }

    private var trimmedQuery: String {
        searchText.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// The stat a position's rows are ranked by. The selected tab uses the
    /// chosen stat; another position, reached only through search, uses the
    /// chosen stat when it offers it and its own default otherwise.
    private func statLabel(for position: PlayerPositionGroup) -> String {
        if position == selectedPosition { return selectedStat }
        let offered = StandardStatCatalog.stats(for: position)
        return offered.contains(selectedStat) ? selectedStat : StandardStatCatalog.defaultStat(for: position)
    }

    private func filteredPlayers(for position: PlayerPositionGroup) -> [Player] {
        players.filter { player in
            player.positionGroup == position
                && numericStat(for: player) != nil
        }
    }

    private var sortedPlayers: [Player] { sortedPlayers(for: selectedPosition) }

    private func sortedPlayers(for position: PlayerPositionGroup) -> [Player] {
        let descending = position == selectedPosition
            ? sortDescending
            : StandardStatCatalog.defaultDescending(for: statLabel(for: position), position: position)
        return filteredPlayers(for: position).sorted { first, second in
            let firstValue = numericStat(for: first) ?? 0
            let secondValue = numericStat(for: second) ?? 0
            if firstValue != secondValue {
                return descending
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
            if isSearching {
                HStack(spacing: 8) {
                    SearchField(text: $searchText, focusOnAppear: true)
                    Button("Cancel") {
                        isSearching = false
                        searchText = ""
                    }
                    .font(GridironType.small)
                }
                .padding(.horizontal, 12)
                .padding(.top, 8)
            }

            ScrollView {
                VStack(spacing: 0) {
                leadersList
                    .padding(.horizontal, 12)
                    .padding(.top, 8)
                    .padding(.bottom, 12)
                if let pendingNote {
                    HStack(alignment: .top, spacing: 6) {
                        Image(systemName: "info.circle")
                            .font(.system(size: 10, weight: .semibold))
                        Text(pendingNote)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .font(GridironType.micro)
                    .foregroundStyle(GridironPalette.inkTertiary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 16)
                }
                Color.clear.frame(height: 88)
                }
            }
            .scrollBounceBehavior(.basedOnSize)
            .scrollDismissesKeyboard(.interactively)
            .refreshable { await viewModel?.load() }
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

            Button {
                isSearching.toggle()
                if !isSearching { searchText = "" }
            } label: {
                GridironChip(systemImage: "magnifyingglass", isActive: isSearching)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Search players or teams")

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
        LazyVStack(spacing: 0) {
            Button {
                sortDescending.toggle()
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
            } label: {
                HStack(spacing: 0) {
                    Text("RANK")
                        .font(GridironType.micro)
                        .foregroundStyle(GridironPalette.inkTertiary)
                        .frame(width: 42, alignment: .leading)
                    // Says who is on the list where the list is read. The rule
                    // otherwise lives only in the View menu, and "leaders" with
                    // no minimum in Week 1 reads like a ranking of the best.
                    Text(sampleLabel)
                        .font(GridironType.micro)
                        .foregroundStyle(GridironPalette.inkTertiary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.75)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Text("TEAM column")
                        .font(GridironType.micro)
                        .foregroundStyle(GridironPalette.inkTertiary)
                        .frame(width: 44, alignment: .leading)
                    HStack(spacing: 4) {
                        Text(MetricName.localized(selectedStat).localizedUppercase)
                            .font(GridironType.micro)
                            .foregroundStyle(GridironPalette.turf)
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                        Image(systemName: sortDescending ? "arrow.down" : "arrow.up")
                            .font(.system(size: 8, weight: .bold))
                            .foregroundStyle(GridironPalette.turf)
                    }
                    .frame(width: 100, alignment: .trailing)
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

            if viewModel?.isLoading == true && players.isEmpty {
                ProgressView("Loading player stats")
                    .padding(.vertical, 48)
            } else if sortedPlayers.isEmpty && trimmedQuery.isEmpty {
                ContentUnavailableView {
                    Label("No data available", systemImage: "chart.bar")
                } description: {
                    Text(String(localized: "No data for \(MetricName.localized(selectedStat)) among \(selectedPosition.localizedPluralName) this season."))
                }
                .padding(.vertical, 48)
                .background(GridironPalette.surface)
            } else {
                let sections = boardSections(query: trimmedQuery)
                if sections.isEmpty {
                    ContentUnavailableView.search(text: searchText)
                        .padding(.vertical, 24)
                }
                ForEach(sections) { section in
                    if section.position != selectedPosition {
                        sectionHeader(for: section.position)
                    }
                    ForEach(section.rows, id: \.player.id) { row in
                        playerRow(rank: row.rank, stripe: row.index, player: row.player, peerValues: section.peerValues)
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
    }

    private struct BoardRow {
        let index: Int
        let rank: Int
        let player: Player
    }

    private struct BoardSection: Identifiable {
        let position: PlayerPositionGroup
        let rows: [BoardRow]
        let peerValues: [String]
        var id: PlayerPositionGroup { position }
    }

    /// The selected position's rows, then, while a search is typed, the other
    /// positions' matches. Each position keeps its own rank and percentile
    /// cohort, so a receiver found from the QB tab is ranked among receivers.
    private func boardSections(query: String) -> [BoardSection] {
        StandardBoardSearch.positions(selected: selectedPosition, query: query).compactMap { position in
            let sorted = sortedPlayers(for: position)
            let ranks = competitionRanks(sorted.map { numericStat(for: $0) })
            let rows = sorted.enumerated().compactMap { index, player -> BoardRow? in
                StandardBoardSearch.matches(player, query: query)
                    ? BoardRow(index: index, rank: ranks[index], player: player)
                    : nil
            }
            guard !rows.isEmpty else { return nil }
            return BoardSection(
                position: position,
                rows: rows,
                peerValues: sorted.compactMap { standardStat(for: $0)?.value }
            )
        }
    }

    private func sectionHeader(for position: PlayerPositionGroup) -> some View {
        Text("\(position.localizedPluralName) · \(MetricName.localized(statLabel(for: position)))".localizedUppercase)
            .font(GridironType.micro)
            .foregroundStyle(GridironPalette.inkTertiary)
            .lineLimit(1)
            .frame(maxWidth: .infinity, alignment: .leading)
            .frame(height: GridironGeo.rowHeightHeader)
            .padding(.horizontal, GridironGeo.padInline)
            .background(GridironPalette.surfaceAlt)
    }

    private func playerRow(rank: Int, stripe: Int, player: Player, peerValues: [String]) -> some View {
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
                        Text([player.displayPosition, volumeText(for: player)].compactMap { $0 }.joined(separator: " · "))
                            .font(GridironType.micro)
                            .foregroundStyle(GridironPalette.inkTertiary)
                            .lineLimit(1)
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

                let pct = percentile(for: player, peerValues: peerValues)
                // A zero count has no honest rank; see `Metric.isUnranked`.
                // Not for a lower-is-better count: a passer's 0 INT is the
                // best line on the board, not an absence.
                let isZero = numericStat(for: player) == 0
                    && StandardStatSemantics.higherIsBetter(label: statLabel(for: player.positionGroup))
                HStack(spacing: 8) {
                    if isZero {
                        Color.clear.frame(width: 34, height: 7)
                    } else {
                        PercentileBarMini(percentile: pct)
                            .frame(width: 34)
                    }
                    Text(statDisplay(for: player))
                        .font(GridironType.statMed)
                        .foregroundStyle(isZero ? GridironPalette.inkTertiary : GridironPalette.turf)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                        .frame(width: 58, alignment: .trailing)
                        .monospacedDigit()
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(
                    isZero
                        ? String(localized: "\(MetricName.localized(statLabel(for: player.positionGroup))): \(statDisplay(for: player)), not ranked")
                        : String(localized: "\(MetricName.localized(statLabel(for: player.positionGroup))): \(statDisplay(for: player)), \(pct.ordinalString) percentile")
                )
            }
            .frame(height: GridironGeo.rowHeight)
            .padding(.horizontal, GridironGeo.padInline)
            .background(
                (stripe + 1).isMultiple(of: 2)
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
            $0.label.compare(statLabel(for: player.positionGroup), options: .caseInsensitive) == .orderedSame
        }
    }

    private var sampleLabel: String {
        guard let viewModel else { return String(localized: "Player").localizedUppercase }
        return (viewModel.qualifierLevel == .qualified ? String(localized: "Qualified Players") : String(localized: "All Players")).localizedUppercase
    }

    /// Defense on the live season, while PFR's advanced table is still out:
    /// this board is the whole defensive picture, and it should say why.
    private var pendingNote: String? {
        guard let viewModel,
              viewModel.selectedSeason == viewModel.freeSeason,
              viewModel.selectedPhase == .regular else { return nil }
        return MetricCoverage.pendingNote(
            category: selectedPosition.primaryCategory,
            advancedDefenseStatus: viewModel.dataFreshness?.advancedDefenseStatus,
            nextGenStatus: nil
        )
    }

    /// The volume behind the headline number: attempts for a passing stat,
    /// carries for rushing, targets for receiving, games otherwise. One game of
    /// 9 yards a carry means little on three carries.
    private func volumeText(for player: Player) -> String? {
        let stats = player.standardStats ?? []
        func value(_ label: String) -> String? {
            stats.first { $0.label.caseInsensitiveCompare(label) == .orderedSame }?.value
        }
        func denominator(_ pair: String?) -> String? {
            pair?.split(separator: "/").last.map(String.init)
        }
        let stat = statLabel(for: player.positionGroup).uppercased()
        if stat.hasPrefix("PASS") || stat == "INT" || stat == "CMP/ATT" || stat == "RATING" || stat == "Y/A" {
            return denominator(value("Cmp/Att")).map { String(localized: "\($0) att") }
        }
        if stat.hasPrefix("RUSH") || stat == "Y/C" {
            return value("Car").map { String(localized: "\($0) car") }
        }
        if stat.hasPrefix("REC") {
            return denominator(value("Rec/Tgt")).map { String(localized: "\($0) tgt") }
        }
        if player.positionGroup == .defense,
           let snaps = viewModel?.profile(for: player)?.defenseSnaps, snaps > 0 {
            return String(localized: "\(snaps) snaps")
        }
        guard stat != "G", let games = value("G"), let count = Int(games) else { return nil }
        return String(localized: "\(count) games")
    }

    private func numericStat(for player: Player) -> Double? {
        guard let stat = standardStat(for: player) else { return nil }
        // Via the shared semantics so a paired value (Cmp/Att, Rec/Tgt) sorts
        // on its rate rather than on the count in front of the slash.
        return StandardStatSemantics.numericValue(label: stat.label, value: stat.value)
    }

    /// Every row on this board has the stat it is ranked by - that is the
    /// filter - so every row can carry a percentile, drawn against the same
    /// position cohort the profile's traditional bars use.
    ///
    /// The cohort is passed in rather than recomputed per row: `filteredPlayers`
    /// walks the whole season pool, and a fifty-row board asking for it twice a
    /// row walked it a hundred times per redraw.
    private func percentile(for player: Player, peerValues: [String]) -> Int {
        guard let stat = standardStat(for: player) else { return 50 }
        return StandardStatSemantics.percentile(
            label: stat.label,
            value: stat.value,
            peerValues: peerValues
        )
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
