#!/usr/bin/env python3
"""Is each StatScout app serving the games that have already been played?

Every refresh pipeline checks itself, and on 2026-10-12 that was no help: the
baseball workflow never started, so nothing ran to notice the app was missing
the previous night's game. This asks from the outside, the way a user would,
by comparing what each app serves against what the league has finished.

* Baseball: the newest game log against MLB's own schedule.
* Football, hockey, basketball: `data_refresh_status.max_game_date` against the
  newest finished game in the app's `games` table, plus whether the refresh
  chain has probed at all lately (`last_checked_at`).

`~/ios/fleet-probe` (on the iMac, every 4 hours) imports `check_app`, and the ops
monitor turns a stale app into a digest item in Discord #ops. fleet-probe's
`deploy.sh` copies this file in, so edit it here. `--github-issues` is a manual
fallback; the GitHub Actions watchdog that ran it was removed 2026-10-18 so stale
stats alert once.

Stdlib only. Reads only what the apps themselves read, with the public anon key.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import date, datetime, timedelta, timezone

UTC = timezone.utc
USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) stat-freshness/1.0"
MLB_SCHEDULE_URL = "https://statsapi.mlb.com/api/v1/schedule"

# Regular season and every postseason round; spring training and the All-Star
# game never reach the app's tables.
MLB_GAME_TYPES = {"R", "F", "D", "L", "W"}
MLB_FINAL_STATES = {"Final", "Game Over", "Completed Early"}

# A game becomes "expected" once this long has passed since its date began in
# UTC. Baseball's last Trends sweep runs at 20:23 UTC the next day, so 46 hours
# after midnight (22:00 UTC the day after) every attempt has had its chance.
MLB_GRACE = timedelta(hours=46)

# For the chain apps: a game is expected this long after kickoff. nflverse,
# MoneyPuck and hoopR all publish within the night; this leaves a full day of
# retries before anyone is paged.
GAME_GRACE = timedelta(hours=36)

# Every planner probes at least once a day, in season and out.
CHAIN_SILENCE = timedelta(hours=36)

APPS = {
    "baseball": {"name": "Baseball StatScout", "kind": "mlb"},
    "football": {"name": "Football StatScout", "kind": "chain"},
    "hockey": {"name": "Hockey StatScout", "kind": "chain"},
    "basketball": {"name": "Basketball StatScout", "kind": "chain"},
}

# Raised when a check could not run. Callers report it as unknown, not stale.
class CheckUnavailable(RuntimeError):
    pass


def _get_json(url: str, headers: dict | None = None):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, **(headers or {})})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)
    except (urllib.error.URLError, TimeoutError, ValueError) as error:
        raise CheckUnavailable(f"{url.split('?')[0]}: {error}") from error


def _rest(app: dict, table: str, params: dict) -> list:
    query = urllib.parse.urlencode(params)
    url = f"{app['url'].rstrip('/')}/rest/v1/{table}?{query}"
    headers = {"apikey": app["anon_key"], "Authorization": f"Bearer {app['anon_key']}"}
    rows = _get_json(url, headers)
    if not isinstance(rows, list):
        raise CheckUnavailable(f"{table}: unexpected response {str(rows)[:200]}")
    return rows


def _day(value: str | None) -> date | None:
    return date.fromisoformat(value[:10]) if value else None


def _timestamp(value: str | None) -> datetime | None:
    if not value:
        return None
    # The Pro's Python 3.9 only parses 3- or 6-digit fractions; Postgres trims trailing zeros.
    value = re.sub(r"\.(\d{1,6})", lambda m: "." + m.group(1).ljust(6, "0"), value.replace("Z", "+00:00"), count=1)
    return datetime.fromisoformat(value)


def _mlb_season(now: datetime) -> int:
    return now.year if now.month >= 3 else now.year - 1


def _mlb_latest_finished(through: date) -> date | None:
    """Newest date on or before `through` with a finished MLB game, within 10 days."""
    params = urllib.parse.urlencode({
        "sportId": 1,
        "startDate": (through - timedelta(days=10)).isoformat(),
        "endDate": through.isoformat(),
    })
    payload = _get_json(f"{MLB_SCHEDULE_URL}?{params}")
    finished = [
        date.fromisoformat(entry["date"])
        for entry in payload.get("dates", [])
        for game in entry.get("games", [])
        if game.get("gameType") in MLB_GAME_TYPES
        and game.get("status", {}).get("detailedState") in MLB_FINAL_STATES
    ]
    return max(finished, default=None)


def check_mlb(app: dict, now: datetime) -> tuple[bool, str]:
    through = (now - MLB_GRACE).date()
    expected = _mlb_latest_finished(through)
    if expected is None:
        return True, "no MLB games due yet"
    season = _mlb_season(now)
    held = []
    for table in ("player_game_logs", "player_postseason_game_logs"):
        rows = _rest(app, table, {
            "select": "game_date", "season": f"eq.{season}",
            "order": "game_date.desc", "limit": "1",
        })
        held += [_day(r.get("game_date")) for r in rows if r.get("game_date")]
    serving = max(held, default=None)
    if serving is None or serving < expected:
        return False, f"serving games through {serving}, MLB finished games on {expected}"
    return True, f"serving games through {serving}"


def check_chain(app: dict, now: datetime) -> tuple[bool, str]:
    status = _rest(app, "data_refresh_status", {"select": "*", "limit": "1"})
    if not status:
        raise CheckUnavailable("data_refresh_status is empty")
    status = status[0]
    serving = _day(status.get("max_game_date"))
    checked = _timestamp(status.get("last_checked_at"))

    problems = []
    if checked is None or now - checked > CHAIN_SILENCE:
        problems.append(f"the refresh chain last probed at {checked}, over {CHAIN_SILENCE} ago")

    cutoff = (now - GAME_GRACE).strftime("%Y-%m-%dT%H:%M:%SZ")
    games = _rest(app, "games", {
        "select": "game_date,kickoff_at",
        "kickoff_at": f"lt.{cutoff}",
        "away_score": "not.is.null",
        "game_type": "neq.PPD",
        "order": "kickoff_at.desc",
        "limit": "1",
    })
    expected = _day(games[0]["game_date"]) if games else None
    if expected and (serving is None or serving < expected):
        problems.append(f"serving games through {serving}, the newest finished game was {expected}")

    if problems:
        return False, "; ".join(problems)
    return True, f"serving games through {serving}, last probed {checked:%Y-%m-%d %H:%M} UTC"


def check_app(app: dict, now: datetime | None = None) -> tuple[bool, str]:
    """Return (fresh, detail). Raises CheckUnavailable when it can't tell."""
    now = now or datetime.now(UTC)
    if app["kind"] == "mlb":
        return check_mlb(app, now)
    return check_chain(app, now)


