from fairfactory.connectors.figueres import parse_licitacions, record_to_document

REC = {
    "_id": 535,
    "RESUM": "Ordenances fiscals per a l'any 2026",
    "DATA_PUB": "2026-09-10T00:00:00",
    "ENLLAÇ": "https://cido.diba.cat/normativa_local/20065504",
    "CODI_ENS": "1706690004",
    "NOM_ENS": "Ajuntament de Figueres",
}

HTML = """
<table><thead><tr><th>Títol</th></tr></thead><tbody>
<tr>
  <td>Contracte del servei de muntatge</td><td>Ajuntament de Figueres</td>
  <td>21/2018</td><td>Ordinari</td><td>Serveis</td><td>Altres serveis</td>
  <td>Obert</td><td>Descripció de la prestació.</td><td>63428.0</td>
  <td>3 mesos</td><td>Figueres</td><td>04/09/2018</td><td>52420.0</td>
  <td>No</td><td></td><td>No</td><td></td>
  <td><a href="https://contractaciopublica.gencat.cat/x?idDoc=1">Enllaç</a></td>
  <td></td><td></td><td></td>
</tr>
<tr>
  <td>Contracte B</td><td>Ajuntament de Figueres</td>
  <td>22/2019</td><td>Menor</td><td>Obres</td><td>Obres</td>
  <td>Contracte menor</td><td>Desc B.</td><td>12.000,50</td>
  <td>1 mes</td><td>Figueres</td><td>05/10/2019</td><td>11.000,0</td>
  <td>No</td><td></td><td>No</td><td></td>
  <td></td><td></td><td></td><td></td>
</tr>
</tbody></table>
"""


def test_record_to_document():
    d = record_to_document("rid1", "Ordenances fiscals", REC)
    assert d["id_extern"] == "rid1:535"
    assert d["titol"] == "Ordenances fiscals per a l'any 2026"
    assert d["categoria"] == "Ordenances fiscals"
    assert d["url"] == "https://cido.diba.cat/normativa_local/20065504"
    assert d["raw"]["CODI_ENS"] == "1706690004"


def test_record_to_document_sense_enllac():
    d = record_to_document("rid1", "X", {"_id": 1, "NOM": "Nom"})
    assert d["titol"] == "Nom"
    assert d["url"]  # NOT NULL


def test_parse_licitacions():
    rows = parse_licitacions(HTML)
    assert len(rows) == 2
    a, b = rows
    assert a["expedient"] == "21/2018"
    assert a["organ"] == "Ajuntament de Figueres"
    assert a["tipus"] == "Serveis"
    assert a["procediment"] == "Obert"
    assert a["import_licitacio"] == 63428.0
    assert a["url"].startswith("https://contractaciopublica.gencat.cat/")
    assert a["raw"]["termini_ofertes"] == "2018-09-04"
    assert b["import_licitacio"] == 12000.50
