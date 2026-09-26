from xml.etree import ElementTree as ET

from fairfactory.connectors.pcsp import classify, parse_entry

ENTRY = """
<entry xmlns="http://www.w3.org/2005/Atom"
       xmlns:cbc="urn:dgpe:names:draft:codice:schema:xsd:CommonBasicComponents-2"
       xmlns:cac="urn:dgpe:names:draft:codice:schema:xsd:CommonAggregateComponents-2"
       xmlns:cac-place-ext="urn:dgpe:names:draft:codice-place-ext:schema:xsd:CommonAggregateComponents-2"
       xmlns:cbc-place-ext="urn:dgpe:names:draft:codice-place-ext:schema:xsd:CommonBasicComponents-2">
  <id>https://contrataciondelestado.es/sindicacion/licitacion/1</id>
  <link href="https://contrataciondelestado.es/detalle/1"/>
  <title>Id licitación: EXP-1; Órgano: Ayuntamiento de Siero; Servicios</title>
  <updated>2024-05-01T10:00:00Z</updated>
  <cac-place-ext:ContractFolderStatus>
    <cbc:ContractFolderID>EXP-1</cbc:ContractFolderID>
    <cbc-place-ext:ContractFolderStatusCode>ADJ</cbc-place-ext:ContractFolderStatusCode>
    <cac-place-ext:LocatedContractingParty>
      <cac:Party>
        <cac:PartyIdentification>
          <cbc:ID schemeName="NIF">P3306600H</cbc:ID>
        </cac:PartyIdentification>
        <cac:PartyName><cbc:Name>Ayuntamiento de Siero</cbc:Name></cac:PartyName>
        <cac:PostalAddress><cbc:CityName>Pola de Siero</cbc:CityName></cac:PostalAddress>
      </cac:Party>
    </cac-place-ext:LocatedContractingParty>
    <cac:ProcurementProject>
      <cbc:TypeCode>2</cbc:TypeCode>
      <cac:BudgetAmount><cbc:TaxExclusiveAmount>1000.50</cbc:TaxExclusiveAmount></cac:BudgetAmount>
      <cac:RequiredCommodityClassification><cbc:ItemClassificationCode>90910000</cbc:ItemClassificationCode></cac:RequiredCommodityClassification>
      <cac:RealizedLocation><cbc:CountrySubentityCode>ES120</cbc:CountrySubentityCode></cac:RealizedLocation>
    </cac:ProcurementProject>
    <cac:TenderResult>
      <cbc:AwardDate>2024-04-30</cbc:AwardDate>
      <cbc:ReceivedTenderQuantity>3</cbc:ReceivedTenderQuantity>
      <cac:WinningParty>
        <cac:PartyIdentification><cbc:ID>B00000000</cbc:ID></cac:PartyIdentification>
        <cac:PartyName><cbc:Name>Neteja SL</cbc:Name></cac:PartyName>
      </cac:WinningParty>
      <cac:AwardedTenderedProject>
        <cac:LegalMonetaryTotal><cbc:TaxExclusiveAmount>900</cbc:TaxExclusiveAmount></cac:LegalMonetaryTotal>
      </cac:AwardedTenderedProject>
    </cac:TenderResult>
    <cac:TenderingProcess><cbc:ProcedureCode>9</cbc:ProcedureCode></cac:TenderingProcess>
  </cac-place-ext:ContractFolderStatus>
</entry>
"""


def test_parse_entry_and_classify():
    row = parse_entry(ET.fromstring(ENTRY))
    assert row["expedient"] == "EXP-1"
    assert row["organ_nif"] == "P3306600H"
    assert row["tipus"] == "Servicios"
    assert row["procediment"] == "Abierto simplificado"
    assert row["import_licitacio"] == 1000.5
    assert row["import_adjudicacio"] == 900.0
    assert row["adjudicatari_nom"] == "Neteja SL"
    assert row["num_ofertes"] == 3
    assert row["cpv"] == "90910000"
    assert row["data_publicacio"] == "2024-05-01"
    assert classify(row) == "siero"


def test_classify_out_of_scope():
    assert classify({"organ": "Ayuntamiento de Madrid", "_nuts": "ES300"}) is None
    assert classify({"organ": "Consejería X", "_nuts": "ES120"}) == "asturias"


def test_classify_catalunya():
    assert classify({"organ": "Ajuntament de Figueres"}) == "figueres"
    assert classify({"organ": "Diputación Provincial de Girona"}) == "diputacio_girona"
    assert classify({"organ": "Generalitat de Catalunya - Dept. Educació"}) == "catalunya"
    assert classify({"organ": "Ajuntament de Girona"}) == "girona"
    # altres ens catalans no s'adjudiquen a cap admin seguida
    assert classify({"organ": "Ayuntamiento de Barcelona", "_nuts": "ES511"}) is None
