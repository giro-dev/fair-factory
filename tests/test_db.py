import pytest

from fairfactory.db import (
    IngestRun,
    administracio_id,
    administracio_ids,
    connect,
    ensure_font,
    init_db,
    upsert,
)


@pytest.fixture
def conn():
    c = connect(":memory:")
    init_db(c)
    return c


def test_init_seeds_administracions(conn):
    assert administracio_id(conn, "siero") != administracio_id(conn, "asturias")
    assert administracio_id(conn, "figueres") != administracio_id(conn, "catalunya")
    assert conn.execute("SELECT COUNT(*) FROM administracio").fetchone()[0] == 6
    row = conn.execute("SELECT comunitat FROM administracio WHERE codi = 'figueres'").fetchone()
    assert row["comunitat"] == "Catalunya"


def test_administracio_identificadors(conn):
    ids = administracio_ids(conn, "figueres")
    assert ids["ine10"] == ["1706690004", "9914065004"]
    assert ids["dir3"] == "L01170669"
    assert administracio_ids(conn, "catalunya")["aoc_ambit"].startswith("Departaments")
    assert administracio_ids(conn, "siero") == {}


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
