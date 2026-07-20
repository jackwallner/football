"""
NFL season-snapshot ingest.

Builds one ``player_snapshots`` row per player per season from the nflverse
data mirror (via ``nflreadpy``), computing within-category percentiles among
qualified players. Powers the iOS player-percentile screens.

Pipeline (all REG-season only):
  1. Aggregate weekly ``load_player_stats`` rows to season totals.
  2. Derive rate stats (cmp%, Y/A, sack%, explosive-rush%, ...).
  3. Merge season-level Next Gen Stats (CPOE, time-to-throw, separation, ...).
  4. Rank each metric within (season, category) among qualified players.
  5. Upsert to Supabase ``player_snapshots`` on_conflict=(id, season).

Env: SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY. STATCAST_SEASON overrides the
season; ``--season N`` overrides both.
"""

import argparse
import logging
import os
import sys
from datetime import datetime, timezone
from typing import Any, Iterator, Optional

import nflreadpy as nfl
import numpy as np
import pandas as pd
import polars as pl
from dotenv import load_dotenv
from supabase import create_client

load_dotenv()

UTC = timezone.utc
logger = logging.getLogger(__name__)

SUPABASE_URL = os.environ.get("SUPABASE_URL", "")
SUPABASE_SERVICE_ROLE_KEY = os.environ.get("SUPABASE_SERVICE_ROLE_KEY", "")

_now = datetime.now(UTC)
DEFAULT_SEASON = _now.year if _now.month >= 9 else _now.year - 1
MIN_SEASON = 1999
SOURCE = "nflverse"

# Qualification thresholds per metric category (see NFL_CONTRACT.md).
QUAL_ATTEMPTS = 150   # Passing
QUAL_CARRIES = 80     # Rushing
QUAL_TARGETS = 40     # Receiving
QUAL_GAMES = 8        # Defense (>= 8 games; the contract's OR-branch, snaps not joined)

# Weekly counting stats summed to season totals.
SUM_COLS = [
    "completions", "attempts", "passing_yards", "passing_tds",
    "passing_interceptions", "sacks_suffered", "passing_air_yards",
    "passing_first_downs", "passing_epa", "rushing_10",
    "carries", "rushing_yards", "rushing_tds", "rushing_first_downs",
    "rushing_epa", "rushing_fumbles",
    "receptions", "targets", "receiving_yards", "receiving_tds",
    "receiving_air_yards", "receiving_yards_after_catch",
    "receiving_first_downs", "receiving_epa",
    "def_tackles_solo", "def_tackle_assists", "def_sacks",
    "def_interceptions", "def_pass_defended", "def_fumbles_forced",
    "def_tackles_for_loss", "def_qb_hits",
]
# Weekly rate stats averaged across games.
MEAN_COLS = ["target_share", "air_yards_share"]

