"""Consorci AOC — Contractes publicats en el Perfil de Contractant.

Dataset agregat multi-ens al CKAN AOC (~484k contractes de tots els ens
catalans, històric des del servei PSCP). Es filtra per administració via els
identificadors de `administracio.identificadors`:

- `ine10`: codi INE10 de l'ens (str o llista — p. ex. Figueres + Figueres de
  Serveis SA)
- `aoc_ambit`: valor de NOM_AMBIT (la Generalitat no té un sol INE10 propi)
"""

from __future__ import annotations

import json
import logging
import re

from fairfactory.connectors.base import Connector
from fairfactory.db import administracio_id, administracio_ids, upsert

log = logging.getLogger(__name__)

API = "https://dadesobertes.seu-e.cat/api/action/datastore_search"
RESOURCE = "7448c675-8880-464e-9980-1b92119e59c8"  # css-rc-contractes-pscp
PAGE = 1000

DATE_FIELDS_PUB = (
    "DATA_PUBLICACIO_ANUNCI",
    "DATA_PUBLICACIO_PREVI",
    "DATA_PUBLICACIO_FUTURA",
)


def _d(v) -> str | None:
    """'2026-04-27T00:00:00' -> '2026-04-27'"""
    return v[:10] if isinstance(v, str) and re.match(r"\d{4}-\d{2}-\d{2}", v) else None


def _f(v) -> float | None:
    try:
        return float(v) if v is not None and v != "" else None
    except (TypeError, ValueError):
        return None


def _i(v) -> int | None:
    f = _f(v)
    return int(f) if f is not None else None


def _first(rec: dict, *keys: str):
    return next((rec[k] for k in keys if rec.get(k) not in (None, "")), None)


def record_to_contracte(rec: dict) -> dict:
    titol = rec.get("OBJECTE_CONTRACTE") or rec.get("DENOMINACIO") or ""
    url = rec.get("ENLLAC_PUBLICACIO") or _first(
        rec,
        "URL_JSON_LICITACIO",
        "URL_JSON_ADJUDICACIO",
        "URL_JSON_FORMALITZACIO",
        "URL_JSON_CPM",
    )
    return {
        "id_extern": "{}:{}:{}:{}".format(
            rec.get("CODI_INE10"),
            rec.get("CODI_EXPEDIENT") or "-",
            rec.get("NUMERO_LOT") or 0,
            rec.get("_id"),
        ),
        "organ": rec.get("NOM_ORGAN") or None,
        "organ_nif": rec.get("CODI_DIR3") or None,
        "expedient": rec.get("CODI_EXPEDIENT") or None,
        "titol": titol[:500],
        "tipus": rec.get("TIPUS_CONTRACTE") or None,
        "procediment": rec.get("PROCEDIMENT") or None,
        "estat": rec.get("FASE_PUBLICACIO") or None,
        "import_licitacio": _f(
            _first(
                rec,
                "PRESSUPOST_LICITACIO_AMB_IVA",
                "PRESSUPOST_LICITACIO_SENSE_IVA",
                "VALOR_ESTIMAT_CONTRACTE",
                "VALOR_ESTIMAT_EXPEDIENT",
            )
        ),
        "import_adjudicacio": _f(
            _first(rec, "IMPORT_ADJUDICACIO_AMB_IVA", "IMPORT_ADJUDICACIO_SENSE_IVA")
        ),
        "data_publicacio": _d(_first(rec, *DATE_FIELDS_PUB)),
        "data_adjudicacio": _d(
            _first(rec, "DATA_PUBLICACIO_ADJUDICACIO", "DATA_ADJUDICACIO_CONTRACTE")
        ),
        "adjudicatari_nom": rec.get("DENOMINACIO_ADJUDICATARI") or None,
        "adjudicatari_nif": rec.get("IDENTIFICACIO_ADJUDICATARI") or None,
        "num_ofertes": _i(rec.get("OFERTES_REBUDES")),
        "cpv": rec.get("CODI_CPV") or None,
        "url": url or API,
        "raw": rec,
    }


class AocContractesConnector(Connector):
    codi = "aoc_contractes"
    grups = ("catalunya",)
    taules = ("contracte",)
    nom = "Consorci AOC — Contractes publicats (dataset agregat CKAN)"
    url = "https://dadesobertes.seu-e.cat/dataset/css-rc-contractes-pscp"
    llicencia = "IOpen Data (AOC)"

    def _filters(self, admin_codi: str) -> list[dict]:
        ids = administracio_ids(self.conn, admin_codi)
        out = []
        ine10 = ids.get("ine10")
        if ine10:
            for c in ine10 if isinstance(ine10, list) else [ine10]:
                out.append({"CODI_INE10": c})
        if ids.get("aoc_ambit"):
            out.append({"NOM_AMBIT": ids["aoc_ambit"]})
        return out

    def ingest(self) -> int:
        written = 0
        for admin in ("figueres", "girona", "diputacio_girona", "catalunya"):
            aid = administracio_id(self.conn, admin)
            for filters in self._filters(admin):
                written += self._query(aid, filters)
        return written

    def _query(self, aid: int, filters: dict) -> int:
        written = offset = 0
        while True:
            data = self.client.get_json(
                API,
                params={
                    "resource_id": RESOURCE,
                    "filters": json.dumps(filters),
                    "limit": PAGE,
                    "offset": offset,
                },
            )
            records = data.get("result", {}).get("records", [])
            if not records:
                break
            rows = [
                {**record_to_contracte(r), "font": self.codi, "administracio_id": aid}
                for r in records
            ]
            written += upsert(self.conn, "contracte", rows)
            self.conn.commit()
            offset += len(records)
            if self.limit and written >= self.limit:
                break
        log.info("aoc_contractes %s: %d registres", filters, written)
        return written
