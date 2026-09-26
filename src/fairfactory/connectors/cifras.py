"""«Siero en cifras» — Observatorio Socioeconómico (portalestadistico.com).

La pàgina `https://www.ayto-siero.es/siero-en-cifras/` enllaça al portal
estadístic extern, que renderitza al servidor una fitxa per cada indicador:
nom, darrer valor amb unitat, variació anual, període, font i descripció,
agrupats en seccions (Demografía, Mercado de Trabajo, ...). Es desa cada
indicador a la taula `indicador`.
"""

from __future__ import annotations

import logging
import re
from urllib.parse import urljoin

from bs4 import BeautifulSoup

from fairfactory.connectors.base import Connector
from fairfactory.db import administracio_id, upsert

log = logging.getLogger(__name__)

LANDING = "https://www.ayto-siero.es/siero-en-cifras/"
FALLBACK = "https://portalestadistico.com/municipioencifras/?pn=ayto-siero&pc=FAH59"
PORTAL_RE = re.compile(r"https?://portalestadistico\.com/[^\"'<>\s]+")
VALUE_RE = re.compile(r"^\s*(-?[\d.,]+)\s*(.*)$")
YEAR_RE = re.compile(r"(?:19|20)\d{2}")


def _num_es(text: str) -> float | None:
    """Spanish-formatted number: '.' thousands, ',' decimals. '52.997' -> 52997.0"""
    try:
        return float(text.replace(".", "").replace(",", "."))
    except ValueError:
        return None


def portal_url(html: str) -> str | None:
    """Extract the portalestadistico.com link from the landing page."""
    m = PORTAL_RE.search(html)
    return m.group(0).replace("&#038;", "&") if m else None


def _value_unit(text: str | None) -> tuple[float | None, str | None]:
    """'52.997 personas' -> (52997.0, 'personas'); '37.495 €' -> (37495.0, '€')."""
    if not text:
        return None, None
    m = VALUE_RE.match(text)
    if not m:
        return None, None
    return _num_es(m.group(1)), m.group(2).strip() or None


def _text(el) -> str | None:
    if el is None:
        return None
    return el.get_text(" ", strip=True).replace("\xa0", " ").strip() or None


def _kv_rows(card) -> dict[str, str]:
    """Label/value pairs of the 'tabla_alineada_vertical_arriba' rows (Periodo, Fuente)."""
    out = {}
    for tr in card.select("table.tabla_alineada_vertical_arriba tr"):
        tds = tr.find_all("td")
        if len(tds) >= 2:
            out[tds[0].get_text(strip=True).rstrip(":")] = _text(tds[1])
    return out


def parse_indicators(html: str, url: str) -> list[dict]:
    """Parse the indicator cards of a municipioencifras page."""
    soup = BeautifulSoup(html, "lxml")
    rows = []
    section = None
    for el in soup.find_all(["th", "table"]):
        classes = el.get("class") or []
        if el.name == "th" and "Titulo_Fila" in classes:
            section = el.get_text(" ", strip=True) or None
            continue
        if el.name != "table" or "Tablas_datos" not in classes:
            continue
        if el.find_parent("table", class_="Tablas_datos") is not None:
            continue  # nested table inside a card
        name_el = el.select_one("h4.h4_grande")
        value_el = el.select_one("h4[class*=h4_muy_grande]")
        if name_el is None:
            continue
        nom = _text(name_el)
        valor = _text(value_el)
        valor_num, unitat = _value_unit(valor)
        var = None
        var_label = el.find(string=re.compile(r"Variación anual"))
        if var_label is not None:
            var = _text(var_label.parent.find_next_sibling())
        kv = _kv_rows(el)
        desc = el.select_one("p.texto_contenido")
        periode = kv.get("Periodo")
        year = YEAR_RE.search(periode or "")
        rows.append(
            {
                "id_extern": f"{section}|{nom}" if section else nom,
                "seccio": section,
                "nom": nom,
                "valor": valor,
                "valor_num": valor_num,
                "unitat": unitat,
                "variacio_anual": var,
                "periode": periode,
                "any": int(year.group(0)) if year else None,
                "font_dada": kv.get("Fuente"),
                "descripcio": _text(desc),
                "url": url,
            }
        )
    return rows


class SieroCifrasConnector(Connector):
    codi = "siero_cifras"
    nom = "Siero en cifras — Observatorio Socioeconómico (portalestadistico.com)"
    url = LANDING
    llicencia = "Informació pública sectorial — font citada a cada indicador"

    def ingest(self) -> int:
        aid = administracio_id(self.conn, "siero")
        url = portal_url(self.client.get(LANDING).text)
        if url is None:
            log.warning("siero_cifras: enllaç a portalestadistico no trobat; uso el de reserva")
            url = FALLBACK
        else:
            url = urljoin(LANDING, url)
        html = self.client.get(url).text
        rows = [
            {**r, "font": self.codi, "administracio_id": aid} for r in parse_indicators(html, url)
        ]
        n = upsert(self.conn, "indicador", rows)
        self.conn.commit()
        return n
