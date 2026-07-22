import SwiftUI

struct YearComparisonView: View {
    let history: [Player]
    @State private var yearA: Int = 0
    @State private var yearB: Int = 0

    // yearA is the later (more recent) year, yearB is the earlier year
    private var recentYear: Int { max(yearA, yearB) }
    private var priorYear: Int { min(yearA, yearB) }

    private var availableYears: [Int] {
        history.compactMap(\.season).uniqued().sorted(by: >)
    }

    private var playerYearA: Player? {
        history.first { $0.season == recentYear }
    }

    private var playerYearB: Player? {
        history.first { $0.season == priorYear }
    }

    var body: some View {
        VStack(spacing: 12) {
            yearPickerCard

            if let p1 = playerYearA, let p2 = playerYearB {
                overallChangeCard(p1: p1, p2: p2)
                comparisonContent(p1: p1, p2: p2)
            } else {
                noDataView
            }
        }
        .padding(.horizontal, 12)
        .padding(.top, 12)
        .onAppear(perform: snapSelections)
    }

    private func snapSelections() {
        let years = availableYears
        guard !years.isEmpty else { return }
        if !years.contains(yearA) { yearA = years.first ?? 0 }
        if !years.contains(yearB) || yearB == yearA {
            yearB = years.first(where: { $0 != yearA }) ?? yearB
        }
    }

