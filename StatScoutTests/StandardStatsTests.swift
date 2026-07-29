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
}