# Metric catalog: category -> list of (id, label, agg_col, fmt, inverted).
# ``inverted`` = lower raw value ranks higher (e.g. turnovers, sacks taken).
METRIC_DEFS: dict[str, list[tuple[str, str, str, str, bool]]] = {
    "Passing": [
        ("pass_yards", "Pass Yds", "passing_yards", "comma", False),
        ("pass_tds", "Pass TD", "passing_tds", "int", False),
        ("cmp_pct", "Cmp%", "cmp_pct", "pct1", False),
        ("ypa", "Y/A", "ypa", "dec1", False),
        ("int_rate", "INT%", "int_rate", "pct1", True),
        ("passer_rating", "Rating", "passer_rating", "dec1", False),
        ("passing_epa", "EPA/Play", "passing_epa_per_play", "dec2", False),
        ("cpoe", "CPOE", "cpoe", "signed1", False),
        ("avg_time_to_throw", "Time to Throw", "avg_time_to_throw", "dec2", False),
        ("aggressiveness", "Aggressiveness", "aggressiveness", "pct1", False),
        ("avg_intended_air_yards", "Intended Air Yds", "avg_intended_air_yards", "dec1", False),
        ("sack_rate", "Sack%", "sack_rate", "pct1", True),
    ],
    "Rushing": [
        ("rush_yards", "Rush Yds", "rushing_yards", "comma", False),
        ("rush_tds", "Rush TD", "rushing_tds", "int", False),
        ("ypc", "Y/C", "ypc", "dec1", False),
        ("rushing_epa", "Rush EPA", "rushing_epa", "dec1", False),
        ("rush_first_downs", "Rush 1D", "rushing_first_downs", "int", False),
        ("explosive_rush_rate", "Explosive%", "explosive_rush_rate", "pct1", False),
        ("fumble_rate", "Fumble%", "fumble_rate", "pct1", True),
        ("rush_yoe", "RYOE", "rush_yoe", "dec1", False),
    ],
    "Receiving": [
        ("receptions", "Rec", "receptions", "int", False),
        ("rec_yards", "Rec Yds", "receiving_yards", "comma", False),
        ("rec_tds", "Rec TD", "receiving_tds", "int", False),
        ("yac", "YAC", "rec_yac", "comma", False),
        ("target_share", "Target Share", "target_share_pct", "pct1", False),
        ("wopr", "WOPR", "wopr", "dec2", False),
        ("racr", "RACR", "racr", "dec2", False),
        ("receiving_epa", "Rec EPA", "receiving_epa", "dec1", False),
        ("catch_pct", "Catch%", "catch_pct", "pct1", False),
        ("avg_separation", "Separation", "avg_separation", "dec1", False),
        ("avg_yac_above_expectation", "YAC+", "avg_yac_above_expectation", "signed1", False),
    ],
    "Defense": [
        ("tackles", "Tackles", "tackles", "int", False),
        ("sacks", "Sacks", "sacks", "dec1", False),
        ("def_ints", "INT", "def_ints", "int", False),
        ("passes_defended", "PD", "passes_defended", "int", False),
        ("forced_fumbles", "FF", "forced_fumbles", "int", False),
        ("tfl", "TFL", "tfl", "int", False),
        ("qb_hits", "QB Hits", "qb_hits", "int", False),
    ],
}

POSITION_GROUP_TO_TYPE = {
    "QB": "qb",
    "RB": "rb",
    "WR": "wr",
    "TE": "te",
    "DB": "def",
    "DL": "def",
    "LB": "def",
}


# --------------------------------------------------------------------------- #
# Pure helpers (unit-tested; no network)
# --------------------------------------------------------------------------- #
def resolve_season(cli_season: Optional[int] = None) -> int:
    """Resolve the season from CLI arg, then STATCAST_SEASON env, then default."""
    if cli_season is not None:
        candidate: Optional[int] = cli_season
    else:
        raw = os.environ.get("STATCAST_SEASON")
        if raw is None or raw == "":
            return DEFAULT_SEASON
        try:
            candidate = int(raw)
        except ValueError:
            return DEFAULT_SEASON
    if candidate is None or candidate < MIN_SEASON or candidate > DEFAULT_SEASON:
        return DEFAULT_SEASON
    return candidate


def gsis_to_id(gsis: Any) -> Optional[int]:
    """Convert an nflverse GSIS id ("00-0034796") to the DB bigint (34796)."""
    if gsis is None or (isinstance(gsis, float) and pd.isna(gsis)):
        return None
    text = str(gsis).strip()
    if not text:
        return None
    tail = text.split("-")[-1]
    try:
        return int(tail)
    except ValueError:
        return None


def player_type_from_position(position: Any, position_group: Any) -> str:
    """Map nflverse position/position_group to a contract player_type."""
    pos = str(position or "").strip().upper()
    if pos == "K":
        return "k"
    group = str(position_group or "").strip().upper()
    return POSITION_GROUP_TO_TYPE.get(group, "def" if group in {"DB", "DL", "LB"} else "")


