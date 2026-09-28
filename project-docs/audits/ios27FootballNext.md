# iOS 27 compatibility audit: Football Next

- Audit date: 2026-08-05
- Runtime: iOS 27.0 (24A5390f)
- Xcode: 26.6 (17F113)
- Scheme: `StatScout`
- Unit target: `StatScoutTests`
- Overall: Pass with UI-test concurrency warnings

## Checks

- Debug build: Pass with the existing local football credentials loaded.
- Unit tests: Pass.
- Normal rebuild after tests: Pass.
- Install and launch smoke test: Pass.
- Runtime UI snapshot: Pass. Onboarding and stats/team views rendered.

## Findings

- `StatScoutUITests/StatScoutComprehensiveUITests.swift`, `SeasonPickerUITests.swift`, and `YearAuditUITests.swift` contain numerous main-actor isolation warnings.
- The test action's app artifact can fail to launch with a missing `lib_TestingInterop.dylib`; a normal post-test rebuild produces a launchable app. This is a test artifact issue, not a production app failure.
- No iOS 27-specific compiler error or runtime blocker was observed in the normal product.

## Recommended follow-up

- Clean up UI-test actor isolation and keep the post-test normal rebuild in compatibility scripts.
