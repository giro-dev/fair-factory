from fairfactory.connectors.siero import extract_documents

HTML = """
<html><body>
<a href="/descarga/87/contratos-menores/5350/contratos-menores-ayto.pdf">Contratos menores Ayto</a>
<a href="https://www.ayto-siero.es/docs/presupuesto-2024.xlsx">Presupuesto 2024</a>
<a href="/otra-pagina/">Pàgina</a>
<a href="/descarga/87/contratos-menores/5350/contratos-menores-ayto.pdf">duplicat</a>
</body></html>
"""


def test_extract_documents():
    docs = extract_documents(HTML, "https://www.ayto-siero.es/portal-de-transparencia/", "test")
    urls = sorted(d["url"] for d in docs)
    assert urls == [
        "https://www.ayto-siero.es/descarga/87/contratos-menores/5350/contratos-menores-ayto.pdf",
        "https://www.ayto-siero.es/docs/presupuesto-2024.xlsx",
    ]
    by_url = {d["url"]: d for d in docs}
    assert by_url[urls[0]]["tipus_fitxer"] == "pdf"
    assert by_url[urls[1]]["tipus_fitxer"] == "xlsx"
    assert all(d["categoria"] == "test" for d in docs)
