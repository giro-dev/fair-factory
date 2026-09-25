# fair-factory

Ingesta de dades de portals de transparència (Ayuntamiento de Siero / Principado de Asturias) cap a
SQLite, publicada com a web estàtic a GitHub Pages (la pàgina carrega `transparencia.sqlite` al
navegador amb sql.js i permet consultes SQL lliures).

## Fonts

| codi                    | font                                              | taula        |
|-------------------------|---------------------------------------------------|--------------|
| `bdns`                  | BDNS – concessions de subvencions (API JSON)      | `subvencio`  |
| `datos_gob`             | datos.gob.es – catàleg del Principat (API)        | `dataset`    |
| `pcsp`                  | Plataforma de Contratación – feed ATOM/CODICE     | `contracte`  |
| `siero_transparencia`   | Portal de transparència de Siero (HTML + PDFs)    | `document`, `contracte` (contractes menors) |
| `asturias_transparencia`| Portal de transparència del Principat (HTML)      | `document`   |

Tot es guarda amb `font` + `id_extern` com a clau natural (upsert idempotent), `url` d'origen i
el registre original a `raw` quan hi ha JSON.

## Ús

```bash
pip install -e .[dev]

fair-factory init                              # crea data/transparencia.sqlite
fair-factory ingest                            # totes les fonts
fair-factory ingest bdns siero_transparencia --limit 500
fair-factory ingest pcsp --pcsp-pages 1        # cada pàgina del feed pesa ~17 MB
fair-factory ingest bdns --since 01/01/2024
fair-factory stats
fair-factory export-summary --out site/summary.json
```

Les respostes HTTP es guarden a `data/cache/` (reintents amb backoff per 429/5xx, 1 s entre
peticions). Cada execució queda registrada a `ingest_run`.

## Web (GitHub Pages)

`site/index.html` llegeix `site/transparencia.sqlite`. El workflow
`.github/workflows/ingest-and-publish.yml` (setmanal o manual) executa la ingesta, copia la base
de dades a `site/` i la publica a Pages. Cal activar Pages amb origen «GitHub Actions» al
repositori.

Per provar-ho en local:

```bash
cp data/transparencia.sqlite site/ && python -m http.server -d site 8000
```

## Desenvolupament

```bash
ruff check . && ruff format --check . && pytest
```
