import os
from datetime import datetime, timezone
from unittest.mock import MagicMock, patch

import pandas as pd
import pytest

import ingest
import ingest_game_logs

NOW = datetime(2026, 7, 20, tzinfo=timezone.utc)


# --------------------------------------------------------------------------- #
# Season resolution
# --------------------------------------------------------------------------- #
def test_resolve_season_cli_wins():
    assert ingest.resolve_season(2022) == 2022


def test_resolve_season_env():
    with patch.dict(os.environ, {"STATCAST_SEASON": "2021"}, clear=False):
        assert ingest.resolve_season(None) == 2021


def test_resolve_season_out_of_range_falls_back():
    assert ingest.resolve_season(1990) == ingest.DEFAULT_SEASON
    assert ingest.resolve_season(ingest.DEFAULT_SEASON + 5) == ingest.DEFAULT_SEASON
    with patch.dict(os.environ, {"STATCAST_SEASON": "abc"}, clear=False):
        assert ingest.resolve_season(None) == ingest.DEFAULT_SEASON


# --------------------------------------------------------------------------- #
# GSIS id conversion
# --------------------------------------------------------------------------- #
def test_gsis_to_id():
    assert ingest.gsis_to_id("00-0034796") == 34796
    assert ingest.gsis_to_id("00-0000001") == 1
    assert ingest.gsis_to_id(None) is None
    assert ingest.gsis_to_id("") is None
    assert ingest.gsis_to_id(float("nan")) is None
    assert ingest.gsis_to_id("garbage") is None


def test_player_type_from_position():
    assert ingest.player_type_from_position("QB", "QB") == "qb"
    assert ingest.player_type_from_position("RB", "RB") == "rb"
    assert ingest.player_type_from_position("WR", "WR") == "wr"
    assert ingest.player_type_from_position("TE", "TE") == "te"
    assert ingest.player_type_from_position("OLB", "LB") == "def"
    assert ingest.player_type_from_position("CB", "DB") == "def"
    assert ingest.player_type_from_position("K", "SPEC") == "k"


# --------------------------------------------------------------------------- #
# Value formatting
# --------------------------------------------------------------------------- #
def test_format_value():
    assert ingest.format_value(4918, "comma") == "4,918"
    assert ingest.format_value(27, "int") == "27"
    assert ingest.format_value(68.34, "pct1") == "68.3%"
    assert ingest.format_value(7.86, "dec1") == "7.9"
    assert ingest.format_value(0.176, "dec2") == "0.18"
    assert ingest.format_value(2.3, "signed1") == "+2.3"
    assert ingest.format_value(-1.2, "signed1") == "-1.2"
    assert ingest.format_value(None, "int") == ""
    assert ingest.format_value(float("nan"), "comma") == ""


def test_passer_rating_perfect_and_zero_attempts():
    # Perfect passer rating is capped at 158.3.
    assert ingest.passer_rating(20, 20, 400, 6, 0) == 158.3
    assert ingest.passer_rating(0, 0, 0, 0, 0) is None


# --------------------------------------------------------------------------- #
# Percentile computation (incl. inverted)
# --------------------------------------------------------------------------- #
def test_rank_percentiles_higher_is_better():
    s = pd.Series({1: 10.0, 2: 20.0, 3: 30.0})
    pct = ingest.rank_percentiles(s, inverted=False)
    assert pct[3] > pct[2] > pct[1]
    assert pct[3] == 100


def test_rank_percentiles_inverted():
    # Lower raw value should rank highest when inverted (e.g. INT%).
    s = pd.Series({1: 1.0, 2: 2.0, 3: 3.0})
    pct = ingest.rank_percentiles(s, inverted=True)
    assert pct[1] > pct[2] > pct[3]
    assert pct[1] == 100


def test_rank_percentiles_ignores_nan():
    s = pd.Series({1: 5.0, 2: float("nan"), 3: 15.0})
    pct = ingest.rank_percentiles(s, inverted=False)
    assert 2 not in pct
    assert pct[3] == 100


