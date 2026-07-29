import SwiftUI

struct AboutView: View {
    @EnvironmentObject private var store: StoreService
    let lastUpdated: Date?
    var onRequestReview: (() -> Void)?
    @State private var paywallTrigger: PaywallTrigger?

    private var version: String {
        let short = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
        let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "0"
        return "\(short) (\(build))"
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                proStatusCard
                glossaryCard
                linkCard
                refreshCard
                aboutCard
                versionCard
                disclaimerCard
            }
            .padding(.horizontal, 12)
            .padding(.top, 12)
            .padding(.bottom, 12)
            Color.clear.frame(height: 88)
        }
        .background(GridironPalette.canvas.ignoresSafeArea())
        .sheet(item: $paywallTrigger) { trigger in
            PaywallView(trigger: trigger)
        }
    }

    private var glossaryCard: some View {
        NavigationLink {
            StatGlossaryView()
        } label: {
            VStack(spacing: 0) {
                GridironSectionBar(title: "REFERENCE")
                row(
                    icon: "text.book.closed.fill",
                    title: "Stat Glossary",
                    subtitle: "Definitions and formulas for every stat in StatScout."
                )
            }
            .background(GridironPalette.surface)
            .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
            .overlay(
                RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                    .stroke(GridironPalette.hairline, lineWidth: 0.5)
            )
        }
        .buttonStyle(.plain)
    }

    private var aboutCard: some View {
        VStack(spacing: 0) {
            GridironSectionBar(title: "STATSCOUT")
            HStack(spacing: 12) {
                Image(systemName: "football.fill")
                    .font(.title2)
                    .foregroundStyle(GridironPalette.turf)
                VStack(alignment: .leading, spacing: 4) {
                    Text("Percentile Rankings")
                        .font(GridironType.cardTitle)
                        .foregroundStyle(GridironPalette.ink)
                    Text("Mobile-first percentile rankings and leaderboards for fans and media.")
                        .font(GridironType.small)
                        .foregroundStyle(GridironPalette.inkSecondary)
                }
                Spacer()
            }
            .padding(GridironGeo.padCard)
        }
        .background(GridironPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
    }

    private var proStatusCard: some View {
        VStack(spacing: 0) {
            GridironSectionBar(title: "STATSCOUT+")
            VStack(spacing: 0) {
                HStack {
                    Image(systemName: store.isPro ? "crown.fill" : "crown")
                        .font(.title2)
                        .foregroundStyle(store.isPro ? Color.yellow : GridironPalette.inkTertiary)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(store.isPro ? "StatScout+ Unlocked" : "Free Version")
                            .font(GridironType.bodyBold)
                            .foregroundStyle(GridironPalette.ink)
                        Text(store.isPro ? "All StatScout+ features are active." : "Unlock historical seasons and year-over-year comparisons.")
                            .font(GridironType.small)
                            .foregroundStyle(GridironPalette.inkSecondary)
                    }
                    Spacer()
                    if !store.isPro {
                        Button(store.isLapsed ? "Renew" : store.upgradeCTALabel) {
                            paywallTrigger = store.defaultUpgradeTrigger
                        }
                        .buttonStyle(.borderedProminent)
                        .tint(GridironPalette.turf)
                        .controlSize(.small)
                    }
                }
                .padding(GridironGeo.padCard)

                Rectangle().fill(GridironPalette.divider).frame(height: GridironGeo.hairline)
                Button {
                    Task { await store.restorePurchases() }
                } label: {
                    HStack {
                        Image(systemName: "arrow.clockwise")
                            .font(.caption)
                        Text("Restore Purchases")
                            .font(GridironType.smallBold)
                    }
                    .foregroundStyle(GridironPalette.linkBlue)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(GridironGeo.padCard)
                }
                .buttonStyle(.plain)

                if let error = store.lastError {
                    Rectangle().fill(GridironPalette.divider).frame(height: GridironGeo.hairline)
                    Text(error)
                        .font(GridironType.small)
                        .foregroundStyle(GridironPalette.turf)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(GridironGeo.padCard)
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

    private var refreshCard: some View {
        VStack(spacing: 0) {
            GridironSectionBar(title: "DATA")
            row(
                icon: "moon.stars.fill",
                title: "Nightly Refresh",
                subtitle: "Refreshed each night using publicly available NFL play-by-play and Next Gen Stats data."
            )
            Rectangle().fill(GridironPalette.divider).frame(height: GridironGeo.hairline)
            row(
                icon: "clock.arrow.circlepath",
                title: "Last Updated",
                subtitle: lastUpdated.map { $0.formatted(date: .long, time: .shortened) } ?? "-"
            )
        }
        .background(GridironPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
    }

    private var linkCard: some View {
        VStack(spacing: 0) {
            GridironSectionBar(title: "SUPPORT & PRIVACY")
            Button {
                if let onRequestReview {
                    onRequestReview()
                } else {
                    ReviewPromptCoordinator.shared.requestEnjoymentPrompt()
                }
            } label: {
                row(
                    icon: "star.fill",
                    title: "Rate or Send Feedback",
                    subtitle: "Help StatScout grow - or tell us what to improve."
                )
            }
            .buttonStyle(.plain)

            Rectangle().fill(GridironPalette.divider).frame(height: GridironGeo.hairline)

            // Always-works fallback: the native rating sheet is rate-limited and
            // may show nothing, so keep a direct write-review link for users who
            // explicitly want to leave a review.
            Link(destination: AppStoreReviewLinks.writeReviewURL) {
                row(
                    icon: "square.and.pencil",
                    title: "Rate on the App Store",
                    subtitle: "Opens the App Store to write a review."
                )
            }
            .buttonStyle(.plain)

            Rectangle().fill(GridironPalette.divider).frame(height: GridironGeo.hairline)

            if let supportURL = URL(string: "https://jackwallner.github.io/football/support.html") {
                Link(destination: supportURL) {
                    row(
                        icon: "envelope.fill",
                        title: "Contact Support",
                        subtitle: "jackwallner+bb@gmail.com"
                    )
                }
                .buttonStyle(.plain)
            }
            
            Rectangle().fill(GridironPalette.divider).frame(height: GridironGeo.hairline)
            
            if let privacyURL = URL(string: "https://jackwallner.github.io/football/privacy-policy.html") {
                Link(destination: privacyURL) {
                    row(
                        icon: "shield.lefthalf.filled",
                        title: "Privacy Policy",
                        subtitle: "No ads or tracking."
                    )
                }
                .buttonStyle(.plain)
            }
        }
        .background(GridironPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
    }

    private var versionCard: some View {
        VStack(spacing: 0) {
            GridironSectionBar(title: "VERSION")
            HStack {
                Text("App Version")
                    .font(GridironType.bodyBold)
                    .foregroundStyle(GridironPalette.ink)
                Spacer()
                Text(version)
                    .font(GridironType.statSmall)
                    .foregroundStyle(GridironPalette.inkSecondary)
            }
            .padding(GridironGeo.padCard)
        }
        .background(GridironPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
    }

    private var disclaimerCard: some View {
        VStack(spacing: 0) {
            GridironSectionBar(title: "DISCLAIMER")
            Text("Not affiliated with, endorsed by, or sponsored by the National Football League, its teams, or the NFLPA. Team names and abbreviations are used for identification only. All trademarks are property of their respective owners.")
                .font(GridironType.small)
                .foregroundStyle(GridironPalette.inkSecondary)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(GridironGeo.padCard)
        }
        .background(GridironPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
    }

    private func row(icon: String, title: String, subtitle: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundStyle(GridironPalette.turf)
                .frame(width: 28)
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(GridironType.bodyBold)
                    .foregroundStyle(GridironPalette.ink)
                Text(subtitle)
                    .font(GridironType.small)
                    .foregroundStyle(GridironPalette.inkSecondary)
            }
            Spacer()
        }
        .padding(GridironGeo.padCard)
    }
}

private struct GlossaryEntry: Identifiable {
    let id: String
    let label: String
    let category: String
    let description: String
}

struct StatGlossaryView: View {
    @State private var searchText = ""

    private let supplemental: [GlossaryEntry] = [
        .init(id: "general-games", label: "G", category: "General", description: "Games in which the player recorded a tracked statistic."),
        .init(id: "passing-cmp-att", label: "Cmp/Att", category: "Passing", description: "Pass completions and attempts."),
        .init(id: "passing-cmp", label: "Cmp", category: "Passing", description: "Completed forward passes."),
        .init(id: "passing-att", label: "Att", category: "Passing", description: "Forward pass attempts."),
        .init(id: "rushing-car", label: "Car", category: "Rushing", description: "Rushing attempts, also called carries."),
        .init(id: "receiving-rec-tgt", label: "Rec/Tgt", category: "Receiving", description: "Receptions and targets."),
        .init(id: "receiving-tgt", label: "Tgt", category: "Receiving", description: "Pass attempts directed at the receiver."),
        .init(id: "percentile", label: "Percentile", category: "General", description: "A 1–100 rank among qualifying players in the same season, season type, and stat category. Higher is always better after lower-is-better stats are inverted."),
    ]

    private var entries: [GlossaryEntry] {
        let registry = FootballMetricRegistry.definitions.map {
            GlossaryEntry(
                id: "\($0.category.rawValue)-\($0.label)",
                label: $0.label,
                category: $0.category.rawValue,
                description: $0.description
            )
        }
        let all = (supplemental + registry).sorted {
            $0.label.localizedCaseInsensitiveCompare($1.label) == .orderedAscending
        }
        guard !searchText.isEmpty else { return all }
        return all.filter {
            $0.label.localizedCaseInsensitiveContains(searchText)
                || $0.category.localizedCaseInsensitiveContains(searchText)
                || $0.description.localizedCaseInsensitiveContains(searchText)
        }
    }

    private var categories: [String] {
        let order = ["General", "Passing", "Rushing", "Receiving", "Defense"]
        return order.filter { category in entries.contains { $0.category == category } }
    }

    var body: some View {
        List {
            Section {
                Text("Values come from nflverse player statistics and NFL Next Gen Stats. Percentiles are calculated separately for qualifying players in each season and season type.")
                    .font(GridironType.small)
                    .foregroundStyle(GridironPalette.inkSecondary)
            }

            ForEach(categories, id: \.self) { category in
                Section(category) {
                    ForEach(entries.filter { $0.category == category }) { entry in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(entry.label)
                                .font(GridironType.bodyBold)
                                .foregroundStyle(GridironPalette.ink)
                            Text(entry.description)
                                .font(GridironType.small)
                                .foregroundStyle(GridironPalette.inkSecondary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        .padding(.vertical, 4)
                    }
                }
            }

            Section("Sources") {
                Link("NFL Next Gen Stats Glossary", destination: URL(string: "https://nextgenstats.nfl.com/glossary")!)
                Link("nflreadpy Player Stats", destination: URL(string: "https://nflreadpy.nflverse.com/api/load_functions/#nflreadpy.load_player_stats")!)
            }
        }
        .searchable(text: $searchText, prompt: "Search stats")
        .navigationTitle("Stat Glossary")
        .navigationBarTitleDisplayMode(.inline)
    }
}

#Preview {
    NavigationStack {
        AboutView(lastUpdated: Date())
            .environmentObject(StoreService.shared)
    }
}
