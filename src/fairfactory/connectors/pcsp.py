"""Plataforma de Contratación del Sector Público — sindicació ATOM/CODICE.

Feed: https://contrataciondelestado.es/sindicacion/sindicacion_643/licitacionesPerfilesContratanteCompleto3.atom
Cada pàgina (~17 MB) enllaça la pàgina anterior via <link rel="next">. Es filtren les entrades
d'òrgans de contractació d'Astúries (NUTS ES12*, ciutat o nom de l'òrgan).
"""

from __future__ import annotations

import logging
from xml.etree import ElementTree as ET

from fairfactory.connectors.base import Connector
from fairfactory.db import administracio_id, upsert

log = logging.getLogger(__name__)

FEED = (
    "https://contrataciondelestado.es/sindicacion/sindicacion_643/"
    "licitacionesPerfilesContratanteCompleto3.atom"
)

NS = {
    "a": "http://www.w3.org/2005/Atom",
    "cbc": "urn:dgpe:names:draft:codice:schema:xsd:CommonBasicComponents-2",
    "cac": "urn:dgpe:names:draft:codice:schema:xsd:CommonAggregateComponents-2",
    "cac-place-ext": "urn:dgpe:names:draft:codice-place-ext:schema:xsd:CommonAggregateComponents-2",
    "cbc-place-ext": "urn:dgpe:names:draft:codice-place-ext:schema:xsd:CommonBasicComponents-2",
}

CONTRACT_TYPES = {
    "1": "Suministros",
    "2": "Servicios",
    "3": "Obras",
    "7": "Administrativo especial",
    "8": "Privado",
    "21": "Gestión de servicios públicos",
    "31": "Concesión de obras",
    "32": "Concesión de servicios",
    "40": "Colaboración público-privada",
    "50": "Patrimonial",
    "999": "Otros",
}
PROCEDURES = {
    "1": "Abierto",
    "2": "Restringido",
    "3": "Negociado sin publicidad",
    "4": "Negociado con publicidad",
    "5": "Diálogo competitivo",
    "6": "Contrato menor",
    "7": "Derivado de acuerdo marco",
    "8": "Concurso de proyectos",
    "9": "Abierto simplificado",
    "10": "Asociación para la innovación",
    "11": "Derivado de asociación para la innovación",
    "12": "Basado en sistema dinámico de adquisición",
    "13": "Licitación con negociación",
    "100": "Normas internas",
    "999": "Otros",
}
STATUS = {
    "PRE": "Anuncio previo",
    "PUB": "Publicada",
    "EV": "Evaluación",
    "ADJ": "Adjudicada",
    "RES": "Resuelta",
    "ANUL": "Anulada",
}

ASTURIAS_NUTS = "ES12"
ASTURIAS_KEYWORDS = ("asturias", "siero")
SIERO_KEYWORDS = ("siero",)


def _text(el, path: str) -> str | None:
    found = el.find(path, NS)
    return found.text.strip() if found is not None and found.text else None


def _float(el, path: str) -> float | None:
    t = _text(el, path)
    try:
        return float(t) if t else None
    except ValueError:
        return None


def parse_entry(entry: ET.Element) -> dict | None:
    """Convert an ATOM entry into a contracte row; returns None for tombstones."""
    status = entry.find("cac-place-ext:ContractFolderStatus", NS)
    if status is None:
        return None
    party = status.find("cac-place-ext:LocatedContractingParty/cac:Party", NS)
    project = status.find("cac:ProcurementProject", NS)
    result = status.find("cac:TenderResult", NS)
    process = status.find("cac:TenderingProcess", NS)

    organ = _text(party, "cac:PartyName/cbc:Name") if party is not None else None
    organ_nif = None
    city = None
    if party is not None:
        for pid in party.findall("cac:PartyIdentification/cbc:ID", NS):
            if pid.get("schemeName") == "NIF":
                organ_nif = (pid.text or "").strip() or None
        city = _text(party, "cac:PostalAddress/cbc:CityName")

    nuts = (
        _text(project, "cac:RealizedLocation/cbc:CountrySubentityCode")
        if project is not None
        else None
    )
    region = (
        _text(project, "cac:RealizedLocation/cbc:CountrySubentity") if project is not None else None
    )

    awardee_name = awardee_nif = None
    if result is not None:
        awardee = result.find("cac:WinningParty", NS)
        if awardee is not None:
            awardee_name = _text(awardee, "cac:PartyName/cbc:Name")
            awardee_nif = _text(awardee, "cac:PartyIdentification/cbc:ID")

    type_code = _text(project, "cbc:TypeCode") if project is not None else None
    proc_code = _text(process, "cbc:ProcedureCode") if process is not None else None
    status_code = _text(status, "cbc-place-ext:ContractFolderStatusCode")
    link = entry.find("a:link", NS)

    return {
        "id_extern": _text(entry, "a:id"),
        "organ": organ,
        "organ_nif": organ_nif,
        "expedient": _text(status, "cbc:ContractFolderID"),
        "titol": _text(entry, "a:title"),
        "tipus": CONTRACT_TYPES.get(type_code, type_code),
        "procediment": PROCEDURES.get(proc_code, proc_code),
        "estat": STATUS.get(status_code, status_code),
        "import_licitacio": _float(project, "cac:BudgetAmount/cbc:TaxExclusiveAmount")
        if project is not None
        else None,
        "import_adjudicacio": _float(
            result, "cac:AwardedTenderedProject/cac:LegalMonetaryTotal/cbc:TaxExclusiveAmount"
        )
        if result is not None
        else None,
        "data_publicacio": (_text(entry, "a:updated") or "")[:10] or None,
        "data_adjudicacio": _text(result, "cbc:AwardDate") if result is not None else None,
        "adjudicatari_nom": awardee_name,
        "adjudicatari_nif": awardee_nif,
        # conserva el 0 legítim (contracte sense ofertes, senyal de baixa concurrència)
        "num_ofertes": (
            int(q) if (q := _text(result, "cbc:ReceivedTenderQuantity")) not in (None, "") else None
        )
        if result is not None
        else None,
        "cpv": ",".join(
            c.text.strip()
            for c in project.findall(
                "cac:RequiredCommodityClassification/cbc:ItemClassificationCode", NS
            )
            if c.text
        )
        if project is not None
        else None,
        "url": link.get("href") if link is not None else None,
        "_nuts": nuts,
        "_region": region,
        "_city": city,
    }


