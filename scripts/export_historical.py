#!/usr/bin/env python3
"""Export current and historical NFL snapshots from Supabase for the iOS bundle."""

import json
import os
import subprocess
import urllib.request

SUPABASE_URL = os.environ["SUPABASE_URL"]
KEY = os.environ["SUPABASE_ANON_KEY"]
CURRENT_SEASON = int(os.environ.get("STATCAST_SEASON", "2025"))

URL = f"{SUPABASE_URL}/rest/v1/player_snapshots"
HEADERS = {
    "apikey": KEY,
    "Authorization": f"Bearer {KEY}",
    "Accept": "application/json",
    "Prefer": "count=exact",
}


def fetch_all(season_filter: str) -> list[dict]:
    page_size = 1000
    offset = 0
    players: list[dict] = []

    while True:
        request = urllib.request.Request(
            f"{URL}?select=*&season={season_filter}&order=id.asc&limit={page_size}&offset={offset}",
            headers=HEADERS,
        )
        with urllib.request.urlopen(request) as response:
            page = json.loads(response.read())
        players.extend(page)
        print(f"  fetched {len(page)} rows (total {len(players)})")
        if len(page) < page_size:
            return players
        offset += page_size



def add_rate_metrics(players: list[dict]) -> None:
    specs = [
        ("Rushing", "Rush EPA", "EPA/Rush", "Car", "rushing_epa_per_carry"),
        ("Receiving", "Rec EPA", "EPA/Tgt", "Rec/Tgt", "receiving_epa_per_target"),
    ]
    for category, total_label, rate_label, denominator_label, metric_id in specs:
        values: list[tuple[dict, float]] = []
        for player in players:
            total = next((m for m in player.get("metrics", []) if m.get("category") == category and m.get("label") == total_label), None)
            denominator_stat = next((s for s in player.get("standard_stats", []) if s.get("label") == denominator_label), None)
            if not total or not denominator_stat:
                continue
            try:
                numerator = float(str(total["value"]).replace(",", ""))
                denominator_text = str(denominator_stat["value"]).split("/")[-1]
                denominator = float(denominator_text.replace(",", ""))
            except (KeyError, TypeError, ValueError):
                continue
            if denominator > 0:
                values.append((player, numerator / denominator))

        ordered = sorted(values, key=lambda item: item[1])
        count = len(ordered)
        for index, (player, value) in enumerate(ordered):
            percentile = 50 if count == 1 else round(index / (count - 1) * 98 + 1)
            player["metrics"].append({
                "id": f"{category.lower()}-{player['id']}-{metric_id}",
                "label": rate_label,
                "value": f"{value:.2f}",
                "percentile": max(1, min(99, percentile)),
                "category": category,
            })

def export(name: str, season_filter: str) -> None:
    print(f"\nExporting {name} ({season_filter})...")
    players = fetch_all(season_filter)
    if name == "players-current":
        add_rate_metrics(players)
    output = f"StatScout/Data/{name}.json"
    with open(output, "w") as file:
        json.dump(players, file, separators=(",", ":"))

    teams = {player.get("team") for player in players if player.get("team")}
    types = {player.get("player_type") for player in players if player.get("player_type")}
    print(f"Saved {len(players)} rows, {len(teams)} teams, types={sorted(types)}")
    subprocess.run(
        ["swift", "scripts/convert_historical_to_plist.swift", name],
        check=True,
    )


os.makedirs("StatScout/Data", exist_ok=True)
export("players-historical", f"lt.{CURRENT_SEASON}")
export("players-current", f"eq.{CURRENT_SEASON}")
