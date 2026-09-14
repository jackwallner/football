import SwiftUI

/// One game: the score first, then the box score, then the few advanced numbers
/// the per-player feed can total honestly.
struct GameDetailView: View {
    @EnvironmentObject private var store: StoreService
    @Bindable var viewModel: DashboardViewModel
    let gameId: String

    enum Mode: Hashable {
        case boxScore
        case advanced
    }

    @State private var mode: Mode = .boxScore
    @State private var paywallTrigger: PaywallTrigger?

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
        .sheet(item: $paywallTrigger) { trigger in
            TrialPitchSheet(trigger: trigger)
        }
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
                Text(([label] + [game.seasonPhase == .regular ? viewModel.record(forTeam: team, through: game) : nil].compactMap { $0 }).joined(separator: " · "))
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
            GridironSegmented(
                segments: [
                    .init(value: Mode.boxScore, label: "Box Score"),
                    .init(value: Mode.advanced, label: "Advanced"),
                ],
                selection: $mode
            )
            .padding(.horizontal, 12)
            .padding(.top, 12)

            switch mode {
            case .boxScore:
                leadersCard
                teamStatsCard(game)
                boxScoreCard(game)
            case .advanced:
                efficiencyCard(game)
                epaLeadersCard
                advancedPlayersCard(game)
                footnote("Next Gen Stats columns (CPOE, time to throw, RYOE, separation, YAC+) appear once Next Gen publishes the game, usually a day or two later. ADOT is air yards per attempt or target.")
                StatGlossaryLink()
                    .padding(.horizontal, 12)
                    .padding(.top, 12)
            }
            if viewModel.freshnessForDisplay?.isAdvancedPending == true {
                footnote("Some advanced numbers for this week are still arriving and can change over the next few days.")
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
        comparisonRow(label, away, home, higherIsBetter: higherIsBetter) { value in
            decimals == 0
                ? Int(value.rounded()).formatted()
                : value.formatted(.number.precision(.fractionLength(decimals)).sign(strategy: .always(includingZero: false)))
        }
    }