# --------------------------------------------------------------------------- #
# Qualification thresholds
# --------------------------------------------------------------------------- #
def test_qualifies():
    assert ingest.qualifies({"attempts": 200}, "Passing", "qb")
    assert not ingest.qualifies({"attempts": 100}, "Passing", "qb")
    assert ingest.qualifies({"carries": 80}, "Rushing", "rb")
    assert not ingest.qualifies({"carries": 79}, "Rushing", "rb")
    assert ingest.qualifies({"targets": 40}, "Receiving", "wr")
    assert not ingest.qualifies({"targets": 39}, "Receiving", "wr")
    assert ingest.qualifies({"games": 8}, "Defense", "def")
    assert not ingest.qualifies({"games": 8}, "Defense", "qb")  # wrong type
    assert not ingest.qualifies({"games": 7}, "Defense", "def")


# --------------------------------------------------------------------------- #
# Aggregation from a synthetic weekly DataFrame
# --------------------------------------------------------------------------- #
def test_aggregate_excludes_postseason(weekly_df):
    agg = ingest.aggregate_seasons(weekly_df, 2025)
    qb = agg.loc[1]
    # 4 REG games of 40 attempts = 160; the POST game (50 att) is excluded.
    assert qb["attempts"] == 160
    assert qb["games"] == 4


def test_aggregate_derived_rates(weekly_df):
    agg = ingest.aggregate_seasons(weekly_df, 2025)
    qb = agg.loc[1]
    # cmp% = 100/160 = 62.5, ypa = 1200/160 = 7.5
    assert round(qb["cmp_pct"], 1) == 62.5
    assert round(qb["ypa"], 1) == 7.5
    # EPA/play uses dropbacks, including sacks suffered.
    assert round(qb["passing_epa_per_play"], 4) == round(qb["passing_epa"] / (160 + qb["sacks_suffered"]), 4)
    # int_rate = 4/160 = 2.5%
    assert round(qb["int_rate"], 2) == 2.5
    rb = agg.loc[2]
    # ypc = 440/100 = 4.4, explosive = 16/100 = 16%, fumble = 4/100 = 4%
    assert round(rb["ypc"], 1) == 4.4
    assert round(rb["rushing_epa_per_carry"], 4) == round(rb["rushing_epa"] / 100, 4)
    assert round(rb["explosive_rush_rate"], 1) == 16.0
    assert round(rb["fumble_rate"], 1) == 4.0
    wr = agg.loc[3]
    # catch% = 32/48 = 66.7; receiving EPA is normalized per target.
    assert round(wr["catch_pct"], 1) == 66.7
    assert round(wr["receiving_epa_per_target"], 4) == round(wr["receiving_epa"] / 48, 4)


def test_aggregate_player_type_and_team(weekly_df):
    agg = ingest.aggregate_seasons(weekly_df, 2025)
    assert agg.loc[1]["player_type"] == "qb"
    assert agg.loc[4]["player_type"] == "def"
    assert agg.loc[1]["team"] == "KC"


def test_aggregate_omits_metrics_without_historical_source_columns(weekly_df):
    old_schema = weekly_df.drop(columns=[
        "rushing_10",
        "receiving_yards_after_catch",
        "target_share",
        "air_yards_share",
        "def_qb_hits",
    ]).copy()
    old_schema["season"] = 2015

    agg = ingest.aggregate_seasons(old_schema, 2015)

    assert "explosive_rush_rate" not in agg.columns
    assert "rec_yac" not in agg.columns
    assert "target_share_pct" not in agg.columns
    assert "wopr" not in agg.columns
    assert "qb_hits" not in agg.columns
    assert "rushing_epa_per_carry" in agg.columns
    assert "receiving_epa_per_target" in agg.columns


def test_merge_ngs(weekly_df, ngs_passing_df, ngs_rushing_df, ngs_receiving_df):
    agg = ingest.aggregate_seasons(weekly_df, 2025)
    agg = ingest.merge_ngs(agg, ngs_passing_df, ngs_rushing_df, ngs_receiving_df, 2025)
    assert round(agg.loc[1]["cpoe"], 1) == 3.4
    assert round(agg.loc[1]["avg_time_to_throw"], 2) == 2.75
    assert round(agg.loc[2]["rush_yoe"], 1) == 120.5
    assert round(agg.loc[3]["avg_separation"], 1) == 3.2


