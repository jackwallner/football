import SwiftUI
import RevenueCat

@main
struct StatScoutApp: App {
    private let api: (any StatcastProviding)?
    @StateObject private var store = StoreService.shared

    init() {
        ReviewPromptTracker.recordAppLaunch()
        guard let urlString = Self.configValue(for: "SUPABASE_URL"),
              let url = URL(string: urlString),
              let key = Self.configValue(for: "SUPABASE_ANON_KEY") else {
            #if targetEnvironment(simulator)
            self.api = OfflineStatcastAPI()
            StoreService.shared.start()
            return
            #else
            self.api = nil
            return
            #endif
        }
        self.api = StatcastAPI(baseURL: url, apiKey: key)
        StoreService.shared.start()
    }

    private static func configValue(for key: String) -> String? {
        if let plistValue = Bundle.main.object(forInfoDictionaryKey: key) as? String,
           !plistValue.isEmpty,
           !plistValue.hasPrefix("$(") {
            return plistValue
        }
        return ProcessInfo.processInfo.environment[key]
    }

    var body: some Scene {
        WindowGroup {
            #if DEBUG
            if let mode = PaywallScreenshotMode.current {
                PaywallScreenshotHarness(mode: mode)
                    .environmentObject(store)
                    .preferredColorScheme(.light)
            } else if let api {
                ContentView(api: api)
                    .environmentObject(store)
                    .preferredColorScheme(.light)
                    .onReceive(NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)) { _ in
                        Task { await store.updateCustomerProductStatus() }
                    }
            } else {
                ConfigMissingView()
                    .preferredColorScheme(.light)
            }
            #else
            if let api {
                ContentView(api: api)
                    .environmentObject(store)
                    .preferredColorScheme(.light)
                    .onReceive(NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)) { _ in
                        Task { await store.updateCustomerProductStatus() }
                    }
            } else {
                ConfigMissingView()
                    .preferredColorScheme(.light)
            }
            #endif
        }
    }
}

struct ContentView: View {
    @AppStorage("hasCompletedOnboarding") private var hasCompletedOnboarding = false
    @EnvironmentObject private var store: StoreService

    @State private var viewModel: DashboardViewModel

    init(api: any StatcastProviding) {
        _viewModel = State(initialValue: DashboardViewModel(provider: api, cache: TwoTierPlayerCache()))
    }

    var body: some View {
        ZStack {
            RootTabView(viewModel: viewModel)
                .disabled(!hasCompletedOnboarding)
                .allowsHitTesting(hasCompletedOnboarding)

            if !hasCompletedOnboarding {
                OnboardingCards(viewModel: viewModel, hasCompletedOnboarding: $hasCompletedOnboarding)
                    .transition(.opacity)
                    .zIndex(1)
            }
        }
        .task { await viewModel.loadIfNeeded() }
        .task(id: store.isPro) {
            guard store.isPro else { return }
            await viewModel.loadHistoricalIfNeeded()
        }
        .onAppear { viewModel.applyProState(store.isPro) }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)) { _ in
            Task { await viewModel.load() }
        }
        .onChange(of: store.isPro) { _, isPro in
            viewModel.applyProState(isPro)
        }
    }
}

struct BulletItem: Identifiable {
    let id = UUID()
    let text: String
    let icon: String
    let color: Color
}

struct OnboardingCards: View {
    @EnvironmentObject private var store: StoreService
    let viewModel: DashboardViewModel
    @Binding var hasCompletedOnboarding: Bool
    @State private var currentPage = 0
    @State private var paywallTrigger: PaywallTrigger?
    @State private var isStartingTrial = false
    @State private var trialError: String?
    @State private var isRestoring = false

    private var isLastPage: Bool { currentPage == pages.count - 1 }
    private var dataReady: Bool { viewModel.isReady }
    private var showsUpsellBlock: Bool { isLastPage && !store.isPro }

    private var proCTALabel: String {
        "Continue with StatScout+"
    }

    private var trialDisclosure: String? {
        store.yearlyCTADisclosureText
    }

