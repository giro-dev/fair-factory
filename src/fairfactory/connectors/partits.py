"""Partit polític del cap de cada administració (taula `administracio_partit`).

Fonts:

- «Historial d'alcaldes/esses dels municipis catalans» (Socrata `2v2p-vu4h`):
  tots els alcaldes des de 1979, amb legislatura i dates de possessió i baixa.
  Crea les administracions municipals que falten (`mun_<ine5>`) — són les
  files que permeten acolorir un mapa municipal per partit.
- «Càrrecs electes dels ens locals» (Socrata `m5nd-xjza`): cap vigent (ordre
  1) dels ens no municipals — diputacions, consells comarcals, mancomunitats,
  consorcis… Els ajuntaments ja queden coberts per l'historial.
- `partits_curats`: registres verificats manualment per als ens que els
  datasets no cobreixen (Generalitat, Principat, Diputació de Girona —que al
  dataset de càrrecs hi surt sense nom— i l'Ajuntament de Siero).

Els codis de partit de les fonts són molt heterogenis («PSC-CP», «GGi-AMUNT»…);
`partit_nom` conserva el literal original i `partit_codi` n'ofereix una
normalització aproximada al partit estatal/autonòmic quan és identificable.
"""

from __future__ import annotations

import json
import logging
import re
import sqlite3
import unicodedata
from collections import defaultdict
from datetime import date, timedelta

from fairfactory.connectors.base import Connector
from fairfactory.db import administracio_id, upsert

log = logging.getLogger(__name__)

SOCRATA = "https://analisi.transparenciacatalunya.cat/resource"
VIEW = "https://analisi.transparenciacatalunya.cat/d"
PAGE = 20000
PROV_CAT = ("08", "17", "25", "43")  # prefixos INE10 dels ajuntaments catalans

# Regles ordenades sobre el codi de partit normalitzat (ASCII majúscules):
# el primer patró que hi encaixa guanya. És una aproximació — el literal
# original queda a `partit_nom`.
PARTIT_MAP = (
    (r"PSUC", "PSUC"),
    (r"UCD", "UCD"),
    (r"CDA|ARANES", "CDA"),
    (r"JXCAT|JUNTS", "Junts"),
    (r"PDCAT|PDECAT", "PDeCAT"),
    (r"CONVERGENCIA I UNIO|^CIU|\bCIU\b|CDC", "CiU"),
    (r"PSC|PSOE|FSA", "PSC/PSOE"),
    (r"ERC|ESQUERRA", "ERC"),
    (r"ICV|EUIA|ICV-EUIA", "ICV-EUiA"),
    (r"CUP", "CUP"),
    (r"COMU|PODEM", "Comuns/Podem"),
    (r"VOX", "VOX"),
    (r"CIUTADANS|C'?S\b|C'S", "Cs"),
    (r"ALIANZA POPULAR|ALIANCA POPULAR|^AP\b|AP/PDP|^PP|\bPP\b|PARTIT POPULAR", "PP/AP"),
    (r"CDS", "CDS"),
    (r"FAC|FORO", "FAC"),
    (r"\bIU\b|IZQUIERDA|ESQUERRA UNIDA", "IU"),
    (r"PNV|EAJ|\bEA\b", "PNV/EA"),
    (r"BNG", "BNG"),
    (r"INDEPENDENT", "Indep."),
    (r"NO ADSCRIT", "No adscrit"),
    (r"ALTRES|^N/A$", "Altres"),
)


def normalize_partit(text: str | None) -> str | None:
    """Codi curt del partit: normalització aproximada del literal de la font."""
    if not text or not text.strip():
        return None
    t = "".join(c for c in unicodedata.normalize("NFD", text) if unicodedata.category(c) != "Mn")
    t = re.sub(r"\s+", " ", t).strip().upper()
    for pat, codi in PARTIT_MAP:
        if re.search(pat, t):
            return codi
    return t[:40]


def _d(v) -> str | None:
    """'2023-06-17T00:00:00.000' -> '2023-06-17'"""
    return v[:10] if isinstance(v, str) and re.match(r"\d{4}-\d{2}-\d{2}", v) else None


