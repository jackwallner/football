import SwiftUI

/// A compact, plain language boundary between the current football data and
/// the time the app last checked for a newer publisher revision.
struct DataFreshnessView: View {
    @Bindable var viewModel: DashboardViewModel
    /// The shared status currently describes the live regular-season dataset.
    /// Hide it for historical and playoff scopes rather than borrowing a
    /// coverage label from another set of numbers.
    var season: Int? = nil
    var phase: SeasonPhase? = nil
    var showRefreshButton = true

    private var freshness: DataFreshness? { viewModel.freshnessForDisplay }
    private var status: DataFreshnessStatus { viewModel.freshnessStatus }
    private var isCurrentScope: Bool {
        guard let season else { return true }
        guard season == viewModel.freeSeason else { return false }
        guard let phase else { return true }
        return phase == .regular
    }

    var body: some View {
        Group {
            if isCurrentScope {
                freshnessRow
            }
        }
    }

    private var freshnessRow: some View {
        HStack(alignment: .top, spacing: 9) {
            Image(systemName: status.iconName)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(iconColor)
                .frame(width: 18, height: 20)

            VStack(alignment: .leading, spacing: 2) {
                Text(primaryText)
                    .font(GridironType.smallBold)
                    .foregroundStyle(GridironPalette.ink)
                    .fixedSize(horizontal: false, vertical: true)

                Text(detailText)
                    .font(GridironType.micro)
                    .foregroundStyle(GridironPalette.inkSecondary)
                    .fixedSize(horizontal: false, vertical: true)

                if let explanation {
                    Text(explanation)
                        .font(GridironType.micro)
                        .foregroundStyle(GridironPalette.inkTertiary)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.top, 1)
                }
            }

            Spacer(minLength: 4)

            if showRefreshButton {
                Button {
                    Task { await viewModel.load() }
                } label: {
                    if viewModel.isRefreshing {
                        ProgressView()
                            .controlSize(.small)
                    } else {
                        Text(status == .ready ? "Refresh" : "Try again")
                            .font(GridironType.micro)
                    }
                }
                .buttonStyle(.bordered)
                .tint(GridironPalette.turf)
                .controlSize(.small)
                .disabled(viewModel.isRefreshing)
                .accessibilityLabel(viewModel.isRefreshing ? "Checking for new game data" : "Refresh game data")
            }
        }
        .padding(.horizontal, GridironGeo.padInline)
        .padding(.vertical, 9)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(backgroundColor)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
        .accessibilityElement(children: .contain)
        .accessibilityLabel(accessibilityText)
    }

    private var iconColor: Color {
        switch status {
        case .ready: GridironPalette.performanceHigh
        case .checking: GridironPalette.linkBlue
        case .pending, .partial: GridironPalette.inkSecondary
        case .stale, .offline, .failed: GridironPalette.performanceLow
        }
    }

    private var backgroundColor: Color {
        switch status {
        case .ready: GridironPalette.surface
        case .checking, .pending, .partial: GridironPalette.surfaceAlt
        case .stale, .offline, .failed: GridironPalette.surfaceAlt
        }
    }

    private var primaryText: String {
        switch status {
        case .ready:
            return coverageText ?? "Player data loaded"
        case .checking:
            return "Checking for new game data"
        case .pending:
            return "Latest game data is still arriving"
        case .partial:
            return "Some game data is still processing"
        case .stale:
            return "Showing saved data"
        case .offline:
            return "You're offline"
        case .failed:
            return "Showing the last complete data"
        }
    }

    private var detailText: String {
        let coverage = coverageText.map { "\($0). " } ?? ""
        let checked = viewModel.lastCheckedAt.map(relativeDate) ?? "not checked yet"
        switch status {
        case .ready:
            return "Last checked \(checked)"
        case .checking:
            return coverage + "Your current numbers stay on screen while we check."
        case .pending:
            return coverage + "Last checked \(checked)."
        case .partial:
            return coverage + "Last checked \(checked)."
        case .stale, .offline, .failed:
            return coverage + "Last checked \(checked)."
        }
    }

    private var explanation: String? {
        switch status {
        case .pending:
            return "Advanced stats can take a little time after the final whistle."
        case .partial:
            if let coverage = freshness?.coverage,
               let expected = coverage.expectedGames,
               let included = coverage.gamesIncluded,
               included < expected {
                return "We are waiting for complete game coverage before advancing this view."
            }
            return "Some optional advanced metrics may still be arriving."
        case .stale:
            return "A newer update is available, but this screen has not loaded it yet."
        case .offline:
            return "Reconnect to check for the latest player stats."
        case .failed:
            return "The latest check did not finish. Your saved data is still available."
        case .ready, .checking:
            return nil
        }
    }

    private var coverageText: String? {
        guard let coverage = freshness?.coverage ?? viewModel.dataCoverage else { return nil }
        let date = coverage.asOf.formatted(.dateTime.month(.abbreviated).day())
        let gameCount: String? = {
            guard let included = coverage.gamesIncluded else { return nil }
            if let expected = coverage.expectedGames {
                return "\(included) of \(expected) games"
            }
            return "\(included) games"
        }()
        if let week = coverage.week {
            let phase = coverage.phase == .playoffs ? " playoffs" : ""
            let count = gameCount.map { " · \($0)" } ?? ""
            return "Stats through Week \(week)\(phase)\(count) · \(date)"
        }
        let count = gameCount.map { " · \($0)" } ?? ""
        return "Stats through\(count) · \(date)"
    }

    private var accessibilityText: String {
        let coverage = coverageText ?? "Coverage unavailable"
        let checked = viewModel.lastCheckedAt.map(relativeDate) ?? "not checked yet"
        return "\(status.accessibilityName). \(coverage). Last checked \(checked)."
    }

    private func relativeDate(_ date: Date) -> String {
        // A check that just finished can land a few ms in the future and read
        // "in 0 seconds". Anything under a minute is simply "just now".
        if abs(date.timeIntervalSinceNow) < 60 { return "just now" }
        return RelativeDateTimeFormatter().localizedString(for: date, relativeTo: .now)
    }
}

#if DEBUG
#Preview {
    DataFreshnessView(viewModel: DashboardViewModel())
        .padding()
        .background(GridironPalette.canvas)
}
#endif
