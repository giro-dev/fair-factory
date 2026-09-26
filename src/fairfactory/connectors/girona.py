"""Diputació de Girona — pressuposts i documents econòmics (seu.ddgi.cat).

La pàgina de pressupost llista els documents oficials per exercici
(Pressupost general, Al·legacions, Compte general) enllaçats a PDFs.
Cada enllaç es desa com a `document`.
"""

from __future__ import annotations

import logging
import re
from urllib.parse import urljoin

from bs4 import BeautifulSoup

from fairfactory.connectors.base import Connector
from fairfactory.db import administracio_id, upsert

log = logging.getLogger(__name__)

BASE = "https://www.ddgi.cat"
PAGES = {
    "pressupost": f"{BASE}/web/nivell/530/s-0/pressupost",
}
DOC_RE = re.compile(r"(/web/recursos/document/|\.pdf($|\?))", re.I)


def extract_documents(html: str, page_url: str, categoria: str) -> list[dict]:
    """Pressupost/al·legacions links -> document rows."""
    soup = BeautifulSoup(html, "lxml")
    seen: set[str] = set()
    docs = []
    for a in soup.select("a[href]"):
        href = urljoin(page_url, a["href"].strip())
        if href in seen or not DOC_RE.search(href):
            continue
        seen.add(href)
        title = a.get_text(" ", strip=True) or href.rsplit("/", 1)[-1]
        docs.append(
            {
                "id_extern": href,
                "titol": title[:500],
                "categoria": categoria,
                "url": href,
                "tipus_fitxer": "pdf" if href.lower().endswith(".pdf") else "html",
                "pagina_origen": page_url,
            }
        )
    return docs


class DiputacioGironaConnector(Connector):
    codi = "diputacio_girona"
    grups = ("catalunya",)
    taules = ("document",)
    nom = "Diputació de Girona — Pressuposts (seu electrònica)"
    url = PAGES["pressupost"]

    def ingest(self) -> int:
        aid = administracio_id(self.conn, "diputacio_girona")
        docs: dict[str, dict] = {}
        for categoria, url in PAGES.items():
            html = self.client.get(url).text
            for d in extract_documents(html, url, categoria):
                docs.setdefault(d["url"], d)
        rows = [{**d, "font": self.codi, "administracio_id": aid} for d in docs.values()]
        n = upsert(self.conn, "document", rows)
        self.conn.commit()
        return n
