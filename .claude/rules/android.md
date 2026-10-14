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
- Package `com.jackwallner.football`, Play name "Pro Football: EPA StatScout"
  (renamed with iOS 1.2.6; en-GB and en-AU listings use "American Football:
  StatScout", es-ES "Fútbol Americano: StatScout", matching the App Store),
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

- Review prompt: same gates as iOS 1.2.6 (second launch on, onboarding done,
  30-day cooldown, the Settings "Rate StatScout" row ends the asks, "Send
  Feedback" never does). A positive moment calls `launchReviewFlow` about 3 s
  later only if no sheet, paywall or dialog is up (`activity.hasWindowFocus()`).
  "Send Feedback" opens a mail draft directly, falling back to the support page.
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
  `app/src/main/assets/players-historical.bin` (gzip JSON) from the iOS bundle.
  Never name an asset `.gz`: the packager inflates it and drops the extension,
  so `assets.open` fails and past seasons silently vanish.
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
`android/play-assets/`. Listing text and changelogs are in
`android/play-assets/metadata/android/<locale>/` (en-US default, en-GB, en-AU,
es-ES; the app UI is English only). Privacy and terms pages are
`docs/android-privacy.html` and `docs/android-terms.html`.

## Play state (verified 2026-10-14)

- App record, store listing (en-US default plus en-GB, en-AU, es-ES text;
  graphics are en-US only), store settings and every App content declaration
  done (privacy policy, ads none, reviewer-code sign-in details, IARC, target
  audience 18+, data safety, advertising ID no).
- 1.2.6 (versionCode 2): internal track rolled out (completed) via API;
  production full rollout to 178 countries sent for review 2026-10-14 with
  the listing and App content changes (14 changes). Managed publishing is
  off, so approval publishes. The next upload needs versionCode 3.
- Production releases are staged in Console (Production > Releases > edit
  release > Next > Save, then Publishing overview > Send for review). The
  service account lacks production release permission. Listing text uploads
  with `fastlane supply --skip_upload_aab true` from
  `android/play-assets/metadata/android`.
- Play products ACTIVE: monthly $1.49 and yearly $7.99 (each with
  `trial-7d`), lifetime `com.jackwallner.football.pro` $15.99. Ethiopia is not
  billable; Argentina, Azerbaijan and Uzbekistan are priced in USD.
- Fleet service account `revenuecat-service-account@gen-lang-client-0612253531`
  has Sober's 9 permissions on this app. Its JSON
  (`~/.config/google-play/mahj-service-account.json`) drives
  `./scripts/play-upload.sh` and `scripts/play-products.py`, and is uploaded
  to RevenueCat app `app384d77e843`. RevenueCat reads both catalogs; its
  purchase-validation check says "package name not found" until a release is
  rolled out to a track. Google developer notifications are not connected.
- RevenueCat Play products are attached to the packages and entitlement
  (`...monthly:monthly`, `...yearly:yearly`, `...pro` one-time).
