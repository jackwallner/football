# Football Next: StatScout, audit823

Fresh max-reasoning rerun, 2026-08-23

## Scope and operating rules

This audit covers only `/Users/jackwallner/football`, the Football Next: StatScout iOS app. It is intended as an implementation handoff for another agent working in Cursor, Claude Code, or Codex.

The audit is read-only. No app code, configuration, metadata, website, script, or other file was changed by this audit. The only intended output is this file. No commit or push is part of the work.

The review focused on:

- App Store discovery, metadata, screenshots, landing-page conversion, and download paths.
- Activation, onboarding, first value, paywall entry points, trial presentation, purchase, restore, and post-purchase behavior.
- RevenueCat product and entitlement wiring, custom paywall impressions, missing attributes, and missing funnel events.
- Ratings and review request timing.
- UX, accessibility, error states, data freshness, offline behavior, and likely release regressions.
- Website, privacy, terms, support, subscription, pricing, product-name, and data-description consistency.
- Crash and production-watchdog signals that can be monitored without adding AI.
- Cursor, Claude, and Codex documentation hygiene, including stale Baseball fork material that could cause an agent to edit or ship the wrong app.

### Evidence labels

- **Observed** means found in local source, configuration, documentation, or the signed-in ASC and RevenueCat context available for this review.
- **Inference** means a plausible risk derived from observed behavior that still needs runtime, ASC, RevenueCat, or production-data validation.
- **Missing evidence** means the audit did not have a trustworthy current value. It is not a zero and should not be filled with a guess.
- **P0** means address before the next release or before trusting an agent with release work.
- **P1** means high-value conversion, reliability, or trust work for the next release cycle.
- **P2** means useful optimization or hygiene work after P0 and P1.

## Executive decision summary

Football has a credible product loop: no-account onboarding, a free current-season experience, player and team analysis, historical depth, recent form, comparisons, a custom SwiftUI paywall, yearly direct purchase, restore, cached data, and a conservative review prompt. The largest risk is not a missing feature. It is that the app, store record, website, source comments, release notes, and agent instructions do not share one current source of truth.

The first implementation pass should do five things:

1. Establish a Football release manifest that records the current ASC version, build, product prices, offering, entitlement, URLs, screenshots, and data-feed status. Mark or archive the Baseball documents and scripts that are currently in active-looking paths.
2. Correct the Football support and feedback address in the app. The app currently points feedback to `jackwallner+bb@gmail.com`, while Football web and privacy materials use `jackwallner+ff@gmail.com`.
3. Make trial copy conservative when RevenueCat intro-offer eligibility is unknown. `StoreService.isEligibleForIntroOffer(_:)` currently treats a missing eligibility result as eligible, which can show a free-trial claim during a transient eligibility failure.
4. Instrument the download-to-value-to-purchase funnel. Current code records local review counters and RevenueCat custom paywall impressions, but it cannot explain trial starts, trial conversion, paywall conversion, first value, failed offerings, or release regressions by surface.
5. Reconcile the website's structured-data version and lifetime price, the app's product names and prices, recent-form terminology, and nightly versus weekly data claims. Pull ASC and RevenueCat values before changing any customer-facing price.

### Prioritized findings

| Priority | Finding | Evidence | Why it matters | First validation |
|---|---|---|---|---|
| P0 | Release and agent source of truth is split | ASC context says `1.1.0`, Ready for Distribution on 2026-08-23. `CLAUDE.md` still says 1.0 is live. `docs/localization-aso.md` and `docs/astro-aso-setup.md` describe a 1.0 draft that was never released. `APP_STORE_SUBMISSION.md` is Baseball. | An agent can use the wrong version, product IDs, URLs, review notes, or even the wrong sport. | Read ASC version/build state for app `6792930447`, compare to `project.yml`, then scan active docs for Football identity and stale Baseball terms. |
| P0 | Football support and review feedback use a Baseball email | `StatScout/Views/ReviewPromptSheet.swift:248` and `StatScout/Views/SettingsView.swift:242` use `jackwallner+bb@gmail.com`. `docs/support.html`, `docs/privacy-policy.html`, `docs/terms.html`, and `PRIVACY.md` use `jackwallner+ff@gmail.com`. | Users with a problem or negative experience can contact the wrong support queue. This directly damages recovery and review sentiment. | Build a release candidate and exercise every support, feedback, privacy, and mailto link on iPhone and iPad. |
| P0 | Trial eligibility can default to true when RevenueCat has no answer | `StoreService.isEligibleForIntroOffer(_:)` is used by onboarding, direct CTA, paywall cards, and screenshot products. The implementation treats missing eligibility as eligible. | A failed or delayed eligibility lookup can display a free-trial label to an ineligible customer. It also corrupts trial-start analysis. | Test eligible, ineligible, unknown, offline, StoreKit test, and restored-customer states. Do not show a trial badge until the state is known, or state that Apple determines eligibility at checkout. |
| P0 | Active release notes and build evidence are contaminated by Baseball artifacts | `APP_STORE_SUBMISSION.md`, `docs/design.md`, `docs/astro-asc-metadata-proposal.md`, `scripts/ui_test.sh`, `scripts/aso-apply-locale-optimizations.py`, and stored build logs contain Baseball paths, products, or assertions. | A Cursor, Claude, or Codex agent can run a script that targets `com.jackwallner.baseball`, write Baseball metadata, or trust an unrelated compile failure. | Classify every root-level document and script as active, Football, historical, or quarantine. Put the classification into the release manifest and agent instructions. |
| P1 | Website and in-app commerce facts do not have one authority | `docs/index.html` structured data says lifetime `$29.99` and `softwareVersion` `1.0.0`. `StoreService` debug products and ASO docs say lifetime `$19.99`; local project is `1.1.0`. Current ASC prices were not captured in this audit. | Price or version mismatches reduce trust and can invalidate acquisition tests. | Pull live ASC IAP prices and version, then make website JSON-LD, visible copy, screenshot copy, metadata, tests, and docs consume the same values. |
| P1 | Onboarding and contextual paywalls optimize different plans | Onboarding directly buys monthly. `PlusDirectCTA` directly buys yearly. `PaywallView` selects yearly by default. | The same user may see different trial and price anchors based only on entry point, with no attribution or controlled experiment. | Add surface, package, offering, trial-copy, and outcome instrumentation before choosing a global default. |
| P1 | Funnel and operational observability are too thin | Only local review counters and `trackCustomPaywallImpression` were found. No crash SDK, MetricKit upload, purchase funnel event set, or first-value events were found. | It is not possible to distinguish an acquisition problem from an onboarding, API, offering, purchase, or release-regression problem. | Define a small event/state contract, add RevenueCat last-value attributes, and pair it with ASC, Supabase, and workflow snapshots. |
| P1 | Recent-form wording may describe weeks as games | Backend and `RecentForm` use `window_weeks`; `DashboardViewModel` documents recent form as weeks in places, while store metadata and several UI surfaces say last 3, 5, or 8 games. | Users can misunderstand the paid feature and an agent can change the wrong layer. | Verify the rendered label and returned row semantics for player, team, leaderboard, and finished-season paths. Choose games or weeks per surface, then align all copy. |
| P1 | Website has a baseball metric description | `docs/support.html:120` calls the source public pitch-tracking data. The Football app uses public NFL data from nflverse and related sources. | It undermines SEO, trust, support accuracy, and App Review review notes. | Load support page and search every Football site page for baseball, Statcast, pitch, MLB, and outdated product terms. |
| P1 | Production data pipeline can fail without a strong data-quality gate | `.github/workflows/nightly-statcast.yml` runs nightly, uploads logs, and creates a GitHub issue on failure, but `continue-on-error: true` is present and no complete post-ingestion freshness, row-count, schema, or coverage assertion was observed in the workflow. | The app can continue serving stale or incomplete data while the failure is only visible in Actions. | Add read-only post-ingest checks and owner notification thresholds. Verify `updated_at`, season coverage, row counts, phase, and recent-form windows. |
| P1 | Network path has no explicit retry or latency instrumentation | `StatScout/Services/StatcastAPI.swift` uses `URLSession.shared.data(for:)` for current, historical, logs, recent form, and coverage requests. | A temporary Supabase or network failure can look like an empty app, and there is no way to measure where activation is lost. | Capture request latency, status, decode failure, retry count, data state, and cache age in a release candidate with a safe test backend. |
| P2 | ASO is at limits but the keyword strategy needs evidence | en-US name is 24 characters, subtitle is 30, and keywords are exactly 100 characters. The keyword field begins with `gen`, which may be weak or ambiguous. | There is little room for accidental edits. A broad change can hurt branded discovery. | Use ASC Search Ads and product-page tests for one controlled change at a time. Keep a versioned keyword rationale by locale. |
| P2 | Landing-page canonical and App Store marketing URLs differ | `docs/index.html:10` canonicalizes to `https://jackwallner.com/ios/football/`, while en-US ASC metadata uses `https://jackwallner.github.io/football/`. | Search, share links, and App Store traffic may split between two deployment paths. | Fetch both hosts, compare content, redirects, HTTPS, canonical tags, App Store links, and legal links. Select one canonical deployment. |
| P2 | Review timing uses profile opens as a positive moment | `PlayerProfileView.swift:207-214` increments profile opens and records a positive moment at the third open, even if the profile was empty or the data request degraded. | The review prompt can appear after repeated browsing without proof that the user received value. | Gate the positive moment on successful data render plus a meaningful action such as compare, favorite, trend inspection, or completed team view. |