    var body: some View {
        ZStack {
            GridironPalette.canvas.ignoresSafeArea()

            VStack(spacing: 0) {
                HStack {
                    Spacer()
                    Button("Skip") {
                        withAnimation { hasCompletedOnboarding = true }
                    }
                    .font(GridironType.bodyBold)
                    .foregroundStyle(GridironPalette.turf)
                    .padding(.trailing, 20)
                    .opacity(isLastPage ? 0 : 1)
                    .allowsHitTesting(!isLastPage)
                }
                .frame(height: 32)

                TabView(selection: $currentPage) {
                    ForEach(Array(pages.enumerated()), id: \.offset) { index, page in
                        OnboardingCard(
                            icon: page.icon,
                            title: page.title,
                            description: page.description,
                            bullets: page.bullets
                        )
                        .tag(index)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .always))
                .indexViewStyle(.page(backgroundDisplayMode: .always))

                bottomButtons
                    .padding(.horizontal, 24)
                    .padding(.bottom, 32)
            }
        }
        .task {
            if store.currentOffering == nil {
                await store.fetchProducts()
            }
        }
        .sheet(item: $paywallTrigger) { trigger in
            PaywallView(trigger: trigger)
        }
        .onChange(of: store.isPro) { _, isPro in
            if isPro {
                paywallTrigger = nil
                withAnimation { hasCompletedOnboarding = true }
            }
        }
    }

    @ViewBuilder
    private var bottomButtons: some View {
        // Layout invariant: the primary button and the 24pt footer slot are the
        // ONLY things below the top of this stack that set the button's Y. The
        // button + footer slot are IDENTICAL on every page (Continue and the
        // trial CTA are both a 52pt turf button with the same 24pt slot
        // beneath), and everything else (Get Started, status line, disclosure,
        // Terms/Privacy) lives ABOVE the button where it grows upward. Because
        // the stack is bottom-anchored, that upper content can never move the
        // button, so the primary button is pixel-identical across all pages and
        // the thumb target never shifts between taps.
        VStack(spacing: 10) {
            VStack(spacing: 10) {
                getStartedButton(prominent: false)

                // Reserved fixed-height status line: a restore/purchase result
                // fills this slot in place instead of being inserted, so nothing
                // above the button shifts either.
                Text(trialError ?? " ")
                    .font(GridironType.micro)
                    .foregroundStyle(GridironPalette.performanceLow)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .frame(height: 32, alignment: .top)

                if let disclosure = trialDisclosure {
                    Text(disclosure)
                        .font(GridironType.micro)
                        .foregroundStyle(GridironPalette.inkTertiary)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                }

                // Terms/Privacy sit just above the purchase point (this CTA can
                // buy the trial directly, no PaywallView handoff).
                HStack(spacing: 12) {
                    Link("Terms", destination: StatScoutLegal.termsURL)
                    Link("Privacy", destination: StatScoutLegal.privacyURL)
                }
                .font(GridironType.micro)
                .foregroundStyle(GridironPalette.inkTertiary)
            }
            .opacity(showsUpsellBlock ? 1 : 0)
            .allowsHitTesting(showsUpsellBlock)
            .accessibilityHidden(!showsUpsellBlock)

            // --- Primary button: identical 52pt turf slot on every page ---
            if isLastPage {
                if store.isPro {
                    getStartedButton(prominent: true)
                } else {
                    Button {
                        buyYearly()
                    } label: {
                        ZStack {
                            Text(proCTALabel)
                                .opacity(isStartingTrial ? 0 : 1)
                            if isStartingTrial {
                                ProgressView().tint(.white)
                            }
                        }
                        .font(GridironType.bodyBold)
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 52)
                        .background(GridironPalette.turf)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                    }
                    .buttonStyle(.plain)
                    .disabled(isStartingTrial)
                }
            } else {
                Button {
                    withAnimation { currentPage += 1 }
                } label: {
                    Text("Continue")
                        .font(GridironType.bodyBold)
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 52)
                        .background(GridironPalette.turf)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                }
                .buttonStyle(.plain)
            }

