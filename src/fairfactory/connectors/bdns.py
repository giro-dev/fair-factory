"""Base de Datos Nacional de Subvenciones (BDNS) — concessions via API pública.

API: https://www.infosubvenciones.es/bdnstrans/api/concesiones/busqueda
"""

from __future__ import annotations

import logging

from fairfactory.connectors.base import Connector, split_nif
from fairfactory.db import administracio_id, upsert

log = logging.getLogger(__name__)

API = "https://www.infosubvenciones.es/bdnstrans/api"
PAGE_SIZE = 500

# ids d'òrgan a la BDNS (GET /organos?vpd=GE&idAdmon=L|A)
# - idAdmon "L": provincia + (muni = tots els fills del node | organ = nom exacte)
# - idAdmon "A": comunitat = tots els òrgans fills del node de la CA
ORGANS = {
    "siero": {"idAdmon": "L", "provincia": "ASTURIAS", "organ": "AYUNTAMIENTO DE SIERO"},
    "asturias": {"idAdmon": "A", "comunitat": "PRINCIPADO DE ASTURIAS"},
    "figueres": {"idAdmon": "L", "provincia": "GIRONA", "organ": "AYUNTAMIENTO DE FIGUERES"},
    "girona": {"idAdmon": "L", "provincia": "GIRONA", "organ": "AYUNTAMIENTO DE GIRONA"},
    "diputacio_girona": {
        "idAdmon": "L",
        "provincia": "GIRONA",
        "muni": "DIPUTACIÓN PROV. DE GIRONA",
    },
    "catalunya": {"idAdmon": "A", "comunitat": "CATALUÑA"},
}


class BDNSConnector(Connector):
    codi = "bdns"
    nom = "Base de Datos Nacional de Subvenciones"
    url = "https://www.infosubvenciones.es/bdnstrans/GE/es/inicio"
    llicencia = "https://www.infosubvenciones.es/bdnstrans/GE/es/avisolegal"

    def __init__(self, *args, since: str | None = None, admins: list[str] | None = None, **kwargs):
        super().__init__(*args, **kwargs)
        self.since = since  # dd/mm/yyyy
        self.admins = admins  # filtra ORGANS (p. ex. jobs separats per administració)

    def organ_ids(self, admin: str) -> list[int]:
        cfg = ORGANS[admin]
        tree = self.client.get_json(
            f"{API}/organos", params={"vpd": "GE", "idAdmon": cfg["idAdmon"]}
        )
        ids: list[int] = []
        if cfg["idAdmon"] == "L":
            for prov in tree:
                if prov["descripcion"] != cfg["provincia"]:
                    continue
                for muni in prov.get("children", []):
                    if "muni" in cfg and muni["descripcion"] == cfg["muni"]:
                        ids.extend(org["id"] for org in muni.get("children", []))
                    for org in muni.get("children", []):
                        if org["descripcion"] == cfg.get("organ"):
                            ids.append(org["id"])
        else:
            for ca in tree:
                if ca["descripcion"].upper() == cfg["comunitat"]:
                    ids.extend(org["id"] for org in ca.get("children", []))
        return ids

    def ingest(self) -> int:
        total = 0
        for admin in self.admins or ORGANS:
            ids = self.organ_ids(admin)
            if not ids:
                log.warning("bdns: cap òrgan trobat per %s", admin)
                continue
            total += self._ingest_organs(admin, ids)
        return total

    def _ingest_organs(self, admin: str, ids: list[int]) -> int:
        aid = administracio_id(self.conn, admin)
        page = 0
        written = 0
        while True:
            params = {
                "vpd": "GE",
                "page": page,
                "pageSize": PAGE_SIZE,
                "organos": ",".join(map(str, ids)),
            }
            if self.since:
                params["fechaDesde"] = self.since
            data = self.client.get_json(f"{API}/concesiones/busqueda", params=params)
            rows = []
            for c in data.get("content", []):
                nif, nom = split_nif(c.get("beneficiario"))
                rows.append(
                    {
                        "font": self.codi,
                        "id_extern": str(c["id"]),
                        "administracio_id": aid,
                        "organ": c.get("nivel3"),
                        "beneficiari": nom,
                        "beneficiari_nif": nif,
                        "import": c.get("importe"),
                        "data_concessio": c.get("fechaConcesion"),
                        "convocatoria_id": str(c.get("numeroConvocatoria") or ""),
                        "convocatoria": c.get("convocatoria"),
                        "instrument": (c.get("instrumento") or "").strip() or None,
                        "url_bases": c.get("urlBR"),
                        "raw": c,
                    }
                )
            written += upsert(self.conn, "subvencio", rows)
            self.conn.commit()
            log.info("bdns/%s pàgina %d/%s (%d)", admin, page + 1, data.get("totalPages"), written)
            page += 1
            if data.get("last", True) or not rows:
                break
            if self.limit and written >= self.limit:
                break
        return written