## 1. App identity, release status, and evidence gaps

### Local identity

**Observed:**

- `project.yml:30` sets bundle ID `com.jackwallner.football`.
- `project.yml:32-33` sets marketing version `1.1.0` and build `35`.
- The product target is named `Gridiron StatScout`, while `StatScout/Info.plist` displays `StatScout`.
- The App Store name in `fastlane/metadata/en-US/name.txt` is `Football Next: StatScout`.
- The website and legal pages use `Football Next` and `StatScout`.
- The paywall uses `StatScout+`, the RevenueCat entitlement is `Football Pro`, and DEBUG StoreKit product titles include `Gridiron Pro Lifetime`.
- RevenueCat product IDs are `com.jackwallner.football.pro`, `com.jackwallner.football.pro.yearly`, and `com.jackwallner.football.pro.monthly`.
- RevenueCat context identifies project `9c303632`, app `app039a312379`, and a Football public App Store key in `StoreService.swift:30-35`.
- Simulator code intentionally does not configure the production RevenueCat key. That is a safety feature and is not a defect.

**Inference:** the names are understandable to a human, but the hierarchy is not explicit enough for agents or customers. `Football Next: StatScout` should be the store brand, `StatScout` should be the app display name, and `StatScout+` should be the customer-facing subscription label, or the owner should choose a different single convention. `Football Pro`, `Gridiron Pro`, and `StatScout+` should not appear interchangeably in customer-facing strings unless that relationship is intentional.

**Recommendation, P1:** create a single Football release manifest containing:

- App Store ID, bundle ID, display name, store name, version, build, and release status.
- Current ASC IAP product IDs, localized prices, trial eligibility policy, RevenueCat offering ID, and entitlement ID.
- Support, privacy, terms, marketing, App Store, and feedback URLs.
- Data source, current season, last successful ingest, coverage, and freshness threshold.
- Screenshot set checksums and the build/version to which each set belongs.
- Active agent docs and scripts, with an explicit quarantine list.

### ASC and release status

**Observed in the signed-in ASC context available on 2026-08-23:**

- App: `Football Next: StatScout`.
- App Store ID: `6792930447`.
- Version shown: `1.1.0`.
- Status shown: `Ready for Distribution`.

**Conflicting local evidence:**

- `CLAUDE.md:9` says 1.0 is live and READY_FOR_SALE as of 2026-08-17.
- `docs/localization-aso.md` describes all locales staged on a draft 1.0 and says it was never released.
- `docs/astro-aso-setup.md` describes a draft 1.0 pending approval and says live ASC was empty.
- `APP_STORE_SUBMISSION.md` is a Baseball submission plan and does not describe this app.
- Existing `build.log`, `build_final.log`, and `xcodebuild.log` include `/Users/jackwallner/baseball`, `Baseball Savvy StatScout`, and a Baseball compile error. They are not valid evidence that the Football target currently fails.

**Missing evidence:** the exact current live build number, current ASC IAP prices, current screenshot checksum state, current storefront rating counts, current review state, and current per-build crash rates were not captured as numeric Football data in this audit. Do not infer these from local mocks or stale notes.

**Required read-only reconciliation:**

1. Read app `6792930447` from ASC and record the live version, build, processing state, release status, availability, and all IAP prices by storefront.
2. Compare the live metadata and screenshot sets to `fastlane/metadata` and `fastlane/screenshots`.
3. Record the RevenueCat current offering, package IDs, product IDs, entitlement, trial configuration, and product availability.
4. Replace the stale release statements in the active source of truth. Archive historical notes with an explicit date and `historical` label rather than leaving them in an active-looking path.

## 2. Acquisition and App Store conversion

### en-US metadata inventory

**Observed in `fastlane/metadata/en-US`:**

| Field | Current value or shape | Audit assessment |
|---|---|---|
| Name | `Football Next: StatScout` | 24 characters. Strong category plus brand, but confirm whether `Football Next` has enough branded search demand. |
| Subtitle | `Advanced NFL Stats & Analytics` | 30 characters, at the limit. Clear value proposition, but the `NFL` term should be kept only if the legal and non-affiliation framing stays visible. |
| Keywords | `gen,epa,cpoe,yac,statistics,compare,player,passing,rushing,receiving,defense,qb,fantasy,teams,trends` | Exactly 100 characters. `gen` is a fragment and may not be an efficient search term. Avoid duplicating name and subtitle terms unless ASC behavior for this locale justifies it. |
| Promotional text | Starts with `Training camp is here.` and names quarterbacks, backs, receivers, tight ends, and defenders | Timely seasonal hook, but it needs a calendar and a refresh owner. It can become stale outside training camp. |
| Description | Leads with advanced EPA, CPOE, YAC, percentiles, player profiles, nightly updates, recent form, historical seasons, comparisons, team scouting, and subscription details | Good feature coverage. The first screen should make the free first value and the paid unlock boundary more concrete. |
| Release notes | Playoffs on team pages, recent-form fixes, glossary, compare-picker and intro-screen truncation fix | Useful but technical. Lead with user outcomes and mention data coverage if a release changes freshness or season behavior. |
| Support URL | `https://jackwallner.github.io/football/support.html` | Football URL and correct `+ff` support address on the page. Verify it is the same deployment as the canonical site. |
| Marketing URL | `https://jackwallner.github.io/football/` | Differs from website canonical `https://jackwallner.com/ios/football/`. Reconcile. |
| Privacy URL | `https://jackwallner.github.io/football/privacy-policy.html` | Football URL and `+ff` contact. Verify the built app, metadata, and website all resolve. |
| Primary category | Sports | Strong fit. |
| Secondary category | Utilities | Defensible for analytics, but compare Sports versus Utilities conversion by storefront if ASC data supports it. |

The description says monthly and yearly subscriptions have a 7-day free trial when eligible, and it describes lifetime purchase. That language must remain conditional because Apple eligibility is customer-specific and the current eligibility path is not always known.

### Discovery strengths

- The name states Football and includes a distinct brand.
- The subtitle communicates advanced statistics and analytics without requiring an account claim.
- The first description paragraph names EPA, CPOE, YAC, percentile rankings, and player profiles, which are concrete product concepts.
- The app provides a real download path from the website to App Store ID `6792930447`.
- The app and website consistently disclaim NFL, team, and NFLPA affiliation in the local materials reviewed.

### Acquisition risks and tests

#### A. Keyword field quality, P2

**Evidence:** the keyword string is at the 100-character limit and begins with `gen`. Some terms are abbreviations and some are broad.

**Hypothesis:** replacing low-intent fragments with validated long-tail search terms such as `advanced football stats`, `player scouting`, `team analytics`, or a position-specific term can increase qualified product-page views without weakening branded discovery.

**Test:** use ASC Search Ads search-term data or another measured source. Change one cluster at a time, keep name, subtitle, screenshots, price, and promotional text fixed, and keep a dated keyword rationale for every locale.

**Guardrails:** product-page conversion, install rate, onboarding completion, first data-ready rate, and trial start rate. Do not optimize only for impressions.

#### B. Seasonal promotional text, P2

**Evidence:** `Training camp is here` is current-season copy.

**Risk:** the copy becomes stale during regular season, playoffs, and offseason, and it does not expose the strongest evergreen reason to download.

**Recommendation:** maintain a four-state calendar: preseason/training camp, regular season, playoffs, and offseason. The evergreen fallback should name the strongest action, such as compare players or inspect advanced percentiles, rather than a date-sensitive event.

