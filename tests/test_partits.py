"""Tests dels connectors de partits polítics (Socrata Generalitat + curats)."""

import pytest

from fairfactory.connectors.partits import (
    AlcaldesHistorialConnector,
    CarrecsElectesConnector,
    PartitsCuratsConnector,
    normalize_partit,
)
from fairfactory.db import administracio_id, connect, init_db


@pytest.fixture
def conn():
    c = connect(":memory:")
    init_db(c)
    return c


class FakeClient:
    """Client fals: retorna la mateixa resposta per qualsevol URL Socrata."""

    def __init__(self, records):
        self.records = records

    def get_json(self, url, **kw):
        offset = int(kw.get("params", {}).get("$offset", 0))
        limit = int(kw.get("params", {}).get("$limit", 20000))
        return self.records[offset : offset + limit]


HISTORIAL = [
    # Girona: mandat vigent amb data real
    {
        "codi_10": "1707920002",
        "nom_ens": "Ajuntament de Girona",
        "nom_alcalde": "Lluc Salellas Vilar",
        "partit_alcalde": "GGi-AMUNT",
        "legislatura_alcalde": "2023-2027",
        "data_pressa_possessio": "2023-06-17T00:00:00.000",
    },
    {
        "codi_10": "1707920002",
        "nom_ens": "Ajuntament de Girona",
        "nom_alcalde": "Marta Madrenas Mir",
        "partit_alcalde": "JxCAT",
        "legislatura_alcalde": "2019-2023",
        "data_pressa_possessio": "2019-06-15T00:00:00.000",
        "data_baixa": "2023-05-27T00:00:00.000",
    },
    # municipi nou (no està a ADMINISTRACIONS): una fila sense dates
    {
        "codi_10": "0800180001",
        "nom_ens": "Ajuntament d'Abrera",
        "nom_alcalde": "Jesús Naharro Rodríguez",
        "partit_alcalde": "PSC-CP",
        "legislatura_alcalde": "2023-2027",
        "data_pressa_possessio": "2023-06-17T00:00:00.000",
    },
    {
        "codi_10": "0800180001",
        "nom_ens": "Ajuntament d'Abrera",
        "nom_alcalde": "JESÚS NAHARRO RODRÍGUEZ",
        "partit_alcalde": "PSC-CP",
        "legislatura_alcalde": "2015-2019",
    },
    # canvi a mitja legislatura: dues files a la mateixa legislatura
    {
        "codi_10": "1706690004",
        "nom_ens": "Ajuntament de Figueres",
        "nom_alcalde": "Alcaldessa Nova",
        "partit_alcalde": "ERC-AM",
        "legislatura_alcalde": "2023-2027",
        "data_pressa_possessio": "2024-01-01T00:00:00.000",
    },
    {
        "codi_10": "1706690004",
        "nom_ens": "Ajuntament de Figueres",
        "nom_alcalde": "Alcalde Vell",
        "partit_alcalde": "JUNTS",
        "legislatura_alcalde": "2023-2027",
        "data_pressa_possessio": "2023-06-17T00:00:00.000",
        "data_baixa": "2023-12-31T00:00:00.000",
    },
]

CARRECS = [
    # ajuntament: s'ignora (cobert per l'historial)
    {
        "codi_ens": "1707920002",
        "nom_complert": "Ajuntament de Girona",
        "ordre": "1",
        "nom_regidor": "LLUC SALELLAS VILAR",
        "carrec": "Alcalde President",
        "partit": "GGi-AMUNT",
        "data_nomenament": "2023-06-17T00:00:00.000",
    },
    # consell comarcal: s'ingereix
    {
        "codi_ens": "8102490004",
        "nom_complert": "Consell Comarcal d'Osona",
        "ordre": "1",
        "nom_regidor": "MARÇAL ORTUÑO SOLIS",
        "carrec": "President",
        "partit": "ERC",
        "data_nomenament": "2025-07-30T00:00:00.000",
    },
    # diputació sense nom ni partit: es salta
    {"codi_ens": "8001760009", "nom_complert": "Diputació de Girona", "ordre": "1"},
]


def _partits(conn, admin_codi):
    aid = administracio_id(conn, admin_codi)
    return conn.execute(
        "SELECT * FROM administracio_partit WHERE administracio_id = ? ORDER BY data_inici",
        (aid,),
    ).fetchall()


def test_normalize_partit():
    assert normalize_partit("PSC-CP") == "PSC/PSOE"
    assert normalize_partit("(PSC-PSOE)-PM") == "PSC/PSOE"
    assert normalize_partit("PSUC") == "PSUC"
    assert normalize_partit("CiU") == "CiU"
    assert normalize_partit("Convergència i Unió") == "CiU"
    assert normalize_partit("JxCAT") == "Junts"
    assert normalize_partit("ERC-AM") == "ERC"
    assert normalize_partit("CUP-PA") == "CUP"
    assert normalize_partit("INDEPENDENTS") == "Indep."
    assert normalize_partit(None) is None
    assert normalize_partit("  ") is None
    assert normalize_partit("GGi-AMUNT") == "GGI-AMUNT"  # desconegut: netejat


