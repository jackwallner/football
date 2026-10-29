---
paths:
  - "StatScout/**/*.swift"
  - "StatScout/Resources/Localizable.xcstrings"
  - "project.yml"
---

# Localization

Languages: en (source), es, de, nl, it. Spanish targets Latin American NFL fans,
especially Mexico. Keep NFL brand names and standard stat codes such as EPA and
CPOE as used in local coverage.

- The app has one String Catalog at `StatScout/Resources/Localizable.xcstrings`.
  Keep `sourceLanguage` as `en`, every entry `extractionState: manual`, and each
  localization `state: translated`.
- SwiftUI string literals localize automatically. Strings stored in models,
  view parameters, view models and errors use `String(localized:)` with complete
  sentences. Do not assemble translated fragments or lowercase translated copy.
  Use `.localizedUppercase` for compact headings.
- NFL metric labels in Supabase are English keys. Keep the raw key for logic and
  display it with `MetricName.localized(_:)`. Add cases and catalog strings when
  the feed adds labels. Use `MetricCategory.title` for category names.
- Use String Catalog plural variations for counts. Use Foundation formatters
  for dates, times, numbers, units and ordinals. StoreKit supplies purchase
  prices. NFL contract values are source data, not purchase prices.
- The app has no WidgetKit, Watch, notification, App Intent or permission-copy
  targets. If one is added, make it use this catalog and add its bundle
  localization declaration.
- `StatScout/Info.plist` declares the four shipped app languages.
  `project.yml` lists the source and shipped regions. Run `xcodegen generate`
  after Swift-file or project.yml changes.

## Check and proof

1. `python3 Tools/Localization/check.py` checks source keys, language coverage
   and format specifiers.
2. Run `gh-sim test` for unit and UI validation. Simulator runs must not use the
   production RevenueCat key.
3. Use `gh-sim launch` with `-AppleLanguages (<language>) -AppleLocale <locale>`
   for onboarding, the trial paywall and the seeded Stats screen. Review every
   screenshot for clipping, English leftovers and untranslated feed labels.

Debug launch arguments include `-ScreenshotData`, `-ResetUITestState`,
`-hasCompletedOnboarding YES`, `-StartTab stats|games|trends|teams|compare`,
and `-PaywallSnapshot trial|monthly|yearly|lifetime|onboarding`. The app has no
widgets to screenshot.
