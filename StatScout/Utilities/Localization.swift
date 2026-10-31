import Foundation

enum MetricName {
    static func localized(_ label: String) -> String {
        switch label {
        case "Aggressiveness": String(localized: "Aggressiveness")
        case "Intended Air Yds": String(localized: "Intended Air Yds")
        case "EPA/Play": String(localized: "EPA/Play")
        case "Pass TD": String(localized: "Pass TD")
        case "Pass Yds": String(localized: "Pass Yds")
        case "Rush EPA": String(localized: "Rush EPA")
        case "Rush TD": String(localized: "Rush TD")
        case "Rush Yds": String(localized: "Rush Yds")
        case "Rec EPA": String(localized: "Rec EPA")
        case "Rec TD": String(localized: "Rec TD")
        case "Rec Yds": String(localized: "Rec Yds")
        case "Rec": String(localized: "Rec")
        case "Time to Throw": String(localized: "Time to Throw")
        case "Separation": String(localized: "Separation")
        case "Tackles": String(localized: "Tackles")
        case "Tgt Allowed": String(localized: "Tgt Allowed")
        case "EPA/Rush": String(localized: "EPA/Rush")
        case "EPA/Tgt": String(localized: "EPA/Tgt")
        case "Cmp%": String(localized: "Cmp%")
        case "INT%": String(localized: "INT%")
        case "Sack%": String(localized: "Sack%")
        case "Explosive%": String(localized: "Explosive%")
        case "Fumble%": String(localized: "Fumble%")
        case "Catch%": String(localized: "Catch%")
        case "Missed Tkl%": String(localized: "Missed Tkl%")
        case "Y/A": String(localized: "Y/A")
        case "Y/C": String(localized: "Y/C")
        case "RYOE": String(localized: "RYOE")
        case "WOPR": String(localized: "WOPR")
        case "Target Share": String(localized: "Target Share")
        case "RACR": String(localized: "RACR")
        case "YAC": String(localized: "YAC")
        case "YAC+": String(localized: "YAC+")
        case "CPOE": String(localized: "CPOE")
        case "Rating": String(localized: "Rating")
        case "Cmp% Allowed": String(localized: "Cmp% Allowed")
        case "Yds/Tgt Allowed": String(localized: "Yds/Tgt Allowed")
        case "Rating Allowed": String(localized: "Rating Allowed")
        case "Pressures": String(localized: "Pressures")
        case "Hurries": String(localized: "Hurries")
        case "QB KD": String(localized: "QB KD")
        case "TFL": String(localized: "TFL")
        case "PD": String(localized: "PD")
        case "Sacks": String(localized: "Sacks")
        case "QB Hits": String(localized: "QB Hits")
        case "INT": String(localized: "INT")
        case "FF": String(localized: "FF")
        case "Cmp": String(localized: "Cmp")
        case "Att": String(localized: "Att")
        case "Car": String(localized: "Car")
        case "Tgt": String(localized: "Tgt")
        default: String(localized: String.LocalizationValue(label))
        }
    }
}

enum LocalizedNumber {
    static func fixed(
        _ value: Double,
        fractionDigits: Int,
        percent: Bool = false,
        signed: Bool = false,
        locale: Locale = .autoupdatingCurrent
    ) -> String {
        let formatter = NumberFormatter()
        formatter.locale = locale
        formatter.numberStyle = percent ? .percent : .decimal
        formatter.usesGroupingSeparator = false
        formatter.minimumFractionDigits = fractionDigits
        formatter.maximumFractionDigits = fractionDigits
        let formattedValue = percent ? value / 100 : value
        var text = formatter.string(from: NSNumber(value: formattedValue)) ?? value.formatted(.number)
        if signed, value > 0 { text = "+" + text }
        return text
    }

    static func fixed(_ value: Double, format: String, locale: Locale = .autoupdatingCurrent) -> String {
        let digits = format.range(of: #"%\.([0-9]+)f"#, options: .regularExpression)
            .flatMap { Int(format[$0].dropFirst(2).dropLast()) } ?? 0
        return fixed(value, fractionDigits: digits, percent: format.contains("%%"), locale: locale)
    }

    static func text(_ rawValue: String, locale: Locale = .autoupdatingCurrent) -> String {
        let value = rawValue.trimmingCharacters(in: .whitespaces)
        guard !value.isEmpty, value != "-" else { return value }

        if value.contains("/"), !value.contains(" ") {
            let parts = value.split(separator: "/", omittingEmptySubsequences: false)
            if parts.count == 2, metricNumericValue(String(parts[1])) != nil {
                return "\(integer(String(parts[0])))/\(integer(String(parts[1])))"
            }
        }

        let pattern = #"^([+-]?\d[\d,]*(?:\.\d+)?)(.*)$"#
        guard let match = value.range(of: pattern, options: .regularExpression) else { return value }
        let numberText = String(value[match])
        guard let numberRange = numberText.range(of: #"^[+-]?\d[\d,]*(?:\.\d+)?"#, options: .regularExpression) else {
            return value
        }
        let rawNumber = String(numberText[numberRange])
        let suffix = String(numberText[numberRange.upperBound...])
        guard let number = metricNumericValue(rawNumber) else { return value }

        let decimalPlaces: Int
        if let decimalIndex = rawNumber.firstIndex(of: ".") {
            decimalPlaces = rawNumber.distance(from: rawNumber.index(after: decimalIndex), to: rawNumber.endIndex)
        } else {
            decimalPlaces = 0
        }
        let isPercent = suffix.trimmingCharacters(in: .whitespaces) == "%"
        let formatter = NumberFormatter()
        formatter.locale = locale
        formatter.numberStyle = isPercent ? .percent : .decimal
        formatter.usesGroupingSeparator = rawNumber.contains(",")
        formatter.minimumFractionDigits = decimalPlaces
        formatter.maximumFractionDigits = decimalPlaces
        let formattedNumber = formatter.string(from: NSNumber(value: isPercent ? number / 100 : number))
            ?? rawNumber
        let sign = rawNumber.hasPrefix("+") ? "+" : ""
        if suffix.isEmpty || isPercent { return sign + formattedNumber }
        if suffix == "/g" { return String(localized: "\(sign + formattedNumber)/g") }
        return String(localized: "\(sign + formattedNumber)\(suffix)")
    }

    private static func integer(_ rawValue: String) -> String {
        guard let number = metricNumericValue(rawValue) else { return rawValue }
        return Int(number.rounded()).formatted(.number.grouping(.never))
    }
}

/// Values the app computes itself, written the way the feed ships them (POSIX
/// digits). `Metric.value` holds this form and `displayValue` localizes it
/// once: a value already formatted as "4,1" is re-read as 41.
enum FeedNumber {
    static func fixed(_ value: Double, format: String) -> String {
        String(format: format, value)
    }