#### C. Screenshot promise and proof, P1

**Evidence:** local screenshot directories contain 9 files under `fastlane/screenshots/en-US`, including 8 iPhone images and 1 iPad image. `docs/` contains six app-store screenshots. `CLAUDE.md` records that duplicate screenshot uploads existed in the live 1.0 set and were fixed in 1.1.0, with checksum verification recommended.

**Risk:** duplicate or stale screenshots waste the most valuable acquisition real estate and can leave a version with a different UI than the screenshot set.

**Validation:** for the current ASC version, export screenshot-set metadata and compute source-image checksums. Verify:

- No duplicate source checksums inside a device family.
- iPhone and iPad sets match the current product and current build.
- No Baseball images, copy, URLs, or product names are present.
- The first two screenshots explain the free value and the paid value separately.
- Recent form, team scouting, comparisons, historical seasons, and trial terms are shown only if they work in the current build.
- Screenshot text remains legible at the actual App Store display size.

#### D. Product page structure, P2

Test a page sequence that makes the first free action obvious:

1. `Find any player and see advanced percentiles`.
2. `Compare players and teams`.
3. `Go back through historical seasons and recent form with StatScout+`.

Avoid making the first frame a purchase promise before explaining what the free app does. Use ASC product-page tests for screenshots and promotional copy. Do not combine a screenshot sequence change with a keyword or price change.

## 3. Landing page and download path

### Current path

**Observed:** `docs/index.html` contains:

- Canonical URL `https://jackwallner.com/ios/football/` at line 10.
- Apple smart banner app ID `6792930447` at line 11.
- App Store links using `https://apps.apple.com/us/app/id6792930447`.
- JSON-LD `softwareVersion` `1.0.0` at line 60.
- JSON-LD lifetime price `29.99` at line 51.
- Visible claims of nightly updates and no account.
- Recent-form copy saying last 3, 5, or 8 games.
- A data table describing nflverse weekly stats and Next Gen Stats refreshed nightly.

`fastlane/metadata/en-US` uses the GitHub Pages landing page as the marketing URL. `docs/index.html` canonicalizes to the `jackwallner.com` path. This may be an intentional mirror, but it is not documented as such.

### Findings

#### P1, structured data version and lifetime price

**Evidence:** website JSON-LD says `1.0.0` and lifetime `$29.99`. Local DEBUG StoreKit products in `StoreService.swift:902-920` use `$19.99` for lifetime. `docs/astro-aso-setup.md:34` also says `$19.99`. The live ASC price was not captured in this audit.

**Inference:** this is definitely an authority conflict. It is not yet proven that the website visible price is wrong, because the live ASC price is missing from evidence.

**Action:** make ASC the price authority. Generate or verify website structured data and visible pricing from the same release manifest. Keep DEBUG StoreKit values intentionally separate but labeled as test fixtures. Update version structured data when the App Store version changes.

**Validation:** compare live ASC prices for monthly, yearly, and lifetime in at least US, one non-US storefront, and a tax-included storefront. Verify the website does not promise a price that differs from the checkout sheet.

#### P1, support page has baseball terminology

**Evidence:** `docs/support.html:120` says percentile rankings are calculated using public pitch-tracking data. Football source documentation and `fastlane/metadata/review_information/notes.txt` identify nflverse and NFL statistics, not pitch tracking.

**Action:** replace the stale concept with a concise Football data-source explanation. Say what is computed, how often the feed is refreshed, and what is not live. Keep exact terminology aligned with `backend/README.md`, `backend/ingest.py`, the app About screen, and App Review notes.

#### P2, landing-page conversion gaps to measure

The site has a Download CTA, but no evidence of a download attribution or post-click funnel. Add measurement outside the app that is compatible with the current privacy posture, or use ASC campaign links where appropriate. At minimum, segment:

- Direct versus search versus social/referral landing-page visits.
- Click-through to App Store.
- Install or first launch where ASC data allows it.
- Onboarding completion.
- First data-ready state.
- First player profile.
- Paywall impression and trial start.

Do not use a custom query parameter inside the app as a substitute for ASC campaign attribution. Keep campaign naming stable and document it in the release manifest.

## 4. User journey and activation audit

### Flow inventory

| Stage | Current implementation | Good behavior | Risk or opportunity | Validation |
|---|---|---|---|---|
| Cold launch | `StatScoutApp.init` records an app launch and constructs Supabase API from build configuration. | No account or sign-in barrier. | A missing data-feed configuration creates a device error state; a bad release configuration can look like an empty app. | Launch a Release build with valid configuration, missing configuration, offline network, and a slow network. |
| Onboarding page 1 | `OnboardingCards` introduces Pocket Scout, qualified players, EPA, CPOE, YAC, and RYOE. | Communicates differentiated metrics. | Jargon may arrive before the user sees a concrete result. | Test with a football fan who does not know CPOE or RYOE. Measure skip and completion. |
| Onboarding page 2 | Explains Stats, Trends, Teams, and Compare. | Shows breadth of the free and paid product. | It describes destinations but does not show a single real player or team result. | Compare a real screenshot or small preview against text-only page. |
| Onboarding page 3 | Explains StatScout+ and presents monthly purchase CTA when products load. | Trial and terms are visible when data is available. | Monetization is placed before first free value. Free `Get Started` is secondary. A loading or missing product state can render generic copy. | Test free-first versus purchase-first with a controlled split and activation guardrails. |
| Free entry | `hasCompletedOnboarding` is stored in `AppStorage`. `Get Started` completes onboarding. | User can skip purchase and use free season. | There is no observed event for onboarding completion or reason for exit. | Record completion, skip, page reached, data-ready state, and first useful action. |
| First data load | `DashboardViewModel.load()` can show current data, cached data, historical fallback, or an error. | Cached fallback protects returning users. | No retry or latency telemetry; current data can be stale without a prominent age indicator. | Exercise live, cache, offline, decode error, empty response, and stale response. |
| First profile | `RootTabView` and dashboard routes lead to `PlayerProfileView`. | Profile is the natural first value moment. | Profile opens count toward a paywall and a review moment without proving successful content. | Test empty, partial, and full profile states and record successful render. |
| Locked season or comparison | `PaywallTrigger` routes to `TrialPitchSheet` and `PaywallView`. | Contextual value proposition is better than a generic wall. | Different triggers use different product defaults and there is no conversion attribution. | Compare trigger, title, benefit list, package, and outcome. |
| Recent form | Player, team, and trends surfaces expose recent windows. | Differentiated, concrete paid feature. | `window_weeks` and `games` terminology needs a final product decision. | Confirm data rows, labels, and edge cases for playoffs and finished seasons. |
| Purchase | Direct yearly CTA or full plan picker uses RevenueCat. | Purchase state distinguishes purchased, pending, cancelled, and failed. | Trial, offer, package, and trigger are not persisted as a funnel record. | Test sandbox purchase, trial, pending Ask to Buy, cancellation, network failure, restore, and already-entitled customer. |
| Restore | Paywall and settings expose Restore Purchases. | Required recovery path exists. | Restore result is not exposed as a measurable success or failure state. | Test a reinstall and restore on the same Apple account and a no-purchases account. |
| Returning user | Foreground refresh calls customer status and refreshes data. | Pro status can recover after purchase outside the app. | There is no visible freshness or subscription-state diagnostic when the network fails. | Return from background after renewal, cancellation, refund, and expiration. |

### Activation recommendation

The activation target should be `onboarding completed -> live or cached data visible -> first player or team opened -> one meaningful action completed`. A meaningful action can be:

- Expand a metric definition or glossary item.
- Open a player profile and scroll through metrics.
- Add a favorite.
- Compare two players.
- Inspect recent form.
- Switch a team or season and see a populated result.

Do not count a profile route opening as a positive moment if the view is empty, cached beyond the accepted age, or showing a data error.

### UX findings

#### P1, first value comes after a three-page gate

**Evidence:** `StatScout/StatScoutApp.swift:72-88` disables the main content until `hasCompletedOnboarding` is true. `OnboardingCards` is a three-page `TabView`. The final page offers monthly purchase and a lower-priority `Get Started` path.

**Inference:** the flow may maximize immediate purchase taps at the expense of activation. Because the product's strongest proof is a player profile, a free current-season view may convert better after the user experiences one result.

**Test:**

- Variant A: current three-page onboarding with monthly CTA.
- Variant B: same education, primary `Get Started`, then a contextual paywall after first successful profile or locked historical tap.