def format_value(value: Any, fmt: str) -> str:
    """Format a raw stat value for display per the contract conventions."""
    if value is None or (isinstance(value, float) and pd.isna(value)):
        return ""
    try:
        v = float(value)
    except (ValueError, TypeError):
        return ""
    if fmt == "comma":
        return f"{int(round(v)):,}"
    if fmt == "int":
        return str(int(round(v)))
    if fmt == "pct1":
        return f"{v:.1f}%"
    if fmt == "dec1":
        return f"{v:.1f}"
    if fmt == "dec2":
        return f"{v:.2f}"
    if fmt == "signed1":
        return f"{v:+.1f}"
    return str(v)


def passer_rating(cmp_: Any, att: Any, yds: Any, td: Any, ints: Any) -> Optional[float]:
    """Standard NFL passer rating (0-158.3) from season totals."""
    try:
        att = float(att)
        if att <= 0:
            return None
        a = min(max(((float(cmp_) / att) - 0.3) * 5, 0.0), 2.375)
        b = min(max(((float(yds) / att) - 3) * 0.25, 0.0), 2.375)
        c = min(max((float(td) / att) * 20, 0.0), 2.375)
        d = min(max(2.375 - (float(ints) / att) * 25, 0.0), 2.375)
    except (ValueError, TypeError):
        return None
    return round((a + b + c + d) / 6 * 100, 1)


def rank_percentiles(series: pd.Series, inverted: bool) -> dict[int, int]:
    """Percentile (1-100) of each non-null value within the series.

    ``inverted`` ranks lower raw values higher (turnovers, sacks taken).
    """
    s = pd.to_numeric(series, errors="coerce").dropna()
    if s.empty:
        return {}
    ranks = s.rank(method="average", ascending=not inverted, pct=True)
    return {int(pid): max(1, min(100, int(round(pct * 100)))) for pid, pct in ranks.items()}


def qualifies(row: Any, category: str, player_type: str) -> bool:
    """Whether a player clears the qualification threshold for a category."""
    def _num(col: str) -> float:
        val = row.get(col)
        try:
            return float(val) if val is not None and not pd.isna(val) else 0.0
        except (ValueError, TypeError):
            return 0.0

    if category == "Passing":
        return _num("attempts") >= QUAL_ATTEMPTS
    if category == "Rushing":
        return _num("carries") >= QUAL_CARRIES
    if category == "Receiving":
        return _num("targets") >= QUAL_TARGETS
    if category == "Defense":
        return player_type == "def" and _num("games") >= QUAL_GAMES
    return False


def _safe_div(numer: pd.Series, denom: pd.Series) -> pd.Series:
    return numer / denom.replace(0, np.nan)