def _legislatura_anys(text: str | None) -> tuple[int, int] | None:
    m = re.match(r"\s*(\d{4})-(\d{4})", text or "")
    return (int(m.group(1)), int(m.group(2))) if m else None


def _prev_day(iso: str) -> str:
    return (date.fromisoformat(iso) - timedelta(days=1)).isoformat()


def _admins_per_ine10(conn: sqlite3.Connection) -> dict[str, int]:
    """codi_10/ine10 -> administracio.id (ine10 pot ser str o llista)."""
    out: dict[str, int] = {}
    for r in conn.execute("SELECT id, identificadors FROM administracio"):
        ids = json.loads(r["identificadors"] or "{}")
        ine10 = ids.get("ine10")
        for v in ine10 if isinstance(ine10, list) else [ine10]:
            if v:
                out[str(v)] = r["id"]
    return out


def _ensure_admin(
    conn: sqlite3.Connection, cache: dict[str, int], codi: str, nom: str, ine10: str
) -> int:
    if codi in cache:
        return cache[codi]
    row = conn.execute("SELECT id FROM administracio WHERE codi = ?", (codi,)).fetchone()
    if row:
        cache[codi] = row["id"]
        return row["id"]
    cur = conn.execute(
        """INSERT INTO administracio (codi, nom, nivell, comunitat, identificadors)
           VALUES (?, ?, 'local', 'Catalunya', ?)""",
        (codi, nom, json.dumps({"ine10": ine10})),
    )
    cache[codi] = cur.lastrowid
    return cur.lastrowid


class _SocrataConnector(Connector):
    """Base comuna: paginació SoQL i mapa ine10 -> administracio."""

    def _socrata_all(self, resource: str, params: dict | None = None) -> list[dict]:
        out: list[dict] = []
        offset = 0
        while True:
            data = self.client.get_json(
                f"{SOCRATA}/{resource}.json",
                params={**(params or {}), "$limit": PAGE, "$offset": offset},
            )
            if not data:
                break
            out.extend(r for r in data if isinstance(r, dict))
            if self.limit and len(out) >= self.limit:
                return out[: self.limit]
            if len(data) < PAGE:
                break
            offset += len(data)
        return out


class AlcaldesHistorialConnector(_SocrataConnector):
    codi = "gencat_historial_alcaldes"
    grups = ("catalunya",)
    taules = ("administracio_partit", "administracio")
    nom = "Generalitat de Catalunya — Historial d'alcaldes/esses (Socrata)"
    url = f"{VIEW}/2v2p-vu4h"
    llicencia = "Dades obertes Generalitat de Catalunya (IODL/CC0)"

    def ingest(self) -> int:
        data = self._socrata_all("2v2p-vu4h")
        if not data:
            raise RuntimeError("l'historial d'alcaldes ha retornat 0 registres")
        ine_map = _admins_per_ine10(self.conn)
        per_ens: dict[str, list[dict]] = defaultdict(list)
        for r in data:
            if r.get("codi_10"):
                per_ens[r["codi_10"]].append(r)
        # la legislatura més recent del dataset és la vigent
        vigent = max(
            (
                a[0]
                for rs in per_ens.values()
                for r in rs
                if (a := _legislatura_anys(r.get("legislatura_alcalde")))
            ),
            default=0,
        )
        cache: dict[str, int] = {}
        rows = []
        for codi10, rs in per_ens.items():
            aid = ine_map.get(codi10) or _ensure_admin(
                self.conn, cache, f"mun_{codi10[:5]}", rs[0].get("nom_ens") or codi10, codi10
            )
            rs.sort(
                key=lambda r: (
                    _legislatura_anys(r.get("legislatura_alcalde")) or (0, 0),
                    _d(r.get("data_pressa_possessio")) or "",
                )
            )
            for i, r in enumerate(rs):
                legs = _legislatura_anys(r.get("legislatura_alcalde"))
                inici = _d(r.get("data_pressa_possessio"))
                estimat = inici is None
                if inici is None and legs:
                    inici = f"{legs[0]}-06-15"  # investidura municipal ~15 de juny
                fi = _d(r.get("data_baixa"))
                if fi is None and i + 1 < len(rs):
                    nxt = rs[i + 1]
                    nxt_inici = _d(nxt.get("data_pressa_possessio"))
                    if nxt_inici is None and (
                        legs_n := _legislatura_anys(nxt.get("legislatura_alcalde"))
                    ):
                        nxt_inici = f"{legs_n[0]}-06-15"
                    if nxt_inici and inici and nxt_inici > inici:
                        fi = _prev_day(nxt_inici)
                if fi is None and legs:
                    fi = None if legs[0] == vigent else f"{legs[1]}-06-14"
                nom = re.sub(r"\s*\(independent\)\s*$", "", r.get("nom_alcalde") or "").strip()
                rows.append(
                    {
                        "font": self.codi,
                        "id_extern": f"{codi10}|{r.get('legislatura_alcalde')}|{inici}|{nom}",
                        "administracio_id": aid,
                        "partit_codi": normalize_partit(r.get("partit_alcalde")),
                        "partit_nom": r.get("partit_alcalde") or None,
                        "responsable": nom or None,
                        "data_inici": inici,
                        "data_fi": fi,
                        "confianca": "mitjana" if estimat else "alta",
                        "url": self.url,
                        "raw": r,
                    }
                )
        n = upsert(self.conn, "administracio_partit", rows)
        self.conn.commit()
        return n