Primary metrics: onboarding completion, first data-ready, first profile, day-one return, trial start, and paid conversion. Guardrails: uninstall, support contacts, paywall dismissal, and review sentiment.

#### P1, generic product state can remove price context

**Evidence:** `StatScout/StatScoutApp.swift:142-156` makes the monthly offer optional. When the package or disclosure is absent, it can show `Upgrade to StatScout+` and route purchase to the full paywall.

**Risk:** a purchase CTA without a price or clear disclosure can create hesitation and may be unsuitable for a final release state.

**Action:** use a loading state until the product is available, or make the free path primary while product state is unknown. Never show a purchase label that implies a price or trial that is not confirmed.

#### P1, direct CTA and full paywall have different anchors

**Evidence:** `StatScout/Views/Components.swift:814-959` implements `PlusDirectCTA`. It tracks an impression, presents a yearly direct CTA, and keeps a `See all plans` link. `PaywallView` defaults to yearly. Onboarding uses monthly.

**Action:** keep the contextual yearly CTA only if it has better net conversion and retention. Otherwise make package choice consistent, or intentionally segment by trigger and document why.

#### P1, recent-form semantics must be resolved

**Evidence:** `backend/README.md` and `RecentForm.swift` use rolling `window_weeks`. `DashboardViewModel.swift:308-310` documents recent trends as weeks. `RecentForm.swift:237-250` contains both games and weeks labels. Store metadata and several UI strings say last 3, 5, or 8 games. Team UI also contains last-N-games copy.

**Inference:** there may be two valid data models, player game-log windows and precomputed weekly recent form, but the acquisition promise does not distinguish them.

**Action:** document each surface:

- Player recent form: last 3, 5, or 8 games if the row is game-based.
- Trends or leaderboard recent form: last 3, 5, or 8 weeks if the rollup is week-based.
- Team logs: last N games or a date window, whichever the code actually uses.

Then align the paywall, onboarding, App Store description, website, glossary, and tests. Do not silently change data semantics while changing only a label.

#### P1, offline promise needs a precise boundary

**Evidence:** `PlayerProfileView` upsell copy includes `Saved offline - works on the road`. `DashboardViewModel` has a current cache and a historical local plist, with current cache freshness documented as 48 hours.

**Inference:** historical data may be available offline, but current data is not guaranteed to be fresh offline.

**Action:** say exactly what is available offline, such as `Historical seasons are bundled. Recent data remains available from cache for up to 48 hours.` Validate cold launch in airplane mode after first use.

#### P2, navigation and accessibility validation

**Observed:** `RootTabView` keeps inactive tabs alive but hides them using opacity, hit testing, and accessibility settings. It uses a custom floating tab bar. The project targets iOS 17 and supports iPhone and iPad.

**Validation required:**

- VoiceOver can identify Stats, Trends, Teams, Compare, close, restore, price cards, trial terms, and locked controls.
- Dynamic Type does not clip the fixed CTA and 460-point trial sheet on small iPhones.
- iPad portrait and landscape do not let the floating bar cover scrollable content.
- Reduce Motion, increased contrast, and color-blind interpretation of percentile bars are acceptable.
- The light-only presentation is legible in bright and dark system environments if the app intentionally forces light mode.
- App Store screenshots reflect the actual accessibility-safe layout, not only the default text size.

## 5. Monetization, trial, and RevenueCat audit

### Current commerce architecture

**Observed in `StatScout/Services/StoreService.swift`:**

- RevenueCat is imported and configured only for real device use.
- Product IDs are `com.jackwallner.football.pro`, `.pro.yearly`, and `.pro.monthly`.
- Primary entitlement is `Football Pro`; fallback entitlement is `pro`.
- `CustomerInfo.hasProEntitlement` accepts either entitlement and also checks product ownership as a fallback.
- Offerings load from `default` or the current offering.
- Package ordering is yearly, monthly, lifetime.
- `introOfferLabel` derives the StoreKit introductory offer.
- `isEligibleForIntroOffer(_:)` is used to decide whether trial labels and disclosures appear.
- `trackPaywallImpression(id:oncePerSession:)` calls RevenueCat custom paywall impression tracking.
- `PaywallGate` limits repeated automatic prompts to two per trigger per session.
- Purchase outcomes include purchased, pending, cancelled, failed, and a missing-plan-picker path.
- Foreground refresh and restore call RevenueCat customer information.
- DEBUG screenshot mode injects StoreKit test products at monthly `$1.99`, yearly `$9.99`, and lifetime `$19.99`, with one-week trials on subscriptions. These are test fixtures, not authoritative live pricing.

This is a sound base for a custom paywall, but it currently gives RevenueCat an impression signal without the surrounding funnel context required to interpret that signal.

### P0, unknown trial eligibility must not look eligible

**Evidence:** `StoreService.isEligibleForIntroOffer(_:)` at line 675 is used by the onboarding offer, direct CTA labels, paywall product cards, and trial disclosure helpers. The implementation treats a missing entry in the eligibility map as eligible.

**Failure mode:** RevenueCat eligibility lookup can be delayed, fail, or return no entry for a product. A missing result is not proof that the customer is eligible. The app can show `7-day free trial` while Apple will not offer it.

**Recommended behavior:** represent eligibility as a tri-state value:

- `eligible`: show the trial label and trial disclosure.
- `ineligible`: show the normal recurring price and no trial badge.
- `unknown`: show no trial badge and a neutral note that any introductory offer is determined by Apple at checkout, or hold the purchase CTA until the state is known.

Do not solve this with a longer timeout that blocks the whole app. Let the free app activate while commerce state loads.

**Validation matrix:**

| Customer state | Expected UI | Expected purchase result |
|---|---|---|
| New eligible sandbox customer | Trial badge and exact conditional disclosure | Apple checkout shows trial, RevenueCat records subscription with trial phase |
| Previously used trial | No trial badge, recurring price only | Checkout has no introductory trial |
| Lifetime owner | Pro state, no acquisition wall | Restore and foreground refresh retain Pro |
| RevenueCat eligibility request fails | No asserted trial badge | Purchase still works with Apple-authoritative terms |
| Product list still loading | Loading state or free path | No generic price-free purchase promise |
| Pending or Ask to Buy | Pending state, no false unlock | Pro unlocks only after active entitlement |

### P1, product and plan strategy

The three product types are sensible for this product:

- Monthly for low-friction entry.
- Yearly for a lower effective monthly rate and stronger retention.
- Lifetime for a high anchor and users who reject subscriptions.

The current UI makes a different default choice by surface:

| Surface | Current default or CTA | Question to answer |
|---|---|---|
| Onboarding page 3 | Monthly direct purchase | Does a low price improve trial starts but reduce annual conversion and net revenue? |
| TrialPitchSheet | Yearly direct CTA through `PlusDirectCTA` | Does contextual value support an annual commitment? |
| Full `PaywallView` | Yearly selected by default | Does annual preselection improve paid conversion without increasing refunds or early cancellations? |
| Full plan picker | Yearly, monthly, lifetime ordering | Is lifetime a useful anchor or a distraction at the moment of intent? |

Do not make one global pricing decision until outcomes are segmented by `PaywallTrigger` and package. Measure trial starts, paid starts, trial-to-paid conversion, refund rate, cancellation, renewal, and net proceeds, not only CTA taps.

### P1, custom attributes to add

RevenueCat custom attributes are last-known values, not an event stream. They are useful for segmentation and debugging. They cannot replace ASC or a durable event source for counts such as trial starts or conversion cohorts.

Use short, stable, low-cardinality values. Do not send raw player names, search text, private notes, or high-volume interaction logs.

