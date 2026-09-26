from fairfactory.connectors.sede import parse_board

HTML = """
<table class="table c-table w-full AdvertisementBoardListPanel responsive-table" id="id2e">
<thead><tr class="headers">
  <th class="class_name"><span>Documento</span></th>
  <th class="class_folderCode"><span>Expediente</span></th>
  <th class="class_folderName"><span>Procedimiento</span></th>
  <th class="class_boardCategory"><span>Categoría</span></th>
  <th class="class_description"><span>Descripción</span></th>
  <th class="class_dateFrom"><span>Fecha de Publicación</span></th>
</tr></thead>
<tbody>
  <tr class="emptyRow"><td></td><td></td><td></td><td></td><td></td><td></td></tr>
  <tr>
    <td class="class_name" data-label="Documento"><span>
      <a id="id30" href="https://siero.sedelectronica.es/preview-document/92cbacb1-817d-4b77-8826-67e8f6e38778"
         title="Lista definitiva de admitidos">Anuncio Listado definitivo</a>
    </span></td>
    <td class="class_folderCode"><span>425/2026</span></td>
    <td class="class_folderName"><span>Selecciones de Personal</span></td>
    <td class="class_boardCategory"><span>Empleo Público</span></td>
    <td class="class_description"><span>Lista definitiva de admitidos y excluidos.</span></td>
    <td class="class_dateFrom"><span>25/09/2026</span></td>
  </tr>
  <tr>
    <td class="class_name"><span>
      <a href="/preview-document/017dce6b-22cb-4b7a-be03-b45fc1988c60">Extracto JGL</a>
    </span></td>
    <td class="class_folderCode"><span>JGL/2026/41</span></td>
    <td class="class_folderName"><span>Convocatoria JGL</span></td>
    <td class="class_boardCategory"><span>Órganos de gobierno</span></td>
    <td class="class_description"><span>Convocatoria del día 25-09-2026</span></td>
    <td class="class_dateFrom"><span>24/09/2026</span></td>
  </tr>
</tbody>
</table>
"""


def test_parse_board():
    docs = parse_board(HTML)
    assert len(docs) == 2
    d0, d1 = docs
    assert d0["id_extern"] == "92cbacb1-817d-4b77-8826-67e8f6e38778"
    assert d0["titol"] == "Anuncio Listado definitivo"
    assert d0["categoria"] == "tablon"
    assert d0["raw"]["expedient"] == "425/2026"
    assert d0["raw"]["categoria"] == "Empleo Público"
    assert d0["raw"]["data_publicacio"] == "2026-09-25"
    assert d1["url"].endswith("preview-document/017dce6b-22cb-4b7a-be03-b45fc1988c60")
    assert d1["url"].startswith("https://siero.sedelectronica.es/")


def test_parse_board_empty():
    assert parse_board("<html><body>sense taula</body></html>") == []
