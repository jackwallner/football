#!/usr/bin/env python3
"""Export current and historical NFL snapshots from Supabase for the iOS bundle."""

import json
import os
import subprocess
import urllib.parse
import urllib.request
from collections import Counter

SUPABASE_URL = os.environ["SUPABASE_URL"]
KEY = os.environ["SUPABASE_ANON_KEY"]
CURRENT_SEASON = int(os.environ.get("STATCAST_SEASON", "2025"))
OLDEST_SUPPORTED_SEASON = 2015
REQUIRED_TYPES = {"qb", "rb", "wr", "te", "def"}

URL = f"{SUPABASE_URL}/rest/v1/player_snapshots"
HEADERS = {
    "apikey": KEY,
    "Authorization": f"Bearer {KEY}",
    "Accept": "application/json",
    "Prefer": "count=exact",
}


def fetch_all(query_filters: list[tuple[str, str]]) -> list[dict]:
    page_size = 1000
    offset = 0
    players: list[dict] = []

    while True:
        query = urllib.parse.urlencode([
            ("select", "*"),
            *query_filters,
            ("order", "season.asc,id.asc"),
            ("limit", str(page_size)),
            ("offset", str(offset)),
        ])
        request = urllib.request.Request(
            f"{URL}?{query}",
            headers=HEADERS,
        )
        with urllib.request.urlopen(request) as response:
            page = json.loads(response.read())
        players.extend(page)
        print(f"  fetched {len(page)} rows (total {len(players)})")
        if len(page) < page_size:
            return players
        offset += page_size


def validate_export(
    players: list[dict],
    expected_seasons: set[int],
    require_rate_metrics: bool,
) -> None:
    seasons = {player.get("season") for player in players}
    if seasons != expected_seasons:
        raise RuntimeError(f"Unexpected seasons: {sorted(seasons, key=str)}")

    keys = [(player.get("id"), player.get("season")) for player in players]
    if len(keys) != len(set(keys)):
        raise RuntimeError("Duplicate player-season keys in export")

    for season in sorted(expected_seasons):
        season_players = [player for player in players if player.get("season") == season]
        teams = {player.get("team") for player in season_players if player.get("team")}
        types = {str(player.get("player_type") or "").lower() for player in season_players}
        missing_types = REQUIRED_TYPES - types
        if len(teams) < 30 or missing_types:
            raise RuntimeError(
                f"Incomplete {season}: {len(teams)} teams, missing types={sorted(missing_types)}"
            )
        if any(not player.get("metrics") for player in season_players):
            raise RuntimeError(f"Season {season} contains rows without metrics")

    if require_rate_metrics:
        labels = {
            metric.get("label")
            for player in players
            for metric in player.get("metrics", [])
        }
        missing_rates = {"EPA/Play", "EPA/Rush", "EPA/Tgt"} - labels
        if missing_rates:
            raise RuntimeError(f"Missing current rate metrics: {sorted(missing_rates)}")


def export(
    name: str,
    query_filters: list[tuple[str, str]],
    expected_seasons: set[int],
    require_rate_metrics: bool = False,
) -> None:
    filters_description = ", ".join(f"{name}={value}" for name, value in query_filters)
    print(f"\nExporting {name} ({filters_description})...")
    players = fetch_all(query_filters)
    validate_export(players, expected_seasons, require_rate_metrics)
    output = f"StatScout/Data/{name}.json"
    with open(output, "w") as file:
        json.dump(players, file, separators=(",", ":"))

    teams = {player.get("team") for player in players if player.get("team")}
    types = Counter(player.get("player_type") for player in players if player.get("player_type"))
    print(f"Saved {len(players)} rows, {len(teams)} teams, types={dict(sorted(types.items()))}")
    subprocess.run(
        ["swift", "scripts/convert_historical_to_plist.swift", name],
        check=True,
    )


os.makedirs("StatScout/Data", exist_ok=True)
export(
    "players-historical",
    [("and", f"(season.gte.{OLDEST_SUPPORTED_SEASON},season.lt.{CURRENT_SEASON})")],
    set(range(OLDEST_SUPPORTED_SEASON, CURRENT_SEASON)),
)
export(
    "players-current",
    [("season", f"eq.{CURRENT_SEASON}")],
    {CURRENT_SEASON},
    require_rate_metrics=True,
)