            // --- Fixed 24pt footer slot BELOW the button, identical every page.
            // On the last page it carries Restore (or the loading state); on the
            // others it is empty. Its constant height is what keeps the button's
            // bottom edge, and therefore its Y, pinned across pages.
            Group {
                if isLastPage, !dataReady {
                    HStack(spacing: 8) {
                        ProgressView()
                            .progressViewStyle(.circular)
                            .tint(GridironPalette.inkSecondary)
                            .scaleEffect(0.7)
                        Text(viewModel.loadingMessage)
                            .font(GridironType.micro)
                    }
                    .foregroundStyle(GridironPalette.inkSecondary)
                } else if isLastPage && !store.isPro {
                    Button {
                        // Surface the outcome through the same trialError line the
                        // purchase CTA uses, a restore that silently does nothing
                        // reads as a dead button. Success flips isPro, which
                        // finishes onboarding via onChange.
                        isRestoring = true
                        Task { @MainActor in
                            defer { isRestoring = false }
                            await store.restorePurchases()
                            if !store.isPro {
                                trialError = store.lastError ?? "No active StatScout+ purchase was found for this Apple ID."
                            }
                        }
                    } label: {
                        Text(isRestoring ? "Restoring…" : "Restore Purchases")
                            .font(GridironType.micro)
                            .foregroundStyle(GridironPalette.inkTertiary)
                    }
                    .buttonStyle(.plain)
                    .disabled(isRestoring)
                } else {
                    Color.clear
                }
            }
            .frame(height: 24)
        }
        .frame(maxWidth: .infinity, alignment: .bottom)
    }

    /// "Get Started" dismisses onboarding into the free tier. `prominent` is the
    /// solo state (Pro users, where it's the only, and primary, action, a filled
    /// button); otherwise it's a de-emphasized, borderless text link that sits
    /// above the turf trial CTA so it never competes for the tap.
    private func getStartedButton(prominent: Bool) -> some View {
        Button {
            withAnimation { hasCompletedOnboarding = true }
        } label: {
            if prominent {
                Text("Get Started")
                    .font(GridironType.bodyBold)
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 52)
                    .background(GridironPalette.turf)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
            } else {
                // Borderless free-tier exit: same text as the boxed version,
                // just no box. Keeps it legible and compliant (the free path
                // must stay clearly visible) while the turf trial button is the
                // prominent action.
                Text("Get Started")
                    .font(GridironType.bodyBold)
                    .foregroundStyle(GridironPalette.ink)
                    .frame(maxWidth: .infinity)
                    .frame(height: 52)
            }
        }
        .buttonStyle(.plain)
    }

    // One-tap conversion: buy the yearly plan in place, trial when eligible,
    // straight purchase otherwise, never a second paywall. PaywallView is only
    // the emergency fallback when products didn't load. Success flips
    // store.isPro, which finishes onboarding via the onChange handler.
    private func buyYearly() {
        guard let yearly = store.yearlyPackage else {
            paywallTrigger = .onboarding
            return
        }
        trialError = nil
        isStartingTrial = true
        Task { @MainActor in
            defer { isStartingTrial = false }
            do {
                switch try await store.purchase(yearly) {
                case .purchased:
                    break
                case .pending:
                    // Ask-to-Buy / deferred payment: onboarding stays up because
                    // isPro hasn't flipped, explain instead of appearing dead.
                    trialError = "Purchase pending approval. StatScout+ unlocks automatically once it's approved."
                case .cancelled:
                    trialError = "Purchase cancelled. Tap again to continue."
                }
            } catch {
                trialError = store.lastError ?? "Couldn't complete the purchase. Please try again."
            }
        }
    }

    private let pages: [OnboardingPage] = [
        OnboardingPage(
            icon: "football.fill",
            title: "Your Pocket\nScout",
            description: "NFL percentile rankings built for a fast mobile view. Every qualified player, every metric, always up to date.",
            bullets: [
                BulletItem(text: "Every qualified player ranked", icon: "checkmark.circle.fill", color: GridironPalette.turf),
                BulletItem(text: "EPA, CPOE, YAC, RYOE, and more", icon: "checkmark.circle.fill", color: GridironPalette.turf),
                BulletItem(text: "Updated nightly, always fresh", icon: "checkmark.circle.fill", color: GridironPalette.turf),
                BulletItem(text: "No account or sign-up", icon: "checkmark.circle.fill", color: GridironPalette.turf)
            ]
        ),
        OnboardingPage(
            icon: "chart.bar.fill",
            title: "Find Insights\nFast",
            description: "Four tabs cover every angle of the game. See what's happening across the league in seconds.",
            bullets: [
                BulletItem(text: "Stats: sort the league, leaders, best and worst", icon: "checkmark.circle.fill", color: GridironPalette.turf),
                BulletItem(text: "Trends: who's heating up and cooling off", icon: "checkmark.circle.fill", color: GridironPalette.turf),
                BulletItem(text: "Teams: browse any roster, see who's hot", icon: "checkmark.circle.fill", color: GridironPalette.turf),
                BulletItem(text: "Compare: stack two players head-to-head", icon: "checkmark.circle.fill", color: GridironPalette.turf)
            ]
        ),
        OnboardingPage(
            icon: "crown.fill",
            title: "Go Deeper\nwith StatScout+",
            description: "Season numbers tell you who's good. StatScout+ tells you who's good right now, and lets you prove it.",
            bullets: [
                BulletItem(text: "The Trends board: the league ranked by who's moving", icon: "flame.fill", color: GridironPalette.turf),
                BulletItem(text: "Last 3 / 5 / 8 game form on any player or team", icon: "chart.bar.fill", color: GridironPalette.turf),
                BulletItem(text: "Head-to-head matchups across every percentile", icon: "person.2.fill", color: GridironPalette.turf),
                BulletItem(text: "Every season back to 2000, plus year-over-year", icon: "calendar.badge.clock", color: GridironPalette.turf)
            ]
        )
    ]
}