class CarrecsElectesConnector(_SocrataConnector):
    """Cap vigent (ordre=1) dels ens locals NO municipals — diputacions,
    consells comarcals, mancomunitats i consorcis. Els ajuntaments ja tenen
    l'historial complet del dataset `2v2p-vu4h`."""

    codi = "gencat_carrecs_electes"
    grups = ("catalunya",)
    taules = ("administracio_partit", "administracio")
    nom = "Generalitat de Catalunya — Càrrecs electes dels ens locals (Socrata)"
    url = f"{VIEW}/m5nd-xjza"
    llicencia = "Dades obertes Generalitat de Catalunya (IODL/CC0)"

    def ingest(self) -> int:
        data = self._socrata_all("m5nd-xjza", params={"$where": "ordre=1"})
        if not data:
            raise RuntimeError("els càrrecs electes han retornat 0 registres")
        ine_map = _admins_per_ine10(self.conn)
        cache: dict[str, int] = {}
        rows = []
        for r in data:
            codi_ens = r.get("codi_ens") or ""
            if codi_ens[:2] in PROV_CAT:  # ajuntament: cobert per l'historial
                continue
            nom = (r.get("nom_regidor") or "").strip()
            if not nom and not (r.get("partit") or "").strip():
                continue  # fila incompleta (p. ex. diputacions sense nom)
            aid = ine_map.get(codi_ens) or _ensure_admin(
                self.conn,
                cache,
                f"ens_{codi_ens}",
                r.get("nom_complert") or codi_ens,
                codi_ens,
            )
            rows.append(
                {
                    "font": self.codi,
                    "id_extern": codi_ens,
                    "administracio_id": aid,
                    "partit_codi": normalize_partit(r.get("partit")),
                    "partit_nom": r.get("partit") or None,
                    "responsable": nom or None,
                    "data_inici": _d(r.get("data_nomenament")),
                    "data_fi": None,  # càrrec vigent
                    "confianca": "alta",
                    "url": self.url,
                    "raw": r,
                }
            )
        n = upsert(self.conn, "administracio_partit", rows)
        self.conn.commit()
        return n


WIKI_SIERO = "https://es.wikipedia.org/wiki/Ayuntamiento_de_Siero"
WIKI_ASTURIAS = "https://es.wikipedia.org/wiki/Presidente_del_Principado_de_Asturias"