| Attribute | Values | Set or update point | Use |
|---|---|---|---|
| `app_build` | `35` | App launch and foreground | Segment purchase and crash behavior by release build. |
| `app_version` | `1.1.0` | App launch | Compare releases without parsing logs. |
| `funnel_stage` | `install`, `onboarding`, `data_ready`, `first_value`, `paywall`, `purchase_pending`, `pro`, `lapsed` | At each durable state transition | Current customer position in the funnel. |
| `onboarding_completed` | `true` or `false` | When `hasCompletedOnboarding` changes | Separate install friction from product friction. |
| `onboarding_exit_page` | `0`, `1`, `2`, `completed` | Skip, Get Started, purchase, or Pro completion | Diagnose education and monetization placement. |
| `data_state` | `live`, `cache`, `historical`, `offline_empty`, `decode_error`, `config_error` | After `DashboardViewModel.load()` determines its display state | Connect activation and conversion to data quality. |
| `data_age_bucket` | `fresh`, `under_24h`, `24_to_48h`, `over_48h`, `unknown` | When data is displayed | Test whether stale data drives churn or support. |
| `first_value_action` | `profile`, `favorite`, `compare`, `recent_form`, `team`, `season`, `glossary` | First successful action only | Define activation without raw content. |
| `last_paywall_trigger` | The existing `PaywallTrigger` raw value | At `PaywallView` and `TrialPitchSheet` presentation | Attribute conversion to context. |
| `last_paywall_surface` | `onboarding`, `trial_sheet`, `full_paywall`, `settings` | At presentation | Compare flow shape. |
| `last_paywall_impression_id` | Existing `statscout_paywall_*` ID | With custom impression tracking | Join RevenueCat impression data to app context. |
| `last_offering_id` | RevenueCat offering identifier | After offerings load | Detect serving and experiment differences. |
| `last_selected_package` | `monthly`, `yearly`, `lifetime` | On selection and before purchase | Understand package intent. |
| `trial_copy_state` | `eligible`, `ineligible`, `unknown`, `not_offered` | When product eligibility resolves | Verify the P0 eligibility fix. |
| `last_purchase_outcome` | `purchased`, `pending`, `cancelled`, `failed`, `restored`, `missing_product` | After purchase or restore finishes | Find payment and offering failures. |
| `pro_state` | `free`, `pro` | After `apply(customerInfo:)` | Segment current status. |
| `recent_form_mode` | `games`, `weeks`, `none` | When a recent-form surface is selected | Detect copy and data-semantic mismatch. |
| `last_tab` | `stats`, `trends`, `teams`, `compare` | On tab selection, debounced | Understand discovery paths with low cardinality. |
| `review_state` | `not_eligible`, `eligible`, `shown`, `rated_link`, `feedback_link`, `deferred` | Review prompt state changes | Diagnose whether the review funnel is helping. |
| `storefront` | StoreKit storefront country code | At launch if available | Segment price and trial performance by market. |
| `device_class` | `iphone`, `ipad` | At launch | Identify iPad layout and conversion differences. |

Set these through a small wrapper around RevenueCat so the app can validate names and values in one place. Do not scatter ad hoc calls across SwiftUI views.

### Exact insertion points for instrumentation

1. `StatScoutApp.swift` launch and foreground: set version, build, storefront, `funnel_stage`, and review state.
2. `OnboardingCards`: set page impressions, skip, completion, `onboarding_exit_page`, and CTA intent. Set `onboarding_completed` only when the free or Pro path actually completes.
3. `DashboardViewModel.load()`: after the state is known, set `data_state`, cache-age bucket, and first-value eligibility. Do not set `live` merely when a request starts.
4. `PlayerProfileView`: set first successful profile action, not only `onAppear`. Preserve the existing low-cardinality count if needed for review logic.
5. `TrialPitchSheet` and `PaywallView`: set trigger, surface, impression ID, offering, package availability, default selection, close, restore intent, and retry intent.
6. `PlusDirectCTA`: set CTA intent and package before `purchaseYearlyDirect()` or the fallback plan picker.
7. `StoreService.purchase(_:)` and `purchaseYearlyDirect()`: set purchase outcome after StoreKit and RevenueCat return. Keep pending distinct from cancelled and failed.
8. `restorePurchases()`: set restored, no_entitlement, or failed outcome, including an error category that does not contain customer content.
9. `ReviewPromptTracker` and `ReviewPromptSheet`: set eligibility, shown, defer, App Store link opened, and feedback link opened.

### Events that require a durable source

The following should be sourced from RevenueCat, ASC, or a small privacy-reviewed event store rather than inferred from last-value attributes:

- App Store product-page view and download.
- First launch and onboarding completion cohorts.
- Trial started.
- Trial converted to paid.
- Renewal, cancellation, refund, billing retry, and expiration.
- Paywall impression to checkout to purchase conversion.
- Crash-free users, crash-free sessions, hangs, launch failures, and top signatures.
- Rating prompt shown, App Store review link opened, rating volume, and rating trend.

## 6. Paywall surface inventory and A/B opportunities

### Current paywall architecture

The app uses a custom SwiftUI paywall, not RevenueCat's native paywall UI. `PaywallView.swift:4-129` defines contextual `PaywallTrigger` values and impression IDs. `PaywallView.swift:172` tracks the custom paywall impression. RevenueCat provides offerings and purchase state, but layout, copy, package cards, CTA, restore, legal links, and trial badges are app-owned.

Current triggers include:

- `pastSeason`
- `lockedSeason(Int)`
- `yearCompare`
- `playerComparison`
- `onboarding`
- `activation`
- `upgrade`
- `pastSeasonsLoad`
- `teamView`
- `winback`
- `playerScouting`
- `recentForm`
- `bestWorst`

Current paywall elements include:

- Context-specific title and subtitle.
- Five benefit rows covering Trends, recent form, head-to-head, team scouting, and historical seasons.
- Yearly, monthly, and lifetime product cards.
- Yearly `Most popular` treatment.
- Savings percentage against 12 times monthly.
- Intro-offer trial badge.
- Per-month yearly anchor.
- Purchase CTA, loading state, error state, pending state, cancellation state, restore, Terms, Privacy, close, and retry.
- A `See all plans` link behind the direct yearly CTA.

### Native paywall and offering nooks to test

Because the surface is custom, the following are the relevant RevenueCat and paywall nooks:

1. Offering assignment by audience or trigger.
2. Product availability and package ordering.
3. Default selected package.
4. Trial badge only after verified eligibility.
5. Yearly savings and per-month anchor.
6. Direct yearly CTA versus full plan picker.
7. Context-specific benefit list and title.
8. `See all plans` prominence.
9. Close and `Maybe later` placement.
10. Restore visibility and post-restore messaging.
11. Loading, retry, product-missing, and pending states.
12. Terms and Privacy link placement.
13. Accessibility labels and long localized product names.
14. Winback trigger for lapsed users, if RevenueCat eligibility and policy permit it.
15. Storefront-specific price and trial copy.

Do not run untracked random client-side variants. Use RevenueCat offerings or a deterministic assignment that is written into the impression ID and a custom attribute. Keep price, trial eligibility, and legal disclosures constant while testing layout or copy unless the test is explicitly a price or offer experiment approved in ASC.

### A/B test backlog

| Test | Variant A | Variant B | Primary metric | Guardrail |
|---|---|---|---|---|
| Onboarding monetization | Current monthly CTA on page 3 | Free `Get Started` primary, contextual paywall after first value | Trial start per install and paid conversion per install | Onboarding completion, first data-ready, day-one return |
| Default plan | Yearly selected | Monthly selected | Paid conversion and net proceeds per paywall impression | Refund, early cancellation, trial conversion |
| Direct CTA | Yearly direct purchase | Always open full plan picker | Purchase start and completion | Close rate, package mix, pending rate |
| Trial sheet content | Three benefits and direct yearly CTA | One concrete player example plus three benefits | Trial start per trigger | Time to first value, dismiss rate |
| Trigger timing | Player profile open count threshold | Successful favorite, compare, or locked historical action | Trial start per activated user | Prompt dismissals per session, reviews, support |
| Paywall context | Generic StatScout+ hero | Trigger-specific hero and locked feature preview | Conversion by trigger | Confusion feedback and close rate |
| Package cards | Yearly, monthly, lifetime | Monthly, yearly, lifetime | Paid conversion and package mix | Lifetime cannibalization, refund rate |
| Plan anchor | Savings and per-month yearly anchor | Total annual price plus explicit monthly equivalent | Purchase completion | Price comprehension and support contacts |
| `See all plans` | Visible link under direct CTA | Full plan picker as primary | Purchase completion | Accidental or confused taps |
| Historical preview | Lock a season before tap | Show a limited historical preview then lock | Trial start after historical intent | Free users reaching first value |
| Review trigger | Third profile open | Third successful value action across sessions | Review link open and rating trend | Prompt dismissals and negative feedback |
| Product page first screenshot | Leaderboard first | Player profile or comparison first | Product-page conversion | Install quality and onboarding completion |

For every test, record:

- Assignment method and stable variant ID.
- App version and build.
- Storefront, device class, and entry trigger.
- Offering and package IDs.
- Trial eligibility state.
- Exposure count, CTA count, checkout count, purchase count, trial start, conversion, refund, and cancellation.
- Test start, stop, decision rule, and guardrails.

