"""Generalitat de Catalunya — execució pressupostària (Socrata).

Dataset «Execució mensual del pressupost de la Generalitat de Catalunya
(Despeses)» a analisi.transparenciacatalunya.cat (view ajns-4mi7). El conjunt
complet és massiu (~1 GB); s'agrega al servidor amb SoQL per exercici ×
capítol econòmic i es desa a la taula `pressupost`.
"""

from __future__ import annotations

import logging
from datetime import UTC, datetime

from fairfactory.connectors.base import Connector
from fairfactory.db import administracio_id, upsert

log = logging.getLogger(__name__)

API = "https://analisi.transparenciacatalunya.cat/resource/ajns-4mi7.json"
YEARS_BACK = 3


def _f(v) -> float | None:
    try:
        return float(v) if v is not None else None
    except (TypeError, ValueError):
        return None


class CatalunyaSocrataConnector(Connector):
    codi = "catalunya_socrata"
    nom = "Generalitat de Catalunya — Execució pressupostària (Socrata)"
    url = "https://analisi.transparenciacatalunya.cat/d/ajns-4mi7"
    llicencia = "Dades obertes Generalitat de Catalunya (IODL)"

    def ingest(self) -> int:
        aid = administracio_id(self.conn, "catalunya")
        any_actual = datetime.now(UTC).year
        exercicis = [str(y) for y in range(any_actual - YEARS_BACK + 1, any_actual + 1)]
        quoted = ",".join(f'"{y}"' for y in exercicis)
        data = self.client.get_json(
            API,
            params={
                "$select": "exercici,cap_tol_codi,cap_tol,"
                "sum(cr_dits_inicials) AS ini,sum(pressupost_definitiu) AS def,"
                "sum(autoritzacions) AS aut,sum(obligacions_reconegudes) AS rec,"
                "sum(obligacions_pagades) AS pag",
                "$where": f"exercici in({quoted})",
                "$group": "exercici,cap_tol_codi,cap_tol",
                "$order": "exercici DESC",
                "$limit": "5000",
            },
            headers={"Accept": "application/json"},
        )
        rows = [
            {
                "font": self.codi,
                "id_extern": f"{r['exercici']}|{r['cap_tol_codi']}|{r['cap_tol']}",
                "administracio_id": aid,
                "exercici": r["exercici"],
                "capitol": r.get("cap_tol"),
                "capitol_codi": r.get("cap_tol_codi"),
                "credit_inicial": _f(r.get("ini")),
                "pressupost_definitiu": _f(r.get("def")),
                "autoritzat": _f(r.get("aut")),
                "obligacions_reconegudes": _f(r.get("rec")),
                "obligacions_pagades": _f(r.get("pag")),
                "url": API,
                "raw": r,
            }
            for r in data
            if isinstance(r, dict) and r.get("exercici")
        ]
        n = upsert(self.conn, "pressupost", rows)
        self.conn.commit()
        return n
