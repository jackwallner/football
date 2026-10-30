import XCTest
@testable import Gridiron_StatScout

/// Date logic pinned to a fixed clock and explicit zones, never the machine's.
///
/// Every test names the past fix it guards.
final class ClockBoundaryTests: XCTestCase {
    private let la: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "America/Los_Angeles")!
        return calendar
    }()

    private let utc: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        return calendar
    }()

    private func local(_ calendar: Calendar, _ year: Int, _ month: Int, _ day: Int,
                       _ hour: Int = 0, _ minute: Int = 0, _ second: Int = 0) -> Date {
        calendar.date(from: DateComponents(
            year: year, month: month, day: day, hour: hour, minute: minute, second: second
        ))!
    }

    private func instant(_ iso: String) -> Date { ISO8601DateFormatter().date(from: iso)! }

    // MARK: Season rollover

    /// 485167c: the season is derived from the calendar, not pinned to a
    /// literal, so a shipped build keeps up when the pipeline starts the next
    /// season. The flip is local midnight on 1 September.
    func testSeasonFlipsAtLocalMidnightOnSeptemberFirst() {
        XCTAssertEqual(StatScoutSeason.season(on: local(la, 2026, 8, 31, 23, 59, 59), calendar: la), 2025)
        XCTAssertEqual(StatScoutSeason.season(on: local(la, 2026, 9, 1, 0, 0, 0), calendar: la), 2026)
        XCTAssertEqual(StatScoutSeason.season(on: local(la, 2026, 9, 1, 0, 0, 1), calendar: la), 2026)
    }

    /// The same instant is a different season on either side of the date line
    /// of the zone it is read in, which is why the calendar is injected.
    func testSeasonFollowsTheViewersZoneAcrossTheFlip() {
        let instantAfterUTCMidnight = instant("2026-09-01T03:00:00Z")
        XCTAssertEqual(StatScoutSeason.season(on: instantAfterUTCMidnight, calendar: utc), 2026)
        XCTAssertEqual(StatScoutSeason.season(on: instantAfterUTCMidnight, calendar: la), 2025)
    }

    /// 485167c: an NFL season is named for the year it kicks off in, so
    /// January and February still belong to the season that began in the fall.
    func testSeasonHoldsThroughNewYearAndTheOffseason() {
        XCTAssertEqual(StatScoutSeason.season(on: local(la, 2026, 12, 31, 23, 59, 59), calendar: la), 2026)
        XCTAssertEqual(StatScoutSeason.season(on: local(la, 2027, 1, 1, 0, 0, 0), calendar: la), 2026)
        XCTAssertEqual(StatScoutSeason.season(on: local(la, 2027, 2, 14), calendar: la), 2026)
        XCTAssertEqual(StatScoutSeason.season(on: local(la, 2027, 8, 31, 23, 59, 59), calendar: la), 2026)
        XCTAssertEqual(StatScoutSeason.season(on: local(la, 2027, 9, 1), calendar: la), 2027)
    }

    /// 485167c: never resolves older than the season the app shipped with,
    /// whatever the device clock says.
    func testSeasonIsFlooredAtTheShippedSeason() {
        XCTAssertEqual(StatScoutSeason.season(on: local(la, 2019, 10, 1), calendar: la), 2025)
        XCTAssertEqual(StatScoutSeason.season(on: local(la, 2025, 3, 1), calendar: la), 2025)
    }

    /// The flip happens on the clock day, so the DST changeovers either side of
    /// the season never move it.
    func testSeasonIsUnaffectedByTheSpringAndFallDSTChangeovers() {
        XCTAssertEqual(StatScoutSeason.season(on: local(la, 2027, 3, 14, 1, 59, 59), calendar: la), 2026)
        XCTAssertEqual(StatScoutSeason.season(on: local(la, 2027, 3, 14, 3, 0, 0), calendar: la), 2026)
        XCTAssertEqual(StatScoutSeason.season(on: local(la, 2026, 11, 1, 1, 30, 0), calendar: la), 2026)
    }

    // MARK: Game-log window

    /// 8ca22e7: the rolling window is anchored to the season's end once the
    /// season is over, and the 120 days are calendar days, so the start lands
    /// on local midnight even though 1 November 2025 is the day before clocks
    /// fall back. A 120 * 86,400 s subtraction would land an hour off.
    func testWindowStartIsACalendarDayCountAcrossTheFallBackChangeover() {
        let start = StatScoutSeason.gameLogWindowStart(
            season: 2025, days: 120, now: local(la, 2026, 8, 17), calendar: la
        )
        XCTAssertEqual(start, local(la, 2025, 11, 1))
        XCTAssertEqual(la.component(.hour, from: start), 0)
    }

    /// 8ca22e7: a live window measured back through 1 November 2026 (the
    /// fall-back changeover) is still a whole number of calendar days, so it
    /// starts at local midnight rather than an hour off.
    func testLiveWindowStartKeepsLocalMidnightAcrossTheFallBackChangeover() {
        let start = StatScoutSeason.gameLogWindowStart(
            season: 2026, days: 60, now: local(la, 2026, 12, 1), calendar: la
        )
        XCTAssertEqual(start, local(la, 2026, 10, 2))
        XCTAssertEqual(la.component(.hour, from: start), 0)
    }

    /// 8ca22e7: the anchor is whichever of today and 1 March is earlier, so
    /// the last second before 1 March still tracks the clock and the first
    /// second of it stops.
    func testWindowAnchorStopsFollowingTheClockAtTheSeasonCap() {
        let cap = local(la, 2026, 3, 1)
        let oneSecondBefore = cap.addingTimeInterval(-1)
        let before = StatScoutSeason.gameLogWindowStart(season: 2025, days: 10, now: oneSecondBefore, calendar: la)
        let at = StatScoutSeason.gameLogWindowStart(season: 2025, days: 10, now: cap, calendar: la)
        let after = StatScoutSeason.gameLogWindowStart(season: 2025, days: 10, now: cap.addingTimeInterval(86_400), calendar: la)
        XCTAssertLessThan(before, at)
        XCTAssertEqual(at, after)
        XCTAssertEqual(at, local(la, 2026, 2, 19))
    }

    // MARK: Current week

    private func game(_ id: String, week: Int, kickoff: String) -> Game {
        Game(id: id, season: 2026, week: week, kickoff: instant(kickoff), awayTeam: "BUF", homeTeam: "HOU")
    }

    /// a29c18d: a week hands over exactly 24 hours before the next week's
    /// first kickoff (it used to be 36 hours after the last), so the second
    /// before that instant is still the old week and the instant itself is
    /// the new one.
    func testWeekHandsOverOnTheExactSecondOneDayBeforeTheNextKickoff() {
        let games = [
            game("w1", week: 1, kickoff: "2026-09-14T00:20:00Z"),
            game("w2", week: 2, kickoff: "2026-09-18T00:15:00Z"),
        ]
        let handover = instant("2026-09-17T00:15:00Z")
        XCTAssertEqual(GameWeek.current(in: games, now: handover.addingTimeInterval(-1))?.week, 1)
        XCTAssertEqual(GameWeek.current(in: games, now: handover)?.week, 2)
    }

    /// a29c18d: before the opener the first week is current, after the last
    /// game the last one is, with no week-less gap at either end.
    func testWeekIsDefinedBeforeTheOpenerAndAfterTheFinal() {
        let games = [
            game("w1", week: 1, kickoff: "2026-09-10T00:20:00Z"),
            game("w2", week: 2, kickoff: "2026-09-17T00:15:00Z"),
        ]
        XCTAssertEqual(GameWeek.current(in: games, now: .distantPast)?.week, 1)
        XCTAssertEqual(GameWeek.current(in: games, now: .distantFuture)?.week, 2)
        XCTAssertNil(GameWeek.current(in: [], now: instant("2026-09-10T00:00:00Z")))
    }

    // MARK: Game status

    /// c158c48: with no live feed, a game is in progress from kickoff until
    /// four and a half hours later and then awaits a score. The kickoff
    /// instant itself is already in progress.
    func testGameStatusBoundaries() {
        let kickoff = instant("2026-11-01T18:00:00Z")
        let sunday = Game(id: "g", season: 2026, week: 8, kickoff: kickoff, awayTeam: "BUF", homeTeam: "HOU")
        XCTAssertEqual(sunday.status(now: kickoff.addingTimeInterval(-1)), .upcoming)
        XCTAssertEqual(sunday.status(now: kickoff), .inProgress)
        XCTAssertEqual(sunday.status(now: kickoff.addingTimeInterval(4.5 * 3_600 - 1)), .inProgress)
        XCTAssertEqual(sunday.status(now: kickoff.addingTimeInterval(4.5 * 3_600)), .awaitingScore)
    }

    // MARK: Eastern game days

    /// 610ed8e: game dates are Eastern midnight, the league's calendar. Read
    /// in the phone's zone they showed the day before west of New York. The
    /// instants below are what the Eastern reading is, whatever the machine's
    /// zone is: 04:00Z in daylight time, 05:00Z in standard time, including
    /// the two changeover days (midnight comes before the 2 AM switch).
    func testGameDatesParseAsEasternMidnightAcrossDST() {
        XCTAssertEqual(DataFreshness.parseDate("2026-09-10"), instant("2026-09-10T04:00:00Z"))
        XCTAssertEqual(DataFreshness.parseDate("2026-11-01"), instant("2026-11-01T04:00:00Z"))
        XCTAssertEqual(DataFreshness.parseDate("2026-11-02"), instant("2026-11-02T05:00:00Z"))
        XCTAssertEqual(DataFreshness.parseDate("2027-03-14"), instant("2027-03-14T05:00:00Z"))
        XCTAssertEqual(DataFreshness.parseDate("2027-03-15"), instant("2027-03-15T04:00:00Z"))
    }

    /// 610ed8e: Sep 10 reads "Sep 10" however the phone is set.
    func testGameDayLabelNamesTheEasternDay() throws {
        let day = try XCTUnwrap(DataFreshness.parseDate("2026-09-10"))
        let label = day.formatted(DataCoverage.gameDayStyle.locale(Locale(identifier: "en_US")))
        XCTAssertEqual(label, "Sep 10")
    }

    /// 610ed8e: a recent-form window's as_of is a bare date and lands on the
    /// same Eastern midnight.
    func testRecentFormAsOfIsEasternMidnight() throws {
        let json = """
        {"player_id":1,"season":2026,"season_type":"REG","player_type":"qb","window_weeks":3,
         "as_of":"2026-11-01","metrics":{},"prior_metrics":{},"delta":{}}
        """
        let form = try JSONDecoder.statScout.decode(RecentForm.self, from: Data(json.utf8))
        XCTAssertEqual(form.asOf, instant("2026-11-01T04:00:00Z"))
    }

    // MARK: Timestamps

    /// 02a20f41: PostgREST writes updated_at with any number of fractional
    /// digits, and every row used to drop when it was not exactly three.
    func testTimestampsParseWithAnyFractionalPrecision() {
        let expected = instant("2026-07-20T10:00:00Z")
        for raw in [
            "2026-07-20T10:00:00+00:00",
            "2026-07-20T10:00:00Z",
            "2026-07-20T10:00:00.9+00:00",
            "2026-07-20T10:00:00.95+00:00",
            "2026-07-20T10:00:00.954+00:00",
            "2026-07-20T10:00:00.95411+00:00",
            "2026-07-20T10:00:00.954113+00:00",
            "2026-07-20T03:00:00.95411-07:00",
        ] {
            let parsed = DataFreshness.parseDate(raw)
            XCTAssertNotNil(parsed, raw)
            XCTAssertEqual(parsed?.timeIntervalSince1970 ?? 0, expected.timeIntervalSince1970, accuracy: 1, raw)
        }
    }

    // MARK: Ages and weekly deltas

    private func profile(born: String) throws -> PlayerProfile {
        let json = #"[{"player_id":1,"season":2026,"birth_date":"\#(born)"}]"#
        return try XCTUnwrap(JSONDecoder.statScout.decode([PlayerProfile].self, from: Data(json.utf8)).first)
    }

    /// Found by this test: age differenced the UTC-midnight birth date in the
    /// machine's zone, so in Pacific time a player turned a year older at
    /// 5 PM the evening before his birthday. Now compares calendar days.
    func testAgeTurnsOverOnTheBirthdayInTheViewersZone() throws {
        let player = try profile(born: "2000-09-24")
        XCTAssertEqual(player.age(on: local(la, 2026, 9, 23, 16, 59), calendar: la), 25)
        XCTAssertEqual(player.age(on: local(la, 2026, 9, 23, 20, 0), calendar: la), 25)
        XCTAssertEqual(player.age(on: local(la, 2026, 9, 23, 23, 59, 59), calendar: la), 25)
        XCTAssertEqual(player.age(on: local(la, 2026, 9, 24, 0, 0, 0), calendar: la), 26)
        XCTAssertEqual(player.age(on: local(la, 2026, 9, 24, 23, 59, 59), calendar: la), 26)
    }

    /// A leap-day birthday turns a year older on 1 March in a common year and
    /// on 29 February in a leap one.
    func testLeapDayBirthdayAge() throws {
        let player = try profile(born: "2000-02-29")
        XCTAssertEqual(player.age(on: local(la, 2027, 2, 28, 23, 59), calendar: la), 26)
        XCTAssertEqual(player.age(on: local(la, 2027, 3, 1), calendar: la), 27)
        XCTAssertEqual(player.age(on: local(la, 2028, 2, 28), calendar: la), 27)
        XCTAssertEqual(player.age(on: local(la, 2028, 2, 29), calendar: la), 28)
    }

    /// The weekly delta counts a game exactly seven days old and drops one a
    /// second older.
    func testWeeklyDeltaWindowEdge() {
        let now = instant("2026-11-03T12:00:00Z")
        func trend(_ id: String, ageSeconds: TimeInterval, delta: Int) -> GameTrend {
            GameTrend(id: id, date: now.addingTimeInterval(-ageSeconds), opponent: "BUF",
                      summary: "", percentileDelta: delta, keyMetric: "EPA/Play")
        }
        let player = Player(
            playerId: 1, name: "T", team: "KC", position: "QB", handedness: "",
            updatedAt: now, metrics: [], standardStats: [],
            games: [
                trend("edge", ageSeconds: 7 * 86_400, delta: 4),
                trend("just-out", ageSeconds: 7 * 86_400 + 1, delta: 50),
                trend("fresh", ageSeconds: 60, delta: -1),
            ]
        )
        XCTAssertEqual(player.weeklyDelta(asOf: now), 3)
    }

    // MARK: Non-Gregorian device calendars

    private func calendar(_ id: Calendar.Identifier, zone: String = "America/Los_Angeles") -> Calendar {
        var calendar = Calendar(identifier: id)
        calendar.timeZone = TimeZone(identifier: zone)!
        return calendar
    }

    /// Overnight audit 2026-10-30 (F1): a phone set to region Thailand uses the
    /// Buddhist calendar, so the season resolved to 2569 and nothing loaded.
    /// Japanese, Islamic and Persian fell back to the 2025 floor. The NFL
    /// season is a Gregorian rule whatever calendar the viewer uses.
    func testSeasonIsGregorianOnEveryDeviceCalendar() {
        let date = local(la, 2026, 10, 30, 12)
        let ids: [Calendar.Identifier] = [.buddhist, .japanese, .islamicUmmAlQura, .persian, .hebrew, .republicOfChina]
        for id in ids {
            XCTAssertEqual(StatScoutSeason.season(on: date, calendar: calendar(id)), 2026, "\(id)")
        }
        XCTAssertEqual(StatScoutSeason.season(on: local(la, 2027, 8, 31, 23), calendar: calendar(.buddhist)), 2026)
        XCTAssertEqual(StatScoutSeason.season(on: local(la, 2027, 9, 1), calendar: calendar(.japanese)), 2027)
    }

    /// F1: the zone of the device calendar still decides the day.
    func testSeasonKeepsTheDeviceZoneOnANonGregorianCalendar() {
        let instantAfterUTCMidnight = instant("2026-09-01T00:30:00Z")
        XCTAssertEqual(StatScoutSeason.season(on: instantAfterUTCMidnight, calendar: calendar(.buddhist, zone: "UTC")), 2026)
        XCTAssertEqual(StatScoutSeason.season(on: instantAfterUTCMidnight, calendar: calendar(.buddhist)), 2025)
    }

    /// F1: the team Recent window asked for `game_date=gte.2569-...`.
    func testGameLogWindowIsGregorianOnABuddhistCalendar() {
        let now = local(la, 2026, 10, 30, 12)
        let buddhist = StatScoutSeason.gameLogWindowStart(season: 2025, days: 10, now: now, calendar: calendar(.buddhist))
        let gregorian = StatScoutSeason.gameLogWindowStart(season: 2025, days: 10, now: now, calendar: la)
        XCTAssertEqual(buddhist, gregorian)
        XCTAssertEqual(la.component(.year, from: buddhist), 2026)
    }

    /// F1: a 1995 birthday read "574 yrs" on a Buddhist calendar.
    func testAgeIsGregorianOnEveryDeviceCalendar() throws {
        let player = try profile(born: "1995-09-17")
        let date = local(la, 2026, 10, 30, 12)
        for id in [Calendar.Identifier.buddhist, .japanese, .islamicUmmAlQura, .hebrew] {
            XCTAssertEqual(player.age(on: date, calendar: calendar(id)), 31, "\(id)")
        }
    }

    /// F1: fixed-format feed dates parsed into 1483 on a Thai-region phone.
    /// Meaningful under `-testRegion TH` as well as on a Gregorian host.
    func testFeedDayParsesAsGregorianOnAnyRegion() throws {
        var eastern = Calendar(identifier: .gregorian)
        eastern.timeZone = TimeZone(identifier: "America/New_York")!
        let parsed = try XCTUnwrap(DataFreshness.parseDate("2026-10-26"))
        XCTAssertEqual(eastern.dateComponents([.year, .month, .day], from: parsed),
                       DateComponents(year: 2026, month: 10, day: 26))
        print("CALENDAR region=\(Locale.current.identifier) calendar=\(Calendar.current.identifier) current=\(StatScoutSeason.current)")
    }

    /// F1: the shared feed-day formatter cannot fall back to the device's
    /// calendar or locale.
    func testFeedDayFormatterIsGregorianAndPOSIX() {
        let formatter = DateFormatter.feedDay
        XCTAssertEqual(formatter.calendar.identifier, .gregorian)
        XCTAssertEqual(formatter.locale.identifier, "en_US_POSIX")
        XCTAssertEqual(formatter.timeZone.identifier, "America/New_York")
    }

    /// F1: `StatScoutSeason.current` itself, on whatever host runs the suite.
    func testCurrentSeasonIsAGregorianYear() {
        var eastern = Calendar(identifier: .gregorian)
        eastern.timeZone = TimeZone(identifier: "America/New_York")!
        let year = eastern.component(.year, from: .now)
        XCTAssertTrue((year - 1...year).contains(StatScoutSeason.current), "\(StatScoutSeason.current)")
    }
}
