import XCTest

/// Persona audit: a skeptical free user weighing what is paid, and looking for
/// the restore and exit paths. Runs on deterministic fixture data with mock
/// priced packages (`-MockProducts`), never a production RevenueCat key. These
/// record behaviour as attachments for the audit report; hard assertions are
/// limited to the app staying up and the surfaces existing.
@MainActor
final class FreeUserSubscriptionUITests: XCTestCase {
    private let fixturePlayer = "Caleb Mercer"

    override func setUp() {
        super.setUp()
        continueAfterFailure = true
    }

    private func launch(tab: String? = nil, extra: [String] = [], reset: Bool = true) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchEnvironment = ["SCREENSHOT_MODE": "1"]
        var args = [
            "-ScreenshotData", "-MockProducts",
            "-hasCompletedOnboarding", "YES",
            "-stats.board", "advanced",
            "-stats.qualifier", "All Players",
        ]
        if reset { args.append("-ResetUITestState") }
        if let tab { args += ["-StartTab", tab] }
        app.launchArguments = args + extra
        app.launch()
        return app
    }

    private func capture(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    private func dump(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(string: app.debugDescription)
        attachment.name = name + "_tree"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    private func openPlayer(_ app: XCUIApplication) -> Bool {
        let row = app.buttons.matching(
            NSPredicate(format: "label MATCHES %@", "^[0-9]+, \(fixturePlayer),.*")
        ).firstMatch
        guard row.waitForExistence(timeout: 120) else { return false }
        row.tap()
        return true
    }

    // MARK: - Flow A: second profile open -> pitch -> plans -> restore -> exit

    func testPitchToPlansToRestoreToExit() throws {
        // Counter pinned to 1 so the very next profile open is the second.
        let app = launch(tab: "stats", extra: ["-profileOpenCount", "1"])
        capture(app, "a0_stats_free_home")
        XCTAssertTrue(openPlayer(app), "player row should exist")
        sleep(3)
        capture(app, "a1_profile_open_pitch")
        dump(app, "a1")

        let seeAll = app.buttons["See all plans"]
        if seeAll.waitForExistence(timeout: 10) {
            seeAll.tap()
            sleep(2)
            capture(app, "a2_plan_picker")
            dump(app, "a2")

            app.swipeUp()
            sleep(1)
            capture(app, "a3_plan_picker_scrolled")

            // Cycle plans: lifetime / monthly disclosure copy.
            for label in ["Monthly", "Lifetime"] {
                let card = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", label)).firstMatch
                if card.exists, card.isHittable {
                    card.tap()
                    sleep(1)
                    capture(app, "a4_plan_\(label.lowercased())")
                }
            }

            let restore = app.buttons["Restore Purchases"]
            if restore.exists {
                restore.tap()
                sleep(2)
                capture(app, "a5_after_restore_tap")
            } else {
                capture(app, "a5_restore_not_found")
            }

            let close = app.buttons["Close"]
            if close.exists {
                close.tap()
                sleep(2)
                capture(app, "a6_after_close_plans")
            }
        } else {
            capture(app, "a2_no_see_all_plans")
        }

        let later = app.buttons["Maybe later"]
        if later.exists {
            later.tap()
            sleep(2)
            capture(app, "a7_after_maybe_later")
        }

        // Back out, open a second profile in the same session: is the pitch capped?
        let back = app.navigationBars.buttons.element(boundBy: 0)
        if back.exists { back.tap() }
        sleep(2)
        capture(app, "a8_back_on_board")
        if openPlayer(app) {
            sleep(3)
            capture(app, "a9_second_open_same_session")
        }
    }

    // MARK: - Flow B: Settings, free tier, restore

    func testSettingsFreeTierAndRestore() throws {
        let app = launch(tab: "stats")
        let gear = app.buttons["Settings"].firstMatch
        XCTAssertTrue(gear.waitForExistence(timeout: 120), "settings gear should exist")
        capture(app, "b0_nav_bar_free")
        gear.tap()
        sleep(2)
        capture(app, "b1_settings_free")

        let restore = app.buttons["Restore Purchases"].firstMatch
        if restore.waitForExistence(timeout: 10) {
            restore.tap()
            sleep(1)
            capture(app, "b2_settings_restore_tapped_1s")
            sleep(2)
            capture(app, "b3_settings_restore_tapped_3s")
        }

        let upgrade = app.buttons.matching(
            NSPredicate(format: "label == 'Upgrade' OR label == 'Try Free' OR label == 'Renew'")
        ).firstMatch
        if upgrade.exists {
            upgrade.tap()
            sleep(2)
            capture(app, "b4_settings_upgrade_paywall")
            // Interactive swipe-down dismiss, the way an uncertain user leaves.
            app.swipeDown(velocity: .fast)
            sleep(2)
            capture(app, "b5_after_swipe_down")
        }
        app.swipeUp()
        sleep(1)
        capture(app, "b6_settings_scrolled")
    }

    // MARK: - Flow C: locked surfaces, what free users see

    func testLockedSurfacesTrendsCompareTeams() throws {
        for tab in ["trends", "compare", "teams"] {
            let app = launch(tab: tab)
            sleep(12)
            capture(app, "c_\(tab)_free")
            app.swipeUp()
            sleep(1)
            capture(app, "c_\(tab)_free_scrolled")
            app.terminate()
        }
    }

    // MARK: - Flow D: locked season from the season menu

    func testLockedSeasonFromMenu() throws {
        let app = launch(tab: "stats")
        let predicate = NSPredicate(format: "label == %@", "Season and season type")
        let control = app.buttons.matching(predicate).firstMatch
        XCTAssertTrue(control.waitForExistence(timeout: 120), "season control should exist")
        capture(app, "d0_stats_free")
        control.tap()
        sleep(2)
        capture(app, "d1_season_menu")
        dump(app, "d1")
        let locked = app.buttons.matching(NSPredicate(format: "label CONTAINS[c] 'lock' OR label MATCHES '.*20[0-9][0-9].*'")).allElementsBoundByIndex
        if let target = locked.last(where: { $0.isHittable }) {
            target.tap()
            sleep(3)
            capture(app, "d2_after_locked_season_tap")
        }
    }

    // MARK: - Flow E: onboarding's purchase page

    func testOnboardingPurchasePage() throws {
        let app = XCUIApplication()
        app.launchArguments = ["-ResetUITestState", "-MockProducts", "-PaywallSnapshot", "onboarding"]
        app.launch()
        sleep(15)
        capture(app, "e1_onboarding_last_page")
        dump(app, "e1")
        let restore = app.buttons["Restore Purchases"]
        if restore.exists {
            restore.tap()
            sleep(3)
            capture(app, "e2_onboarding_restore_tapped")
        }
        let getStarted = app.buttons["Get Started"].firstMatch
        if getStarted.exists {
            getStarted.tap()
            sleep(5)
            capture(app, "e3_after_get_started")
        }
    }
}
