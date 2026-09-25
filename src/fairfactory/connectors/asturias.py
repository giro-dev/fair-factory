"""Portal de Transparencia del Principado de Asturias — inventari de documents enllaçats."""

from __future__ import annotations

import logging

from fairfactory.connectors.base import Connector
from fairfactory.connectors.siero import extract_documents
from fairfactory.db import administracio_id, upsert

log = logging.getLogger(__name__)

BASE = "https://transparencia.asturias.es"
PAGES = {
    "portada": f"{BASE}/",
    "presupuestos": f"{BASE}/economica/presupuestos-y-cuentas-anuales",
    "contratacion": f"{BASE}/economica/contratacion-y-perfil-del-contratante",
    "subvenciones": f"{BASE}/economica/subvenciones-y-ayudas",
    "estadisticas": f"{BASE}/estadisticas-asturias",
}


class AsturiasConnector(Connector):
    codi = "asturias_transparencia"
    nom = "Principado de Asturias — Portal de Transparencia"
    url = BASE

    def ingest(self) -> int:
        aid = administracio_id(self.conn, "asturias")
        docs: dict[str, dict] = {}
        for categoria, url in PAGES.items():
            try:
                html = self.client.get(url).text
            except Exception as exc:  # noqa: BLE001
                log.warning("asturias: no s'ha pogut llegir %s: %s", url, exc)
                continue
            for d in extract_documents(html, url, categoria):
                docs.setdefault(d["url"], d)
        rows = [{**d, "font": self.codi, "administracio_id": aid} for d in docs.values()]
        n = upsert(self.conn, "document", rows)
        self.conn.commit()
        return n