def classify(row: dict) -> str | None:
    """Return the administracio codi ('siero', 'asturias', 'figueres',
    'diputacio_girona', 'catalunya') or None if the entry is out of scope."""
    organ = (row.get("organ") or "").lower()
    haystack = " ".join(
        filter(None, [row.get("organ"), row.get("_city"), row.get("_region")])
    ).lower()
    nuts = row.get("_nuts") or ""
    # Catalunya: només per nom d'òrgan (el NUTS ES51 cobriria tota la CA)
    if "figueres" in organ:
        return "figueres"
    if "girona" in organ and ("diputaci" in organ or "provincial" in organ):
        return "diputacio_girona"
    if "ajuntament de girona" in organ or "ayuntamiento de girona" in organ:
        return "girona"
    if "generalitat" in organ:
        return "catalunya"
    # Astúries: idem — ES12 és el NUTS2 de tota la CA; adscriure contractes
    # d'ajuntaments de Gijón/Oviedo al Principat contamina les estadístiques
    if any(k in organ for k in SIERO_KEYWORDS):
        return "siero"
    if ("asturias" in organ or "asturiana" in organ) and not any(
        k in organ for k in ("ayuntamiento", "ayto", "municipio", "concejo", "vecinos")
    ):
        return "asturias"
    if nuts.startswith(ASTURIAS_NUTS):
        # òrgans regionals (consejerías, agències, empreses públiques del Principat)
        if any(k in organ for k in ("consejer", "principado", "sociedad pública", "agencia")):
            return "asturias"
        if any(k in haystack for k in SIERO_KEYWORDS):
            return "siero"
    return None


class PCSPConnector(Connector):
    codi = "pcsp"
    nom = "Plataforma de Contratación del Sector Público (sindicación)"
    url = "https://contrataciondelestado.es"
    llicencia = "https://contrataciondelestado.es/wps/portal/avisolegal"
    grups = ("compartit",)
    taules = ("contracte",)
    cli_options = (
        (
            "--pcsp-pages",
            {
                "type": int,
                "metavar": "N",
                "help": "pàgines màximes del feed ATOM de la PCSP (~17 MB cadascuna)",
            },
            "max_pages",
        ),
    )

    def __init__(self, *args, max_pages: int = 20, **kwargs):
        super().__init__(*args, **kwargs)
        self.max_pages = max_pages  # sostre de seguretat; la parada real és el checkpoint

    def ingest(self) -> int:
        url: str | None = FEED
        written = 0
        pages = 0
        # punt de represa: atura quan el feed arriba a entrades anteriors a la
        # darrera ingesta amb èxit (el feed va ordenat per <updated> desc)
        last_ok = self.conn.execute(
            "SELECT MAX(fi) FROM ingest_run WHERE font = ? AND estat = 'ok'", (self.codi,)
        ).fetchone()[0]
        admin_ids = {
            codi: administracio_id(self.conn, codi)
            for codi in ("siero", "asturias", "figueres", "girona", "diputacio_girona", "catalunya")
        }
        while url and pages < self.max_pages:
            resp = self.client.get(url, cache=url != FEED)
            root = ET.fromstring(resp.content)
            rows = []
            updateds = []
            for entry in root.findall("a:entry", NS):
                if upd := _text(entry, "a:updated"):
                    updateds.append(upd)
                row = parse_entry(entry)
                if row is None:
                    continue
                admin = classify(row)
                if admin is None:
                    continue
                raw = {k: v for k, v in row.items() if k.startswith("_")}
                row = {k: v for k, v in row.items() if not k.startswith("_")}
                row.update(font=self.codi, administracio_id=admin_ids[admin], raw=raw)
                rows.append(row)
            written += upsert(self.conn, "contracte", rows)
            self.conn.commit()
            pages += 1
            log.info("pcsp pàgina %d: %d entrades (%d acumulades)", pages, len(rows), written)
            nxt = root.find("a:link[@rel='next']", NS)
            url = nxt.get("href") if nxt is not None else None
            if last_ok and updateds and max(updateds) < last_ok:
                log.info("pcsp: checkpoint assolit (entrades anteriors a %s)", last_ok)
                break
            if self.limit and written >= self.limit:
                break
        return written