    private var noDataView: some View {
        ContentUnavailableView {
            Label("No Data Available", systemImage: "calendar.badge.clock")
        } description: {
            Text(availableYears.isEmpty
                 ? "No historical data is available for this player."
                 : "Data for \(recentYear) or \(priorYear) is not available.")
        }
        .padding(.vertical, 48)
        .frame(maxWidth: .infinity)
        .background(GridironPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
    }

    // MARK: - Year Picker Card

    private var yearPickerCard: some View {
        VStack(spacing: 12) {
            HStack(spacing: 12) {
                yearButton(year: $yearA, otherYear: yearB, label: yearA > 0 ? String(yearA) : "Select")
                Image(systemName: "arrow.right")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(GridironPalette.inkTertiary)
                yearButton(year: $yearB, otherYear: yearA, label: yearB > 0 ? String(yearB) : "Select")
            }
        }
        .padding(16)
        .background(GridironPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
    }

    private func yearButton(year: Binding<Int>, otherYear: Int, label: String) -> some View {
        Menu {
            ForEach(availableYears.filter { $0 != otherYear }, id: \.self) { y in
                Button {
                    year.wrappedValue = y
                } label: {
                    HStack {
                        Text(String(y))
                        if year.wrappedValue == y {
                            Image(systemName: "checkmark")
                        }
                    }
                }
            }
        } label: {
            VStack(spacing: 2) {
                Text(label)
                    .font(GridironType.statLarge)
                    .foregroundStyle(GridironPalette.ink)
                Text(year.wrappedValue == recentYear ? "Recent" : "Prior")
                    .font(GridironType.micro)
                    .tracking(0.3)
                    .foregroundStyle(GridironPalette.inkTertiary)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(GridironPalette.surfaceAlt)
            .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
            .overlay(
                HStack {
                    Spacer()
                    Image(systemName: "chevron.down")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundStyle(GridironPalette.inkTertiary)
                        .padding(.trailing, 8)
                },
                alignment: .trailing
            )
        }
    }

    // MARK: - Overall Change Card

    private func overallChangeCard(p1: Player, p2: Player) -> some View {
        let delta = p1.overallPercentile - p2.overallPercentile
        let isUp = delta > 0
        let isDown = delta < 0
        let color: Color = isUp ? .green : (isDown ? GridironPalette.turf : GridironPalette.inkSecondary)
        let icon = isUp ? "arrow.up.circle.fill" : (isDown ? "arrow.down.circle.fill" : "minus.circle.fill")

        return HStack(spacing: 16) {
            Image(systemName: icon)
                .font(.system(size: 32))
                .foregroundStyle(color)

            VStack(alignment: .leading, spacing: 2) {
                Text("Overall Average")
                    .font(GridironType.small)
                    .foregroundStyle(GridironPalette.inkSecondary)
                Text("\(p2.overallPercentile)% (\(String(priorYear))) → \(p1.overallPercentile)% (\(String(recentYear)))")
                    .font(GridironType.bodyBold)
                    .foregroundStyle(GridironPalette.ink)
            }

            Spacer()

            HStack(spacing: 2) {
                Text(isUp ? "+\(delta)%" : "\(delta)%")
                    .font(GridironType.statLarge)
            }
            .foregroundStyle(color)
        }
        .padding(16)
        .background(GridironPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
    }

    // MARK: - Comparison Content

    private func comparisonContent(p1: Player, p2: Player) -> some View {
        let comparisons = buildComparisons(p1: p1, p2: p2)
        let grouped = Dictionary(grouping: comparisons) { $0.category }

        if comparisons.isEmpty {
            return AnyView(noMetricsView)
        }

        return AnyView(
            LazyVStack(spacing: 12) {
                ForEach(MetricCategory.allCases, id: \.self) { category in
                    if let items = grouped[category], !items.isEmpty {
                        categoryCard(category: category, items: items)
                    }
                }
            }
        )
    }

    private var noMetricsView: some View {
        ContentUnavailableView {
            Label("No Comparable Metrics", systemImage: "chart.bar.xaxis")
        } description: {
            Text("These seasons don't have overlapping metrics to compare.")
        }
        .padding(.vertical, 48)
        .frame(maxWidth: .infinity)
        .background(GridironPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
    }

    // MARK: - Category Card

    private func categoryCard(category: MetricCategory, items: [MetricComparison]) -> some View {
        VStack(spacing: 0) {
            GridironSubSectionBar(title: category.rawValue.uppercased())

            columnHeader

            ForEach(Array(items.enumerated()), id: \.element.metricLabel) { idx, item in
                comparisonRow(item: item, isAlt: idx % 2 == 1)
            }
        }
        .background(GridironPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: GridironGeo.radiusCard))
        .overlay(
            RoundedRectangle(cornerRadius: GridironGeo.radiusCard)
                .stroke(GridironPalette.hairline, lineWidth: 0.5)
        )
    }

    private var columnHeader: some View {
        HStack(spacing: 0) {
            Text("Metric")
                .font(GridironType.micro)
                .foregroundStyle(GridironPalette.inkSecondary)
                .frame(maxWidth: .infinity, alignment: .leading)

            Text(String(priorYear))
                .font(GridironType.micro)
                .foregroundStyle(GridironPalette.inkSecondary)
                .frame(width: 72)

            Text(String(recentYear))
                .font(GridironType.micro)
                .foregroundStyle(GridironPalette.inkSecondary)
                .frame(width: 72)

            Text("Δ")
                .font(GridironType.micro)
                .foregroundStyle(GridironPalette.inkSecondary)
                .frame(width: 48)
        }
        .padding(.horizontal, GridironGeo.padInline)
        .frame(height: 28)
        .background(GridironPalette.surfaceAlt)
    }

    private func comparisonRow(item: MetricComparison, isAlt: Bool) -> some View {
        let isUp = item.change > 0
        let isDown = item.change < 0
        let deltaColor: Color = isUp ? .green : (isDown ? GridironPalette.turf : GridironPalette.inkSecondary)
        let arrow = isUp ? "↑" : (isDown ? "↓" : "→")

        return HStack(spacing: 0) {
            // Metric label
            Text(item.metricLabel)
                .font(GridironType.body)
                .foregroundStyle(GridironPalette.ink)
                .frame(maxWidth: .infinity, alignment: .leading)
                .lineLimit(1)

            // Prior year (earlier)
            yearValueColumn(
                percentile: item.percentileB,
                value: item.valueB,
                isFaded: true
            )
            .frame(width: 72)

            // Recent year (later) - emphasized with color
            yearValueColumn(
                percentile: item.percentileA,
                value: item.valueA,
                isFaded: false
            )
            .frame(width: 72)

            // Delta (in percentile points)
            HStack(spacing: 2) {
                Text(arrow)
                    .font(GridironFont.condensed(12, weight: .bold))
                Text("\(abs(item.change))%")
                    .font(GridironType.bodyBold)
            }
            .foregroundStyle(deltaColor)
            .frame(width: 48)
        }
        .frame(height: 48)
        .padding(.horizontal, GridironGeo.padInline)
        .background(isAlt ? GridironPalette.surfaceAlt : GridironPalette.surface)
        .overlay(
            Rectangle()
                .fill(GridironPalette.divider)
                .frame(height: GridironGeo.hairline),
            alignment: .bottom
        )
    }

    private func yearValueColumn(percentile: Int, value: String, isFaded: Bool) -> some View {
        VStack(spacing: 1) {
            HStack(spacing: 4) {
                Text("\(percentile)")
                    .font(GridironType.bodyBold)
                    .foregroundStyle(isFaded ? GridironPalette.inkTertiary : GridironPalette.color(forPercentile: percentile))
                // Mini bar
                RoundedRectangle(cornerRadius: 1)
                    .fill(GridironPalette.color(forPercentile: percentile))
                    .frame(width: CGFloat(percentile) * 0.3, height: 4)
                    .opacity(isFaded ? 0.5 : 1)
            }
            if !value.isEmpty {
                Text(value)
                    .font(GridironType.micro)
                    .foregroundStyle(isFaded ? GridironPalette.inkTertiary : GridironPalette.inkSecondary)
                    .lineLimit(1)
            }
        }
    }

    // MARK: - Comparisons Builder

    private func buildComparisons(p1: Player, p2: Player) -> [MetricComparison] {
        let metrics1 = Dictionary(grouping: p1.metrics) { $0.label }
        let metrics2 = Dictionary(grouping: p2.metrics) { $0.label }
        let allLabels = Set(metrics1.keys).union(metrics2.keys)

        return allLabels.compactMap { label in
            guard let m1 = metrics1[label]?.first, let m2 = metrics2[label]?.first else { return nil }
            return MetricComparison(
                metricLabel: label,
                category: m1.category,
                percentileA: m1.percentile,
                percentileB: m2.percentile,
                valueA: m1.value,
                valueB: m2.value,
                change: m1.percentile - m2.percentile
            )
        }.sorted { a, b in
            a.category == b.category
                ? a.category.sortMetrics(a.metricLabel, b.metricLabel)
                : MetricCategory.allCases.firstIndex(of: a.category)! < MetricCategory.allCases.firstIndex(of: b.category)!
        }
    }
}

private struct MetricComparison {
    let metricLabel: String
    let category: MetricCategory
    let percentileA: Int
    let percentileB: Int
    let valueA: String
    let valueB: String
    let change: Int
}

private extension Sequence where Element: Hashable {
    func uniqued() -> [Element] {
        var seen = Set<Element>()
        return filter { seen.insert($0).inserted }
    }
}
