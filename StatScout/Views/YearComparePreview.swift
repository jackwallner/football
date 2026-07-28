import SwiftUI

/// A visually rich preview of the Year Comparison feature, shown to free users.
/// Uses mock data that mimics the real YearComparisonView layout with a blur overlay + CTA.
struct YearComparePreview: View {
    @EnvironmentObject private var store: StoreService
    let playerName: String
    let onUnlock: () -> Void

    var body: some View {
        ZStack(alignment: .bottom) {
            // Mock comparison content stays visible (blurred) as the hook.
            mockContent
                .blur(radius: 5)
                .clipped()

            BlurGateUnlock(
                headline: "See how \(playerName) evolved season to season",
                trigger: .yearCompare
            )
        }
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
        .padding(.horizontal, 12)
        .padding(.top, 12)
    }

    // MARK: - Mock Content (looks like the real YearComparisonView)

    private var mockContent: some View {
        VStack(spacing: 12) {
            // Mock year picker
            mockYearPicker

            // Mock overall change
            mockOverallChange

            // Mock category comparison
            mockCategoryCard
        }
        .padding(.horizontal, 12)
        .padding(.top, 12)
        .padding(.bottom, 24)
    }

    private var mockYearPicker: some View {
        HStack(spacing: 12) {
            mockYearButton(label: "2026", subtitle: "Recent")
            Image(systemName: "arrow.right")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(GridironPalette.inkTertiary)
            mockYearButton(label: "2025", subtitle: "Prior")
        }
        .padding(16)
        .background(GridironPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
    }

    private func mockYearButton(label: String, subtitle: String) -> some View {
        VStack(spacing: 2) {
            Text(label)
                .font(GridironType.statLarge)
                .foregroundStyle(GridironPalette.ink)
            Text(subtitle)
                .font(GridironType.micro)
                .foregroundStyle(GridironPalette.inkTertiary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
        .background(GridironPalette.surfaceAlt)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
    }

    private var mockOverallChange: some View {
        HStack(spacing: 16) {
            Image(systemName: "arrow.up.circle.fill")
                .font(.system(size: 32))
                .foregroundStyle(.green)

            VStack(alignment: .leading, spacing: 2) {
                Text("Overall Change")
                    .font(GridironType.small)
                    .foregroundStyle(GridironPalette.inkSecondary)
                Text("72nd (2025) → 85th (2026)")
                    .font(GridironType.bodyBold)
                    .foregroundStyle(GridironPalette.ink)
            }

            Spacer()

            HStack(spacing: 4) {
                Text("+")
                    .font(GridironType.statLarge)
                Text("13")
                    .font(GridironType.statLarge)
            }
            .foregroundStyle(.green)
        }
        .padding(16)
        .background(GridironPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
    }

    private var mockCategoryCard: some View {
        VStack(spacing: 0) {
            mockHeader("PASSING")

            mockRow(label: "Pass Yds", priorPct: 68, recentPct: 82, delta: 14, priorVal: "3,650", recentVal: "4,180")
            mockRow(label: "Pass TD", priorPct: 55, recentPct: 79, delta: 24, priorVal: "24", recentVal: "34")
            mockRow(label: "Rating", priorPct: 72, recentPct: 76, delta: 4, priorVal: "94.5", recentVal: "98.1")
            mockRow(label: "EPA/Play", priorPct: 81, recentPct: 78, delta: -3, priorVal: "0.18", recentVal: "0.15")
        }
        .background(GridironPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
    }

    private func mockHeader(_ title: String) -> some View {
        HStack(spacing: 0) {
            Text("Metric")
                .font(GridironType.micro)
                .foregroundStyle(GridironPalette.inkSecondary)
                .frame(maxWidth: .infinity, alignment: .leading)
            Text("2025")
                .font(GridironType.micro)
                .foregroundStyle(GridironPalette.inkSecondary)
                .frame(width: 72)
            Text("2026")
                .font(GridironType.micro)
                .foregroundStyle(GridironPalette.inkSecondary)
                .frame(width: 72)
            Text("Δ")
                .font(GridironType.micro)
                .foregroundStyle(GridironPalette.inkSecondary)
                .frame(width: 36)
        }
        .padding(.horizontal, GridironGeo.padInline)
        .frame(height: 28)
        .background(GridironPalette.surfaceAlt)
    }

    private func mockRow(label: String, priorPct: Int, recentPct: Int, delta: Int, priorVal: String, recentVal: String) -> some View {
        let isUp = delta > 0
        let isDown = delta < 0
        let deltaColor: Color = isUp ? .green : (isDown ? GridironPalette.turf : GridironPalette.inkSecondary)
        let arrow = isUp ? "↑" : (isDown ? "↓" : "→")

        return HStack(spacing: 0) {
            Text(label)
                .font(GridironType.body)
                .foregroundStyle(GridironPalette.ink)
                .frame(maxWidth: .infinity, alignment: .leading)
                .lineLimit(1)

            mockYearValue(percentile: priorPct, value: priorVal, isFaded: true)
                .frame(width: 72)

            mockYearValue(percentile: recentPct, value: recentVal, isFaded: false)
                .frame(width: 72)

            HStack(spacing: 2) {
                Text(arrow)
                    .font(GridironType.smallBold)
                Text("\(abs(delta))")
                    .font(GridironType.bodyBold)
            }
            .foregroundStyle(deltaColor)
            .frame(width: 36)
        }
        .frame(height: 48)
        .padding(.horizontal, GridironGeo.padInline)
        .background(GridironPalette.surface)
        .overlay(
            Rectangle()
                .fill(GridironPalette.divider)
                .frame(height: GridironGeo.hairline),
            alignment: .bottom
        )
    }

    private func mockYearValue(percentile: Int, value: String, isFaded: Bool) -> some View {
        VStack(spacing: 1) {
            HStack(spacing: 4) {
                Text("\(percentile)")
                    .font(GridironType.bodyBold)
                    .foregroundStyle(isFaded ? GridironPalette.inkTertiary : GridironPalette.color(forPercentile: percentile))
                RoundedRectangle(cornerRadius: 1)
                    .fill(GridironPalette.color(forPercentile: percentile))
                    .frame(width: CGFloat(percentile) * 0.3, height: 4)
                    .opacity(isFaded ? 0.5 : 1)
            }
            Text(value)
                .font(GridironType.micro)
                .foregroundStyle(isFaded ? GridironPalette.inkTertiary : GridironPalette.inkSecondary)
                .lineLimit(1)
        }
    }
}

#Preview {
    YearComparePreview(playerName: "Shohei Ohtani", onUnlock: {})
        .environmentObject(StoreService.shared)
}
