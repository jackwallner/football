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
                cta: store.paywallBlurCTA,
                subtext: store.paywallBlurSubtext,
                action: onUnlock
            )
        }
        .clipShape(RoundedRectangle(cornerRadius: SavantGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: SavantGeo.radiusCard)
                .stroke(SavantPalette.hairline, lineWidth: 0.5)
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
                .foregroundStyle(SavantPalette.inkTertiary)
            mockYearButton(label: "2025", subtitle: "Prior")
        }
        .padding(16)
        .background(SavantPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: SavantGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: SavantGeo.radiusCard)
                .stroke(SavantPalette.hairline, lineWidth: 0.5)
        )
    }

    private func mockYearButton(label: String, subtitle: String) -> some View {
        VStack(spacing: 2) {
            Text(label)
                .font(SavantType.statLarge)
                .foregroundStyle(SavantPalette.ink)
            Text(subtitle)
                .font(SavantType.micro)
                .tracking(0.3)
                .foregroundStyle(SavantPalette.inkTertiary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
        .background(SavantPalette.surfaceAlt)
        .clipShape(RoundedRectangle(cornerRadius: SavantGeo.radiusCard))
    }

    private var mockOverallChange: some View {
        HStack(spacing: 16) {
            Image(systemName: "arrow.up.circle.fill")
                .font(.system(size: 32))
                .foregroundStyle(.green)

            VStack(alignment: .leading, spacing: 2) {
                Text("Overall Change")
                    .font(SavantType.small)
                    .foregroundStyle(SavantPalette.inkSecondary)
                Text("72nd (2025) → 85th (2026)")
                    .font(SavantType.bodyBold)
                    .foregroundStyle(SavantPalette.ink)
            }

            Spacer()

            HStack(spacing: 4) {
                Text("+")
                    .font(SavantType.statLarge)
                Text("13")
                    .font(SavantType.statLarge)
            }
            .foregroundStyle(.green)
        }
        .padding(16)
        .background(SavantPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: SavantGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: SavantGeo.radiusCard)
                .stroke(SavantPalette.hairline, lineWidth: 0.5)
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
        .background(SavantPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: SavantGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: SavantGeo.radiusCard)
                .stroke(SavantPalette.hairline, lineWidth: 0.5)
        )
    }

    private func mockHeader(_ title: String) -> some View {
        HStack(spacing: 0) {
            Text("Metric")
                .font(SavantType.micro)
                .foregroundStyle(SavantPalette.inkSecondary)
                .frame(maxWidth: .infinity, alignment: .leading)
            Text("2025")
                .font(SavantType.micro)
                .foregroundStyle(SavantPalette.inkSecondary)
                .frame(width: 72)
            Text("2026")
                .font(SavantType.micro)
                .foregroundStyle(SavantPalette.inkSecondary)
                .frame(width: 72)
            Text("Δ")
                .font(SavantType.micro)
                .foregroundStyle(SavantPalette.inkSecondary)
                .frame(width: 36)
        }
        .padding(.horizontal, SavantGeo.padInline)
        .frame(height: 28)
        .background(SavantPalette.surfaceAlt)
    }

    private func mockRow(label: String, priorPct: Int, recentPct: Int, delta: Int, priorVal: String, recentVal: String) -> some View {
        let isUp = delta > 0
        let isDown = delta < 0
        let deltaColor: Color = isUp ? .green : (isDown ? SavantPalette.savantRed : SavantPalette.inkSecondary)
        let arrow = isUp ? "↑" : (isDown ? "↓" : "→")

        return HStack(spacing: 0) {
            Text(label)
                .font(SavantType.body)
                .foregroundStyle(SavantPalette.ink)
                .frame(maxWidth: .infinity, alignment: .leading)
                .lineLimit(1)

            mockYearValue(percentile: priorPct, value: priorVal, isFaded: true)
                .frame(width: 72)

            mockYearValue(percentile: recentPct, value: recentVal, isFaded: false)
                .frame(width: 72)

            HStack(spacing: 2) {
                Text(arrow)
                    .font(SavantFont.condensed(12, weight: .bold))
                Text("\(abs(delta))")
                    .font(SavantType.bodyBold)
            }
            .foregroundStyle(deltaColor)
            .frame(width: 36)
        }
        .frame(height: 48)
        .padding(.horizontal, SavantGeo.padInline)
        .background(SavantPalette.surface)
        .overlay(
            Rectangle()
                .fill(SavantPalette.divider)
                .frame(height: SavantGeo.hairline),
            alignment: .bottom
        )
    }

    private func mockYearValue(percentile: Int, value: String, isFaded: Bool) -> some View {
        VStack(spacing: 1) {
            HStack(spacing: 4) {
                Text("\(percentile)")
                    .font(SavantType.bodyBold)
                    .foregroundStyle(isFaded ? SavantPalette.inkTertiary : SavantPalette.color(forPercentile: percentile))
                RoundedRectangle(cornerRadius: 1)
                    .fill(SavantPalette.color(forPercentile: percentile))
                    .frame(width: CGFloat(percentile) * 0.3, height: 4)
                    .opacity(isFaded ? 0.5 : 1)
            }
            Text(value)
                .font(SavantType.micro)
                .foregroundStyle(isFaded ? SavantPalette.inkTertiary : SavantPalette.inkSecondary)
                .lineLimit(1)
        }
    }
}

#Preview {
    YearComparePreview(playerName: "Shohei Ohtani", onUnlock: {})
        .environmentObject(StoreService.shared)
}
