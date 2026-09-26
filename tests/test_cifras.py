from fairfactory.connectors.cifras import parse_indicators, portal_url

LANDING = """
<html><body>
<a href="https://portalestadistico.com/municipioencifras/?pn=ayto-siero&#038;pc=FAH59">Acceso</a>
</body></html>
"""

PAGE = """
<th colspan="2" class="Titulo_Fila"><h2>1. Demografía</h2></th>
<table class="Tablas_datos"><tr><td colspan="2">
  <table class="Subtitulo_indicador"><tr class="Tablas_datos_cabecera">
    <td class="columna_izquierda"><h4 class="h4_grande">Población total</h4></td>
    <td class="columna_derecha"><h4 class="h4_muy_grande negativos_ie">52.997 personas</h4></td>
  </tr></table>
</td></tr><tr><td colspan="2">
  <div class="recuadro_texto_derecha_vertical">
    <span class="helper_texto_centrado_vertical columna_izquierda">Variación anual: </span>
    <span class="helper_texto_centrado_vertical texto_muy_grande">0,9%</span>
  </div>
  <table class="tabla_alineada_vertical_arriba">
    <tr><td class="columna_estrecha">Periodo:</td><td class="negrita">Anual&nbsp;2025</td></tr>
    <tr><td class="columna_estrecha">Fuente:</td><td class="negrita">INE, Censo</td></tr>
  </table>
</td></tr><tr><td colspan="2">
  <p class="texto_contenido">Cifra oficial de población residente.</p>
</td></tr></table>
<table class="Tablas_datos"><tr><td colspan="2">
  <table class="Subtitulo_indicador"><tr class="Tablas_datos_cabecera">
    <td class="columna_izquierda"><h4 class="h4_grande">Edad media</h4></td>
    <td class="columna_derecha"><h4 class="h4_muy_grande">46,7 años</h4></td>
  </tr></table>
</td></tr><tr><td colspan="2">
  <table class="tabla_alineada_vertical_arriba">
    <tr><td>Periodo:</td><td>Anual&nbsp;2024</td></tr>
    <tr><td>Fuente:</td><td>INE</td></tr>
  </table>
</td></tr></table>
<th colspan="2" class="Titulo_Fila"><h2>2. Mercado de Trabajo</h2></th>
<table class="Tablas_datos"><tr><td colspan="2">
  <table class="Subtitulo_indicador"><tr class="Tablas_datos_cabecera">
    <td><h4 class="h4_grande">Tasa de desempleo</h4></td>
    <td><h4 class="h4_muy_grande">7,1 %</h4></td>
  </tr></table>
</td></tr><tr><td>
  <table class="tabla_alineada_vertical_arriba">
    <tr><td>Periodo:</td><td>Trimestral&nbsp;2025T2</td></tr>
    <tr><td>Fuente:</td><td>SEPE</td></tr>
  </table>
</td></tr></table>
"""


def test_portal_url():
    assert portal_url(LANDING) == (
        "https://portalestadistico.com/municipioencifras/?pn=ayto-siero&pc=FAH59"
    )
    assert portal_url("<html></html>") is None


def test_parse_indicators():
    rows = parse_indicators(PAGE, "https://portalestadistico.com/x")
    assert len(rows) == 3
    pob, edat, atur = rows
    assert pob["seccio"] == "1. Demografía"
    assert pob["nom"] == "Población total"
    assert pob["valor_num"] == 52997.0
    assert pob["unitat"] == "personas"
    assert pob["variacio_anual"] == "0,9%"
    assert pob["periode"] == "Anual 2025"
    assert pob["any"] == 2025
    assert pob["font_dada"] == "INE, Censo"
    assert "población residente" in pob["descripcio"]
    assert pob["id_extern"] == "1. Demografía|Población total|2025"  # any a la clau
    assert edat["valor_num"] == 46.7
    assert edat["unitat"] == "años"
    assert atur["seccio"] == "2. Mercado de Trabajo"
    assert atur["unitat"] == "%"
    assert atur["any"] == 2025
