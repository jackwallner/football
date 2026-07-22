import SwiftUI

/// Native, Apple-Health-"Vitals"-style trial pitch. A focused, friendly
/// pre-paywall: hero glyph, headline, feature bullets, one prominent CTA,
/// and the trial fine print. The CTA hands off to the native `PaywallView`
/// for the actual transaction.
struct TrialPitchSheet: View {
    @EnvironmentObject private var store: StoreService
    @Environment(\.dismiss) private var dismiss

    let trigger: PaywallTrigger
    @State private var showingPaywall = false
    @State private var isPurchasing = false
    @State private var purchaseError: String?

    private struct Benefit: Identifiable {
        let id = UUID()
        let icon: String
        let title: String
        let detail: String
    }

    // Bullets are context-aware. For `.playerScouting` (the player-page
    // first-tap pitch) we lead with current-season value — Recent Form,
    // head-to-head, full-roster scouting. Triggers that explicitly opened a
    // history feature still get the time-shaped pitch.
    private var benefits: [Benefit] {
        switch trigger {
        case .playerScouting, .recentForm, .upgrade, .onboarding, .activation:
            return [
                Benefit(icon: "flame.fill",
                        title: "Recent form",
                        detail: "Last 1 / 3 / 5 game splits on any player or team. Season vs. recent on one screen."),
                Benefit(icon: "person.2.fill",
                        title: "Head-to-head matchups",
                        detail: "Stack any two players across every NFL percentile: EPA, CPOE, YAC, RYOE, and more."),
                Benefit(icon: "arrow.left.arrow.right.circle.fill",
                        title: "Year-over-year trends",
                        detail: "Compare any two seasons and see which metrics moved, not just this year's snapshot."),
                Benefit(icon: "calendar.badge.clock",
                        title: "Every past season",
                        detail: "Browse back to 2020 with full percentile history, not locked to the current year."),
                Benefit(icon: "shield.lefthalf.filled",
                        title: "Full team scouting",
                        detail: "Every player on every roster, tap any team stat to sort the whole squad.")
            ]
        case .pastSeason, .pastSeasonsLoad, .yearCompare:
            return [
                Benefit(icon: "calendar.badge.clock",
                        title: "Every past season",
                        detail: "Back to 2020. See how the numbers held up year by year."),
                Benefit(icon: "arrow.left.arrow.right.circle.fill",
                        title: "Year-over-year trends",
                        detail: "Compare a player's percentile rankings across any two seasons."),
                Benefit(icon: "person.2.fill",
                        title: "Head-to-head matchups",
                        detail: "Stack any two players across every NFL metric.")
            ]
        case .playerComparison, .teamView, .winback:
            return [
                Benefit(icon: "person.2.fill",
                        title: "Head-to-head matchups",
                        detail: "Stack any two players across every NFL metric."),
                Benefit(icon: "shield.lefthalf.filled",
                        title: "Full team scouting",
                        detail: "See every player on every roster, not just qualified starters."),
                Benefit(icon: "arrow.left.arrow.right.circle.fill",
                        title: "Year-over-year trends",
                        detail: "Compare any two seasons when you want the longer arc.")
            ]
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(spacing: 22) {
                    hero
                    benefitList
                }
                .padding(.horizontal, 24)
                .padding(.top, 32)
                .padding(.bottom, 16)
            }
            .scrollBounceBehavior(.basedOnSize)

            footer
        }
        .background(GridironPalette.canvas.ignoresSafeArea())
        // Tick the session cap on the *pitch* sheet too, not just the full
        // PaywallView. Otherwise PaywallGate.shouldPresent(.playerScouting)
        // stays true forever for sheets that never reach PaywallView, and the
        // half-sheet would auto-present on every player open.
        .onAppear { PaywallGate.shared.markPresented(trigger) }
        .task {
            store.trackPaywallImpression(id: "statscout_trial_sheet")
            if store.currentOffering == nil { await store.fetchProducts() }
        }
        .onChange(of: store.isPro) { _, isPro in
            if isPro { dismiss() }
        }
        .sheet(isPresented: $showingPaywall) {
            PaywallView(trigger: trigger)
        }
    }

    private var hero: some View {
        VStack(spacing: 14) {
            ZStack {
                Circle()
                    .fill(GridironPalette.turf.opacity(0.12))
                    .frame(width: 84, height: 84)
                Image(systemName: trigger.icon)
                    .font(.system(size: 36, weight: .semibold))
                    .foregroundStyle(GridironPalette.turf)
            }

            Text(trigger.title)
                .font(GridironType.pageTitle)
                .foregroundStyle(GridironPalette.ink)
                .multilineTextAlignment(.center)

            Text(trigger.subtitle)
                .font(GridironType.body)
                .foregroundStyle(GridironPalette.inkSecondary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var benefitList: some View {
        VStack(spacing: 0) {
            ForEach(Array(benefits.enumerated()), id: \.element.id) { index, benefit in
                HStack(spacing: 14) {
                    Image(systemName: benefit.icon)
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(GridironPalette.turf)
                        .frame(width: 30)

                    VStack(alignment: .leading, spacing: 2) {
                        Text(benefit.title)
                            .font(GridironType.bodyBold)
                            .foregroundStyle(GridironPalette.ink)
                        Text(benefit.detail)
                            .font(GridironType.small)
                            .foregroundStyle(GridironPalette.inkSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }

                    Spacer(minLength: 0)
                }
                .padding(.vertical, 14)

                if index < benefits.count - 1 {
                    Rectangle()
                        .fill(GridironPalette.divider)
                        .frame(height: GridironGeo.hairline)
                }
            }
        }
        .padding(.horizontal, 16)
        .background(GridironPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
    }

    private var footer: some View {
        VStack(spacing: 10) {
            Button {
                buyYearly()
            } label: {
                ZStack {
                    Text(store.paywallBlurCTA)
                        .opacity(isPurchasing ? 0 : 1)
                    if isPurchasing {
                        ProgressView().tint(.white)
                    }
                }
                .font(GridironType.bodyBold)
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 50)
                .background(GridironPalette.turf)
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
            .buttonStyle(.plain)
            .disabled(isPurchasing)

            // Full auto-renew disclosure — this CTA buys the yearly plan
            // directly (trial when eligible, paid otherwise), so the renewal
            // terms must sit beside the purchase point (Apple 3.1.2).
            if let disclosure = store.yearlyCTADisclosureText {
                Text(disclosure)
                    .font(GridironType.micro)
                    .foregroundStyle(GridironPalette.inkTertiary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }

            if let purchaseError {
                Text(purchaseError)
                    .font(GridironType.small)
                    .foregroundStyle(GridironPalette.turf)
                    .multilineTextAlignment(.center)
            }

            HStack(spacing: 12) {
                Link("Terms", destination: StatScoutLegal.termsURL)
                Link("Privacy", destination: StatScoutLegal.privacyURL)
            }
            .font(GridironType.micro)
            .foregroundStyle(GridironPalette.inkTertiary)

            Button("Maybe later") { dismiss() }
                .font(GridironType.small)
                .foregroundStyle(GridironPalette.inkSecondary)
                .padding(.top, 2)
        }
        .padding(.horizontal, 24)
        .padding(.top, 14)
        .padding(.bottom, 12)
        .background(
            GridironPalette.surface
                .overlay(
                    Rectangle()
                        .fill(GridironPalette.divider)
                        .frame(height: GridironGeo.hairline),
                    alignment: .top
                )
                .ignoresSafeArea(edges: .bottom)
        )
    }

    // One-tap conversion: buy the yearly plan in place — trial when eligible,
    // straight purchase otherwise — so this pop-up never hands off to a second
    // paywall. PaywallView is only the emergency fallback when products didn't
    // load. Success dismisses via onChange(isPro).
    private func buyYearly() {
        guard let yearly = store.yearlyPackage else {
            showingPaywall = true
            return
        }
        purchaseError = nil
        isPurchasing = true
        Task { @MainActor in
            defer { isPurchasing = false }
            do {
                switch try await store.purchase(yearly) {
                case .purchased:
                    break
                case .pending:
                    // Ask-to-Buy / deferred payment: not unlocked yet, not an
                    // error — the sheet stays up, so say what's happening.
                    purchaseError = "Purchase pending approval. StatScout+ unlocks automatically once it's approved."
                case .cancelled:
                    purchaseError = "Purchase cancelled. Tap again to continue."
                }
            } catch {
                purchaseError = store.lastError ?? "Couldn't complete the purchase. Please try again."
            }
        }
    }
}

#if DEBUG
#Preview {
    Color.gray
        .sheet(isPresented: .constant(true)) {
            TrialPitchSheet(trigger: .upgrade)
                .environmentObject(StoreService.shared)
        }
}
#endif
