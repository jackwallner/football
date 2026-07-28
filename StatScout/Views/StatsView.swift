import SwiftUI

/// Position-first home for league-wide NFL statistics. The dashboard owns the
/// position, metric, qualifier, and sort controls; the season remains in the
/// navigation bar so it applies to the whole surface.
struct StatsView: View {
    let viewModel: DashboardViewModel
    @EnvironmentObject private var store: StoreService

    @State private var paywallTrigger: PaywallTrigger?

    var body: some View {
        DashboardView(viewModel: viewModel)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(GridironPalette.canvas)
        .toolbar {
            // The green pill is its own capsule; suppress the iOS 26 Liquid
                // Glass container or it reads as a pill inside a pill, which is
                // exactly what the upgrade CTA on the other side already avoids.
                if #available(iOS 26.0, *) {
                    ToolbarItem(placement: .topBarLeading) { seasonMenu }
                        .sharedBackgroundVisibility(.hidden)
                } else {
                    ToolbarItem(placement: .topBarLeading) { seasonMenu }
                }
            ToolbarItem(placement: .principal) {
                Text("Stats")
                    .font(GridironType.bodyBold)
                    .foregroundStyle(.white)
            }
        }
        // Contextual past-season pitches route through the low-friction
        // TrialPitchSheet (its CTA starts the yearly trial directly), not the
        // full multi-plan PaywallView.
        .sheet(item: $paywallTrigger) { trigger in
            TrialPitchSheet(trigger: trigger)
        }
    }

    // Compact season selector for the leading toolbar slot. It sits on the
    // midnight navigation bar as a turf-green year pill.
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
                        paywallTrigger = .pastSeasonsLoad
                    }
                } label: {
                    Label(store.isPro ? "Load past seasons" : "Past seasons require StatScout+", systemImage: store.isPro ? "clock.arrow.circlepath" : "crown.fill")
                }
            }
            ForEach(viewModel.availableSeasons, id: \.self) { season in
                let isLocked = viewModel.isSeasonLocked(season)
                Button {
                    if isLocked {
                        paywallTrigger = .pastSeason
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
        .accessibilityHint("Choose which season's stats to view")
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        StatsView(viewModel: DashboardViewModel())
            .environmentObject(StoreService.shared)
    }
}
#endif