# Registres verificats manualment per als ens que les fonts automàtiques no
# cobreixen. `confianca` 'alta' = font oficial, 'mitjana' = font secundària
# (Wikipedia/premsa) o dates aproximades de mandat.
SEEDS = [
    # Generalitat de Catalunya — presidents (Parlament/gencat)
    (
        "catalunya",
        "Junts",
        "Junts per Catalunya",
        "Joaquim Torra i Pla",
        "2018-05-17",
        "2020-09-28",
        "alta",
        "https://www.parlament.cat/web/composicio/govern",
    ),
    (
        "catalunya",
        "ERC",
        "Esquerra Republicana de Catalunya",
        "Pere Aragonès i Garcia",
        "2020-09-28",
        "2024-08-08",
        "alta",
        "https://www.parlament.cat/web/composicio/govern",
    ),
    (
        "catalunya",
        "PSC",
        "Partit dels Socialistes de Catalunya",
        "Salvador Illa i Roca",
        "2024-08-08",
        None,
        "alta",
        "https://www.parlament.cat/web/composicio/govern",
    ),
    # Principado de Asturias — presidents
    (
        "asturias",
        "FSA-PSOE",
        "Federación Socialista Asturiana (PSOE)",
        "Javier Fernández Fernández",
        "2012-07-26",
        "2019-07-15",
        "mitjana",
        WIKI_ASTURIAS,
    ),
    (
        "asturias",
        "FSA-PSOE",
        "Federación Socialista Asturiana (PSOE)",
        "Adrián Barbón Rodríguez",
        "2019-07-20",
        None,
        "alta",
        "https://www.asturias.es",
    ),
    # Diputació de Girona — el dataset de càrrecs no en porta el nom
    (
        "diputacio_girona",
        "Junts",
        "Junts per Catalunya",
        "Miquel Noguer i Planas",
        "2018-07-11",
        None,
        "alta",
        "https://www.ddgi.cat/web/nivell/369/s-/miquel-noguer-i-planas",
    ),
    # Ayuntamiento de Siero — historial (Wikipedia, dates de mandat aprox.)
    (
        "siero",
        "FSA-PSOE",
        "Federación Socialista Asturiana (PSOE)",
        "Manuel Marino Villa Díaz",
        "1983-05-23",
        "1995-06-17",
        "mitjana",
        WIKI_SIERO,
    ),
    (
        "siero",
        "PP",
        "Partido Popular",
        "José Aurelio Álvarez Fernández",
        "1995-06-17",
        "1999-06-12",
        "mitjana",
        WIKI_SIERO,
    ),
    (
        "siero",
        "FSA-PSOE",
        "Federación Socialista Asturiana (PSOE)",
        "Juan José Corrales Montequín",
        "1999-06-12",
        "2010-12-01",
        "mitjana",
        WIKI_SIERO,
    ),
    (
        "siero",
        "PP",
        "Partido Popular",
        "José Antonio Noval Cueto",
        "2010-12-01",
        "2011-06-11",
        "mitjana",
        WIKI_SIERO,
    ),
    (
        "siero",
        "FSA-PSOE",
        "Federación Socialista Asturiana (PSOE)",
        "Guillermo Martínez Suárez",
        "2011-06-11",
        "2012-03-01",
        "mitjana",
        WIKI_SIERO,
    ),
    (
        "siero",
        "FAC",
        "Foro Asturias",
        "Eduardo Martínez Llosa",
        "2012-03-01",
        "2015-06-13",
        "mitjana",
        WIKI_SIERO,
    ),
    (
        "siero",
        "FSA-PSOE",
        "Federación Socialista Asturiana (PSOE)",
        "Ángel Antonio García González",
        "2015-06-13",
        None,
        "alta",
        "https://www.ayto-siero.es/grupos-municipales/grupos-municipales-concejales/",
    ),
]


class PartitsCuratsConnector(Connector):
    codi = "partits_curats"
    grups = ("compartit",)
    taules = ("administracio_partit",)
    nom = "Registres de partits verificats manualment (fonts oficials)"
    url = "https://github.com/giro-dev/fair-factory"
    llicencia = "Dades públiques de càrrecs electes"

    def ingest(self) -> int:
        rows = [
            {
                "font": self.codi,
                "id_extern": f"{admin}|{inici}|{resp}",
                "administracio_id": administracio_id(self.conn, admin),
                "partit_codi": codi,
                "partit_nom": nom,
                "responsable": resp,
                "data_inici": inici,
                "data_fi": fi,
                "confianca": conf,
                "url": url,
            }
            for admin, codi, nom, resp, inici, fi, conf, url in SEEDS
        ]
        n = upsert(self.conn, "administracio_partit", rows)
        self.conn.commit()
        return n
