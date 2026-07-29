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
                subtitle: lastUpdated.map { $0.formatted(date: .long, time: .shortened) } ?? "—"
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
                    subtitle: "Help StatScout grow — or tell us what to improve."
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

#Preview {
    NavigationStack {
        AboutView(lastUpdated: Date())
            .environmentObject(StoreService.shared)
    }
}