def aggregate_seasons(weekly: pd.DataFrame, season: int) -> pd.DataFrame:
    """Aggregate weekly REG rows to one season-total row per player (indexed by id).

    Pure: takes a DataFrame, returns a DataFrame with all counting totals,
    derived rate columns, and identity columns. No NGS, no network.
    """
    df = weekly[(weekly["season"] == season) & (weekly["season_type"] == "REG")].copy()
    if df.empty:
        return pd.DataFrame()

    df["pid"] = df["player_id"].map(gsis_to_id)
    df = df[df["pid"].notna()].copy()
    df["pid"] = df["pid"].astype(int)

    present_sum = [c for c in SUM_COLS if c in df.columns]
    present_mean = [c for c in MEAN_COLS if c in df.columns]

    sums = df.groupby("pid")[present_sum].sum(min_count=1)
    means = df.groupby("pid")[present_mean].mean() if present_mean else pd.DataFrame(index=sums.index)
    games = df.groupby("pid").size().rename("games")

    # Identity from the player's most recent (max week) row.
    latest = df.sort_values("week").groupby("pid").tail(1).set_index("pid")
    identity = latest[["player_display_name", "team", "position", "position_group"]]

    agg = sums.join(means).join(games).join(identity)

    agg["name"] = agg["player_display_name"].astype(str)
    agg["player_type"] = [
        player_type_from_position(p, g)
        for p, g in zip(agg["position"], agg["position_group"])
    ]

    # Passing derived rates.
    agg["cmp_pct"] = _safe_div(agg["completions"], agg["attempts"]) * 100
    agg["ypa"] = _safe_div(agg["passing_yards"], agg["attempts"])
    agg["int_rate"] = _safe_div(agg["passing_interceptions"], agg["attempts"]) * 100
    agg["sack_rate"] = _safe_div(agg["sacks_suffered"], agg["attempts"] + agg["sacks_suffered"]) * 100
    agg["passing_epa_per_play"] = _safe_div(agg["passing_epa"], agg["attempts"])
    agg["passer_rating"] = [
        passer_rating(c, a, y, t, i)
        for c, a, y, t, i in zip(
            agg["completions"], agg["attempts"], agg["passing_yards"],
            agg["passing_tds"], agg["passing_interceptions"],
        )
    ]

    # Rushing derived rates.
    agg["ypc"] = _safe_div(agg["rushing_yards"], agg["carries"])
    agg["explosive_rush_rate"] = _safe_div(agg["rushing_10"], agg["carries"]) * 100
    agg["fumble_rate"] = _safe_div(agg["rushing_fumbles"], agg["carries"]) * 100

    # Receiving derived rates.
    agg["catch_pct"] = _safe_div(agg["receptions"], agg["targets"]) * 100
    agg["racr"] = _safe_div(agg["receiving_yards"], agg["receiving_air_yards"])
    agg["rec_yac"] = agg["receiving_yards_after_catch"]
    if "target_share" in agg.columns:
        agg["target_share_pct"] = agg["target_share"] * 100
        agg["wopr"] = 1.5 * agg["target_share"] + 0.7 * agg.get("air_yards_share", 0)
    else:
        agg["target_share_pct"] = np.nan
        agg["wopr"] = np.nan

    # Defense aliases.
    agg["tackles"] = agg["def_tackles_solo"].fillna(0) + agg["def_tackle_assists"].fillna(0)
    agg["sacks"] = agg["def_sacks"]
    agg["def_ints"] = agg["def_interceptions"]
    agg["passes_defended"] = agg["def_pass_defended"]
    agg["forced_fumbles"] = agg["def_fumbles_forced"]
    agg["tfl"] = agg["def_tackles_for_loss"]
    agg["qb_hits"] = agg["def_qb_hits"]

    return agg


def merge_ngs(
    agg: pd.DataFrame,
    ngs_passing: pd.DataFrame,
    ngs_rushing: pd.DataFrame,
    ngs_receiving: pd.DataFrame,
    season: int,
) -> pd.DataFrame:
    """Join season-level (week 0, REG) Next Gen Stats columns onto ``agg``.

    Pure: NGS DataFrames in, augmented ``agg`` out.
    """
    def _season_level(ngs: pd.DataFrame, cols: dict[str, str]) -> pd.DataFrame:
        if ngs is None or ngs.empty:
            return pd.DataFrame(columns=list(cols.values()))
        d = ngs[
            (ngs["season"] == season)
            & (ngs["season_type"] == "REG")
            & (ngs["week"] == 0)
        ].copy()
        if d.empty:
            return pd.DataFrame(columns=list(cols.values()))
        d["pid"] = d["player_gsis_id"].map(gsis_to_id)
        d = d[d["pid"].notna()].copy()
        d["pid"] = d["pid"].astype(int)
        d = d[~d["pid"].duplicated(keep="first")].set_index("pid")
        present = {src: dst for src, dst in cols.items() if src in d.columns}
        return d[list(present.keys())].rename(columns=present)

    pass_ngs = _season_level(ngs_passing, {
        "completion_percentage_above_expectation": "cpoe",
        "avg_time_to_throw": "avg_time_to_throw",
        "aggressiveness": "aggressiveness",
        "avg_intended_air_yards": "avg_intended_air_yards",
    })
    rush_ngs = _season_level(ngs_rushing, {
        "rush_yards_over_expected": "rush_yoe",
    })
    rec_ngs = _season_level(ngs_receiving, {
        "avg_separation": "avg_separation",
        "avg_yac_above_expectation": "avg_yac_above_expectation",
    })

    for extra in (pass_ngs, rush_ngs, rec_ngs):
        if not extra.empty:
            agg = agg.join(extra, how="left")
    return agg


