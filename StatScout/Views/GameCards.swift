import SwiftUI

/// A team's game this week, on its team page: the result, the next kickoff, or
/// the bye. It sits above the roster cards so the first thing a team page says
/// is what happened on the field.
struct TeamWeekGameCard: View {
    @Bindable var viewModel: DashboardViewModel
    let team: String

    var body: some View {
        if let week = viewModel.currentGameWeek {
            if let game = viewModel.currentGame(forTeam: team) {
                NavigationLink(value: GameRoute(gameId: game.id)) {
                    content(week: week, game: game)
                }
                .buttonStyle(.plain)
                .accessibilityHint("Opens the game")
            } else if week.phase == .regular {
                shell(week: week) {
                    Text("Bye week")
                        .font(GridironType.bodyBold)
                        .foregroundStyle(GridironPalette.ink)
                    Spacer(minLength: 0)
                }
            }
        }
    }

    private func content(week: GameWeek, game: Game) -> some View {
        shell(week: week) {
            TeamColorDot(abbr: game.opponent(of: team), size: 10)
            VStack(alignment: .leading, spacing: 2) {
                Text("\(game.matchupLabel(for: team)) · \(teamFullName(game.opponent(of: team)))")
                    .font(GridironType.bodyBold)
                    .foregroundStyle(GridironPalette.ink)
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
                Text(detail(game))
                    .font(GridironType.micro)
                    .foregroundStyle(GridironPalette.inkTertiary)
            }
            Spacer(minLength: 8)
            if let line = game.resultLine(for: team) {
                Text(line)
                    .font(GridironType.statMed)
                    .foregroundStyle(game.result(for: team) == "L" ? GridironPalette.performanceLow : GridironPalette.performanceHigh)
                    .monospacedDigit()
            }
            Image(systemName: "chevron.right")
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(GridironPalette.inkTertiary)
        }
    }

    private func detail(_ game: Game) -> String {
        switch game.status() {
        case .final:
            return viewModel.hasStats(game) ? "Final · box score" : "Final · stats arriving"
        case .inProgress, .awaitingScore:
            return "In progress"
        case .upcoming:
            return "\(game.dayLabel), \(game.kickoff?.formatted(date: .omitted, time: .shortened) ?? "time TBD")"
        }
    }

    private func shell<Content: View>(week: GameWeek, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(week.label.uppercased())
                .font(GridironType.micro)
                .foregroundStyle(GridironPalette.inkSecondary)
            HStack(spacing: 10) {
                content()
            }
        }
        .padding(.horizontal, GridironGeo.padInline)
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(GridironPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
        .contentShape(Rectangle())
    }
}

/// A player's most recent game on his profile: opponent, result, his line, and
/// a way into the box score. Free, and real: the one-game answer to "how did he
/// look?" that the recent-form cards can't give until there are several games.
struct PlayerLastGameCard: View {
    @Bindable var viewModel: DashboardViewModel
    let player: Player
    let season: Int
    let phase: SeasonPhase

    @State private var log: PlayerGameLog?
    @State private var loadedKey: String?

    private var key: String {
        "\(player.playerId)-\(season)-\(phase.rawValue)-\(viewModel.freshnessRevision ?? "none")"
    }

    private var game: Game? {
        log?.gameId.flatMap { viewModel.game(id: $0) }
    }

    var body: some View {
        // A VStack, not a Group: modifiers on an empty Group land on no view,
        // so the task that fetches the log would never run.
        VStack(spacing: 0) {
            if let log {
                card(log)
                    .padding(.top, 10)
            }
        }
        .task(id: key) { await load() }
    }

    private func load() async {
        guard loadedKey != key else { return }
        do {
            let logs = try await viewModel.fetchGameLogs(playerId: player.playerId, season: season, seasonPhase: phase)
            log = logs.max { $0.gameDate < $1.gameDate }
            loadedKey = key
        } catch {
            if !isTaskCancellation(error) { loadedKey = nil }
        }
    }

    @ViewBuilder
    private func card(_ log: PlayerGameLog) -> some View {
        let line = GameBoxScore(logs: [log]).lines[0]
        let team = log.team ?? player.team
        let summary = GameBoxScore.summary(line)
        let content = VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                Text(title(log).uppercased())
                    .font(GridironType.micro)
                    .foregroundStyle(GridironPalette.inkSecondary)
                Spacer(minLength: 0)
                if game != nil {
                    Text("Box score")
                        .font(GridironType.micro)
                        .foregroundStyle(GridironPalette.turf)
                    Image(systemName: "chevron.right")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(GridironPalette.turf)
                }
            }
            HStack(spacing: 8) {
                Text(matchup(log, team: team))
                    .font(GridironType.bodyBold)
                    .foregroundStyle(GridironPalette.ink)
                    .lineLimit(1)
                if let result = game?.resultLine(for: team) {
                    Text(result)
                        .font(GridironType.statSmall)
                        .foregroundStyle(game?.result(for: team) == "L" ? GridironPalette.performanceLow : GridironPalette.performanceHigh)
                }
                Spacer(minLength: 0)
            }
            if !summary.isEmpty {
                Text(summary)
                    .font(GridironType.small)
                    .foregroundStyle(GridironPalette.inkSecondary)
                    .monospacedDigit()
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(.horizontal, GridironGeo.padInline)
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(GridironPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
        .contentShape(Rectangle())

        if let game {
            NavigationLink(value: GameRoute(gameId: game.id)) { content }
                .buttonStyle(.plain)
                .accessibilityHint("Opens the game")
        } else {
            content
        }
    }

    private func title(_ log: PlayerGameLog) -> String {
        if let game { return "Last game · \(game.roundLabel)" }
        return "Last game · \(log.gameDate.formatted(DataCoverage.gameDayStyle))"
    }

    private func matchup(_ log: PlayerGameLog, team: String) -> String {
        if let game { return "\(game.matchupLabel(for: team)) · \(game.dayLabel)" }
        return "vs \(displayTeamAbbr(log.opponent ?? ""))"
    }
}