def test_build_agg_skips_ngs_before_2016(weekly_df):
    old_weekly = weekly_df.copy()
    old_weekly["season"] = 2015
    with patch("ingest.nfl.load_player_stats", return_value=old_weekly):
        with patch("ingest.nfl.load_nextgen_stats") as load_ngs:
            with patch("ingest.load_headshots", return_value={}):
                agg = ingest.build_agg_for_season(2015)

    assert not agg.empty
    load_ngs.assert_not_called()
    assert "cpoe" not in agg.columns


def test_build_agg_requests_only_selected_ngs_season(weekly_df):
    empty_ngs = pd.DataFrame()
    with patch("ingest.nfl.load_player_stats", return_value=weekly_df):
        with patch("ingest.nfl.load_nextgen_stats", return_value=empty_ngs) as load_ngs:
            with patch("ingest.load_headshots", return_value={}):
                ingest.build_agg_for_season(2025)

    assert load_ngs.call_count == 3
    assert all(call.args[0] == [2025] for call in load_ngs.call_args_list)


# --------------------------------------------------------------------------- #
# Snapshot row building
# --------------------------------------------------------------------------- #
def _build_rows(weekly_df, ngs_p, ngs_r, ngs_rec):
    agg = ingest.aggregate_seasons(weekly_df, 2025)
    agg = ingest.merge_ngs(agg, ngs_p, ngs_r, ngs_rec, 2025)
    agg["image_url"] = None
    return ingest.build_snapshot_rows(agg, 2025, NOW)


def test_build_snapshot_rows_shape(weekly_df, ngs_passing_df, ngs_rushing_df, ngs_receiving_df):
    rows = _build_rows(weekly_df, ngs_passing_df, ngs_rushing_df, ngs_receiving_df)
    by_id = {r["id"]: r for r in rows}
    # The sub-threshold WR (id 5, 5 targets) is dropped (no metrics).
    assert 5 not in by_id
    # QB present with Passing metrics.
    qb = by_id[1]
    assert qb["player_type"] == "qb"
    cats = {m["category"] for m in qb["metrics"]}
    assert "Passing" in cats
    # Rushing QB also gets Rushing? QB carries = 20 total < 80, so no.
    assert "Rushing" not in cats
    # Every metric has the contract shape.
    for m in qb["metrics"]:
        assert set(m) == {"id", "label", "value", "percentile", "category"}
        assert 1 <= m["percentile"] <= 100
    rb = by_id[2]
    assert next(m for m in rb["metrics"] if m["label"] == "EPA/Rush")["value"]
    wr = by_id[3]
    assert next(m for m in wr["metrics"] if m["label"] == "EPA/Tgt")["value"]
    # Season label and source.
    assert qb["season"] == 2025
    assert qb["source"] == "nflverse"
    assert qb["handedness"] == ""


def test_build_snapshot_inverted_metric_present(weekly_df, ngs_passing_df, ngs_rushing_df, ngs_receiving_df):
    rows = _build_rows(weekly_df, ngs_passing_df, ngs_rushing_df, ngs_receiving_df)
    qb = next(r for r in rows if r["id"] == 1)
    int_metric = next(m for m in qb["metrics"] if m["label"] == "INT%")
    # Only one qualified passer, so it ranks 100 by default; value formatted as %.
    assert int_metric["value"].endswith("%")


def test_build_snapshot_value_formatting(weekly_df, ngs_passing_df, ngs_rushing_df, ngs_receiving_df):
    rows = _build_rows(weekly_df, ngs_passing_df, ngs_rushing_df, ngs_receiving_df)
    qb = next(r for r in rows if r["id"] == 1)
    pass_yds = next(m for m in qb["metrics"] if m["label"] == "Pass Yds")
    assert pass_yds["value"] == "1,200"  # 4 games * 300


