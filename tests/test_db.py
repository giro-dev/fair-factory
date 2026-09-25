import pytest

from fairfactory.db import IngestRun, administracio_id, connect, ensure_font, init_db, upsert


@pytest.fixture
def conn():
    c = connect(":memory:")
    init_db(c)
    return c


def test_init_seeds_administracions(conn):
    assert administracio_id(conn, "siero") != administracio_id(conn, "asturias")
    assert conn.execute("SELECT COUNT(*) FROM administracio").fetchone()[0] == 2


def test_upsert_is_idempotent(conn):
    ensure_font(conn, "f", "Font", "http://x", "CC")
    admin = administracio_id(conn, "siero")
    row = {
        "font": "f",
        "id_extern": "1",
        "administracio_id": admin,
        "titol": "A",
        "import_adjudicacio": 10.0,
        "raw": {"k": "v"},
    }
    assert upsert(conn, "contracte", [row]) == 1
    assert upsert(conn, "contracte", [{**row, "titol": "B"}]) == 1
    rows = conn.execute("SELECT titol, raw FROM contracte").fetchall()
    assert len(rows) == 1
    assert rows[0]["titol"] == "B"
    assert '"k"' in rows[0]["raw"]


def test_ingest_run_records_error(conn):
    ensure_font(conn, "f", "Font", "http://x", "CC")
    with pytest.raises(RuntimeError), IngestRun(conn, "f") as run:
        run.registres = 3
        raise RuntimeError("boom")
    r = conn.execute("SELECT estat, registres, missatge FROM ingest_run").fetchone()
    assert r["estat"] == "error"
    assert "boom" in r["missatge"]
