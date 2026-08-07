"""Drop per-game history for every season but the newest one.

`player_game_logs` and the `player_recent_form` rollup built from it were the
two biggest tables in the database by a wide margin - 245 MB and 112 MB against
74 MB for every season snapshot ever ingested - because they hold one row per
player per game rather than one per player per season. All of that weight was
paying for a feature the app now offers on the live season only: a "last 3
games" board for 2017 is a curiosity, and nobody was opening it.

Season snapshots are never touched. Those are the whole app (percentiles,
leaderboards, Compare, the All Time rollup) and every year of them together is
smaller than one year of game logs.

The cut is "older than the newest season present in the table", not a calendar
date, and that is what makes the September rollover safe: last season's rows
survive until the first game of the new one has actually landed, so Trends is
never anchored to a season the database has no rows for. It also means running
this twice is a no-op.

Deleting is cheap to undo if a historical window is ever wanted again: game
logs re-ingest from nflverse with
`python backend/ingest_game_logs.py --season N --full`, and the rollup rebuilds
with `python backend/rollup_recent_form.py --season N`.

Note that DELETE alone does not shrink the database on disk - it marks rows
dead and leaves the space for reuse. Reclaiming it for real needs a
`VACUUM FULL` over psql, which is a one-off after the first big purge; the
nightly deletes are a season at a time and autovacuum keeps up with those.

Env: SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY (same as ingest.py).
"""

import argparse
import logging
import os
import sys
from typing import Optional

from dotenv import load_dotenv
from supabase import create_client

load_dotenv()

logger = logging.getLogger(__name__)

SUPABASE_URL = os.environ.get("SUPABASE_URL", "")
SUPABASE_SERVICE_ROLE_KEY = os.environ.get("SUPABASE_SERVICE_ROLE_KEY", "")

# Both tables are pruned to the same season so a window and the logs behind it
# can never disagree about which years exist.
TABLES = ("player_game_logs", "player_recent_form")


def newest_season(client, table: str) -> Optional[int]:
    """The highest season present, or None for an empty table."""
    response = (
        client.table(table)
        .select("season")
        .order("season", desc=True)
        .limit(1)
        .execute()
    )
    rows = response.data or []
    return int(rows[0]["season"]) if rows else None


def count_older_than(client, table: str, season: int) -> int:
    response = (
        client.table(table)
        .select("season", count="exact")
        .lt("season", season)
        .limit(1)
        .execute()
    )
    return response.count or 0


def prune(client, table: str, dry_run: bool) -> int:
    keep = newest_season(client, table)
    if keep is None:
        logger.info("%s is empty; nothing to prune.", table)
        return 0

    stale = count_older_than(client, table, keep)
    if stale == 0:
        logger.info("%s: already at %d only.", table, keep)
        return 0

    if dry_run:
        logger.info("%s: would delete %d rows older than %d.", table, stale, keep)
        return stale

    logger.info("%s: deleting %d rows older than %d...", table, stale, keep)
    client.table(table).delete().lt("season", keep).execute()
    remaining = count_older_than(client, table, keep)
    if remaining:
        # PostgREST caps a single delete; loop until the tail is gone rather
        # than reporting success over rows that are still there.
        logger.info("%s: %d rows left, continuing...", table, remaining)
        while remaining:
            client.table(table).delete().lt("season", keep).execute()
            after = count_older_than(client, table, keep)
            if after == remaining:
                raise RuntimeError(
                    f"{table}: delete stopped making progress with {after} rows left"
                )
            remaining = after
    logger.info("%s: pruned to %d.", table, keep)
    return stale


def run(dry_run: bool = False) -> None:
    if not SUPABASE_URL or not SUPABASE_SERVICE_ROLE_KEY:
        raise SystemExit("error: set SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY")
    client = create_client(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)
    total = sum(prune(client, table, dry_run) for table in TABLES)
    logger.info("%s %d rows.", "Would delete" if dry_run else "Deleted", total)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Report what would be deleted without deleting it.",
    )
    args = parser.parse_args()
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(message)s",
        stream=sys.stdout,
    )
    run(dry_run=args.dry_run)


if __name__ == "__main__":
    main()