def test_historial_mapeja_administracio_existent(conn):
    c = AlcaldesHistorialConnector(conn, client=FakeClient(HISTORIAL))
    assert c.run() == 6
    girona = _partits(conn, "girona")
    assert len(girona) == 2
    vigent = girona[-1]
    assert vigent["responsable"] == "Lluc Salellas Vilar"
    assert vigent["data_inici"] == "2023-06-17"
    assert vigent["data_fi"] is None
    assert vigent["confianca"] == "alta"


def test_historial_crea_municipis_nous(conn):
    AlcaldesHistorialConnector(conn, client=FakeClient(HISTORIAL)).run()
    mun = conn.execute("SELECT * FROM administracio WHERE codi = 'mun_08001'").fetchone()
    assert mun is not None
    assert mun["nom"] == "Ajuntament d'Abrera"
    assert "0800180001" in mun["identificadors"]
    rows = conn.execute(
        "SELECT * FROM administracio_partit WHERE administracio_id = ? ORDER BY data_inici",
        (mun["id"],),
    ).fetchall()
    assert len(rows) == 2
    # la fila sense dates estima la legislatura i tanca abans de la següent
    assert rows[0]["data_inici"] == "2015-06-15"
    assert rows[0]["data_fi"] == "2023-06-16"
    assert rows[0]["confianca"] == "mitjana"


def test_historial_canvi_mitja_legislatura(conn):
    AlcaldesHistorialConnector(conn, client=FakeClient(HISTORIAL)).run()
    figueres = _partits(conn, "figueres")
    assert figueres[0]["responsable"] == "Alcalde Vell"
    assert figueres[0]["data_fi"] == "2023-12-31"
    assert figueres[1]["data_fi"] is None  # vigent


def test_historial_font_buida_falla(conn):
    from fairfactory.db import ensure_font

    ensure_font(conn, "gencat_historial_alcaldes", "x", "http://x", "CC")
    with pytest.raises(RuntimeError):
        AlcaldesHistorialConnector(conn, client=FakeClient([])).ingest()


def test_historial_idempotent(conn):
    c = AlcaldesHistorialConnector(conn, client=FakeClient(HISTORIAL))
    c.run()
    c.run()
    assert conn.execute("SELECT COUNT(*) FROM administracio_partit").fetchone()[0] == 6
    assert conn.execute("SELECT COUNT(*) FROM administracio").fetchone()[0] == 6 + 1


def test_carrecs_salta_municipis_i_files_buides(conn):
    n = CarrecsElectesConnector(conn, client=FakeClient(CARRECS)).run()
    assert n == 1
    consell = conn.execute("SELECT * FROM administracio WHERE codi = 'ens_8102490004'").fetchone()
    assert consell["nom"] == "Consell Comarcal d'Osona"
    row = conn.execute(
        "SELECT * FROM administracio_partit WHERE administracio_id = ?", (consell["id"],)
    ).fetchone()
    assert row["responsable"] == "MARÇAL ORTUÑO SOLIS"
    assert row["partit_codi"] == "ERC"
    assert row["data_inici"] == "2025-07-30"
    assert row["data_fi"] is None


def test_partits_curats(conn):
    n = PartitsCuratsConnector(conn).run()
    assert n > 0
    siero = _partits(conn, "siero")
    assert siero[-1]["responsable"] == "Ángel Antonio García González"
    assert siero[-1]["data_fi"] is None
    gencat = _partits(conn, "catalunya")
    assert gencat[-1]["responsable"] == "Salvador Illa i Roca"
    assert (
        conn.execute(
            "SELECT COUNT(*) FROM administracio_partit WHERE administracio_id = ?",
            (administracio_id(conn, "diputacio_girona"),),
        ).fetchone()[0]
        == 1
    )


def test_vista_contractes_per_partit(conn):
    from fairfactory.db import ensure_font, upsert

    ensure_font(conn, "f", "Font", "http://x", "CC")
    PartitsCuratsConnector(conn).run()
    aid = administracio_id(conn, "siero")
    upsert(
        conn,
        "contracte",
        [
            {
                "font": "f",
                "id_extern": "1",
                "administracio_id": aid,
                "titol": "T",
                "import_adjudicacio": 100.0,
                "data_adjudicacio": "2020-05-01",
            },
            {
                "font": "f",
                "id_extern": "2",
                "administracio_id": aid,
                "titol": "T2",
                "import_adjudicacio": 200.0,
                "data_adjudicacio": "2013-05-01",
            },
        ],
    )
    rows = conn.execute(
        "SELECT partit, any, n, total FROM v_contractes_per_partit ORDER BY any"
    ).fetchall()
    assert ("FAC", "2013", 1, 200.0) in [tuple(r) for r in rows]
    assert ("FSA-PSOE", "2020", 1, 100.0) in [tuple(r) for r in rows]
