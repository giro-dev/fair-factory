"""Portal de Transparencia del Ayuntamiento de Siero (HTML + PDF).

- Inventari de documents (PDF/XLS/CSV) enllaçats des de les pàgines de transparència.
- Contractes menors formalitzats: PDF tabular (Nº Operación, Fase, Fecha, Aplicación, Importe,
  Nombre Ter., Texto Libre, Tipo Contrato) parsejat per posició de columna.
"""

from __future__ import annotations

import io
import logging
import re
from urllib.parse import urljoin

import pdfplumber
from bs4 import BeautifulSoup

from fairfactory.connectors.base import Connector, parse_es_date, parse_es_number
from fairfactory.db import administracio_id, upsert

log = logging.getLogger(__name__)

BASE = "https://www.ayto-siero.es"
PAGES = {
    "transparencia": f"{BASE}/portal-de-transparencia/",
    "contratos_menores": f"{BASE}/portal-de-transparencia/contratos-menores-formalizados/",
    "presupuestos": f"{BASE}/presupuestos-municipales/",
    "economico_financiera": f"{BASE}/informacion-economico-financiera/",
    "plenos": f"{BASE}/portal-de-transparencia/ordenes-del-dia-de-los-plenos-municipales/",
    "juntas_gobierno": f"{BASE}/portal-de-transparencia/juntas-de-gobierno-local/",
    "subvenciones": f"{BASE}/subvenciones/",
    "perfil_contratante": f"{BASE}/perfil-del-contratante/",
}
FILE_RE = re.compile(r"\.(pdf|xlsx?|csv|docx?|ods)(\?.*)?$", re.I)

# Columnes del llistat de contractes menors; cada una comença on comença la capçalera indicada
HEADERS = [
    ("operacio", "Nº"),
    ("data", "Fecha"),
    ("aplicacio", "Aplicación"),
    ("import_nom", "Importe"),
    ("text", "Texto"),
    ("tipus", "Tipo"),
]
ROW_RE = re.compile(r"^(\d{12})([A-Z]+)$")
IMPORT_RE = re.compile(r"^(-?[\d.]*\d,\d{2})(.*)$")


def extract_documents(html: str, page_url: str, categoria: str) -> list[dict]:
    soup = BeautifulSoup(html, "lxml")
    seen: set[str] = set()
    docs = []
    for a in soup.select("a[href]"):
        href = urljoin(page_url, a["href"].strip())
        if href in seen or not (FILE_RE.search(href) or "/descarga/" in href):
            continue
        seen.add(href)
        title = a.get_text(" ", strip=True) or a.get("title") or href.rsplit("/", 1)[-1]
        ext = FILE_RE.search(href)
        docs.append(
            {
                "id_extern": href,
                "titol": title[:500],
                "categoria": categoria,
                "url": href,
                "tipus_fitxer": ext.group(1).lower() if ext else "pdf",
                "pagina_origen": page_url,
            }
        )
    return docs


def _column_bounds(words: list[dict], prev: list[tuple[str, float, float]] | None):
    """Derive column x-ranges from the header row; fall back to the previous page's."""
    starts = {}
    for name, header in HEADERS:
        for w in words:
            if w["text"] == header and name not in starts:
                starts[name] = w["x0"] - 2
    if len(starts) < len(HEADERS):
        return prev
    xs = [starts[name] for name, _ in HEADERS]
    return [
        (name, 0 if i == 0 else xs[i], xs[i + 1] if i + 1 < len(xs) else 10_000)
        for i, (name, _) in enumerate(HEADERS)
    ]


def _rows_from_page(page, cols) -> tuple[list[dict[str, str]], list]:
    words = page.extract_words(keep_blank_chars=False, use_text_flow=False)
    cols = _column_bounds(words, cols)
    if cols is None:
        return [], None
    lines: dict[int, list] = {}
    for w in words:
        lines.setdefault(round(w["top"]), []).append(w)
    rows = []
    for top in sorted(lines):
        cells = {name: [] for name, _, _ in cols}
        for w in sorted(lines[top], key=lambda w: w["x0"]):
            for name, x0, x1 in cols:
                if x0 <= w["x0"] < x1:
                    cells[name].append(w["text"])
                    break
        rows.append({k: " ".join(v) for k, v in cells.items()})
    return rows, cols


