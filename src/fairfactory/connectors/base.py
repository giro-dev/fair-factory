from __future__ import annotations

import logging
import re
import sqlite3
from abc import ABC, abstractmethod

from fairfactory.db import IngestRun, ensure_font
from fairfactory.http import Client

log = logging.getLogger(__name__)

NIF_RE = re.compile(r"^\s*([A-Z]\d{7}[A-Z0-9]|\d{8}[A-Z]|[XYZ]\d{7}[A-Z]|\*{3}\d{4}\*{2})\s+(.*)$")


def split_nif(text: str | None) -> tuple[str | None, str | None]:
    """BDNS-style 'B12345678 EMPRESA SL' -> ('B12345678', 'EMPRESA SL')."""
    if not text:
        return None, None
    m = NIF_RE.match(text.strip())
    if not m:
        return None, text.strip()
    return m.group(1), m.group(2).strip() or None


def parse_es_number(text: str | None) -> float | None:
    """'2.626,30' -> 2626.30"""
    if text is None:
        return None
    t = text.strip().replace("€", "").replace(" ", "")
    if not t:
        return None
    if "," in t:
        t = t.replace(".", "").replace(",", ".")
    elif re.fullmatch(r"-?\d{1,3}(\.\d{3})+", t):
        # "2.626" són milers, no decimals (però "63428.0" és decimal)
        t = t.replace(".", "")
    try:
        return float(t)
    except ValueError:
        return None


def parse_es_date(text: str | None) -> str | None:
    """'17-02-2022' or '17/02/2022' -> '2022-02-17'"""
    if not text:
        return None
    m = re.match(r"^\s*(\d{1,2})[-/](\d{1,2})[-/](\d{4})", text)
    if not m:
        return None
    d, mo, y = m.groups()
    return f"{y}-{int(mo):02d}-{int(d):02d}"


class Connector(ABC):
    """A connector pulls data from one source and upserts it into the SQLite database.

    Contracte mínim d'un connector:

    - `codi`, `nom`, `url`: metadades de la font (a la taula `font`)
    - `grups`: job(s) del workflow d'ingesta on s'executa
      ("asturias" | "catalunya" | "compartit")
    - `taules`: taules de dades on escriu (contracte, subvencio, document...)
    - `cli_options`: opcions pròpies de la CLI — tuples
      `(flag, kwargs_per_add_argument, nom_del_paràmetre_d_init)`.
      El CLI les registra automàticament i passa el valor a `__init__`;
      afegir un connector nou NO requereix tocar `cli.py`.
    - `ingest()`: baixa i upsert (idempotent per `font`+`id_extern`); retorna
      el nombre de files escrites.
    """

    codi: str
    nom: str
    url: str
    llicencia: str = "Reutilització d'informació del sector públic (Llei 37/2007)"
    grups: tuple[str, ...] = ("compartit",)
    taules: tuple[str, ...] = ()
    cli_options: tuple = ()

    def __init__(
        self, conn: sqlite3.Connection, client: Client | None = None, limit: int | None = None
    ):
        self.conn = conn
        self.client = client or Client()
        self.limit = limit

    def run(self) -> int:
        ensure_font(self.conn, self.codi, self.nom, self.url, self.llicencia)
        self.conn.commit()
        with IngestRun(self.conn, self.codi) as run:
            n = self.ingest()
            run.registres = n
        log.info("%s: %d registres", self.codi, n)
        return n

    @abstractmethod
    def ingest(self) -> int:
        """Fetch and upsert. Returns number of rows written."""