### Paywall flow acceptance criteria

- A user always sees a clear free path when products are loading or unavailable.
- A user never sees a trial badge solely because eligibility is unknown.
- The billed period and amount are the most prominent price facts. Trial copy is subordinate and conditional.
- A user can reach all plans without being trapped in a direct-purchase path.
- Restore is available from both the paywall and Settings.
- Pending, cancellation, failure, and retry states are understandable and do not imply Pro access prematurely.
- The paywall works with long localized names, Dynamic Type, VoiceOver, iPad, no network, and no RevenueCat offering.
- Terms, Privacy, subscription renewal, cancellation, and trial rules are reachable before purchase.
- The exact package selected is present in the purchase outcome and RevenueCat context.

## 7. Ratings and review funnel

### Current implementation

**Observed:**

- `StatScoutApp.swift` calls `ReviewPromptTracker.recordAppLaunch()` at app initialization.
- `ReviewPromptTracker.swift` stores state in `UserDefaults`.
- Passive eligibility requires onboarding completion, at least 5 launches, at least 7 days since first open, at least 3 distinct use days, at least 3 positive moments, and no cooldown.
- The normal cooldown is 120 days. A soft defer cooldown is 30 days.
- `PlayerProfileView.swift:207-214` increments `profileOpenCount` and calls `recordPositiveMoment()` at the third profile open for free users, after the profile appears.
- `RootTabView.swift` schedules the enjoyment prompt about 3.5 seconds after a positive moment, only once per session.
- The sheet asks whether the user is enjoying the app. A positive response opens `AppStoreReviewLinks.writeReviewURL`; a negative response opens feedback email.
- `Maybe later` records a soft defer and the app may call Apple's native `requestReview()` after dismissal.
- Settings provides a manual `Rate on the App Store` path.
- `AppStoreReviewLinks.swift` uses App Store ID `6792930447` and a country-aware write-review URL.

### Findings

#### P0, negative feedback address is wrong

`ReviewPromptSheet.swift:248` constructs the feedback mailto path with `jackwallner+bb@gmail.com`. `SettingsView.swift:242` displays the same Baseball address. The Football support and legal pages use `jackwallner+ff@gmail.com`.

This is a concrete user-recovery defect. Fix and validate every contact path before the next release.

#### P1, positive moment is only a profile-open count

The third profile open is not proof that the profile loaded, was understood, or produced value. A stale-cache or empty-data profile can trigger the prompt.

Use a positive moment such as:

- Profile fully rendered with at least one metric group.
- Player comparison completed.
- Favorite added.
- Recent-form chart or team scouting result viewed successfully.
- Glossary or metric definition opened after data is visible.

Keep the conservative launch/day/cooldown gates. Change only the quality of the moment first, then test timing.

#### P1, ratings evidence is missing

No current Football rating count, average, distribution, review volume, response latency, or post-release rating delta was supplied in the evidence used for this audit.

Pull ASC storefront rating and review snapshots before deciding whether the review funnel is underperforming. Compare the seven and thirty days before and after each release, segmented by version where ASC allows it. Do not use local prompt counts as rating counts.

#### P2, manual review and feedback should be measured separately

Track prompt shown, rate-link opened, feedback-link opened, mail client availability, and dismiss/defer. App Store link opens are not ratings. Apple's native `requestReview()` may no-op, so treat it as a request attempt, not a confirmed prompt or rating.

## 8. Data, reliability, and regression signals

### Current data path

**Observed:**

- `.github/workflows/nightly-statcast.yml` runs at `0 9 * * *` and supports manual full game-log reingest, backfill, and rollup inputs.
- The workflow comments explain that nflverse data is weekly even though the job is scheduled nightly.
- The workflow ingests snapshots, optional historical backfill, per-game logs, rolling windows, and recent form.
- On failure it uploads logs and creates a GitHub issue, but the issue creation step has `continue-on-error: true`.
- `backend/README.md` documents seasons from 2000 onward, regular and postseason data, qualification thresholds, and missing target data for 2003-2008.
- `backend/ingest.py` documents source availability by metric and explicit upsert behavior.
- `supabase/schema.sql` exposes read policies for app tables and uses service-role writes.
- `StatcastAPI.swift` fetches current players, historical players, player game logs, team logs, recent form, and data coverage through Supabase REST.
- `DashboardViewModel.load()` can fall back to current cache or historical local data and displays messages such as `Showing saved data` and `Data format changed - app may need an update`.
- Historical data is bundled or cached locally; current data has a documented 48-hour cache behavior.
- Coverage is fetched off the critical data path and failures can be swallowed, so a blank About coverage field is not necessarily a data failure.

### P1, data freshness and completeness watchdog

The nightly schedule does not guarantee new source data. A source can be weekly, delayed, empty, partially ingested, or schema-changed. Add read-only checks after each workflow run:

- Workflow completed successfully and all required jobs passed.
- Latest `updated_at` is within a configurable threshold after a known source release.
- Current season and season type are expected.
- `end_week` or equivalent coverage moved when a new game week was expected.
- Player row count is within a baseline band by player type.
- No unexpected zero rows for a previously populated position or team.
- Duplicate primary keys are zero.
- Percentile values are within 0 to 100.
- Required metric categories exist for qualified players.
- Regular and postseason rows do not get mixed in one recent window.
- Recent-form windows 3, 5, and 8 exist where the product promises them.
- 2003-2008 target fallback behavior remains explicit.
- Supabase REST response status is 2xx and JSON decodes against the current models.
- The app's displayed freshness date is not newer than the data source.

If an assertion fails, fail the ingest or mark the data as stale, rather than silently publishing a partial feed.

### P1, API resilience and diagnostics

`StatcastAPI.swift:111-247` uses `URLSession.shared.data(for:)` for multiple requests. The inspected path has no explicit retry policy, exponential backoff, request timeout contract, or latency event.

Recommended behavior:

- One bounded retry for transient network and 5xx errors.
- No retry for auth, schema, 4xx, or decode errors.
- Per-request timeout and cancellation.
- Error categories: offline, timeout, server, auth/config, decode, empty, stale-cache.
- Request latency and response row count, without player names or raw payloads.
- A visible retry action that preserves cached data.
- A release diagnostic that reports whether first data was live, cached, historical, or empty.

Do not turn a data-source error into a generic paywall. A user who cannot see current data should not be asked to pay to solve a connectivity problem.

### Crash and live-user watchdog status

**Observed:** no MetricKit, Crashlytics, Sentry, or other crash-upload integration was found in the Football app source reviewed. No current Football crash-rate snapshot was supplied. The app's local fallback and error strings are not crash telemetry.

**Conclusion:** a local script cannot detect a live user's crash from this repository alone. It needs an external source such as ASC crash and diagnostics data, MetricKit uploads, Xcode Cloud, or a crash service. The implementation agent should scaffold the watcher around whichever source is authorized, but this audit-only rerun did not create or deploy a notifier.

### Recommended configurable watchdog contract

The future MacBook Pro watchdog should run read-only and be configurable through environment variables or a local ignored configuration file. It should produce JSON and Markdown snapshots before sending any notification. The notification layer can remain a disabled scaffold.

Monitor these signals:

| Signal | Source | Alert condition to start with | Why |
|---|---|---|---|
| Crash-free users | ASC or crash provider | Drop more than 1 percentage point versus the prior 7-day baseline, or exceed a configurable absolute floor | Detect broad regressions after a release. |
| Crash-free sessions | ASC or crash provider | More than 2x baseline or configurable count of affected sessions | Catch repeat crashes for a small cohort. |
| New crash signature | ASC or crash provider | 3 or more affected users, or 3 occurrences in 15 minutes | Catch a new release-specific failure quickly. |
| Hangs or launch failures | ASC diagnostics | Any new signature above a small threshold | Users may report a hang as an uninstall. |
| API availability | Supabase health and synthetic read | 3 consecutive failures or 5-minute error rate above threshold | Separate app failures from backend outages. |
| API latency | Synthetic read and app telemetry | p95 above 2x baseline or a configurable absolute limit | Protect first data and paywall entry. |
| Data freshness | Supabase coverage and workflow artifacts | No expected movement for 36 hours or configurable source window | Detect stale feed before users report it. |
| Row and schema quality | Post-ingest SQL or JSON checks | Any required table empty, duplicate key, invalid percentile, or decode-contract break | Prevent partial data releases. |
| Offering load | RevenueCat or app event snapshot | More than 5% offering load failure or no current package | Prevent blank or generic purchase UI. |
| Purchase state | RevenueCat and StoreKit outcome export | Pending, failed, or cancelled rate above baseline by a configurable factor | Detect commerce regressions. |
| Trial cohort | RevenueCat and ASC | Trial starts or conversion materially diverge from the prior cohort | Detect eligibility and paywall issues. |
| Rating trend | ASC | Average or rating distribution drops by a configurable amount, or a release receives repeated one-star reviews | Detect UX regressions not visible in crash data. |
| Release state | ASC and TestFlight | Build stuck processing, wrong version, missing screenshots, or unexpected release status | Stop accidental release drift. |
| Website and legal links | HTTP checks | Non-2xx, redirect loop, wrong canonical, stale App Store ID, or Baseball text | Protect acquisition and trust. |
| Agent hygiene | Local file scanner | Active docs or scripts contain the wrong bundle ID, sport, URL, product ID, or stale version | Prevent agents from implementing the wrong app. |

