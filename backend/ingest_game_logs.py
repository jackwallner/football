"""
Per-player-per-game NFL ingest. Powers the Recent Form card (last 7/15/30 day
windows; the iOS layer may reinterpret as last 1/3/5 games) on the player
profile.

Reads weekly ``load_player_stats`` rows (one row per player per game), joins
``load_schedules`` for the game date, and upserts one row per player per game
into Supabase ``public.player_game_logs``.

Incremental by default: starts from the latest game_date already in the DB for
the season. Pass ``--full`` to re-ingest the whole season. ``--season N``
overrides the resolved season.

Env: SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY (same as ingest.py).
"""

import argparse
import logging
import os
import sys
from datetime import datetime, timezone
from typing import Any, Optional

import nflreadpy as nfl
import pandas as pd
import polars as pl
from dotenv import load_dotenv
from supabase import create_client

from ingest import gsis_to_id, player_type_from_position, resolve_season

load_dotenv()

logger = logging.getLogger(__name__)
UTC = timezone.utc

SUPABASE_URL = os.environ.get("SUPABASE_URL", "")
SUPABASE_SERVICE_ROLE_KEY = os.environ.get("SUPABASE_SERVICE_ROLE_KEY", "")

# Per-game metric columns carried into the ``metrics`` jsonb (source -> key).
METRIC_COLS = {
    "passing_yards": "passing_yards",
    "passing_tds": "passing_tds",
    "passing_interceptions": "interceptions",
    "completions": "completions",
    "attempts": "attempts",
    "sacks_suffered": "sacks_suffered",
    "carries": "carries",
    "rushing_yards": "rushing_yards",
    "rushing_tds": "rushing_tds",
    "receptions": "receptions",
    "targets": "targets",
    "receiving_yards": "receiving_yards",
    "receiving_tds": "receiving_tds",
    "receiving_yards_after_catch": "receiving_yac",
    "def_interceptions": "def_interceptions",
    "def_sacks": "def_sacks",
}
EPA_COLS = ["passing_epa", "rushing_epa", "receiving_epa"]


def _to_pandas(frame: Any) -> pd.DataFrame:
    if isinstance(frame, pl.DataFrame):
        return frame.to_pandas()
    return frame


def _num(row: pd.Series, col: str) -> float:
    val = row.get(col)
    try:
        return float(val) if val is not None and not pd.isna(val) else 0.0
    except (ValueError, TypeError):
        return 0.0


def schedule_map(schedule: pd.DataFrame) -> dict[str, str]:
    """Map game_id -> gameday (ISO date string)."""
    out: dict[str, str] = {}
    for _, row in schedule.iterrows():
        gid = row.get("game_id")
        day = row.get("gameday")
        if gid is not None and day is not None and not pd.isna(day):
            out[str(gid)] = str(day)[:10]
    return out


def build_game_log_rows(
    weekly: pd.DataFrame,
    sched: dict[str, str],
    season: int,
    now: datetime,
) -> list[dict]:
    """Build one player_game_logs row per player per game (pure)."""
    df = weekly[weekly["season"] == season].copy()
    if df.empty:
        return []

    now_str = now.isoformat()
    rows: list[dict] = []

    def defensive_involvement(r: pd.Series) -> float:
        return (
            _num(r, "def_tackles_solo") + _num(r, "def_tackle_assists")
            + _num(r, "def_sacks") + _num(r, "def_interceptions")
            + _num(r, "def_pass_defended") + _num(r, "def_fumbles_forced")
        )

    for _, r in df.iterrows():
        pid = gsis_to_id(r.get("player_id"))
        if pid is None:
            continue
        game_date = sched.get(str(r.get("game_id")))
        if not game_date:
            continue

        plays = int(_num(r, "attempts") + _num(r, "carries") + _num(r, "targets"))
        touches = int(_num(r, "completions") + _num(r, "carries") + _num(r, "receptions"))
        player_type = player_type_from_position(r.get("position"), r.get("position_group"))

        if plays == 0 and defensive_involvement(r) == 0:
            continue

        metrics: dict[str, Any] = {}
        for src, key in METRIC_COLS.items():
            if src in r:
                metrics[key] = round(_num(r, src), 2)
        epa_total = sum(_num(r, c) for c in EPA_COLS if c in r)
        metrics["epa_total"] = round(epa_total, 3)

        rows.append({
            "player_id": pid,
            "season": season,
            "game_date": game_date,
            "player_type": player_type or "def",
            "team": str(r.get("team") or ""),
            "opponent": str(r.get("opponent_team") or ""),
            "plays": plays,
            "touches": touches,
            "metrics": metrics,
            "updated_at": now_str,
        })

    return rows


def _latest_game_date(client, season: int) -> Optional[str]:
    resp = (
        client.table("player_game_logs")
        .select("game_date")
        .eq("season", season)
        .order("game_date", desc=True)
        .limit(1)
        .execute()
    )
    if not resp.data:
        return None
    return str(resp.data[0]["game_date"])[:10]


def _upsert(client, rows: list[dict]) -> None:
    batch_size = 200
    for i in range(0, len(rows), batch_size):
        batch = rows[i:i + batch_size]
        try:
            client.table("player_game_logs").upsert(
                batch,
                on_conflict="player_id,season,game_date,player_type",
            ).execute()
        except Exception:
            logger.exception("Upsert failed for batch starting at %d", i)
            raise


def run(full: bool = False, cli_season: Optional[int] = None) -> None:
    url = SUPABASE_URL or os.environ.get("SUPABASE_URL", "")
    key = SUPABASE_SERVICE_ROLE_KEY or os.environ.get("SUPABASE_SERVICE_ROLE_KEY", "")
    if not url or not key:
        logger.error("Missing Supabase URL or service role key.")
        sys.exit(1)

    client = create_client(url, key)
    season = resolve_season(cli_season)
    now = datetime.now(UTC)

    logger.info("Loading weekly stats + schedule for %s...", season)
    weekly = _to_pandas(nfl.load_player_stats([season]))
    sched = schedule_map(_to_pandas(nfl.load_schedules([season])))

    rows = build_game_log_rows(weekly, sched, season, now)
    logger.info("Built %d game-log rows for %s", len(rows), season)

    if not full:
        latest = _latest_game_date(client, season)
        if latest:
            # Re-ingest the latest known day too (late/updated games).
            before = len(rows)
            rows = [r for r in rows if r["game_date"] >= latest]
            logger.info("Incremental: keeping %d/%d rows on/after %s", len(rows), before, latest)

    if not rows:
        logger.info("Nothing to ingest for %s.", season)
        return

    _upsert(client, rows)
    logger.info("Done. Upserted %d game-log rows for %s.", len(rows), season)


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--full", action="store_true", help="Re-ingest the whole season, not incremental.")
    parser.add_argument("--season", type=int, default=None, help="Season (starting year) to ingest.")
    return parser.parse_args()


if __name__ == "__main__":
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )
    args = _parse_args()
    run(full=args.full, cli_season=args.season)