    private func comparisonRow(_ label: String, _ away: Double, _ home: Double, higherIsBetter: Bool, format: @escaping (Double) -> String) -> some View {
        let awayBetter = higherIsBetter ? away > home : away < home
        let homeBetter = higherIsBetter ? home > away : home < away
        return HStack {
            Text(format(away))
                .font(GridironType.statMed)
                .fontWeight(awayBetter ? .bold : .regular)
                .foregroundStyle(awayBetter ? GridironPalette.ink : GridironPalette.inkSecondary)
                .frame(width: 64, alignment: .leading)
            Spacer()
            Text(label)
                .font(GridironType.small)
                .foregroundStyle(GridironPalette.inkSecondary)
            Spacer()
            Text(format(home))
                .font(GridironType.statMed)
                .fontWeight(homeBetter ? .bold : .regular)
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

    // MARK: - Advanced

    private func teamHeaderRow(_ game: Game) -> some View {
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
    }

    @ViewBuilder
    private func rateRow(_ label: String, _ away: Double?, _ home: Double?, higherIsBetter: Bool, style: RateStyle) -> some View {
        if let away, let home {
            comparisonRow(label, away, home, higherIsBetter: higherIsBetter) { style.format($0) }
        }
    }

    enum RateStyle {
        case epa
        case yards
        case percent

        func format(_ value: Double) -> String {
            switch self {
            case .epa: return value.formatted(.number.precision(.fractionLength(2)).sign(strategy: .always(includingZero: false)))
            case .yards: return value.formatted(.number.precision(.fractionLength(1)))
            case .percent: return value.formatted(.number.precision(.fractionLength(1))) + "%"
            }
        }
    }

    /// Free: how efficiently each offense moved the ball. Rates, not totals,
    /// so a team that ran twenty more plays doesn't win every row.
    private func efficiencyCard(_ game: Game) -> some View {
        let away = boxScore.totals(for: game.awayTeam)
        let home = boxScore.totals(for: game.homeTeam)
        return card(title: "Offensive efficiency") {
            teamHeaderRow(game)
            rateRow("EPA per play", away.epaPerPlay, home.epaPerPlay, higherIsBetter: true, style: .epa)
            rateRow("EPA per dropback", away.epaPerDropback, home.epaPerDropback, higherIsBetter: true, style: .epa)
            rateRow("EPA per carry", away.epaPerCarry, home.epaPerCarry, higherIsBetter: true, style: .epa)
            rateRow("Yards per play", away.yardsPerPlay, home.yardsPerPlay, higherIsBetter: true, style: .yards)
            rateRow("Net yards per dropback", away.netYardsPerDropback, home.netYardsPerDropback, higherIsBetter: true, style: .yards)
            rateRow("Yards per carry", away.yardsPerCarry, home.yardsPerCarry, higherIsBetter: true, style: .yards)
            rateRow("First down rate", away.firstDownRate, home.firstDownRate, higherIsBetter: true, style: .percent)
            rateRow("Sack rate", away.sackRate, home.sackRate, higherIsBetter: false, style: .percent)
            rateRow("Air yards per attempt", away.airYardsPerAttempt, home.airYardsPerAttempt, higherIsBetter: true, style: .yards)
            rateRow("Yards after catch share", away.yacShare, home.yacShare, higherIsBetter: true, style: .percent)
            footnoteRow("EPA (expected points added) measures how much each play changed a team's scoring chances. A dropback is a pass attempt or a sack.")
        }
    }

    /// Free: the players who moved the needle most, by total EPA.
    private var epaLeadersCard: some View {
        card(title: "Most valuable by EPA") {
            ForEach(Array(boxScore.epaLeaders().enumerated()), id: \.element.id) { index, line in
                playerRow(line: line, index: index) {
                    HStack(spacing: 10) {
                        Text("\(index + 1)")
                            .font(GridironType.statSmall)
                            .foregroundStyle(GridironPalette.inkTertiary)
                            .frame(width: 18, alignment: .leading)
                        VStack(alignment: .leading, spacing: 2) {
                            nameText(line)
                            Text("\(displayTeamAbbr(line.team)) · \(GameBoxScore.summary(line))")
                                .font(GridironType.micro)
                                .foregroundStyle(GridironPalette.inkTertiary)
                                .lineLimit(1)
                                .minimumScaleFactor(0.8)
                        }
                        Spacer(minLength: 8)
                        Text(RateStyle.epa.format(GameBoxScore.totalEPA(line) ?? 0))
                            .font(GridironType.statMed)
                            .foregroundStyle((GameBoxScore.totalEPA(line) ?? 0) >= 0 ? GridironPalette.performanceHigh : GridironPalette.performanceLow)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func advancedPlayersCard(_ game: Game) -> some View {
        if store.isPro {
            advancedPlayerTables(game)
        } else {
            VStack(spacing: 10) {
                Image(systemName: "lock.fill")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(GridironPalette.inkTertiary)
                Text("Every player's advanced line")
                    .font(GridironType.cardTitle)
                    .foregroundStyle(GridironPalette.ink)
                Text("EPA per dropback, CPOE, time to throw, RYOE, separation and YAC over expected for both rosters.")
                    .font(GridironType.small)
                    .foregroundStyle(GridironPalette.inkSecondary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                PlusDirectCTA(trigger: .advancedBoxScore, style: .capsule)
            }
            .padding(20)
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
    }

    private func advancedPlayerTables(_ game: Game) -> some View {
        let team = boxTeam.isEmpty ? game.awayTeam : boxTeam
        return VStack(spacing: 0) {
            teamPicker(game, team: team)

            let passers = boxScore.passers(for: team)
            let passNGS = passers.contains { $0.metrics["cpoe"] != nil || $0.metrics["avg_time_to_throw"] != nil }
            card(title: "Passing") {
                tableHeader(["EPA", "EPA/DB", "ADOT"] + (passNGS ? ["CPOE", "TTT"] : []), width: passNGS ? 44 : 56)
                ForEach(Array(passers.enumerated()), id: \.element.id) { index, line in
                    let dropbacks = line.value("attempts") + line.value("sacks_suffered")
                    tableRow(line, index: index, width: passNGS ? 44 : 56, values: [
                        epa(line.metrics["passing_epa"]),
                        epa(dropbacks > 0 ? line.metrics["passing_epa"].map { $0 / dropbacks } : nil),
                        per(line.value("passing_air_yards"), line.value("attempts")),
                    ] + (passNGS ? [
                        signed(line.metrics["cpoe"], places: 1),
                        plain(line.metrics["avg_time_to_throw"], places: 2),
                    ] : []))
                }
            }
            let rushers = boxScore.rushers(for: team)
            let rushNGS = rushers.contains { $0.metrics["rush_yoe"] != nil }
            card(title: "Rushing") {
                tableHeader(["EPA", "EPA/C", "1D"] + (rushNGS ? ["RYOE"] : []), width: 52)
                ForEach(Array(rushers.enumerated()), id: \.element.id) { index, line in
                    tableRow(line, index: index, width: 52, values: [
                        epa(line.metrics["rushing_epa"]),
                        epa(line.value("carries") > 0 ? line.metrics["rushing_epa"].map { $0 / line.value("carries") } : nil),
                        "\(line.int("rushing_first_downs"))",
                    ] + (rushNGS ? [signed(line.metrics["rush_yoe"], places: 0)] : []))
                }
            }
            let receivers = boxScore.receivers(for: team)
            let recNGS = receivers.contains { $0.metrics["avg_separation"] != nil || $0.metrics["avg_yac_above_expectation"] != nil }
            card(title: "Receiving") {
                tableHeader(["EPA", "YAC", "ADOT"] + (recNGS ? ["SEP", "YAC+"] : []), width: recNGS ? 44 : 56)
                ForEach(Array(receivers.enumerated()), id: \.element.id) { index, line in
                    tableRow(line, index: index, width: recNGS ? 44 : 56, values: [
                        epa(line.metrics["receiving_epa"]),
                        "\(line.int("receiving_yac"))",
                        per(line.value("receiving_air_yards"), line.value("targets")),
                    ] + (recNGS ? [
                        plain(line.metrics["avg_separation"], places: 1),
                        signed(line.metrics["avg_yac_above_expectation"], places: 1),
                    ] : []))
                }
            }
            card(title: "Pass rush and havoc") {
                tableHeader(["SCK", "HITS", "TFL", "FF"], width: 46)
                ForEach(Array(havocPlayers(for: team).enumerated()), id: \.element.id) { index, line in
                    tableRow(line, index: index, width: 46, values: [
                        line.value("def_sacks").formatted(.number.precision(.fractionLength(0...1))),
                        "\(line.int("def_qb_hits"))",
                        "\(line.int("def_tackles_for_loss"))",
                        "\(line.int("def_fumbles_forced"))",
                    ])
                }
            }
        }
    }

    private func havocPlayers(for team: String) -> [GameBoxScore.PlayerLine] {
        boxScore.lines(for: team)
            .map { ($0, $0.value("def_sacks") * 2 + $0.value("def_qb_hits") + $0.value("def_tackles_for_loss") + $0.value("def_fumbles_forced") * 2) }
            .filter { $0.1 > 0 }
            .sorted { $0.1 > $1.1 }
            .map(\.0)
    }

    private func epa(_ value: Double?) -> String {
        value.map { RateStyle.epa.format($0) } ?? "-"
    }

    private func signed(_ value: Double?, places: Int) -> String {
        value.map { $0.formatted(.number.precision(.fractionLength(places)).sign(strategy: .always(includingZero: false))) } ?? "-"
    }

    private func plain(_ value: Double?, places: Int) -> String {
        value.map { $0.formatted(.number.precision(.fractionLength(places))) } ?? "-"
    }

    private func per(_ numerator: Double, _ denominator: Double) -> String {
        denominator > 0 ? (numerator / denominator).formatted(.number.precision(.fractionLength(1))) : "-"
    }

    private func teamPicker(_ game: Game, team: String) -> some View {
        GridironSegmented(
            segments: [
                .init(value: game.awayTeam, label: teamFullName(game.awayTeam)),
                .init(value: game.homeTeam, label: teamFullName(game.homeTeam)),
            ],
            selection: Binding(get: { team }, set: { boxTeam = $0 })
        )
        .padding(.horizontal, 12)
        .padding(.top, 16)
    }

    private func boxScoreCard(_ game: Game) -> some View {
        let team = boxTeam.isEmpty ? game.awayTeam : boxTeam
        return VStack(spacing: 0) {
            teamPicker(game, team: team)

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
            title: "Not started yet",
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

    private func tableHeader(_ columns: [String], width: CGFloat = 46) -> some View {
        HStack(spacing: 0) {
            Text("PLAYER")
                .frame(maxWidth: .infinity, alignment: .leading)
            ForEach(columns, id: \.self) { column in
                Text(column)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                    .frame(width: width, alignment: .trailing)
            }
        }
        .font(GridironType.micro)
        .foregroundStyle(GridironPalette.inkTertiary)
        .padding(.horizontal, GridironGeo.padInline)
        .frame(height: 26)
        .background(GridironPalette.surfaceAlt)
    }

    private func tableRow(_ line: GameBoxScore.PlayerLine, index: Int, width: CGFloat = 46, values: [String]) -> some View {
        playerRow(line: line, index: index) {
            HStack(spacing: 0) {
                nameText(line)
                    .frame(maxWidth: .infinity, alignment: .leading)
                ForEach(Array(values.enumerated()), id: \.offset) { _, value in
                    Text(value)
                        .font(GridironType.statSmall)
                        .foregroundStyle(GridironPalette.ink)
                        .frame(width: width, alignment: .trailing)
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
