import Foundation
import os
@preconcurrency import RevenueCat

enum StatScoutProduct {
    static let lifetime = "com.jackwallner.football.pro"
    static let yearly = "com.jackwallner.football.pro.yearly"
    static let monthly = "com.jackwallner.football.pro.monthly"
    static let all: [String] = [lifetime, yearly, monthly]
}

enum RevenueCatConfig {
    // RevenueCat "Football" project (proj9c303632), App Store app app039a312379.
    // Public SDK key (appl_...) — used only in device Release / TestFlight / App
    // Store builds; simulator runs skip Purchases.configure (see configureIfNeeded).
    static let apiKey = "appl_ivmIolnYwgJaeylzJwULBKiNrIx"
    static let proEntitlement = "Football Pro"
    static let fallbackEntitlement = "pro"
}

enum StatScoutSeason {
    /// The season the nightly pipeline is currently writing. Single source of
    /// truth for the current/historical split — the API filters, the two-tier
    /// cache partition, and the free-tier gate all read this, so the yearly
    /// rollover is a one-line change.
    static let current = 2025
    /// Oldest season included in the supported historical archive.
    static let oldest = 2015
    /// The only season available without Pro. Everything older is gated.
    static let free = current
}

enum StatScoutLegal {
    /// Apple's standard EULA — required on the paywall unless a custom one is hosted.
    static let termsURL = URL(string: "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/")!
    static let privacyURL = URL(string: "https://jackwallner.github.io/football/privacy-policy.html")!
}

/// Session-scoped cap so the same contextual paywall can't be re-presented
/// endlessly as a user pokes at locked features. Resets on app relaunch.
@MainActor
final class PaywallGate: ObservableObject {
    static let shared = PaywallGate()
    private var presentedCount: [PaywallTrigger: Int] = [:]
    private let maxPerTrigger = 2

    /// Returns true if the paywall for this trigger may still be shown.
    /// User-explicit entry points (Settings, toolbar) should bypass this.
    func shouldPresent(_ trigger: PaywallTrigger) -> Bool {
        presentedCount[trigger, default: 0] < maxPerTrigger
    }

    func markPresented(_ trigger: PaywallTrigger) {
        presentedCount[trigger, default: 0] += 1
    }
}

enum PurchaseState {
    case purchased
    case cancelled
    case pending
}

enum RCProductKind: Int {
    case lifetime = 0
    case yearly = 1
    case monthly = 2
    case other = 3
}

extension RCProductKind {
    init(package: Package) {
        switch package.packageType {
        case .lifetime:
            self = .lifetime
        case .annual:
            self = .yearly
        case .monthly:
            self = .monthly
        default:
            let identifiers = [package.identifier, package.storeProduct.productIdentifier].map { $0.lowercased() }
            if identifiers.contains(where: { $0.contains(StatScoutProduct.lifetime.lowercased()) }) {
                self = .lifetime
            } else if identifiers.contains(where: { $0.contains(StatScoutProduct.yearly.lowercased()) || $0.contains("annual") }) {
                self = .yearly
            } else if identifiers.contains(where: { $0.contains(StatScoutProduct.monthly.lowercased()) }) {
                self = .monthly
            } else {
                self = .other
            }
        }
    }
}

extension Package {
    var productKind: RCProductKind {
        RCProductKind(package: self)
    }

    var displayName: String {
        switch productKind {
        case .lifetime: return "Lifetime"
        case .yearly: return "Yearly"
        case .monthly: return "Monthly"
        case .other: return storeProduct.localizedTitle
        }
    }

    var priceLabel: String {
        guard let period = storeProduct.subscriptionPeriod else { return storeProduct.localizedPriceString }
        let unit: String
        switch period.unit {
        case .day: unit = period.value == 1 ? "day" : "days"
        case .week: unit = period.value == 1 ? "week" : "weeks"
        case .month: unit = period.value == 1 ? "month" : "months"
        case .year: unit = period.value == 1 ? "year" : "years"
        @unknown default: unit = ""
        }
        if period.value == 1 {
            return "\(storeProduct.localizedPriceString) / \(unit)"
        } else {
            return "\(storeProduct.localizedPriceString) / \(period.value) \(unit)"
        }
    }