def build_standard_stats(row: Any) -> list[dict[str, str]]:
    """Assemble the standard_stats jsonb array from an aggregated row."""
    def n(col: str) -> float:
        val = row.get(col)
        try:
            return float(val) if val is not None and not pd.isna(val) else 0.0
        except (ValueError, TypeError):
            return 0.0

    stats: list[dict[str, str]] = []

    def add(label: str, value: str) -> None:
        stats.append({"id": f"std-{label}", "label": label, "value": value})

    add("G", str(int(n("games"))))

    if n("attempts") > 0:
        add("Cmp/Att", f"{int(n('completions'))}/{int(n('attempts'))}")
        add("Pass Yds", f"{int(n('passing_yards')):,}")
        add("Pass TD", str(int(n("passing_tds"))))
        add("INT", str(int(n("passing_interceptions"))))
    if n("carries") > 0:
        add("Car", str(int(n("carries"))))
        add("Rush Yds", f"{int(n('rushing_yards')):,}")
        add("Rush TD", str(int(n("rushing_tds"))))
    if n("targets") > 0:
        add("Rec/Tgt", f"{int(n('receptions'))}/{int(n('targets'))}")
        add("Rec Yds", f"{int(n('receiving_yards')):,}")
        add("Rec TD", str(int(n("receiving_tds"))))
    if row.get("player_type") == "def":
        add("Tackles", str(int(n("tackles"))))
        add("Sacks", f"{n('sacks'):.1f}")
        add("Def INT", str(int(n("def_ints"))))

    return stats


def build_snapshot_rows(agg: pd.DataFrame, season: int, now: datetime) -> list[dict]:
    """Build player_snapshots rows from an aggregated (id-indexed) DataFrame.

    Percentiles are computed per (category) among qualified players only, and
    a player receives every category's metrics for which they qualify.
    """
    if agg.empty:
        return []

    now_str = now.isoformat()
    players: dict[int, dict] = {}

    def _ensure(pid: int, row: Any) -> dict:
        if pid not in players:
            players[pid] = {
                "id": pid,
                "name": str(row.get("name") or ""),
                "team": str(row.get("team") or "TBD"),
                "position": str(row.get("position") or ""),
                "handedness": "",
                "image_url": row.get("image_url") if pd.notna(row.get("image_url")) else None,
                "player_type": row.get("player_type") or "",
                "season": season,
                "source": SOURCE,
                "metrics": [],
                "standard_stats": build_standard_stats(row),
                "games": [],
                "updated_at": now_str,
            }
        return players[pid]

    for category, defs in METRIC_DEFS.items():
        qual_ids = [
            int(pid) for pid, row in agg.iterrows()
            if qualifies(row, category, str(row.get("player_type") or ""))
        ]
        if not qual_ids:
            continue
        sub = agg.loc[qual_ids]

        pct_maps: dict[str, dict[int, int]] = {}
        for mid, _label, col, _fmt, inverted in defs:
            if col in sub.columns:
                pct_maps[mid] = rank_percentiles(sub[col], inverted)

        for pid in qual_ids:
            row = agg.loc[pid]
            player = _ensure(pid, row)
            for mid, label, col, fmt, _inverted in defs:
                if col not in agg.columns:
                    continue
                raw = row.get(col)
                if raw is None or pd.isna(raw):
                    continue
                percentile = pct_maps.get(mid, {}).get(pid)
                if percentile is None:
                    continue
                player["metrics"].append({
                    "id": f"{category.lower()}-{pid}-{mid}",
                    "label": label,
                    "value": format_value(raw, fmt),
                    "percentile": percentile,
                    "category": category,
                })

    snapshots = [p for p in players.values() if p["metrics"]]
    return snapshots