    static func perGame(_ value: Double) -> String {
        String(format: value < 10 && value != value.rounded() ? "%.1f/g" : "%.0f/g", value)
    }

    /// "3,322", grouped the way the feed ships yardage.
    static func integer(_ value: Double) -> String {
        Int(value.rounded()).formatted(.number.grouping(.automatic).locale(Locale(identifier: "en_US")))
    }
}

extension MetricCategory {
    var title: String {
        switch self {
        case .passing: String(localized: "Passing")
        case .rushing: String(localized: "Rushing")
        case .receiving: String(localized: "Receiving")
        case .defense: String(localized: "Defense")
        }
    }
}

extension MetricFamily {
    var localizedTitle: String {
        switch self {
        case .efficiency: String(localized: "Efficiency")
        case .accuracy: String(localized: "Accuracy")
        case .pressure: String(localized: "Pressure")
        case .aggressiveness: String(localized: "Aggressiveness")
        case .rushing: String(localized: "Rushing")
        case .expectedProduction: String(localized: "Expected")
        case .explosiveness: String(localized: "Explosiveness")
        case .usage: String(localized: "Usage")
        case .receiving: String(localized: "Receiving")
        case .separation: String(localized: "Separation")
        case .yac: String(localized: "YAC")
        case .production: String(localized: "Production")
        case .passRush: String(localized: "Pass Rush")
        case .turnovers: String(localized: "Turnovers")
        case .coverage: String(localized: "Coverage")
        case .tackling: String(localized: "Tackling")
        }
    }
}

extension PlayerPositionGroup {
    var localizedName: String {
        switch self {
        case .qb: String(localized: "quarterback")
        case .rb: String(localized: "running back")
        case .wr: String(localized: "receiver")
        case .te: String(localized: "tight end")
        case .defense: String(localized: "defensive player")
        }
    }

    var localizedPluralName: String {
        switch self {
        case .qb: String(localized: "quarterbacks")
        case .rb: String(localized: "running backs")
        case .wr: String(localized: "receivers")
        case .te: String(localized: "tight ends")
        case .defense: String(localized: "defensive players")
        }
    }
}

enum InjuryName {
    static func localized(_ injury: String) -> String {
        switch injury.trimmingCharacters(in: .whitespaces).lowercased() {
        case "abdomen": String(localized: "Abdomen")
        case "achilles": String(localized: "Achilles")
        case "ankle": String(localized: "Ankle")
        case "back", "lower back": String(localized: "Back")
        case "calf": String(localized: "Calf")
        case "chest": String(localized: "Chest")
        case "concussion": String(localized: "Concussion")
        case "elbow": String(localized: "Elbow")
        case "foot": String(localized: "Foot")
        case "forearm": String(localized: "Forearm")
        case "groin": String(localized: "Groin")
        case "hamstring": String(localized: "Hamstring")
        case "hand": String(localized: "Hand")
        case "head": String(localized: "Head")
        case "hip": String(localized: "Hip")
        case "illness": String(localized: "Illness")
        case "knee": String(localized: "Knee")
        case "neck": String(localized: "Neck")
        case "oblique": String(localized: "Oblique")
        case "pectoral": String(localized: "Pectoral")
        case "quadriceps": String(localized: "Quadriceps")
        case "ribs", "rib": String(localized: "Ribs")
        case "shoulder": String(localized: "Shoulder")
        case "thigh": String(localized: "Thigh")
        case "toe": String(localized: "Toe")
        case "triceps": String(localized: "Triceps")
        case "wrist": String(localized: "Wrist")
        case "undisclosed": String(localized: "Undisclosed")
        default: injury
        }
    }
}

private enum OrdinalFormat {
    nonisolated(unsafe) static let formatter: NumberFormatter = {
        let formatter = NumberFormatter()
        formatter.numberStyle = .ordinal
        formatter.locale = .autoupdatingCurrent
        return formatter
    }()
}

extension Int {
    var ordinal: String {
        OrdinalFormat.formatter.string(from: NSNumber(value: self)) ?? formatted(.number.grouping(.never))
    }
}