    /// Localized per-month price for any recurring package. For a yearly
    /// product priced at $29.99/yr this returns "$2.50". Lifetime / non-
    /// recurring products return nil. Used on the paywall plan card so the
    /// annual price doesn't look like sticker shock next to monthly.
    var monthlyEquivalentLabel: String? {
        guard let period = storeProduct.subscriptionPeriod else { return nil }
        let monthsDecimal: Decimal
        switch period.unit {
        case .day:   monthsDecimal = Decimal(period.value) / Decimal(30)
        case .week:  monthsDecimal = Decimal(period.value) * Decimal(7) / Decimal(30)
        case .month: monthsDecimal = Decimal(period.value)
        case .year:  monthsDecimal = Decimal(period.value) * Decimal(12)
        @unknown default: return nil
        }
        // Only show /mo breakdown for periods that aren't already monthly —
        // showing "$4.99/mo" under a "$4.99/month" price is noise.
        guard monthsDecimal > 1 else { return nil }
        let perMonth = storeProduct.price / monthsDecimal
        let formatter = storeProduct.priceFormatter ?? Self.defaultCurrencyFormatter(currencyCode: storeProduct.currencyCode)
        return formatter.string(from: perMonth as NSDecimalNumber)
    }

    /// Compact "/mo" label suitable for a strike-through anchor on the yearly
    /// card. Returns the package's localized monthly price (per-month for an
    /// annual product, the price itself for a true monthly product).
    var monthlyEquivalentAnchorLabel: String? {
        switch productKind {
        case .monthly:
            return "\(storeProduct.localizedPriceString)/mo"
        case .yearly, .lifetime, .other:
            guard let perMonth = monthlyEquivalentLabel else { return nil }
            return "\(perMonth)/mo"
        }
    }

    private static func defaultCurrencyFormatter(currencyCode: String?) -> NumberFormatter {
        let f = NumberFormatter()
        f.numberStyle = .currency
        if let code = currencyCode { f.currencyCode = code }
        return f
    }

    var introOfferLabel: String? {
        guard let intro = storeProduct.introductoryDiscount, intro.paymentMode == .freeTrial else {
            return nil
        }
        let period = intro.subscriptionPeriod
        let unit: String
        switch period.unit {
        case .day: unit = period.value == 1 ? "day" : "days"
        case .week: unit = period.value == 1 ? "week" : "weeks"
        case .month: unit = period.value == 1 ? "month" : "months"
        case .year: unit = period.value == 1 ? "year" : "years"
        @unknown default: unit = ""
        }
        if period.unit == .week {
            return "\(period.value * 7)-day free trial"
        } else {
            return "\(period.value)-\(unit.dropLast(period.value == 1 ? 0 : 1)) free trial"
        }
    }
}

extension CustomerInfo {
    var hasProEntitlement: Bool {
        let active = entitlements.active
        if active[RevenueCatConfig.proEntitlement]?.isActive == true
            || active[RevenueCatConfig.fallbackEntitlement]?.isActive == true {
            return true
        }
        // Belt-and-suspenders: if the entitlement mapping on the dashboard is
        // missing or mis-named, fall back to product ownership. Lifetime is a
        // non-consumable; recurring products show up under activeSubscriptions.
        if nonSubscriptions.contains(where: { $0.productIdentifier == StatScoutProduct.lifetime }) {
            return true
        }
        let recurring: Set<String> = [StatScoutProduct.yearly, StatScoutProduct.monthly]
        if !activeSubscriptions.intersection(recurring).isEmpty {
            return true
        }
        return false
    }
}

extension Offering {
    /// Paywall display order: yearly first (conversion default with trial +
    /// savings badge), then monthly (price anchor), then lifetime (commitment).
    var sortedPackages: [Package] {
        let displayOrder: [RCProductKind] = [.yearly, .monthly, .lifetime, .other]
        return availablePackages.sorted {
            let lhs = displayOrder.firstIndex(of: $0.productKind) ?? displayOrder.count
            let rhs = displayOrder.firstIndex(of: $1.productKind) ?? displayOrder.count
            if lhs != rhs { return lhs < rhs }
            return $0.storeProduct.productIdentifier < $1.storeProduct.productIdentifier
        }
    }
}

extension Offerings {
    var paywallOffering: Offering? {
        offering(identifier: "default") ?? current
    }
}

@MainActor
final class StoreService: NSObject, ObservableObject {
    static let shared = StoreService()

    @Published private(set) var products: [Package] = []
    @Published private(set) var currentOffering: Offering?
    @Published private(set) var customerInfo: CustomerInfo?
    @Published private(set) var isPro: Bool = false
    @Published private(set) var purchaseInFlight: Bool = false
    @Published private(set) var isLoadingProducts: Bool = false
    @Published private(set) var lastError: String?