### Release regression window

For every new Football build:

1. Capture the prior 7-day and 30-day baseline.
2. Record the new version and build in the watchdog snapshot.
3. Watch the first 15 minutes, 1 hour, 6 hours, 24 hours, and 72 hours after release.
4. Segment by iOS version, device class, storefront, and release channel.
5. Compare crash signatures, first data readiness, API errors, offering load, trial starts, purchases, refunds, and rating changes.
6. Escalate a broad regression before attempting a marketing or paywall experiment.

Suggested initial thresholds are starting points, not claims about current performance. Keep them in configuration and tune them after two clean releases.

### Release-script caution

`scripts/testflight.sh:40-44` increments `CURRENT_PROJECT_VERSION` in `project.yml` before archiving. It then runs xcodegen, archives, and delegates to the upload script. This is appropriate for a release helper, but a watchdog or audit scanner must never invoke it. A future release wrapper should verify the working tree, record the old and new build, and confirm the archive's bundle ID, version, build, Supabase project, and RevenueCat environment before upload.

## 9. Website, privacy, terms, and data consistency

### Consistent or mostly consistent items

- App ID `6792930447` is repeated in the site, review utility, metadata, and app links.
- Football URLs are used in `docs/`, app support links, privacy links, and metadata.
- `docs/privacy-policy.html`, `docs/terms.html`, and `PRIVACY.md` use Football language and the `jackwallner+ff@gmail.com` contact.
- Legal pages explain no account, public NFL statistics, local preferences, Apple purchase processing, subscription cancellation, and non-affiliation.
- `fastlane/metadata/review_information/notes.txt` describes no login, no account, no imagery, no live scores, and public nflverse or nflreadpy data.
- The app's source intentionally does not render player headshots. `Player.swift` comments and review notes agree on that point. Do not add a photo-related inconsistency finding.

### P0, contact identity mismatch

The in-app Settings support row and negative-review feedback email use `+bb`, while Football web and legal materials use `+ff`. This is the most direct trust and recovery defect in the app.

### P1, terms authority decision

`StoreService.swift:182-183` links Terms to Apple's standard EULA and Privacy to the Football GitHub Pages policy. `docs/terms.html` is a custom Football Terms of Use page, and `PRIVACY.md` links to it.

This may be intentional. It is still a discoverability and authority decision that should be explicit:

- If Apple's standard EULA is the intended Terms link in the app and ASC, say so consistently and keep the custom page as a clearly labeled supplementary Terms of Use page.
- If `docs/terms.html` is the intended customer terms page, use it in the app, website, metadata, and release manifest.
- Ensure both paths have identical product names, prices, trial eligibility wording, renewal, cancellation, refund, and support contact.

Do not change the legal authority casually. Have the owner approve the selected source.

### P1, refresh cadence language

The app, metadata, and website frequently say updated nightly or every morning. The workflow comments say nflverse publishes on a weekly cadence, with a nightly job checking and ingesting available data.

Use precise copy such as `The app checks for new public NFL data nightly. Coverage depends on source publication and is shown in the app.` Add a visible coverage date or week where possible. Avoid promising that statistics reflect the most recent game if the public source has not published it.

### P1, data-source terminology

Remove `pitch-tracking data` from `docs/support.html`. Confirm that `Statcast`, `MLB`, baseball, and pitch terms do not appear in active Football pages, metadata, review notes, or screenshots. Historical archive material can retain those terms only if clearly marked historical and excluded from agent search paths.

### Out of scope by explicit instruction

This audit does not flag any inconsistency between RevenueCat processing and App Store or website statements about data collection. That topic was explicitly excluded. The recommendations for RevenueCat attributes and measurement are limited to conversion diagnostics and operational observability, not a privacy-policy rewrite.

## 10. Agent documentation and workspace hygiene

### Current structure

**Observed:**

- `AGENTS.md` is a symlink to `CLAUDE.md`, so the shared global agent guidance is available through the canonical Football guide.
- `CLAUDE.md` is Football-specific in most of its technical guidance, but its release-status line and support email are stale or wrong.
- `.claude/scheduled_tasks.lock` and `.claude/settings.local.json` exist.
- `.commandcode/taste/taste.md` exists.
- No `.cursor`, `.codex`, or `.agents` directory was found in the Football repository.
- `archive/` contains historical audit and Baseball issue material.
- `handoff/` contains both Football handoffs and a clearly named `BASEBALL_CHANGES_SINCE_FORK.html` historical file.

The symlink is good. The absence of tool-specific directories is not itself a problem, but agents need one clear, current entry point that explains the shared conventions and the quarantine areas.

### P0, active-looking stale documents

These should be updated, moved under a dated archive, or marked `DO NOT USE` in a future documentation pass. They are listed here only; they were not edited in this audit.

| Path | Observed stale content | Risk |
|---|---|---|
| `APP_STORE_SUBMISSION.md` | Baseball app name, MLB URLs, Baseball products, baseball review flow, headshots, and Baseball metadata | Highest risk because its filename looks like the release runbook. |
| `docs/design.md` | Baseball RevenueCat paywall, `com.jackwallner.baseball` products, and Statcast features | Agent can copy the wrong entitlement and product IDs. |
| `docs/astro-asc-metadata-proposal.md` | Baseball Savvy, MLB, Statcast, and baseball keyword proposal | Agent can overwrite Football ASO. |
| `scripts/ui_test.sh` | Baseball app path, `com.jackwallner.baseball`, Baseball Savvy app assertions | Running it tests the wrong app and can obscure Football coverage. |
| `scripts/aso-apply-locale-optimizations.py` | Baseball brand, MLB, Statcast, and baseball keyword sets | A mechanical metadata script can damage all Football locales. |
| `scripts/generate_icon_variations.py` | Baseball graphics and `/Users/jackwallner/baseball/icon_variations` output | Can write assets outside this app and target the wrong brand. |
| `scripts/format_transcript.py` | Baseball absolute paths | Can read or write the wrong repository. |
| `build.log`, `build_final.log`, `xcodebuild.log` | Baseball paths and module names, including a Baseball compile error | Agents can mistake historical Baseball output for a Football failure. |
| `docs/localization-aso.md` | Draft 1.0 and never-released status | Contradicts current ASC context. |
| `docs/astro-aso-setup.md` | Draft 1.0 pending approval and stale price/status assumptions | Contradicts current ASC context and website price. |
| `README.md` | Football identity is correct, but next production steps list features that already exist | Agents may repeat completed work or misread product scope. |
| Comments in `StoreService.swift` | Some screenshot and approval comments refer to Baseball or Baseball-specific history | Source comments can influence future agents even when code is Football. |

### Recommended documentation layout

Use a small active set:

1. `CLAUDE.md`, with `AGENTS.md` remaining a symlink, as the short operating guide.
2. `docs/FOOTBALL_RELEASE_MANIFEST.md`, the current ASC, RevenueCat, URLs, product, data, and release source of truth.
3. `docs/FOOTBALL_ASO.md`, current metadata rationale, locale status, screenshot mapping, and experiment history.
4. `docs/FOOTBALL_MONITORING.md`, watchdog inputs, thresholds, release windows, and escalation.
5. `docs/FOOTBALL_DATA_CONTRACT.md`, source cadence, schema, season, phase, metric, coverage, and freshness contract.
6. `archive/football/YYYY-MM/`, dated historical audits and superseded handoffs.
7. `archive/baseball/`, Baseball fork material that must never be treated as active Football instructions.

