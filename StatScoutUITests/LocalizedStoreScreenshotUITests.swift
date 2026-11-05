import UIKit
import XCTest

@MainActor
final class LocalizedStoreScreenshotUITests: XCTestCase {
    struct LocaleCase: Decodable {
        let store, language, region, following, compare, years: String
        let scenes: [Int]?
        let board: String?
    }

    func testLocalizedStoreScreens() throws {
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "StoreScreenshotLocales", withExtension: "json"))
        let locales = try JSONDecoder().decode([LocaleCase].self, from: Data(contentsOf: url))
        for locale in locales {
            for scene in locale.scenes ?? Array(1...7) {
                let app = launch(locale, tab: scene == 3 ? "trends" : scene == 4 ? "teams" : "stats")
                if scene == 4 {
                    wait("Kansas City Chiefs", app)
                } else {
                    wait("Caleb Mercer", app)
                }
                if [2, 6, 7].contains(scene) {
                    let row = app.buttons.matching(NSPredicate(format: "label MATCHES %@", "^[0-9]+, Caleb Mercer,.*")).firstMatch
                    XCTAssertTrue(row.waitForExistence(timeout: 90))
                    row.tap()
                    wait("Caleb Mercer", app)
                }
                if scene == 3 {
                    let fiveWeeks = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "5 ")).firstMatch
                    XCTAssertTrue(fiveWeeks.waitForExistence(timeout: 30))
                    fiveWeeks.tap()
                }
                if scene == 5 { tap(locale.following, app) }
                if scene == 6 {
                    tap(locale.compare, app)
                    let peer = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "Mason Reed")).firstMatch
                    XCTAssertTrue(peer.waitForExistence(timeout: 30))
                    peer.tap()
                    wait("Mason Reed", app)
                }
                if scene == 7 { tap(locale.years, app) }
                sleep(3)
                let attachment = XCTAttachment(screenshot: app.screenshot())
                attachment.name = "\(locale.store)__\(String(format: "%02d", scene))"
                attachment.lifetime = .keepAlways
                add(attachment)
                if locale.store != locales.last?.store || scene != (locale.scenes ?? Array(1...7)).last {
                    app.terminate()
                }
            }
        }
    }

    private func launch(_ locale: LocaleCase, tab: String) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchEnvironment = ["FORCE_PRO": "1", "STATSCOUT_FORCE_PRO": "1", "SCREENSHOT_MODE": "1", "TEST_RUNNER_SCREENSHOT_APP_VERSION": "1.2"]
        app.launchArguments = ["-AppleLanguages", "(\(locale.language))", "-AppleLocale", locale.region, "-ScreenshotData", "-StoreScreenshotData", "-hasCompletedOnboarding", "YES", "-stats.board", locale.board ?? (UIDevice.current.userInterfaceIdiom == .pad ? "advanced" : "standard"), "-stats.qualifier", "All Players", "-ResetUITestState", "-StartTab", tab]
        if UIDevice.current.userInterfaceIdiom == .pad { app.launchArguments.append("-StoreScreenshotIPad") }
        app.launch()
        return app
    }

    private func wait(_ text: String, _ app: XCUIApplication) {
        let element = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@ OR value == %@", text, text)).firstMatch
        XCTAssertTrue(element.waitForExistence(timeout: 90), "Missing \(text)")
    }

    private func tap(_ label: String, _ app: XCUIApplication) {
        let button = app.buttons[label]
        XCTAssertTrue(button.waitForExistence(timeout: 30), "Missing \(label)")
        button.tap()
    }
}
