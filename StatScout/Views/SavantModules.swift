import SwiftUI

private func positionAndHandedness(_ player: Player) -> String {
    let pos = player.displayPosition.trimmingCharacters(in: .whitespaces)
    let hand = player.handedness.trimmingCharacters(in: CharacterSet.whitespaces.union(.punctuationCharacters))
    if pos.isEmpty && hand.isEmpty { return "" }
    if hand.isEmpty { return pos }
    if pos.isEmpty { return hand }
    return "\(pos) · \(hand)"
}

private func displayTeamFullName(_ abbr: String) -> String {
    let trimmed = abbr.trimmingCharacters(in: .whitespaces).uppercased()
    if trimmed.isEmpty || trimmed == "TBD" || trimmed == "\u{2014}" || trimmed == "-" {
        return "Free Agent"
    }
    return teamFullName(abbr)
}

// MARK: - Module 1: Player Identity Strip

struct PlayerIdentityStrip: View {
    let player: Player
    var showOverallBadge: Bool = false

    var body: some View {
        HStack(alignment: .center, spacing: 14) {
            PlayerHeadshot(team: player.team, initials: player.initials, size: 72)
                .overlay(Circle().stroke(.white, lineWidth: 2))
            VStack(alignment: .leading, spacing: 4) {
                Text(player.name)
                    .font(GridironType.playerName)
                    .foregroundStyle(GridironPalette.inkOnDark)
                    .lineLimit(1).minimumScaleFactor(0.7)
                Text(displayTeamFullName(player.team))
                    .font(GridironType.bodyBold)
                    .foregroundStyle(.white.opacity(0.85))
                Text(positionAndHandedness(player))
                    .font(GridironType.small)
                    .foregroundStyle(.white.opacity(0.65))
            }
            Spacer(minLength: 8)
            if showOverallBadge {
                OverallPercentileBadge(percentile: player.overallPercentile)
            }
        }
        .padding(.horizontal, GridironGeo.padPage)
        .padding(.vertical, GridironGeo.padPage)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(GridironPalette.midnight)
    }
}

struct TeamIdentityStrip: View {
    let team: String
    var season: Int? = nil

    private var normalizedTeam: String {
        normalizedTeamAbbreviation(team)
    }

    private var seasonLabel: String {
        let year = season ?? Calendar(identifier: .gregorian).component(.year, from: Date())
        return String(year) + " Season"
    }

    var body: some View {
        HStack(alignment: .center, spacing: 14) {
            ZStack {
                Circle()
                    .fill(NFLTeamColor.color(normalizedTeam))
                    .frame(width: 56, height: 56)
                Text(normalizedTeam)
                    .font(GridironType.pageTitle)
                    .foregroundStyle(.white)
            }
            VStack(alignment: .leading, spacing: 4) {
                Text(teamFullName(normalizedTeam))
                    .font(GridironType.playerName)
                    .foregroundStyle(GridironPalette.inkOnDark)
                    .lineLimit(1).minimumScaleFactor(0.7)
                Text(seasonLabel)
                    .font(GridironType.small)
                    .foregroundStyle(.white.opacity(0.65))
            }
            Spacer(minLength: 8)
        }
        .padding(.horizontal, GridironGeo.padPage)
        .padding(.vertical, GridironGeo.padPage)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(GridironPalette.midnight)
    }
}

// MARK: - Module 3: Section Bar

struct GridironSectionBar: View {
    let title: String
    var trailing: AnyView? = nil

    var body: some View {
        HStack(spacing: 0) {
            Text(title.uppercased())
                .font(GridironType.sectionTitle)
                .foregroundStyle(GridironPalette.ink)
                .padding(.leading, GridironGeo.padCard)
            Spacer()
            if let trailing { trailing.padding(.trailing, 12) }
        }
        .frame(height: GridironGeo.rowHeightHeader)
        .background(GridironPalette.surfaceSunk)
    }
}

struct GridironSubSectionBar: View {
    let title: String
    var trailing: String? = nil
    var trailingColor: Color = GridironPalette.inkSecondary

    var body: some View {
        HStack {
            Text(title.uppercased())
                .font(GridironType.micro)
                .foregroundStyle(GridironPalette.inkSecondary)
            Spacer()
            if let trailing {
                Text(trailing)
                    .font(GridironType.statSmall)
                    .foregroundStyle(trailingColor)
            }
        }
        .frame(height: 26)
        .padding(.horizontal, GridironGeo.padCard)
        .background(GridironPalette.surfaceAlt)
        .overlay(Rectangle().fill(GridironPalette.divider).frame(height: 0.5), alignment: .bottom)
    }
}

// MARK: - Module 5: Tab Bar

struct GridironTabs: View {
    let tabs: [String]
    @Binding var selected: String

    var body: some View {
        HStack(spacing: 0) {
            ForEach(tabs, id: \.self) { tab in
                Button(action: {
                    selected = tab
                    let generator = UIImpactFeedbackGenerator(style: .light)
                    generator.impactOccurred()
                }) {
                    VStack(spacing: 0) {
                        Text(tab.uppercased())
                            .font(GridironType.smallBold)
                            .foregroundStyle(selected == tab ? GridironPalette.ink : GridironPalette.inkTertiary)
                            .lineLimit(1)
                            .minimumScaleFactor(0.75)
                            .padding(.horizontal, 4)
                            .frame(maxWidth: .infinity)
                            .frame(height: 40)
                        Rectangle()
                            .fill(selected == tab ? GridironPalette.turf : Color.clear)
                            .frame(height: 3)
                    }
                }
                .buttonStyle(.plain)
                .frame(maxWidth: .infinity)
            }
        }
        .frame(maxWidth: .infinity)
        .background(GridironPalette.surface)
        .overlay(Rectangle().fill(GridironPalette.hairline).frame(height: GridironGeo.hairline), alignment: .bottom)
    }
}