The future agent should be instructed to read the release manifest and data contract before touching ASC, RevenueCat, metadata, or release scripts. The future agent should be instructed to ignore `archive/`, stored logs, and any script whose bundle ID is not `com.jackwallner.football`.

### Agent-safe static scanner rules

A non-AI scanner can report, without editing anything:

- Active files containing `com.jackwallner.baseball`, `/Users/jackwallner/baseball`, `Baseball Savvy`, `MLB`, `Statcast`, `pitch-tracking`, or baseball product IDs.
- Active files missing `com.jackwallner.football` where an app identity is expected.
- Metadata URLs not containing `/football/` or App Store ID `6792930447`.
- Product IDs that do not match the Football product set.
- `softwareVersion` or marketing version different from the release manifest.
- Support email `+bb` in Football source.
- Lifetime or subscription prices that differ from the ASC snapshot.
- Recent-form claims that say games where the selected data surface is weeks, or vice versa.
- Terms, privacy, support, and marketing links that return non-2xx or point to the wrong app.
- Screenshot dimensions, duplicate checksums, wrong sport text, or stale App Store ID.
- `CLAUDE.md`, `AGENTS.md`, and release-manifest symlink or file drift.
- Uncommitted build-number changes after a release script runs.
- Stale workflow artifacts and last successful ingest dates.

It should output findings with path, line, rule ID, severity, current value, expected value, and a suggested validation. It should never rewrite metadata, commit, push, upload, or run `scripts/testflight.sh`.

## 11. Recommended implementation roadmap

### Before the next release, P0

1. Fix the Football support and feedback email in `SettingsView` and `ReviewPromptSheet`.
2. Confirm the current ASC version, build, IAP prices, entitlement, offerings, screenshot sets, and rating state.
3. Make unknown trial eligibility non-promotional.
4. Quarantine or clearly mark the Baseball submission document, design doc, ASO proposal, UI script, ASO script, icon script, transcript helper, and stale build logs.
5. Create the Football release manifest and make it the first agent-read document.
6. Remove or update the support page's pitch-tracking wording.

### Next release cycle, P1

1. Add funnel events and low-cardinality RevenueCat attributes at the insertion points above.
2. Decide monthly versus yearly defaults by evidence, not by surface-specific accident.
3. Resolve games versus weeks semantics for every recent-form surface.
4. Add first-value and data-state instrumentation.
5. Add post-ingest quality assertions, freshness checks, and an owner notification path.
6. Add request latency, error category, retry, and cache-age diagnostics.
7. Align website structured data, visible price, version, canonical URL, terms authority, and support copy with the manifest.
8. Validate current screenshot checksums and device sets in ASC after every metadata upload.
9. Run the full paywall, purchase, restore, pending, offline, and accessibility acceptance matrix on a release candidate.

### Optimization cycle, P2

1. Test onboarding free-first versus purchase-first.
2. Test monthly, yearly, and plan-picker defaults.
3. Test contextual paywall copy and trigger timing.
4. Test screenshot order and one ASO metadata variable at a time.
5. Add storefront and device-class segmentation.
6. Tune review prompt timing after rating and feedback data are available.
7. Consolidate active docs and add the static scanner to a local preflight command.

## 12. Validation matrix for the implementation agent

### ASC validation

- App ID is `6792930447`.
- Bundle ID is `com.jackwallner.football`.
- Current version and build match `project.yml` and the release manifest.
- Version status is recorded with a timestamp.
- Product IDs, display names, subscription periods, prices, trial durations, and availability match RevenueCat and the app's customer-facing copy.
- All intended locales have the expected metadata.
- Screenshot sets have the expected device families, dimensions, current UI, and unique checksums.
- Support, marketing, privacy, and terms URLs resolve and point to Football.
- Rating and review snapshots are exported before and after each release.

### RevenueCat validation

- Project `9c303632` and app `app039a312379` are the intended Football project and app.
- Current offering is present and has monthly, yearly, and lifetime packages.
- Entitlement `Football Pro` is active for a test purchase.
- Fallback `pro` behavior is tested and documented, not relied on accidentally.
- Intro-offer eligibility is tested for eligible, ineligible, unknown, and offline states.
- Product identifiers match ASC exactly.
- Custom paywall impressions include trigger and variant context.
- Purchase outcomes distinguish purchased, pending, cancelled, failed, restored, and missing product.
- Trial start and conversion are read from RevenueCat or ASC transaction data, not inferred from a trial badge tap.

### Runtime validation

- New install, skip, Get Started, and purchase onboarding paths.
- First current-season data load with live network.
- Cached current data within 48 hours.
- Stale cache beyond 48 hours.
- Historical bundle with no network.
- Supabase 401, 403, 404, 429, 5xx, timeout, invalid JSON, empty array, and partial-row response.
- Player profile with complete, partial, and empty metrics.
- Favorites, compare, recent form, team scouting, historical season, year-over-year, best and worst, and locked controls.
- Every `PaywallTrigger` title, subtitle, feature list, impression ID, and package route.
- Yearly direct purchase and `See all plans` fallback.
- Monthly and lifetime selection.
- Trial eligible, trial ineligible, trial unknown, existing Pro, pending, cancellation, error, refund, restore, and renewal.
- Terms, Privacy, Support, App Store review, and feedback mailto links.
- VoiceOver, Dynamic Type, iPad, landscape, reduced motion, contrast, and offline states.
- No production RevenueCat key on a simulator. Use StoreKit test products or a controlled device/TestFlight path.

### Static consistency validation

Run a read-only scanner over active files and report:

- Football identity and App Store ID.
- Product and entitlement sets.
- Version and build.
- Support email.
- Marketing, support, privacy, and terms URLs.
- Price and trial claims.
- Data-source and refresh-cadence terminology.
- Recent-form games versus weeks terminology.
- Baseball contamination.
- Screenshot duplicates and stale dimensions.
- Stale active docs and logs.

The scanner should emit a report for an agent to fix. It should not edit any file automatically until each rule has an explicit safe rewrite policy.

## 13. Evidence gaps to resolve, without guessing

The following questions remain open because the audit did not have trustworthy current numeric evidence:

1. What is the exact live build currently serving alongside ASC version 1.1.0?
2. What are the current monthly, yearly, and lifetime prices by storefront?
3. Is the live RevenueCat offering named `default`, another offering, or an experiment assignment?
4. How many installs, trials, active subscriptions, renewals, refunds, and conversions does Football have by version and storefront?
5. What are crash-free users, crash-free sessions, top signatures, hangs, and launch failures after the latest release?
6. What are current App Store rating count, average, distribution, review volume, and review-response latency?
7. Which website host is the actual production marketing URL, and does the other host redirect or mirror it?
8. Is lifetime `$19.99` or `$29.99` the current ASC authority?
9. Which recent-form surfaces are intentionally week-based and which are game-based?
10. Does the current cache promise exactly match what a cold offline launch can render?
11. Does the live app show duplicate screenshots or stale 1.0 screenshot content despite the local 1.1.0 cleanup?
12. Which stale docs and scripts are still referenced by scheduled tasks or a human release checklist?

The implementation agent should answer these with current ASC, RevenueCat, workflow, website, and release-candidate evidence. It should not infer them from the local DEBUG StoreKit fixtures, old logs, or stale Baseball documents.

## 14. Completion criteria for this audit handoff

This audit is considered implemented only when the follow-on agent can demonstrate:

- A single current Football release manifest.
- No wrong Football support address in app-facing support and feedback paths.
- No trial badge on unknown RevenueCat eligibility.
- ASC and RevenueCat product, entitlement, version, and price reconciliation.
- A measured funnel from download or first launch through first value, paywall, trial, purchase, renewal, cancellation, and review prompt.
- A post-ingest data-quality and freshness check.
- A crash and regression watchdog connected to an authorized external crash or ASC data source, with notifications still configurable and disabled by default if desired.
- A static consistency report for wrong sport, wrong app ID, wrong URLs, wrong prices, stale docs, stale screenshots, and stale data claims.
- Active agent instructions that direct Cursor, Claude, and Codex to Football source-of-truth files and exclude Baseball archive material.
- Runtime validation of onboarding, data fallback, trial eligibility, purchase, restore, paywall entry points, accessibility, and legal/support links.

Until those checks pass, current conversion, trial, revenue, rating, and crash numbers should be treated as unknown rather than healthy or unhealthy.

