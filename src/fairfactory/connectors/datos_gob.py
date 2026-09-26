"""Catàleg datos.gob.es — datasets publicats pel Principat d'Astúries (API DCAT/Linked Data).

Docs: https://datos.gob.es/es/accessible-apidata
"""

from __future__ import annotations

import logging

from fairfactory.connectors.base import Connector
from fairfactory.db import administracio_id, upsert

log = logging.getLogger(__name__)

API = "https://datos.gob.es/apidata/catalog/dataset"
PAGE_SIZE = 50

# Identificadors DIR3 dels publicadors al catàleg
PUBLISHERS = {
    "asturias": "A03002951",  # Principado de Asturias
}


def _lang(values, lang="es") -> str | None:
    """Pick a literal from a list of {'_value', '_lang'} (or a plain string)."""
    if values is None:
        return None
    if isinstance(values, str):
        return values
    if isinstance(values, dict):
        values = [values]
    by_lang = {v.get("_lang"): v.get("_value") for v in values if isinstance(v, dict)}
    return by_lang.get(lang) or next(iter(by_lang.values()), None)


def _list(value) -> list:
    if value is None:
        return []
    return value if isinstance(value, list) else [value]


class DatosGobConnector(Connector):
    codi = "datos_gob"
    grups = ("compartit",)
    taules = ("dataset",)
    nom = "datos.gob.es — Catálogo de datos abiertos"
    url = "https://datos.gob.es"
    llicencia = "https://datos.gob.es/es/aviso-legal"

    def ingest(self) -> int:
        total = 0
        for admin, publisher in PUBLISHERS.items():
            total += self._ingest_publisher(admin, publisher)
        return total

    def _ingest_publisher(self, admin: str, publisher: str) -> int:
        aid = administracio_id(self.conn, admin)
        page = 0
        written = 0
        while True:
            data = self.client.get_json(
                f"{API}/publisher/{publisher}",
                params={"_pageSize": PAGE_SIZE, "_page": page, "_sort": "-modified"},
            )
            result = data.get("result", {})
            items = result.get("items", [])
            rows = []
            for ds in items:
                dists = _list(ds.get("distribution"))
                formats = sorted(
                    {
                        (d.get("format") or {}).get("value", "").split("/")[-1].upper()
                        for d in dists
                        if isinstance(d, dict) and d.get("format")
                    }
                    - {""}
                )
                themes = [t.rsplit("/", 1)[-1] for t in _list(ds.get("theme"))]
                rows.append(
                    {
                        "font": self.codi,
                        "id_extern": ds["_about"],
                        "administracio_id": aid,
                        "titol": _lang(ds.get("title")),
                        "descripcio": _lang(ds.get("description")),
                        "publicador": publisher,
                        "temes": ",".join(themes) or None,
                        "formats": ",".join(formats) or None,
                        "url": ds["_about"],
                        "data_modificacio": ds.get("modified") or ds.get("issued"),
                        "raw": ds,
                    }
                )
            written += upsert(self.conn, "dataset", rows)
            self.conn.commit()
            log.info("datos_gob/%s pàgina %d (%d)", admin, page + 1, written)
            page += 1
            if not items or len(items) < PAGE_SIZE or "next" not in result:
                break
            if self.limit and written >= self.limit:
                break
        return written
