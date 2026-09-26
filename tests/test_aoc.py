from fairfactory.connectors.aoc import record_to_contracte

REC = {
    "_id": 42,
    "NOM_ORGAN": "Ajuntament de Figueres",
    "CODI_DIR3": "L01170669",
    "CODI_INE10": "1706690004",
    "CODI_EXPEDIENT": "2026/05",
    "NUMERO_LOT": None,
    "TIPUS_CONTRACTE": "Serveis",
    "PROCEDIMENT": "Obert",
    "FASE_PUBLICACIO": "Adjudicació",
    "OBJECTE_CONTRACTE": "Servei de manteniment",
    "PRESSUPOST_LICITACIO_AMB_IVA": 209697.2,
    "IMPORT_ADJUDICACIO_AMB_IVA": 209697.2,
    "DATA_PUBLICACIO_ANUNCI": "2026-04-27T00:00:00",
    "DATA_PUBLICACIO_ADJUDICACIO": "2026-08-10T00:00:00",
    "DENOMINACIO_ADJUDICATARI": "ANNCON LLEURE I OCI SL",
    "IDENTIFICACIO_ADJUDICATARI": "B17767583",
    "OFERTES_REBUDES": 2,
    "CODI_CPV": "80000000-4",
    "ENLLAC_PUBLICACIO": "https://contractaciopublica.cat/ca/detall-publicacio/x",
}


def test_record_to_contracte():
    c = record_to_contracte(REC)
    assert c["id_extern"].startswith("1706690004:2026/05")
    assert c["organ"] == "Ajuntament de Figueres"
    assert c["organ_nif"] == "L01170669"
    assert c["titol"] == "Servei de manteniment"
    assert c["import_licitacio"] == 209697.2
    assert c["import_adjudicacio"] == 209697.2
    assert c["data_publicacio"] == "2026-04-27"
    assert c["data_adjudicacio"] == "2026-08-10"
    assert c["adjudicatari_nif"] == "B17767583"
    assert c["num_ofertes"] == 2
    assert c["cpv"] == "80000000-4"
    assert c["url"].startswith("https://contractaciopublica.cat/")
    assert c["raw"] is REC
