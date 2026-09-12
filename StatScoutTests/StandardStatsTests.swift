import XCTest
@testable import Gridiron_StatScout

final class StandardStatsTests: XCTestCase {
    func testPositionCatalogUsesNFLStatLines() {
        XCTAssertEqual(StandardStatCatalog.defaultStat(for: .qb), "Pass Yds")
        XCTAssertEqual(StandardStatCatalog.defaultStat(for: .rb), "Rush Yds")
        XCTAssertEqual(StandardStatCatalog.defaultStat(for: .wr), "Rec Yds")
        XCTAssertEqual(StandardStatCatalog.defaultStat(for: .te), "Rec Yds")
        XCTAssertEqual(StandardStatCatalog.defaultStat(for: .defense), "Tackles")
    }

    func testQuarterbackInterceptionsDefaultLowestFirst() {
        XCTAssertFalse(StandardStatCatalog.defaultDescending(for: "INT", position: .qb))
        XCTAssertTrue(StandardStatCatalog.defaultDescending(for: "Def INT", position: .defense))
    }

    func testMetricCategoryDecodesCaseInsensitively() throws {
        for rawValue in ["passing", "PASSING", "Passing"] {
            let json = """
            {"id":"m","label":"EPA/Play","value":"0.12","percentile":88,"category":"\(rawValue)"}
            """.data(using: .utf8)!
            let metric = try JSONDecoder().decode(Metric.self, from: json)
            XCTAssertEqual(metric.category, .passing)
        }
    }

    func testCompositeStandardStatsUseRatesInsteadOfLeadingCounts() {
        XCTAssertEqual(
            StandardStatSemantics.numericValue(label: "Rec/Tgt", value: "8/11")!,
            72.727,
            accuracy: 0.001
        )
        XCTAssertEqual(
            StandardStatSemantics.numericValue(label: "Cmp/Att", value: "15/25")!,
            60,
            accuracy: 0.001
        )
    }

    func testStandardComparisonRespectsDirectionAndRates() {
        XCTAssertEqual(
            StandardStatSemantics.winner(label: "Rec/Tgt", left: "8/11", right: "5/9"),
            .left
        )
        XCTAssertEqual(
            StandardStatSemantics.winner(label: "INT", left: "1", right: "3"),
            .left
        )
        XCTAssertNil(StandardStatSemantics.winner(label: "G", left: "1", right: "1"))
    }

    func testEveryExistingStandardStatGetsAPercentile() {
        XCTAssertEqual(
            StandardStatSemantics.percentile(
                label: "Rec/Tgt",
                value: "8/11",
                peerValues: ["8/11", "5/9", "10/10"]
            ),
            50
        )
        XCTAssertEqual(
            StandardStatSemantics.percentile(
                label: "G",
                value: "1",
                peerValues: []
            ),
            50
        )
    }
}