def parse_contratos_menores(pdf_bytes: bytes) -> list[dict]:
    """Parse the 'contratos menores formalizados' PDF into contracte dicts."""
    out: list[dict] = []
    current: dict | None = None
    cols = None
    with pdfplumber.open(io.BytesIO(pdf_bytes)) as pdf:
        for page in pdf.pages:
            rows, cols = _rows_from_page(page, cols)
            for cells in rows:
                m = ROW_RE.match(cells["operacio"].replace(" ", ""))
                if m:
                    operacio, fase = m.groups()
                    im = IMPORT_RE.match(cells["import_nom"])
                    import_txt, nom = (
                        (im.group(1), im.group(2)) if im else (None, cells["import_nom"])
                    )
                    data_txt = cells["data"][:10]
                    aplic = cells["aplicacio"].split()
                    current = {
                        "id_extern": f"{operacio}{fase}",
                        "expedient": operacio,
                        "estat": fase,
                        "data_adjudicacio": parse_es_date(data_txt),
                        "import_adjudicacio": parse_es_number(import_txt),
                        "adjudicatari_nom": nom.strip() or None,
                        "titol": cells["text"].strip(),
                        "tipus": cells["tipus"].strip() or None,
                        "raw": {
                            "programa": aplic[0] if aplic else None,
                            "economica": aplic[1] if len(aplic) > 1 else None,
                            "exercici": cells["data"][10:14] or None,
                        },
                    }
                    out.append(current)
                elif current is not None and not cells["operacio"] and not cells["data"]:
                    # continuation line (wrapped name / description)
                    if cells["import_nom"]:
                        current["adjudicatari_nom"] = (
                            f"{current['adjudicatari_nom'] or ''} {cells['import_nom']}".strip()
                        )
                    if cells["text"]:
                        current["titol"] = f"{current['titol']} {cells['text']}".strip()
    return out


class SieroConnector(Connector):
    codi = "siero_transparencia"
    grups = ("asturias",)
    taules = ("document", "contracte")
    nom = "Ayuntamiento de Siero — Portal de Transparencia"
    url = PAGES["transparencia"]

    def ingest(self) -> int:
        aid = administracio_id(self.conn, "siero")
        written = 0
        docs_by_url: dict[str, dict] = {}
        for categoria, url in PAGES.items():
            try:
                html = self.client.get(url).text
            except Exception as exc:  # noqa: BLE001 - keep going with other pages
                log.warning("siero: no s'ha pogut llegir %s: %s", url, exc)
                continue
            for d in extract_documents(html, url, categoria):
                docs_by_url.setdefault(d["url"], d)
        rows = [{**d, "font": self.codi, "administracio_id": aid} for d in docs_by_url.values()]
        written += upsert(self.conn, "document", rows)
        self.conn.commit()
        log.info("siero: %d documents", len(rows))

        for d in docs_by_url.values():
            if d["categoria"] != "contratos_menores" or d["tipus_fitxer"] != "pdf":
                continue
            try:
                pdf = self.client.get(d["url"], cache=True).content
                contracts = parse_contratos_menores(pdf)
            except Exception as exc:  # noqa: BLE001
                log.warning("siero: error parsejant %s: %s", d["url"], exc)
                continue
            organ = d["titol"]
            rows = [
                {
                    **c,
                    "id_extern": f"{d['url'].rsplit('/', 1)[-1]}:{c['id_extern']}",
                    "font": self.codi,
                    "administracio_id": aid,
                    "organ": organ,
                    "procediment": "Contrato menor",
                    "url": d["url"],
                }
                for c in contracts
            ]
            written += upsert(self.conn, "contracte", rows)
            self.conn.commit()
            log.info("siero: %d contractes menors de %s", len(rows), d["url"])
        return written
