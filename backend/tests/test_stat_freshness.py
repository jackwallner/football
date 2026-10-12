import sys
from datetime import datetime, timezone
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "scripts"))
import stat_freshness as sf  # noqa: E402

NOW = datetime(2026, 10, 12, 22, 30, tzinfo=timezone.utc)
CHAIN = {"name": "Hockey StatScout", "kind": "chain", "url": "https://x.supabase.co", "anon_key": "k"}
MLB = {"name": "Baseball StatScout", "kind": "mlb", "url": "https://x.supabase.co", "anon_key": "k"}


def _fake_rest(tables):
    def rest(app, table, params):
        return tables[table]
    return rest


def test_chain_app_serving_the_newest_game_is_fresh(monkeypatch):
    monkeypatch.setattr(sf, "_rest", _fake_rest({
        "data_refresh_status": [{"max_game_date": "2026-10-11", "last_checked_at": "2026-10-12T20:00:00+00:00"}],
        "games": [{"game_date": "2026-10-10"}],
    }))
    fresh, _ = sf.check_app(CHAIN, NOW)
    assert fresh


def test_chain_app_behind_the_newest_finished_game_is_stale(monkeypatch):
    monkeypatch.setattr(sf, "_rest", _fake_rest({
        "data_refresh_status": [{"max_game_date": "2026-10-08", "last_checked_at": "2026-10-12T20:00:00+00:00"}],
        "games": [{"game_date": "2026-10-10"}],
    }))
    fresh, detail = sf.check_app(CHAIN, NOW)
    assert not fresh
    assert "2026-10-10" in detail


def test_a_silent_chain_is_stale_even_in_the_offseason(monkeypatch):
    monkeypatch.setattr(sf, "_rest", _fake_rest({
        "data_refresh_status": [{"max_game_date": "2026-06-13", "last_checked_at": "2026-10-10T09:00:00+00:00"}],
        "games": [{"game_date": "2026-06-13"}],
    }))
    fresh, detail = sf.check_app(CHAIN, NOW)
    assert not fresh
    assert "last probed" in detail


def test_baseball_missing_a_finished_game_is_stale(monkeypatch):
    monkeypatch.setattr(sf, "_mlb_latest_finished", lambda through: through)
    monkeypatch.setattr(sf, "_rest", _fake_rest({
        "player_game_logs": [{"game_date": "2026-09-27"}],
        "player_postseason_game_logs": [{"game_date": "2026-10-10"}],
    }))
    fresh, detail = sf.check_app(MLB, NOW)
    assert not fresh
    assert "2026-10-11" in detail


def test_baseball_gets_until_the_evening_after_to_catch_up(monkeypatch):
    seen = {}

    def latest(through):
        seen["through"] = through
        return through

    monkeypatch.setattr(sf, "_mlb_latest_finished", latest)
    monkeypatch.setattr(sf, "_rest", _fake_rest({
        "player_game_logs": [],
        "player_postseason_game_logs": [{"game_date": "2026-10-11"}],
    }))
    afternoon = datetime(2026, 10, 12, 20, 0, tzinfo=timezone.utc)
    fresh, _ = sf.check_app(MLB, afternoon)
    assert fresh
    assert str(seen["through"]) == "2026-10-10"


def test_no_games_due_is_fresh(monkeypatch):
    monkeypatch.setattr(sf, "_mlb_latest_finished", lambda through: None)
    fresh, _ = sf.check_app(MLB, NOW)
    assert fresh


def test_an_empty_status_row_is_unknown_not_stale(monkeypatch):
    monkeypatch.setattr(sf, "_rest", _fake_rest({"data_refresh_status": []}))
    with pytest.raises(sf.CheckUnavailable):
        sf.check_app(CHAIN, NOW)
