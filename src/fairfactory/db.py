from __future__ import annotations

import json
import sqlite3
from collections.abc import Iterable, Mapping
from datetime import UTC, datetime
from importlib import resources
from pathlib import Path
from typing import Any

DEFAULT_DB = Path("data/transparencia.sqlite")

ADMINISTRACIONS = [
    ("siero", "Ayuntamiento de Siero", "local", "Asturias", "https://www.ayto-siero.es"),
    ("asturias", "Principado de Asturias", "autonomic", "Asturias", "https://www.asturias.es"),
]


def now_iso() -> str:
    return datetime.now(UTC).replace(microsecond=0).isoformat()


def connect(path: Path | str = DEFAULT_DB) -> sqlite3.Connection:
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(path)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


def init_db(conn: sqlite3.Connection) -> None:
    schema = resources.files("fairfactory").joinpath("schema.sql").read_text(encoding="utf-8")
    conn.executescript(schema)
    cols = {r[1] for r in conn.execute("PRAGMA table_info(administracio)")}
    if "comunitat" not in cols:
        conn.execute("ALTER TABLE administracio ADD COLUMN comunitat TEXT")
    conn.executemany(
        """INSERT INTO administracio (codi, nom, nivell, comunitat, url)
           VALUES (?, ?, ?, ?, ?)
           ON CONFLICT(codi) DO UPDATE SET nom = excluded.nom, nivell = excluded.nivell,
               comunitat = excluded.comunitat, url = excluded.url""",
        ADMINISTRACIONS,
    )
    conn.commit()


def administracio_id(conn: sqlite3.Connection, codi: str) -> int:
    row = conn.execute("SELECT id FROM administracio WHERE codi = ?", (codi,)).fetchone()
    if row is None:
        raise KeyError(f"administració desconeguda: {codi}")
    return int(row["id"])


def ensure_font(conn: sqlite3.Connection, codi: str, nom: str, url: str, llicencia: str) -> None:
    conn.execute(
        """INSERT INTO font (codi, nom, url, llicencia) VALUES (?, ?, ?, ?)
           ON CONFLICT(codi) DO UPDATE SET nom = excluded.nom, url = excluded.url,
                                           llicencia = excluded.llicencia""",
        (codi, nom, url, llicencia),
    )


def upsert(conn: sqlite3.Connection, table: str, rows: Iterable[Mapping[str, Any]]) -> int:
    """Insert or update rows keyed by (font, id_extern). Returns number of rows written."""
    n = 0
    for row in rows:
        data = dict(row)
        if "raw" in data and not isinstance(data["raw"], str | type(None)):
            data["raw"] = json.dumps(data["raw"], ensure_ascii=False, default=str)
        data.setdefault("actualitzat", now_iso())
        cols = list(data)
        updates = ", ".join(f"{c} = excluded.{c}" for c in cols if c not in ("font", "id_extern"))
        sql = (
            f"INSERT INTO {table} ({', '.join(cols)}) VALUES ({', '.join('?' for _ in cols)}) "
            f"ON CONFLICT(font, id_extern) DO UPDATE SET {updates}"
        )
        conn.execute(sql, [data[c] for c in cols])
        n += 1
    return n


class IngestRun:
    """Context manager that records a row in ingest_run."""

    def __init__(self, conn: sqlite3.Connection, font: str):
        self.conn = conn
        self.font = font
        self.registres = 0
        self.id: int | None = None

    def __enter__(self) -> IngestRun:
        cur = self.conn.execute(
            "INSERT INTO ingest_run (font, inici) VALUES (?, ?)", (self.font, now_iso())
        )
        self.id = cur.lastrowid
        self.conn.commit()
        return self

    def __exit__(self, exc_type, exc, tb) -> bool:
        estat = "error" if exc else "ok"
        missatge = f"{exc_type.__name__}: {exc}" if exc else None
        self.conn.execute(
            "UPDATE ingest_run SET fi = ?, estat = ?, registres = ?, missatge = ? WHERE id = ?",
            (now_iso(), estat, self.registres, missatge, self.id),
        )
        self.conn.commit()
        return False