    /// Per-product intro-offer eligibility. Populated with `fetchProducts` so
    /// trial copy only appears for users StoreKit will actually grant a trial.
    @Published private(set) var introEligibility: [String: Bool] = [:]

    private var paywallImpressionsThisSession: Set<String> = []

    var proPrice: String? {
        products.first(where: { $0.productKind == .lifetime })?.storeProduct.localizedPriceString
    }

    /// CTA label for the blurred contextual paywalls (Year Compare, Player
    /// Compare). Leads with the yearly free-trial offer when available so the
    /// upsell emphasizes the low-friction option instead of the lifetime price.
    var paywallBlurCTA: String {
        if let yearly = products.first(where: { $0.productKind == .yearly }) {
            if isEligibleForIntroOffer(yearly), let trial = yearly.introOfferLabel {
                return "Start \(trial)"
            }
            return "Try StatScout+ for \(yearly.priceLabel)"
        }
        if let price = proPrice {
            return "Unlock StatScout+ for \(price)"
        }
        return "Unlock StatScout+"
    }

    /// One-line secondary caption shown under the CTA when a trial is offered,
    /// so the price after the trial isn't hidden.
    var paywallBlurSubtext: String? {
        guard let yearly = products.first(where: { $0.productKind == .yearly }),
              isEligibleForIntroOffer(yearly),
              yearly.introOfferLabel != nil else { return nil }
        return "Then \(yearly.priceLabel). Cancel anytime."
    }

    /// The yearly package — the one-tap conversion target for every trial /
    /// teaser pop-up (onboarding, TrialPitchSheet, blur CTAs). Those surfaces
    /// purchase this directly, trial or not; the full `PaywallView` is only the
    /// fallback when this is nil (products not loaded), or for deliberate
    /// upgrade entry points where the user should pick a plan.
    var yearlyPackage: Package? {
        products.first { $0.productKind == .yearly }
    }

    /// Full Apple-3.1.2 auto-renew disclosure for the yearly plan, shown next
    /// to any direct-purchase CTA so the price (and trial terms, when offered)
    /// are present at the point of purchase.
    var yearlyCTADisclosureText: String? {
        guard let yearly = yearlyPackage else { return nil }
        let renew = "Auto-renews unless cancelled at least 24 hours before the end of the current period. Manage or cancel in Settings › Apple ID › Subscriptions."
        if isEligibleForIntroOffer(yearly), let trial = yearly.introOfferLabel {
            return "\(trial.capitalized), then \(yearly.priceLabel). \(renew)"
        }
        return "\(yearly.priceLabel). \(renew)"
    }

    /// The monthly package, when present. Used as the anchor when computing
    /// yearly savings % and rendering a strike-through monthly-equivalent price.
    var monthlyPackage: Package? {
        products.first { $0.productKind == .monthly }
    }

    /// Integer savings % the yearly package offers vs. 12× the monthly price.
    /// Returns nil unless both packages are present and the math is favorable.
    func yearlySavingsPercent(yearly: Package) -> Int? {
        guard yearly.productKind == .yearly, let monthly = monthlyPackage else { return nil }
        let yearlyPrice = yearly.storeProduct.price
        let twelveMonths = monthly.storeProduct.price * Decimal(12)
        guard twelveMonths > 0, yearlyPrice < twelveMonths else { return nil }
        let saving = (twelveMonths - yearlyPrice) / twelveMonths * Decimal(100)
        var rounded = Decimal()
        var src = saving
        NSDecimalRound(&rounded, &src, 0, .plain)
        let percent = NSDecimalNumber(decimal: rounded).intValue
        return percent > 0 ? percent : nil
    }

    /// Strike-through anchor price for the yearly card — "$4.99/mo" if a
    /// monthly package exists. Nil when there's nothing to anchor against.
    var monthlyAnchorPriceLabel: String? {
        monthlyPackage?.monthlyEquivalentAnchorLabel
    }

    /// True when this package advertises a free trial and the user is eligible.
    /// Unknown eligibility resolves to true so a transient lookup failure does
    /// not hide a trial the user likely qualifies for (Vitals pattern).
    func isEligibleForIntroOffer(_ package: Package) -> Bool {
        guard package.introOfferLabel != nil else { return false }
        return introEligibility[package.storeProduct.productIdentifier] ?? true
    }

