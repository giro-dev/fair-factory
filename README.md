# fair-factory

Auditoria ciutadana de la despesa pública: ingesta periòdica de portals oficials de
transparència, contractació, subvencions i dades obertes cap a una base de dades SQLite
normalitzada, publicada com a web estàtic a GitHub Pages. La pàgina carrega
`transparencia.sqlite.gz` al navegador amb sql.js i Vue — gràfiques, taules filtrables per
administració i consultes SQL lliures sobre el model.

Administracions cobertes actualment: **Ayuntamiento de Siero**, **Principado de Asturias**,
**Ajuntament de Figueres**, **Ajuntament de Girona**, **Diputació de Girona** i
**Generalitat de Catalunya**.

## Fonts

| codi                    | font                                              | taula        |
|-------------------------|---------------------------------------------------|--------------|
| `bdns`                  | BDNS – concessions de subvencions (API JSON)      | `subvencio`  |
| `datos_gob`             | datos.gob.es – catàleg del Principat (API)        | `dataset`    |
| `pcsp`                  | Plataforma de Contratación – feed ATOM/CODICE     | `contracte`  |
| `siero_transparencia`   | Portal de transparència de Siero (HTML + PDFs)    | `document`, `contracte` (contractes menors) |
| `asturias_transparencia`| Portal de transparència del Principat (HTML)      | `document`   |
| `siero_sede`            | Sede electrònica de Siero – tabló d'anuncis       | `document`   |
| `siero_cifras`          | "Siero en cifras" – observatori socioeconòmic     | `indicador`  |
| `figueres_ckan`         | Figueres – dades obertes (CKAN AOC, CODI_ENS)     | `document`   |
| `figueres_contractes`   | Figueres – licitacions en tràmit (taula HTML)     | `contracte`  |
| `aoc_contractes`        | AOC – dataset agregat PSCP (ine10/àmbit per admin) | `contracte`  |
| `diputacio_girona`      | Diputació de Girona – pressuposts (PDFs)          | `document`   |
| `catalunya_socrata`     | Generalitat – execució pressupostària (Socrata)   | `pressupost` |

Els connectors `bdns` i `pcsp` també cobreixen les administracions catalanes
(Figueres, Diputació de Girona i Generalitat).

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
fair-factory ingest bdns --bdns-admin siero,asturias   # filtra òrgans BDNS
fair-factory merge --out data/final.sqlite part-a.sqlite part-b.sqlite
fair-factory stats
fair-factory export-summary --out docs/summary.json
```

Les respostes HTTP es guarden a `data/cache/` (reintents amb backoff per 429/5xx, 1 s entre
peticions). Cada execució queda registrada a `ingest_run`.

## Web (GitHub Pages)

El repositori és la font de dades: el workflow `.github/workflows/ingest-and-publish.yml`
(setmanal o manual) executa la ingesta **en paral·lel per administració** — un job matrix per a
Astúries, un per a Catalunya i un per a les fonts compartides (`datos_gob`, `pcsp`); així els
timeouts d'una font no alenteixen les altres. Cada job genera `part-<grup>.sqlite` com a
artefacte i el job `publish` les fusiona (`fair-factory merge`, per `codi`/`id_extern`,
els ids primaris poden diferir entre parcials), buida `subvencio.raw` a l'artefacte
publicat (`fair-factory slim` — duplica els camps ja normalitzats a columnes) i la comprimeix a
`docs/transparencia.sqlite.gz` (la BD supera el límit de 100 MB de GitHub, així que es publica
comprimida), genera `docs/summary.json` i **commiteja els dos fitxers al repositori**. GitHub
Pages serveix la carpeta `docs/` de `main` tal qual; `docs/index.html` baixa el `.gz`, el
descomprimeix al navegador (`DecompressionStream`) i el carrega amb sql.js (sempre en mode de
només lectura — la consulta és a memòria, el fitxer del repositori no es modifica mai). No cal
cap artefacte ni pas de desplegament: cada commit de la pipeline actualitza la web.

Cal activar Pages amb origen **«Deploy from a branch» → `main` → `/docs`** a la configuració del
repositori (una sola vegada).

Per provar-ho en local:

```bash
gzip -9c data/transparencia.sqlite > docs/transparencia.sqlite.gz
python -m http.server -d docs 8000
```

## Com col·laborar

- **Reporta errors o incoherències** en les dades obrint una
  [issue](https://github.com/giro-dev/fair-factory/issues).
- **Proposa noves fonts o administracions**: un connector nou és un mòdul a
  `src/fairfactory/connectors/` (subclasse de `Connector` amb `ingest()`), més una entrada a
  `ADMINISTRACIONS` (`db.py`) si és una administració nova — els `identificadors` (ine10, dir3…)
  permeten filtrar fonts multi-ens sense tocar codi.
- **Contribueix codi**: `ruff check . && ruff format . && pytest` han de passar.
- **Reutilitza les dades**: `docs/transparencia.sqlite.gz` és la BD completa publicada;
  respecta la llicència de cada font (taula `font`) i cita l'origen.

## Desenvolupament

```bash
ruff check . && ruff format --check . && pytest
```
