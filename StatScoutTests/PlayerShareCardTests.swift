import XCTest
@testable import Gridiron_StatScout

@MainActor
final class PlayerShareCardTests: XCTestCase {
    private func metric(_ label: String, _ value: String, _ pct: Int) -> Metric {
        Metric(id: label, label: label, value: value, percentile: pct, category: .passing)
    }

    private var card: PlayerShareCard {
        let efficiency = [metric("EPA/Play", "0.24", 96), metric("Success Rate", "51.2%", 90), metric("CPOE", "+4.1", 88)]
        let aggressiveness = [metric("aDOT", "8.9", 72), metric("Time to Throw", "2.71", 41)]
        let player = Player(playerId: 1, name: "Geno Smith", team: "SEA", position: "QB", handedness: "",
            updatedAt: Date(), season: StatScoutSeason.current, playerType: "qb",
            metrics: efficiency + aggressiveness, standardStats: [], games: [])
        return PlayerShareCard(
            player: player,
            groups: [("Efficiency", efficiency), ("Aggressiveness", aggressiveness)],
            seasonLabel: String(StatScoutSeason.current),
            isCurrentSeason: true
        )
    }

    func testRendersPNGAtThreeTimesCardWidth() throws {
        let image = try XCTUnwrap(PlayerShareImage.render(card))
        let rendered = try XCTUnwrap(UIImage(data: image.png))
        XCTAssertEqual(rendered.size.width * rendered.scale, PlayerShareCard.width * 3, accuracy: 1)
        XCTAssertEqual(image.fileName, "Geno-Smith-\(StatScoutSeason.current).png")

        let attachment = XCTAttachment(data: image.png, uniformTypeIdentifier: "public.png")
        attachment.name = "share-card"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    func testShareMessageLinksTheAppStoreListing() {
        let message = StatScoutShare.message(for: card.player, seasonLabel: "2026")
        XCTAssertTrue(message.contains("https://apps.apple.com/app/id\(AppStoreReviewLinks.appStoreID)"))
        XCTAssertTrue(message.hasPrefix("Geno Smith, 2026 percentiles"))
    }
}
