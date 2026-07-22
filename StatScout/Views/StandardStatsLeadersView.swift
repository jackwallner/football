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
}

struct StandardStatsLeadersView: View {
    @EnvironmentObject private var store: StoreService
    let players: [Player]
    @State private var selectedCategory: StandardStatCategory = .passing
    @State private var selectedStat: String = "Pass Yds"
    @State private var sortDescending = true

    /// Stats where lower is the better outcome — sort defaults to ascending so the
    /// leader sits at the top.
    private static let lowerIsBetter: Set<String> = ["INT"]

    private func defaultDescending(for stat: String) -> Bool {
        !Self.lowerIsBetter.contains(stat)
    }

    // Available (numeric) stats per category — labels match the backend
    // standard_stats jsonb exactly.
    var availableStats: [String] {
        switch selectedCategory {
        case .passing:
            return ["Pass Yds", "Pass TD", "INT", "G"]
        case .rushing:
            return ["Rush Yds", "Rush TD", "Car", "G"]
        case .receiving:
            return ["Rec Yds", "Rec TD", "G"]
        case .defense:
            return ["Tackles", "Sacks", "Def INT", "G"]
        }
    }

    // Filter players who have the selected stat
    var filteredPlayers: [Player] {
        players.filter { player in
            guard let stats = player.standardStats else { return false }
            guard matchesPlayerType(player: player) else { return false }
            return stats.contains { $0.label == selectedStat }
        }
    }

    private func matchesPlayerType(player: Player) -> Bool {
        // Require a percentile in the matching category — that's the pipeline's
        // qualification signal — so the board shows genuine contributors rather
        // than anyone who happens to carry a shared standard-stat label.
        if selectedCategory == .defense {
            return player.playerType?.lowercased() == "def"
        }
        return player.metrics.contains { $0.category == selectedCategory.metricCategory }
    }

    // Sort players by the selected stat value
    var sortedPlayers: [Player] {
        filteredPlayers.sorted { p1, p2 in
            let v1 = statValue(for: p1)
            let v2 = statValue(for: p2)
            return sortDescending ? v1 > v2 : v1 < v2
        }
    }

    // Get numeric value for sorting
    private func statValue(for player: Player) -> Double {
        guard let stats = player.standardStats,
              let stat = stats.first(where: { $0.label == selectedStat }) else {
            return 0
        }
        return DashboardViewModel.rawNumeric(stat.value) ?? 0
    }
    
    // Get formatted stat value for display
    private func statDisplay(for player: Player) -> String {
        guard let stats = player.standardStats,
              let stat = stats.first(where: { $0.label == selectedStat }) else {
            return "—"
        }
        return stat.value
    }
    
    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                // Category selector
                categorySelector

                // Stat selector
                statSelector

                // Leaders list
                leadersList
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 12)
        }
        .scrollBounceBehavior(.basedOnSize)
        .background(GridironPalette.canvas.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
    }
    
    private var categorySelector: some View {
        HStack(spacing: 8) {
            ForEach(StandardStatCategory.allCases, id: \.self) { category in
                Button(action: {
                    selectedCategory = category
                    let stat = availableStats.first ?? "AVG"
                    selectedStat = stat
                    sortDescending = defaultDescending(for: stat)
                    let generator = UIImpactFeedbackGenerator(style: .light)
                    generator.impactOccurred()
                }) {
                    Text(category.rawValue)
                        .font(GridironType.bodyBold)
                        .foregroundStyle(selectedCategory == category ? .white : GridironPalette.ink)
                        .frame(maxWidth: .infinity)
                        .frame(height: 44)
                        .background(selectedCategory == category ? GridironPalette.turf : GridironPalette.surface)
                        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
                }
                .buttonStyle(.plain)
            }
        }
    }
    
    private var statSelector: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(availableStats, id: \.self) { stat in
                    Button(action: {
                        selectedStat = stat
                        sortDescending = defaultDescending(for: stat)
                        let generator = UIImpactFeedbackGenerator(style: .light)
                        generator.impactOccurred()
                    }) {
                        Text(stat)
                            .font(GridironType.body)
                            .foregroundStyle(selectedStat == stat ? .white : GridironPalette.ink)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                            .background(selectedStat == stat ? GridironPalette.midnight : GridironPalette.surface)
                            .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }
    
    private var leadersList: some View {
        VStack(spacing: 0) {
            // Header - tap stat column to toggle sort direction
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
                        Image(systemName: sortDescending ? "arrow.down" : "arrow.up")
                            .font(.system(size: 8, weight: .bold))
                            .foregroundStyle(GridironPalette.turf)
                    }
                    .frame(width: 80, alignment: .trailing)
                }
                .frame(height: GridironGeo.rowHeightHeader)
                .padding(.horizontal, GridironGeo.padInline)
                .background(GridironPalette.surfaceAlt)
                .overlay(Rectangle().fill(GridironPalette.divider).frame(height: GridironGeo.hairline), alignment: .bottom)
            }
            .buttonStyle(.plain)
            
            // Players
            if sortedPlayers.isEmpty {
                ContentUnavailableView {
                    Label("No data available", systemImage: "chart.bar")
                } description: {
                    Text("No players have \(selectedStat) data for the current season.")
                }
                .padding(.vertical, 48)
                .background(GridironPalette.surface)
            } else {
                ForEach(Array(sortedPlayers.prefix(50).enumerated()), id: \.element.id) { index, player in
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
                // Rank
                Text("\(rank)")
                    .font(GridironType.statSmall)
                    .foregroundStyle(GridironPalette.inkSecondary)
                    .frame(width: 36, alignment: .leading)
                    .monospacedDigit()

                // Player info
                HStack(spacing: 10) {
                    PlayerHeadshot(team: player.team, initials: player.initials, size: 36)
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

                // Team with color dot
                HStack(spacing: 4) {
                    TeamColorDot(abbr: player.team, size: 6)
                    Text(displayTeamAbbr(player.team))
                        .font(GridironType.small)
                        .foregroundStyle(GridironPalette.inkSecondary)
                }
                .frame(width: 44, alignment: .leading)

                // Stat value
                Text(statDisplay(for: player))
                    .font(GridironType.statMed)
                    .foregroundStyle(GridironPalette.turf)
                    .frame(width: 70, alignment: .trailing)
                    .monospacedDigit()
            }
            .frame(height: GridironGeo.rowHeight)
            .padding(.horizontal, GridironGeo.padInline)
            .background(rank % 2 == 1 ? GridironPalette.surface : GridironPalette.surfaceAlt)
            .overlay(
                Rectangle()
                    .fill(GridironPalette.divider)
                    .frame(height: GridironGeo.hairline),
                alignment: .bottom
            )
        }
        .buttonStyle(.plain)
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        StandardStatsLeadersView(players: SampleData.players)
            .environmentObject(StoreService.shared)
            .navigationTitle("Standard Stats")
    }
}
#endif
