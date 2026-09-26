"""Ajuntament de Figueres — Dades Obertes (CKAN AOC) i perfil de contractant.

- `FigueresCkanConnector`: portal CKAN del Consorci AOC (dadesobertes.seu-e.cat).
  Conjunts multi-ens filtrats per CODI_ENS=1706690004 via `datastore_search`;
  cada registre és un document (RESUM + ENLLAÇ al document oficial).
- `FigueresContractesConnector`: taula «Licitacions en tràmit» de la seu
  electrònica (renderitzada al servidor, 21 columnes) → `contracte`.
"""

from __future__ import annotations

import json
import logging
import re
from urllib.parse import urljoin

from bs4 import BeautifulSoup

from fairfactory.connectors.base import Connector, parse_es_date
from fairfactory.db import administracio_id, upsert

log = logging.getLogger(__name__)

CODI_ENS = "1706690004"  # Ajuntament de Figueres
CKAN_API = "https://dadesobertes.seu-e.cat/api/action/datastore_search"

DATASETS = {
    "fc660dac-21cc-444c-8bef-00e13eb01520": "Estatuts",
    "ed9ac827-1734-412f-a33a-4c85fa850d7e": "Plecs de clàusules generals",
    "eb131bb1-f521-4aeb-9004-2fea1f372e89": "Càrrecs electes",
    "dbfe266c-da98-4f4c-9296-b16f40e6b23f": "Ordenances fiscals",
    "d9f131a1-5489-4cd7-a8ab-902b99df7968": "Convocatòries de personal (resultats)",
    "c633d5e6-b3b4-4ca1-8fc6-9e7c049c1e29": "Registre funcionaris habilitats",
    "b5d370d0-7916-48b6-8a69-3c7fa62a1467": "Actes de Ple",
    "b39a563c-3b96-4e64-a856-e313458f3dad": "Convocatòries de subvencions i ajuts",
    "ab53cbf3-a439-4f59-a2f5-658bee1994e5": "Dades generals de l'ens",
    "59218973-d6a6-4cc1-bd3f-c9b9b851cb53": "Calendaris i padrons fiscals",
}

# camps candidats per al títol del document, per ordre de preferència
TITLE_FIELDS = ("RESUM", "TITOL", "NOM", "DESCRIPCIO", "OBJECTE")
FILE_RE = re.compile(r"\.(pdf|xlsx?|csv|docx?|ods)(\?.*)?$", re.I)


def record_to_document(resource_id: str, categoria: str, rec: dict) -> dict:
    titol = next((str(rec[k]).strip() for k in TITLE_FIELDS if rec.get(k)), None)
    url = rec.get("ENLLAÇ") or rec.get("ENLLAC") or rec.get("URL")
    ext = FILE_RE.search(url) if url else None
    return {
        "id_extern": f"{resource_id}:{rec.get('_id', titol)}",
        "titol": (titol or categoria)[:500],
        "categoria": categoria,
        "url": url or CKAN_API,
        "tipus_fitxer": ext.group(1).lower() if ext else "json",
        "pagina_origen": f"{CKAN_API}?resource_id={resource_id}",
        "raw": rec,
    }


class FigueresCkanConnector(Connector):
    codi = "figueres_ckan"
    grups = ("catalunya",)
    taules = ("document",)
    nom = "Ajuntament de Figueres — Dades Obertes (CKAN AOC)"
    url = "https://dadesobertes.seu-e.cat"

    def ingest(self) -> int:
        aid = administracio_id(self.conn, "figueres")
        written = 0
        for resource_id, categoria in DATASETS.items():
            try:
                written += self._dataset(aid, resource_id, categoria)
            except Exception as exc:  # noqa: BLE001
                log.warning("figueres_ckan: error a %s: %s", categoria, exc)
        return written

    def _dataset(self, aid: int, resource_id: str, categoria: str) -> int:
        written = offset = 0
        while True:
            data = self.client.get_json(
                CKAN_API,
                params={
                    "resource_id": resource_id,
                    "filters": json.dumps({"CODI_ENS": CODI_ENS}),
                    "limit": 1000,
                    "offset": offset,
                },
            )
            records = data.get("result", {}).get("records", [])
            if not records:
                break
            rows = [
                {
                    **record_to_document(resource_id, categoria, r),
                    "font": self.codi,
                    "administracio_id": aid,
                }
                for r in records
            ]
            written += upsert(self.conn, "document", rows)
            self.conn.commit()
            offset += len(records)
            if self.limit and written >= self.limit:
                break
        log.info("figueres_ckan/%s: %d registres", categoria, written)
        return written


LICITACIONS = (
    "https://seu-e.cat/ca/web/figueres/govern-obert-i-transparencia/"
    "contractes-convenis-i-subvencions/relacio-de-contractes/"
    "licitacions-en-tramit-perfil-de-contractant"
)
NUM_RE = re.compile(r"(-?[\d.,]+)")


def _num(text: str | None) -> float | None:
    """'63428.0' -> 63428.0; '63.428,0' -> 63428.0"""
    if not text:
        return None
    m = NUM_RE.search(text)
    if not m:
        return None
    t = m.group(1)
    if "," in t:
        t = t.replace(".", "").replace(",", ".")
    try:
        return float(t)
    except ValueError:
        return None


def parse_licitacions(html: str, page_url: str = LICITACIONS) -> list[dict]:
    """Parse the 21-column 'Licitacions en tràmit' table into contracte rows."""
    soup = BeautifulSoup(html, "lxml")
    out = []
    for tr in soup.select("tbody tr"):
        tds = tr.find_all("td")
        if len(tds) < 18:
            continue
        txt = [td.get_text(" ", strip=True) for td in tds]
        link = tds[17].find("a", href=True)
        url = urljoin(page_url, link["href"]) if link else page_url
        out.append(
            {
                "id_extern": txt[2] or txt[0],
                "expedient": txt[2] or None,
                "organ": txt[1] or None,
                "titol": txt[0],
                "tipus": txt[4] or None,
                "procediment": txt[6] or None,
                "import_licitacio": _num(txt[8]),
                "url": url,
                "raw": {
                    "tipus_expedient": txt[3] or None,
                    "subtipus": txt[5] or None,
                    "descripcio": txt[7] or None,
                    "durada": txt[9] or None,
                    "ambit": txt[10] or None,
                    "termini_ofertes": parse_es_date(txt[11]),
                    "valor_estimat": _num(txt[12]),
                    "subhasta": txt[13] or None,
                },
            }
        )
    return out


class FigueresContractesConnector(Connector):
    codi = "figueres_contractes"
    grups = ("catalunya",)
    taules = ("contracte",)
    nom = "Ajuntament de Figueres — Licitacions (perfil de contractant)"
    url = LICITACIONS

    def ingest(self) -> int:
        aid = administracio_id(self.conn, "figueres")
        html = self.client.get(LICITACIONS).text
        rows = [{**r, "font": self.codi, "administracio_id": aid} for r in parse_licitacions(html)]
        n = upsert(self.conn, "contracte", rows)
        self.conn.commit()
        return n
