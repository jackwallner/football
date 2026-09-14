import SwiftUI

/// One game: the score first, then the box score, then the few advanced numbers
/// the per-player feed can total honestly.
struct GameDetailView: View {
    @Bindable var viewModel: DashboardViewModel
    let gameId: String

    @State private var logs: [PlayerGameLog] = []
    @State private var isLoading = false
    @State private var loadError: String?
    @State private var boxTeam: String = ""

    private var game: Game? { viewModel.game(id: gameId) }

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                if let game {
                    header(game)
                    detail(for: game)
                } else if viewModel.isGamesLoading {
                    ProgressView().padding(.vertical, 64)
                } else {
                    ContentUnavailableView("Game not found", systemImage: "calendar.badge.exclamationmark")
                        .padding(.vertical, 48)
                }
                Color.clear.frame(height: 88)
            }
        }
        .scrollBounceBehavior(.basedOnSize)
        .background(GridironPalette.canvas.ignoresSafeArea())
        .navigationTitle(game.map { "\(displayTeamAbbr($0.awayTeam)) at \(displayTeamAbbr($0.homeTeam))" } ?? "Game")
        .navigationBarTitleDisplayMode(.inline)
        .refreshable {
            await viewModel.loadGames(force: true)
            await loadLogs(force: true)
        }
        .task { await viewModel.loadGames() }
        .task(id: "\(gameId)-\(game.map(viewModel.hasStats) ?? false)-\(viewModel.freshnessRevision ?? "none")") {
            await loadLogs(force: false)
        }
    }

    private var boxScore: GameBoxScore { GameBoxScore(logs: logs) }

    private func loadLogs(force: Bool) async {
        guard let game, game.status() == .final || viewModel.hasStats(game) else { return }
        if !force, !logs.isEmpty, viewModel.hasStats(game) { return }
        isLoading = logs.isEmpty
        do {
            logs = try await viewModel.fetchGameLogs(gameId: gameId)
            loadError = nil
        } catch {
            if !isTaskCancellation(error), logs.isEmpty {
                loadError = "Couldn't load the box score. Pull to try again."
            }
        }
        if boxTeam.isEmpty { boxTeam = game.awayTeam }
        isLoading = false
    }

    // MARK: - Header

    private func header(_ game: Game) -> some View {
        let status = game.status()
        return VStack(spacing: 10) {
            HStack(alignment: .center, spacing: 8) {
                teamColumn(game.awayTeam, game: game, label: "Away")
                VStack(spacing: 4) {
                    if game.isFinal {
                        HStack(spacing: 10) {
                            scoreText(game.awayScore, winner: game.result(for: game.awayTeam) != "L")
                            Text("-")
                                .font(GridironType.statLarge)
                                .foregroundStyle(GridironPalette.inkTertiary)
                            scoreText(game.homeScore, winner: game.result(for: game.homeTeam) != "L")
                        }
                    } else {
                        Text(status == .upcoming ? game.kickoffLabel : "In progress")
                            .font(GridironType.cardTitle)
                            .foregroundStyle(status == .upcoming ? GridironPalette.ink : GridironPalette.performanceLow)
                    }
                    Text(statusLine(game, status: status))
                        .font(GridironType.micro)
                        .foregroundStyle(GridironPalette.inkTertiary)
                }
                .frame(minWidth: 110)
                teamColumn(game.homeTeam, game: game, label: "Home")
            }

            Text([game.roundLabel, game.dayLabel, game.stadium].compactMap { $0 }.joined(separator: " · "))
                .font(GridironType.micro)
                .foregroundStyle(GridironPalette.inkTertiary)
                .multilineTextAlignment(.center)
        }
        .padding(.vertical, 18)
        .padding(.horizontal, 12)
        .frame(maxWidth: .infinity)
        .background(GridironPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
        .padding(.horizontal, 12)
        .padding(.top, 12)
    }

    private func teamColumn(_ team: String, game: Game, label: String) -> some View {
        NavigationLink(value: TeamDestination(abbr: normalizedTeamAbbreviation(team))) {
            VStack(spacing: 6) {
                ZStack {
                    Circle().fill(NFLTeamColor.color(team))
                    Text(displayTeamAbbr(team))
                        .font(GridironType.smallBold)
                        .foregroundStyle(.white)
                        .minimumScaleFactor(0.7)
                }
                .frame(width: 48, height: 48)
                Text(teamFullName(team))
                    .font(GridironType.smallBold)
                    .foregroundStyle(GridironPalette.ink)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .minimumScaleFactor(0.85)
                Text(label)
                    .font(GridironType.micro)
                    .foregroundStyle(GridironPalette.inkTertiary)
            }
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(.plain)
        .accessibilityHint("Opens the team")
    }

    private func scoreText(_ score: Int?, winner: Bool) -> some View {
        Text(score.map(String.init) ?? "-")
            .font(GridironType.statHero)
            .foregroundStyle(winner ? GridironPalette.ink : GridironPalette.inkTertiary)
            .monospacedDigit()
    }

    private func statusLine(_ game: Game, status: GameStatus) -> String {
        switch status {
        case .final: return game.overtime ? "Final/OT" : "Final"
        case .inProgress, .awaitingScore: return "Score posts at the final"
        case .upcoming: return game.kickoff.map { $0.formatted(.dateTime.month(.abbreviated).day()) } ?? ""
        }
    }

    // MARK: - Body

    @ViewBuilder
    private func detail(for game: Game) -> some View {
        if !logs.isEmpty {
            leadersCard
            teamStatsCard(game)
            boxScoreCard(game)
            if viewModel.freshnessForDisplay?.isAdvancedPending == true {
                footnote("Next Gen and advanced defense numbers for this week are still arriving and can change over the next few days.")
            }
        } else if isLoading {
            ProgressView("Loading box score")
                .frame(maxWidth: .infinity)
                .padding(.vertical, 40)
        } else if let loadError {
            footnote(loadError)
        } else {
            switch game.status() {
            case .final:
                notice(
                    icon: "clock",
                    title: "Stats arriving",
                    text: "The final score is in. Player stats usually post within two hours of the final whistle."
                )
            case .inProgress, .awaitingScore:
                notice(
                    icon: "football",
                    title: "Game in progress",
                    text: "The score and box score post when the game goes final."
                )
            case .upcoming:
                upcomingCard(game)
            }
        }
    }

    private var leadersCard: some View {
        card(title: "Game leaders") {
            ForEach(Array(boxScore.leaders.enumerated()), id: \.element.id) { index, leader in
                playerRow(line: leader.line, index: index) {
                    VStack(alignment: .leading, spacing: 2) {
                        HStack(spacing: 4) {
                            TeamColorDot(abbr: leader.line.team, size: 6)
                            Text("\(leader.title.uppercased()) · \(displayTeamAbbr(leader.line.team))")
                                .font(GridironType.micro)
                                .foregroundStyle(GridironPalette.inkTertiary)
                        }
                        nameText(leader.line)
                        Text(leader.summary)
                            .font(GridironType.small)
                            .foregroundStyle(GridironPalette.inkSecondary)
                            .monospacedDigit()
                    }
                }
            }
        }
    }

    private func teamStatsCard(_ game: Game) -> some View {
        let away = boxScore.totals(for: game.awayTeam)
        let home = boxScore.totals(for: game.homeTeam)
        return card(title: "Team stats") {
            HStack {
                Text(displayTeamAbbr(game.awayTeam)).frame(width: 64, alignment: .leading)
                Spacer()
                Text(displayTeamAbbr(game.homeTeam)).frame(width: 64, alignment: .trailing)
            }
            .font(GridironType.smallBold)
            .foregroundStyle(GridironPalette.inkSecondary)
            .padding(.horizontal, GridironGeo.padCard)
            .frame(height: 30)
            .background(GridironPalette.surfaceAlt)

            comparisonRow("Total yards", away.totalYards, home.totalYards, higherIsBetter: true)
            comparisonRow("Passing yards", away.passingYards - away.sackYardsLost, home.passingYards - home.sackYardsLost, higherIsBetter: true)
            comparisonRow("Rushing yards", away.rushingYards, home.rushingYards, higherIsBetter: true)
            comparisonRow("First downs", away.firstDowns, home.firstDowns, higherIsBetter: true)
            comparisonRow("Turnovers", away.turnovers, home.turnovers, higherIsBetter: false)
            comparisonRow("Sacks taken", away.sacksTaken, home.sacksTaken, higherIsBetter: false)
            if let awayEPA = away.epaPerPlay, let homeEPA = home.epaPerPlay {
                comparisonRow("EPA per play", awayEPA, homeEPA, higherIsBetter: true, decimals: 2)
            }
            footnoteRow("Totals add up each team's player lines. Turnovers count interceptions and lost fumbles on runs. EPA per play is passing and rushing EPA over dropbacks and carries.")
        }
    }

    private func comparisonRow(_ label: String, _ away: Double, _ home: Double, higherIsBetter: Bool, decimals: Int = 0) -> some View {
        let awayBetter = higherIsBetter ? away > home : away < home
        let homeBetter = higherIsBetter ? home > away : home < away
        let format: (Double) -> String = { value in
            decimals == 0
                ? Int(value.rounded()).formatted()
                : value.formatted(.number.precision(.fractionLength(decimals)).sign(strategy: .always(includingZero: false)))
        }
        return HStack {
            Text(format(away))
                .font(awayBetter ? GridironType.statMed : GridironType.statSmall)
                .foregroundStyle(awayBetter ? GridironPalette.ink : GridironPalette.inkSecondary)
                .frame(width: 64, alignment: .leading)
            Spacer()
            Text(label)
                .font(GridironType.small)
                .foregroundStyle(GridironPalette.inkSecondary)
            Spacer()
            Text(format(home))
                .font(homeBetter ? GridironType.statMed : GridironType.statSmall)
                .foregroundStyle(homeBetter ? GridironPalette.ink : GridironPalette.inkSecondary)
                .frame(width: 64, alignment: .trailing)
        }
        .monospacedDigit()
        .padding(.horizontal, GridironGeo.padCard)
        .frame(height: 36)
        .overlay(Rectangle().fill(GridironPalette.divider).frame(height: GridironGeo.hairline), alignment: .bottom)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(label): \(teamFullName(game?.awayTeam ?? "")) \(format(away)), \(teamFullName(game?.homeTeam ?? "")) \(format(home))")
    }

    private func boxScoreCard(_ game: Game) -> some View {
        let team = boxTeam.isEmpty ? game.awayTeam : boxTeam
        return VStack(spacing: 0) {
            GridironSegmented(
                segments: [
                    .init(value: game.awayTeam, label: teamFullName(game.awayTeam)),
                    .init(value: game.homeTeam, label: teamFullName(game.homeTeam)),
                ],
                selection: Binding(get: { team }, set: { boxTeam = $0 })
            )
            .padding(.horizontal, 12)
            .padding(.top, 16)

            card(title: "Passing") {
                tableHeader(["C/ATT", "YDS", "TD", "INT"])
                ForEach(Array(boxScore.passers(for: team).enumerated()), id: \.element.id) { index, line in
                    tableRow(line, index: index, values: [
                        "\(line.int("completions"))/\(line.int("attempts"))",
                        "\(line.int("passing_yards"))", "\(line.int("passing_tds"))", "\(line.int("interceptions"))",
                    ])
                }
            }
            card(title: "Rushing") {
                tableHeader(["CAR", "YDS", "AVG", "TD"])
                ForEach(Array(boxScore.rushers(for: team).enumerated()), id: \.element.id) { index, line in
                    let carries = line.value("carries")
                    tableRow(line, index: index, values: [
                        "\(line.int("carries"))", "\(line.int("rushing_yards"))",
                        carries > 0 ? (line.value("rushing_yards") / carries).formatted(.number.precision(.fractionLength(1))) : "-",
                        "\(line.int("rushing_tds"))",
                    ])
                }
            }
            card(title: "Receiving") {
                tableHeader(["REC", "TGT", "YDS", "TD"])
                ForEach(Array(boxScore.receivers(for: team).enumerated()), id: \.element.id) { index, line in
                    tableRow(line, index: index, values: [
                        "\(line.int("receptions"))", "\(line.int("targets"))",
                        "\(line.int("receiving_yards"))", "\(line.int("receiving_tds"))",
                    ])
                }
            }
            card(title: "Defense") {
                tableHeader(["TKL", "SCK", "INT", "PD"])
                ForEach(Array(boxScore.defenders(for: team).prefix(12).enumerated()), id: \.element.id) { index, line in
                    tableRow(line, index: index, values: [
                        "\(Int(line.tackles.rounded()))",
                        line.value("def_sacks").formatted(.number.precision(.fractionLength(0...1))),
                        "\(line.int("def_interceptions"))", "\(line.int("def_pass_defended"))",
                    ])
                }
            }
        }
    }

    private func upcomingCard(_ game: Game) -> some View {
        notice(
            icon: "calendar",
            title: "Kickoff \(game.dayLabel), \(game.kickoff?.formatted(date: .omitted, time: .shortened) ?? "time TBD")",
            text: "The score and box score post here when the game goes final. Scout both rosters from the team pages above."
        )
    }

    // MARK: - Pieces

    private func card<Content: View>(title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(spacing: 0) {
            GridironSectionBar(title: title)
            content()
        }
        .background(GridironPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
        .padding(.horizontal, 12)
        .padding(.top, 12)
    }

    private func tableHeader(_ columns: [String]) -> some View {
        HStack(spacing: 0) {
            Text("PLAYER")
                .frame(maxWidth: .infinity, alignment: .leading)
            ForEach(columns, id: \.self) { column in
                Text(column).frame(width: 46, alignment: .trailing)
            }
        }
        .font(GridironType.micro)
        .foregroundStyle(GridironPalette.inkTertiary)
        .padding(.horizontal, GridironGeo.padInline)
        .frame(height: 26)
        .background(GridironPalette.surfaceAlt)
    }

    private func tableRow(_ line: GameBoxScore.PlayerLine, index: Int, values: [String]) -> some View {
        playerRow(line: line, index: index) {
            HStack(spacing: 0) {
                nameText(line)
                    .frame(maxWidth: .infinity, alignment: .leading)
                ForEach(Array(values.enumerated()), id: \.offset) { _, value in
                    Text(value)
                        .font(GridironType.statSmall)
                        .foregroundStyle(GridironPalette.ink)
                        .frame(width: 46, alignment: .trailing)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                }
            }
        }
    }

    /// A tappable row when the player is in the live dataset, a plain one if not.
    @ViewBuilder
    private func playerRow<Content: View>(line: GameBoxScore.PlayerLine, index: Int, @ViewBuilder content: () -> Content) -> some View {
        let row = content()
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, GridironGeo.padInline)
            .padding(.vertical, 8)
            .frame(minHeight: 40)
            .background(index.isMultiple(of: 2) ? GridironPalette.surface : GridironPalette.surfaceAlt)
            .contentShape(Rectangle())
        if let player = player(for: line) {
            NavigationLink(value: player) { row }
                .buttonStyle(.plain)
        } else {
            row
        }
    }

    private func nameText(_ line: GameBoxScore.PlayerLine) -> some View {
        Text(player(for: line)?.name ?? "Player \(line.playerId)")
            .font(GridironType.bodyBold)
            .foregroundStyle(GridironPalette.ink)
            .lineLimit(1)
            .minimumScaleFactor(0.85)
    }

    private func player(for line: GameBoxScore.PlayerLine) -> Player? {
        guard let game else { return nil }
        return viewModel.player(id: line.playerId, season: game.season, phase: game.seasonPhase)
    }

    private func notice(icon: String, title: String, text: String) -> some View {
        VStack(spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 22))
                .foregroundStyle(GridironPalette.inkTertiary)
            Text(title)
                .font(GridironType.cardTitle)
                .foregroundStyle(GridironPalette.ink)
            Text(text)
                .font(GridironType.small)
                .foregroundStyle(GridironPalette.inkSecondary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        .background(GridironPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
        .padding(.horizontal, 12)
        .padding(.top, 12)
    }

    private func footnote(_ text: String) -> some View {
        Text(text)
            .font(GridironType.micro)
            .foregroundStyle(GridironPalette.inkTertiary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, 16)
            .padding(.top, 12)
    }

    private func footnoteRow(_ text: String) -> some View {
        Text(text)
            .font(GridironType.micro)
            .foregroundStyle(GridironPalette.inkTertiary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, GridironGeo.padCard)
            .padding(.vertical, 10)
    }
}