struct OnboardingCard: View {
    let icon: String
    let title: String
    let description: String
    let bullets: [BulletItem]

    var body: some View {
        VStack(spacing: 20) {
            Spacer(minLength: 12)

            ZStack {
                StatcastBarBackdrop()
                    .frame(width: 220, height: 120)
                ZStack {
                    Circle()
                        .fill(GridironPalette.midnight)
                        .frame(width: 96, height: 96)
                    Image(systemName: icon)
                        .font(.system(size: 42, weight: .semibold))
                        .foregroundStyle(.white)
                }
                .shadow(color: GridironPalette.midnight.opacity(0.25), radius: 12, y: 4)
            }

            Text(title)
                .font(GridironType.playerName)
                .foregroundStyle(GridironPalette.ink)
                .multilineTextAlignment(.center)

            Text(description)
                .font(GridironType.body)
                .foregroundStyle(GridironPalette.inkSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)

            VStack(alignment: .leading, spacing: 10) {
                ForEach(bullets) { bullet in
                    HStack(alignment: .firstTextBaseline, spacing: 10) {
                        Image(systemName: bullet.icon)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(bullet.color)
                        Text(bullet.text)
                            .font(GridironType.body)
                            .foregroundStyle(GridironPalette.ink)
                        Spacer(minLength: 0)
                    }
                }
            }
            .padding(.horizontal, 36)
            .padding(.top, 4)

            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

struct OnboardingPage {
    let icon: String
    let title: String
    let description: String
    let bullets: [BulletItem]
}

private struct StatcastBarBackdrop: View {
    private let percentiles: [Int] = [92, 78, 65, 48, 32, 88, 71, 55, 42, 80]

    var body: some View {
        HStack(alignment: .bottom, spacing: 6) {
            ForEach(Array(percentiles.enumerated()), id: \.offset) { _, pct in
                RoundedRectangle(cornerRadius: 3)
                    .fill(GridironPalette.color(forPercentile: pct).opacity(0.55))
                    .frame(width: 14, height: CGFloat(pct) * 1.1)
            }
        }
        .frame(maxHeight: .infinity, alignment: .bottom)
    }
}

struct ConfigMissingView: View {
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 44, weight: .bold))
                .foregroundStyle(GridironPalette.turf)
            Text("StatScout can't load")
                .font(GridironType.playerName)
                .foregroundStyle(GridironPalette.ink)
            Text("This build is missing its data-feed configuration. Please install the latest TestFlight build or contact support.")
                .font(GridironType.body)
                .foregroundStyle(GridironPalette.inkSecondary)
                .multilineTextAlignment(.center)
            if let supportURL = URL(string: "https://jackwallner.github.io/football/support.html") {
                Link("Contact Support", destination: supportURL)
                    .buttonStyle(.borderedProminent)
                    .tint(GridironPalette.turf)
            }
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(GridironPalette.canvas.ignoresSafeArea())
    }
}
