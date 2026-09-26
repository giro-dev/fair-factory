"""Sede Electrónica del Ayuntamiento de Siero (siero.sedelectronica.es).

El tablón de anuncios (`/board`) mostra els darrers anuncis públics en una taula
HTML renderitzada al servidor (Wicket): Documento, Expediente, Procedimiento,
Categoría, Descripción y Fecha de Publicación. Cada anunci enllaça a
`/preview-document/<uuid>`, que mostra el PDF original. La paginació depèn de
tokens de sessió, així que el connector recull els anuncis visibles a cada
execució i els acumula amb upserts idempotents.
"""

from __future__ import annotations

import logging
import re
from urllib.parse import urljoin

from bs4 import BeautifulSoup

from fairfactory.connectors.base import Connector, parse_es_date
from fairfactory.db import administracio_id, upsert

log = logging.getLogger(__name__)

BASE = "https://siero.sedelectronica.es"
BOARD = f"{BASE}/board"
CELLS = {
    "titol": "class_name",
    "expedient": "class_folderCode",
    "procediment": "class_folderName",
    "categoria": "class_boardCategory",
    "descripcio": "class_description",
    "data_publicacio": "class_dateFrom",
}
UUID_RE = re.compile(r"/preview-document/([0-9a-f-]{36})")


def parse_board(html: str, page_url: str = BOARD) -> list[dict]:
    """Parse the AdvertisementBoardListPanel table into document rows."""
    soup = BeautifulSoup(html, "lxml")
    table = soup.find("table", class_=re.compile(r"AdvertisementBoardListPanel"))
    if table is None:
        return []
    docs = []
    for tr in table.select("tbody tr"):
        cells = {key: tr.find("td", class_=cls) for key, cls in CELLS.items()}
        if cells["titol"] is None or cells["expedient"] is None:
            continue
        link = cells["titol"].find("a", href=True)
        url = urljoin(page_url, link["href"]) if link else page_url
        m = UUID_RE.search(url)
        docs.append(
            {
                "id_extern": m.group(1) if m else url,
                "titol": (link.get_text(" ", strip=True) if link else None)
                or cells["titol"].get_text(" ", strip=True),
                "categoria": "tablon",
                "url": url,
                "tipus_fitxer": "pdf",
                "pagina_origen": page_url,
                "raw": {
                    "expedient": cells["expedient"].get_text(" ", strip=True) or None,
                    "procediment": cells["procediment"].get_text(" ", strip=True) or None,
                    "categoria": cells["categoria"].get_text(" ", strip=True) or None,
                    "descripcio": cells["descripcio"].get_text(" ", strip=True) or None,
                    "data_publicacio": parse_es_date(
                        cells["data_publicacio"].get_text(" ", strip=True)
                    ),
                },
            }
        )
    return docs


class SedeSieroConnector(Connector):
    codi = "siero_sede"
    nom = "Ayuntamiento de Siero — Sede Electrónica (tablón de anuncios)"
    url = BOARD

    def ingest(self) -> int:
        aid = administracio_id(self.conn, "siero")
        html = self.client.get(BOARD).text
        rows = [{**d, "font": self.codi, "administracio_id": aid} for d in parse_board(html)]
        n = upsert(self.conn, "document", rows)
        self.conn.commit()
        return n