# --------------------------------------------------------------------------- #
# Network loaders
# --------------------------------------------------------------------------- #
def _to_pandas(frame: Any) -> pd.DataFrame:
    if isinstance(frame, pl.DataFrame):
        return frame.to_pandas()
    return frame


def load_headshots() -> dict[int, str]:
    """Map DB player id -> headshot URL from load_players()."""
    players = _to_pandas(nfl.load_players())
    lookup: dict[int, str] = {}
    for _, row in players.iterrows():
        pid = gsis_to_id(row.get("gsis_id"))
        url = row.get("headshot")
        if pid is not None and isinstance(url, str) and url:
            lookup[pid] = url
    logger.info("Loaded %d headshots", len(lookup))
    return lookup


def build_agg_for_season(season: int) -> pd.DataFrame:
    """Fetch nflverse data and produce the fully-merged aggregate DataFrame."""
    logger.info("Loading weekly player stats for %s...", season)
    weekly = _to_pandas(nfl.load_player_stats([season]))
    logger.info("Weekly rows: %d", len(weekly))

    agg = aggregate_seasons(weekly, season)
    if agg.empty:
        return agg
    logger.info("Aggregated to %d players", len(agg))

    logger.info("Loading Next Gen Stats...")
    ngs_pass = _to_pandas(nfl.load_nextgen_stats(stat_type="passing"))
    ngs_rush = _to_pandas(nfl.load_nextgen_stats(stat_type="rushing"))
    ngs_rec = _to_pandas(nfl.load_nextgen_stats(stat_type="receiving"))
    agg = merge_ngs(agg, ngs_pass, ngs_rush, ngs_rec, season)

    headshots = load_headshots()
    agg["image_url"] = [headshots.get(int(pid)) for pid in agg.index]

    return agg


def chunks(lst: list, n: int) -> Iterator[list]:
    for i in range(0, len(lst), n):
        yield lst[i:i + n]


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--season", type=int, default=None, help="Season (starting year) to ingest.")
    args = parser.parse_args()

    url = SUPABASE_URL or os.environ.get("SUPABASE_URL", "")
    key = SUPABASE_SERVICE_ROLE_KEY or os.environ.get("SUPABASE_SERVICE_ROLE_KEY", "")
    if not url or not key:
        logger.error("Missing Supabase URL or service role key.")
        sys.exit(1)

    client = create_client(url, key)
    season = resolve_season(args.season)
    now = datetime.now(UTC)

    logger.info("=== Ingesting NFL season %s ===", season)
    try:
        agg = build_agg_for_season(season)
        rows = build_snapshot_rows(agg, season, now)
        if not rows:
            logger.error("No rows to upsert for %s.", season)
            sys.exit(1)

        by_type: dict[str, int] = {}
        for r in rows:
            by_type[r["player_type"]] = by_type.get(r["player_type"], 0) + 1
        logger.info("Built %d snapshots by type: %s", len(rows), by_type)

        batch_size = 150
        for i, batch in enumerate(chunks(rows, batch_size)):
            logger.info("Upserting batch %d (%d rows) for %s...", i + 1, len(batch), season)
            client.table("player_snapshots").upsert(batch, on_conflict="id,season").execute()

        logger.info("Upserted %d player snapshots for %s.", len(rows), season)

        # Prune rows no longer qualified, guarded by a sanity floor.
        if len(rows) >= 150:
            kept = {r["id"] for r in rows}
            existing = (
                client.table("player_snapshots")
                .select("id")
                .eq("season", season)
                .execute()
                .data
            )
            orphans = [r["id"] for r in existing if r["id"] not in kept]
            for batch in chunks(orphans, 100):
                client.table("player_snapshots").delete().in_("id", batch).eq("season", season).execute()
            logger.info("Pruned %d stale/unqualified rows for %s.", len(orphans), season)
        else:
            logger.warning("Only %d rows built — skipping prune (sanity floor).", len(rows))
    except Exception:
        logger.exception("Failed to process season %s", season)
        sys.exit(1)


if __name__ == "__main__":
    main()
