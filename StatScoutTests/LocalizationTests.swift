import Foundation
import XCTest
@testable import Gridiron_StatScout

final class LocalizationTests: XCTestCase {
    private let languages = ["es", "de", "nl", "it"]

    private func bundle(_ language: String) -> Bundle? {
        let hosts = [Bundle.main] + Bundle.allBundles
        for host in hosts {
            if let path = host.path(forResource: language, ofType: "lproj"),
               let bundle = Bundle(path: path) {
                return bundle
            }
        }
        return nil
    }

    func testEveryLanguageHasACompiledBundle() {
        for language in languages {
            XCTAssertNotNil(bundle(language), "\(language).lproj is missing")
        }
    }

    func testRepresentativeStringsDoNotFallBackToEnglish() {
        let expected: [String: String] = [
            "es": "Prueba gratis",
            "de": "Gratis testen",
            "nl": "Probeer gratis",
            "it": "Prova gratis",
        ]
        for language in languages {
            guard let localizedBundle = bundle(language) else {
                XCTFail("\(language).lproj is missing")
                continue
            }
            XCTAssertEqual(
                String(localized: "Try Free", bundle: localizedBundle),
                expected[language]
            )
        }
    }

    func testGameCountPluralFormsResolve() {
        let singular: [String: String] = [
            "es": "1 partido",
            "de": "1 Spiel",
            "nl": "1 wedstrijd",
            "it": "1 partita",
        ]
        let plural: [String: String] = [
            "es": "3 partidos",
            "de": "3 Spiele",
            "nl": "3 wedstrijden",
            "it": "3 partite",
        ]
        for language in languages {
            guard let localizedBundle = bundle(language) else {
                XCTFail("\(language).lproj is missing")
                continue
            }
            let locale = Locale(identifier: language)
            XCTAssertEqual(String(localized: "\(1) games", bundle: localizedBundle, locale: locale), singular[language])
            XCTAssertEqual(String(localized: "\(3) games", bundle: localizedBundle, locale: locale), plural[language])
        }
    }

    func testPluralSubstitutionWithMultipleArguments() {
        let expected: [String: String] = [
            "es": "Hasta la semana 3, 1 partido",
            "de": "Bis Woche 3, 1 Spiel",
            "nl": "Tot week 3, 1 wedstrijd",
            "it": "Fino alla settimana 3, 1 partita",
        ]
        for language in languages {
            guard let localizedBundle = bundle(language) else {
                XCTFail("\(language).lproj is missing")
                continue
            }
            XCTAssertEqual(
                String(localized: "Through Week \(3), \(1) games", bundle: localizedBundle, locale: Locale(identifier: language)),
                expected[language]
            )
        }
    }
}