    /// Reports a custom-paywall impression to RevenueCat (required for native UI).
    func trackPaywallImpression(id: String, oncePerSession: Bool = false) {
        configureIfNeeded()
        #if DEBUG
        if ProcessInfo.processInfo.environment["FORCE_PRO"] == "1" { return }
        #endif
        guard isConfigured else { return }
        if oncePerSession {
            guard !paywallImpressionsThisSession.contains(id) else { return }
            paywallImpressionsThisSession.insert(id)
        }
        Purchases.shared.trackCustomPaywallImpression(
            CustomPaywallImpressionParams(paywallId: id)
        )
    }

    /// True when the user once held a Pro entitlement that has since expired and
    /// isn't currently active. Used to show a tailored win-back paywall.
    var isLapsed: Bool {
        guard !isPro, let info = customerInfo else { return false }
        return info.entitlements.all.values.contains { entitlement in
            !entitlement.isActive
                && (entitlement.expirationDate.map { $0 < Date() } ?? false)
        }
    }

    /// The generic "upgrade" ask, swapped to a win-back variant for lapsed users.
    var defaultUpgradeTrigger: PaywallTrigger {
        isLapsed ? .winback : .upgrade
    }

    private let logger = Logger(subsystem: "com.jackwallner.football", category: "Store")
    private var isConfigured = false

    private override init() {}

    func start() {
        #if DEBUG
        // UI-test / local hook: force Pro so the gated surfaces (Recent Form,
        // Compare) render without a sandbox purchase. Never compiled into Release.
        if ProcessInfo.processInfo.environment["FORCE_PRO"] == "1" {
            isPro = true
            return
        }
        #endif
        configureIfNeeded()
        Task { await updateCustomerProductStatus(fetchPolicy: .fetchCurrent) }
        Task { await fetchProducts() }
    }

    func fetchProducts() async {
        configureIfNeeded()
        guard isConfigured else { return }
        isLoadingProducts = true
        defer { isLoadingProducts = false }
        do {
            let offerings = try await Purchases.shared.offerings()
            let offering = offerings.paywallOffering
            currentOffering = offering
            products = offering?.sortedPackages ?? []
            lastError = nil
            await refreshIntroEligibility()
        } catch {
            logger.error("Product fetch failed: \(String(describing: error), privacy: .public)")
            lastError = "Couldn't load subscription options. Check your connection and try again."
        }
    }

    @discardableResult
    func purchase(_ product: Package) async throws -> PurchaseState {
        configureIfNeeded()
        guard isConfigured else { return .pending }
        purchaseInFlight = true
        defer { purchaseInFlight = false }

        let result = try await Purchases.shared.purchase(package: product)
        apply(customerInfo: result.customerInfo)
        if result.userCancelled {
            return .cancelled
        } else if result.customerInfo.hasProEntitlement {
            return .purchased
        } else {
            return .pending
        }
    }

    func updateCustomerProductStatus(fetchPolicy: CacheFetchPolicy = .default) async {
        configureIfNeeded()
        guard isConfigured else { return }
        do {
            let info = try await Purchases.shared.customerInfo(fetchPolicy: fetchPolicy)
            apply(customerInfo: info)
            lastError = nil
        } catch {
            logger.error("Customer info refresh failed: \(String(describing: error), privacy: .public)")
            lastError = "Couldn't refresh your subscription status. Check your connection and try again."
        }
    }

    func restorePurchases() async {
        configureIfNeeded()
        guard isConfigured else { return }
        lastError = nil
        do {
            let info = try await Purchases.shared.restorePurchases()
            apply(customerInfo: info)
            lastError = isPro ? nil : "No active StatScout+ purchase was found for this Apple ID."
        } catch {
            logger.error("Restore failed: \(String(describing: error), privacy: .public)")
            lastError = "Couldn't restore purchases. Try again."
        }
    }

    private func refreshIntroEligibility() async {
        let identifiers = products
            .filter { $0.storeProduct.introductoryDiscount != nil }
            .map(\.storeProduct.productIdentifier)
        guard !identifiers.isEmpty else {
            introEligibility = [:]
            return
        }
        guard isConfigured else { return }
        let result = await Purchases.shared.checkTrialOrIntroDiscountEligibility(productIdentifiers: identifiers)
        introEligibility = result.mapValues { $0.status == .eligible }
    }