def apps_from_env() -> dict[str, dict]:
    """APPS with credentials from <KEY>_SUPABASE_URL / <KEY>_SUPABASE_ANON_KEY."""
    configured = {}
    for key, app in APPS.items():
        url = os.environ.get(f"{key.upper()}_SUPABASE_URL")
        anon = os.environ.get(f"{key.upper()}_SUPABASE_ANON_KEY")
        configured[key] = {**app, "key": key, "url": url, "anon_key": anon}
    return configured


# --------------------------------------------------------------------------
# GitHub issues: one per stale app, opened with an @mention, closed on recovery
# --------------------------------------------------------------------------

def _github(method: str, path: str, body: dict | None = None):
    url = f"https://api.github.com/repos/{os.environ['GITHUB_REPOSITORY']}{path}"
    request = urllib.request.Request(
        url,
        method=method,
        data=json.dumps(body).encode() if body is not None else None,
        headers={
            "Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}",
            "Accept": "application/vnd.github+json",
            "User-Agent": USER_AGENT,
        },
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def _title(app: dict) -> str:
    return f"{app['name']} data is stale"


def sync_issues(results: dict[str, tuple[str, str]], apps: dict[str, dict], mention: str) -> None:
    open_issues = {
        issue["title"]: issue
        for issue in _github("GET", "/issues?state=open&per_page=100")
        if "pull_request" not in issue
    }
    for key, (state, detail) in results.items():
        title = _title(apps[key])
        issue = open_issues.get(title)
        if state == "stale" and issue is None:
            _github("POST", "/issues", {
                "title": title,
                "body": (
                    (f"@{mention} " if mention else "") + f"{apps[key]['name']} is behind: {detail}.\n\n"
                    f"Found by the stat watchdog in {os.environ['GITHUB_REPOSITORY']}. "
                    "This closes itself once the app catches up."
                ),
            })
            print(f"opened: {title}")
        elif state == "fresh" and issue is not None:
            _github("POST", f"/issues/{issue['number']}/comments", {"body": f"Caught up: {detail}."})
            _github("PATCH", f"/issues/{issue['number']}", {"state": "closed"})
            print(f"closed: {title}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--github-issues", action="store_true",
                        help="open or close one issue per app in $GITHUB_REPOSITORY")
    parser.add_argument("--mention", default="", help="GitHub user to @mention (none by default: "
                        "stale data reaches Jack through the ops digest via fleet-probe)")
    parser.add_argument("--simulate-stale", default="", metavar="KEY",
                        help="report this app as stale regardless, to test the alert path")
    args = parser.parse_args()

    apps = apps_from_env()
    results: dict[str, tuple[str, str]] = {}
    for key, app in apps.items():
        if not app["url"] or not app["anon_key"]:
            results[key] = ("unknown", "no Supabase URL or anon key configured")
        else:
            try:
                fresh, detail = check_app(app)
                results[key] = ("fresh" if fresh else "stale", detail)
            except CheckUnavailable as error:
                results[key] = ("unknown", str(error))
        if key == args.simulate_stale:
            results[key] = ("stale", "test alert from a manual run; the next scheduled check closes this")
        print(f"{key}: {results[key][0]}: {results[key][1]}")

    if args.github_issues:
        sync_issues(results, apps, args.mention)

    # Unknown fails the run so a broken watchdog shows up red in Actions rather
    # than reading as all clear. Stale is reported through the issues.
    return 1 if any(state == "unknown" for state, _ in results.values()) else 0


if __name__ == "__main__":
    sys.exit(main())
