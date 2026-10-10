---
paths:
  - "android/**"
  - "scripts/play_screenshot_compositor.py"
  - "scripts/sync-android-historical.py"
  - "scripts/make_android_icon.py"
---

# StatScout Android rules

## Identity and products

- Native Android code is in `android/`, a screen-for-screen port of the iOS app
  (same copy, tokens, flows, product IDs). When iOS changes, port the change.
- Package `com.jackwallner.football`, Play name "Football Next: StatScout",
  launcher label "StatScout", paid tier "StatScout+". Play Console app ID
  `4973822987801656046` on the E3 Apps developer account.
- Products (same IDs as iOS): subscriptions `com.jackwallner.football.pro.monthly`
  and `com.jackwallner.football.pro.yearly` (base plans `monthly` / `yearly`,
  7-day `trial-7d` new-customer offer), lifetime in-app product
  `com.jackwallner.football.pro`. Android US ladder $1.49 / $7.99 / $15.99,
  about 20% under iOS; local prices are Play's conversion. Never put prices in
  listing copy or app literals.
- RevenueCat project `proja0cce872`: Test Store app `app354fdcd06c` (Debug key),
  Play app `app384d77e843` (`goog_` key, release only), entitlement lookup key
  `Football Pro` (`entl4d2856d9eb`, app falls back to `pro`), current offering
  `android` (`ofrng83bd52e0ef`) with `$rc_monthly`, `$rc_annual`, `$rc_lifetime`.
- Season model matches iOS: the calendar season is free and default; older
  seasons are StatScout+.
- Play reviewers unlock StatScout+ with a code (long-press Settings > App
  Version). Only its SHA-256 is in the build (`PLAY_REVIEW_CODE_SHA256` in
  `local.properties`); the code is `FOOTBALL_PLAY_REVIEW_CODE` in
  `~/.football_credentials` and in Play Console App access. Never commit it.

## Deliberate differences from iOS

- Review prompt: Google forbids an enjoyment question before the Play card, so
  positive moments call `launchReviewFlow` directly (same tracker cooldowns),
  and Settings has separate "Rate on Google Play" and "Send Feedback" rows.
- Android has a Games tab in the bottom bar.
- Live and history loads merge at ingest time on the main thread
  (`DashboardViewModel.ingestPlayers(mergePlayers(...))`), so whichever lands
  second no longer drops the other's seasons.

## Build

- JDK 17, checked-in Gradle wrapper, AGP 9.4.1, minSdk 26, target/compile 36.
- `android/local.properties` (ignored) holds `REVENUECAT_TEST_KEY`,
  `REVENUECAT_PLAY_KEY`, `SUPABASE_URL`, `SUPABASE_ANON_KEY`,
  `PLAY_REVIEW_CODE_SHA256` and `PLAY_UPLOAD_*` (upload keystore
  `~/.android/keystores/football-upload.p12`, alias `upload`, passwords in
  `~/.football_credentials`). Release builds fail without them.
- `qa` build type: R8 release config, debug signing, no RevenueCat key. Use it for
  emulator speed checks; the Debug build is far slower to parse the 2.4 MB
  player feed on the emulator (about 75 s versus under 1 s).
- `scripts/sync-android-historical.py` regenerates
  `app/src/main/assets/players-historical.json.gz` from the iOS bundle.
- Increment `versionCode` on every Play upload.

## Emulator (remote MacBook Pro only)

Follow the `android-dev` skill's Remote MacBook Pro section. Host
`jackwallner@192.168.4.25`, SDK `/Users/jackwallner/Library/Android/sdk`.
This app has used a temporary AVD `football_agent_test` on port 5560
(`emulator-5560`); create it per run and delete only it afterwards. Never touch
`small_phone`. Stop it with
`adb -s emulator-5560 emu kill` run on the Pro, then confirm it is gone from
`adb devices -l`.

Debug launch extras (`DebugLaunchOptions`, debug builds only): `resetAll`,
`onboarded`, `forcePro`, `previewStore`, `previewTrialUsed`, `tab`,
`onboardingPage`, `uiTest`, `statsBoard`, and `screenshotData` (the fictional
iOS fixture feed, `ScreenshotFixtureApi`).

## Store assets

Raw captures are 1080x2400 from the debug build with `--ez screenshotData true
--ez forcePro true --ei tab <n>` and System UI demo mode (clock 0941, mobile
hidden). `python3 scripts/play_screenshot_compositor.py` writes the seven
1080x1920 frames (App Store headlines) and the 1024x500 feature graphic into
`android/play-assets/`. Listing text is in
`android/play-assets/metadata/android/en-US/`. Privacy and terms pages are
`docs/android-privacy.html` and `docs/android-terms.html`.

## Play state (verified 2026-10-10)

- App record created. Store listing (text, icon, feature graphic, 7 phone
  screenshots), store settings (Sports, contact email and website), and every
  App content declaration are saved, unsent: privacy policy, ads (none),
  sign-in details (reviewer code instructions), content rating (IARC), target
  audience 18+, data safety (purchase history, app interactions, device IDs;
  collected, not shared, encrypted in transit, deletion URL), advertising ID
  (no), government, financial, health (none).
- No release, no track, nothing sent for review. Subscriptions and the lifetime
  product cannot be created until an AAB with the BILLING permission is
  uploaded; RevenueCat's Play app then needs a Play service account (ask Jack
  before creating or granting it).