    func apply(customerInfo: CustomerInfo) {
        self.customerInfo = customerInfo
        let activeKeys = customerInfo.entitlements.active.keys.sorted().joined(separator: ", ")
        let allKeys = customerInfo.entitlements.all.keys.sorted().joined(separator: ", ")
        logger.info("Applied customerInfo — active: [\(activeKeys, privacy: .public)] all: [\(allKeys, privacy: .public)]")
        let hasActiveSubscription = customerInfo.hasProEntitlement
        if isPro != hasActiveSubscription {
            isPro = hasActiveSubscription
            logger.info("isPro updated to \(hasActiveSubscription, privacy: .public)")
        }
    }

    #if DEBUG
    /// Populates `products` with mock packages (RevenueCat `TestStoreProduct`) so
    /// the real PaywallView renders with correct prices/trials for App Store and
    /// App Review screenshots — no `Purchases.configure`, no StoreKit, no network,
    /// so prod RevenueCat stays clean. Used by `PaywallScreenshotHarness`.
    func loadScreenshotProducts() {
        let locale = Locale(identifier: "en_US")
        func weekTrial() -> TestStoreProductDiscount {
            TestStoreProductDiscount(
                identifier: "free_trial", price: 0, localizedPriceString: "$0.00",
                paymentMode: .freeTrial, subscriptionPeriod: .init(value: 1, unit: .week),
                numberOfPeriods: 1, type: .introductory)
        }
        let monthly = TestStoreProduct(
            localizedTitle: "Gridiron Pro Monthly", price: 1.99, currencyCode: "USD",
            localizedPriceString: "$1.99", productIdentifier: StatScoutProduct.monthly,
            productType: .autoRenewableSubscription, localizedDescription: "Gridiron Pro, billed monthly.",
            subscriptionPeriod: .init(value: 1, unit: .month), introductoryDiscount: weekTrial(), locale: locale)
        let yearly = TestStoreProduct(
            localizedTitle: "Gridiron Pro Yearly", price: 14.99, currencyCode: "USD",
            localizedPriceString: "$14.99", productIdentifier: StatScoutProduct.yearly,
            productType: .autoRenewableSubscription, localizedDescription: "Gridiron Pro, billed yearly.",
            subscriptionPeriod: .init(value: 1, unit: .year), introductoryDiscount: weekTrial(), locale: locale)
        let lifetime = TestStoreProduct(
            localizedTitle: "Gridiron Pro Lifetime", price: 29.99, currencyCode: "USD",
            localizedPriceString: "$29.99", productIdentifier: StatScoutProduct.lifetime,
            productType: .nonConsumable, localizedDescription: "Gridiron Pro, one-time purchase.",
            subscriptionPeriod: nil, introductoryDiscount: nil, locale: locale)
        products = [
            Package(identifier: "$rc_annual", packageType: .annual,
                    storeProduct: yearly.toStoreProduct(), offeringIdentifier: "default", webCheckoutUrl: nil),
            Package(identifier: "$rc_monthly", packageType: .monthly,
                    storeProduct: monthly.toStoreProduct(), offeringIdentifier: "default", webCheckoutUrl: nil),
            Package(identifier: "$rc_lifetime", packageType: .lifetime,
                    storeProduct: lifetime.toStoreProduct(), offeringIdentifier: "default", webCheckoutUrl: nil),
        ]
        introEligibility = [StatScoutProduct.monthly: true, StatScoutProduct.yearly: true]
        isLoadingProducts = false
        lastError = nil
    }
    #endif

    private func configureIfNeeded() {
        guard !isConfigured else { return }
        #if targetEnvironment(simulator)
        // Agent/sim runs must NOT hit the prod RevenueCat project — configuring
        // the prod appl_ key on the simulator creates fake "new customers" in the
        // prod charts (RC has no dashboard switch to exclude sim installs). Skip
        // configure entirely; use StoreKit Testing + a local Pro override for
        // paywall/IAP flows on device-less runs.
        return
        #else
        #if DEBUG
        Purchases.logLevel = .debug
        #endif
        Purchases.configure(withAPIKey: RevenueCatConfig.apiKey)
        Purchases.shared.delegate = self
        isConfigured = true
        #endif
    }
}

extension StoreService: PurchasesDelegate {
    nonisolated func purchases(_ purchases: Purchases, receivedUpdated customerInfo: CustomerInfo) {
        Task { @MainActor in
            StoreService.shared.apply(customerInfo: customerInfo)
        }
    }
}