def test_build_standard_stats(weekly_df):
    agg = ingest.aggregate_seasons(weekly_df, 2025)
    stats = ingest.build_standard_stats(agg.loc[1])
    labels = {s["label"] for s in stats}
    assert "G" in labels
    assert "Cmp/Att" in labels
    cmp_att = next(s for s in stats if s["label"] == "Cmp/Att")
    assert cmp_att["value"] == "100/160"


# --------------------------------------------------------------------------- #
# Game logs
# --------------------------------------------------------------------------- #
def test_build_game_log_rows(weekly_df):
    sched = {
        "2025_01_KC_DEN": "2025-09-07", "2025_02_KC_DEN": "2025-09-14",
        "2025_03_KC_LV": "2025-09-21", "2025_04_KC_LV": "2025-09-28",
    }
    rows = ingest_game_logs.build_game_log_rows(weekly_df, sched, 2025, NOW)
    qb_rows = [r for r in rows if r["player_id"] == 1]
    # Only 4 QB games have scheduled dates in the map; POST game (week 20) not mapped.
    assert len(qb_rows) == 4
    r0 = qb_rows[0]
    assert r0["game_date"] == "2025-09-07"
    assert r0["player_type"] == "qb"
    assert r0["plays"] == 45  # 40 att + 5 carries (week 1)
    assert r0["touches"] == 30  # 25 cmp + 5 carries
    assert r0["metrics"]["passing_yards"] == 300
    assert "epa_total" in r0["metrics"]


def test_build_game_log_rows_skips_unmapped_games(weekly_df):
    rows = ingest_game_logs.build_game_log_rows(weekly_df, {}, 2025, NOW)
    assert rows == []


def test_schedule_map():
    sched = pd.DataFrame([
        {"game_id": "2025_01_KC_DEN", "gameday": "2025-09-07"},
        {"game_id": "2025_02_SF_SEA", "gameday": "2025-09-14"},
    ])
    m = ingest_game_logs.schedule_map(sched)
    assert m["2025_01_KC_DEN"] == "2025-09-07"


# --------------------------------------------------------------------------- #
# Upsert batching / main flow
# --------------------------------------------------------------------------- #
def test_chunks():
    rows = [{"id": i} for i in range(350)]
    batches = list(ingest.chunks(rows, 150))
    assert [len(b) for b in batches] == [150, 150, 50]


def test_main_upserts_batches():
    rows = [{"id": i, "player_type": "qb"} for i in range(200)]
    mock_client = MagicMock()
    mock_table = MagicMock()
    mock_client.table.return_value = mock_table
    mock_table.upsert.return_value = mock_table
    mock_table.select.return_value = mock_table
    mock_table.eq.return_value = mock_table
    mock_table.execute.return_value = MagicMock(data=[])
    with patch.dict(os.environ, {"SUPABASE_URL": "https://t.supabase.co", "SUPABASE_SERVICE_ROLE_KEY": "k"}):
        with patch("ingest.create_client", return_value=mock_client):
            with patch("ingest.build_agg_for_season", return_value=pd.DataFrame({"x": [1]})):
                with patch("ingest.build_snapshot_rows", return_value=rows):
                    with patch("sys.argv", ["ingest.py", "--season", "2025"]):
                        ingest.main()
    assert mock_table.upsert.call_count == 2  # 200 rows / 150 batch


def test_main_exits_when_no_rows():
    mock_client = MagicMock()
    with patch.dict(os.environ, {"SUPABASE_URL": "https://t.supabase.co", "SUPABASE_SERVICE_ROLE_KEY": "k"}):
        with patch("ingest.create_client", return_value=mock_client):
            with patch("ingest.build_agg_for_season", return_value=pd.DataFrame()):
                with patch("ingest.build_snapshot_rows", return_value=[]):
                    with patch("sys.argv", ["ingest.py", "--season", "2025"]):
                        with pytest.raises(SystemExit) as exc:
                            ingest.main()
    assert exc.value.code == 1
