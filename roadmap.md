# Roadmap

## Objectiu

Fer que **Fair Factory** sigui fàcil d'estendre amb noves fonts de dades, mantenint una arquitectura simple:

- Python per a la ingesta.
- SQLite com a model de dades i snapshot.
- GitHub Actions com a pipeline d'execució.
- GitHub Pages com a publicació.
- Vue + sql.js al navegador.
- Sense backend ni infraestructura permanent.

La prioritat és augmentar la capacitat d'afegir fonts i administracions sense convertir el projecte en un framework.

---

## 1. Interfície comuna d'ingesta

### Objectiu

Eliminar dependències entre cada connector i la CLI, de manera que afegir un connector nou no obligui a modificar `cli.py`.

### Estat actual

Ja existeix una classe base `Connector` i un registre `CONNECTORS`. El principal punt de millora és que la CLI encara coneix opcions específiques de connectors.

### Tasques

- [ ] Definir una interfície comuna i petita per a tots els connectors.
- [ ] Moure la configuració específica del connector fora de `cli.py`.
- [ ] Fer que cada connector declari la seva configuració/metadades.
- [ ] Mantenir el registre explícit de connectors; evitar autodiscovery màgic.
- [ ] Documentar el contracte mínim que ha de complir un connector.

Exemple conceptual:

~~~python
class ExempleConnector(Connector):
    codi = "exemple"
    nom = "Ajuntament de X — Pressupost"
    url = "https://..."

    def ingest(self) -> int: ...
~~~

Afegir una font nova hauria d'implicar principalment:

1. Crear el connector.
2. Normalitzar les dades a una taula existent o definir-ne una de nova si és necessari.
3. Afegir proves.
4. Registrar el connector.
5. Assignar-lo al grup de pipeline corresponent.
6. Documentar la font a la web.

---

## 2. Metadades de les fonts

Cada connector hauria de declarar prou informació perquè la mateixa informació pugui alimentar la ingesta, la documentació i el resum públic.

### Metadades proposades

- `codi`
- `nom`
- `url`
- `grup`
- `dataset`
- administracions cobertes
- tipus de dades
- mètode d'ingesta

No cal convertir immediatament aquestes metadades en una nova taula de base de dades. Inicialment poden formar part del registre de connectors.

### Tasques

- [ ] Afegir metadades comunes als connectors.
- [ ] Evitar duplicar aquesta informació entre Python, README i web.
- [ ] Fer que el resum de la ingesta pugui reutilitzar aquestes metadades.

---

## 3. Administracions i datasets

Separar conceptualment:

**Font → Dataset → Administració**

Exemple:

~~~text
AOC
└── Contractació pública
    ├── Girona
    ├── Figueres
    └── altres administracions
~~~

Això és especialment important per a fonts agregades com AOC, BDNS o PCSP.

### Tasques

- [ ] Identificar clarament quines administracions cobreix cada connector.
- [ ] Evitar crear un connector nou quan només canvia l'identificador d'una administració.
- [ ] Centralitzar els identificadors externs de cada administració.
- [ ] Registrar l'administració afectada en cada execució d'ingesta.

---

## 4. Traçabilitat de les ingestes

La taula `ingest_run` ha de permetre saber no només que un connector ha fallat, sinó **quina administració o dataset ha fallat**.

### Tasques

- [ ] Afegir `administracio_codi` a `ingest_run`.
- [ ] Valorar `dataset` com a segon identificador.
- [ ] Registrar inici, final, estat, registres, warnings i missatge.
- [ ] Conservar l'URL de la font quan sigui útil per diagnosticar errors.
- [ ] Fer que els errors parcials siguin visibles al resum publicat.

---

## 5. Qualitat de dades

El pipeline actual permet continuar quan una font falla. Això és útil per no bloquejar tota la publicació, però la web ha de deixar clar quan el snapshot és parcial.

### Validacions

- [ ] Detectar fonts que inesperadament retornen 0 registres.
- [ ] Comparar el nombre de registres amb la ingesta anterior.
- [ ] Detectar caigudes anormalment grans.
- [ ] Validar identificadors d'administració.
- [ ] Validar dates.
- [ ] Validar imports i valors numèrics.
- [ ] Evitar publicar un dataset buit quan l'anterior contenia dades significatives.
- [ ] Diferenciar entre `success`, `partial`, `warning` i `error`.

### Principi

Una font amb error no ha d'impedir necessàriament publicar les altres fonts, però l'estat de la publicació ha de ser observable.

---

## 6. Resum públic de la ingesta

`docs/summary.json` hauria de ser més que un recompte global.

### Informació proposada

Per cada font:

- connector
- administració
- dataset
- estat
- nombre de registres
- data/hora de l'última ingesta
- URL de la font
- warnings
- errors

Exemple:

~~~json
{
  "font": "aoc_contractes",
  "administracio": "girona",
  "dataset": "contractacio_publica",
  "estat": "success",
  "registres": 1234,
  "updated_at": "2026-09-27T04:32:00Z",
  "source_url": "https://..."
}
~~~

Aquesta informació permetrà que la web expliqui l'estat real de les dades sense consultar la base de dades completa.

---

## 7. Documentació de la web

Afegir una secció **Fonts i metodologia** a la web.

### Contingut

#### Fonts

Mostrar per cada font:

- nom
- administracions cobertes
- dataset
- font oficial
- enllaç a la font original
- última actualització
- nombre de registres
- estat de la ingesta

#### Metodologia

Explicar de forma visual i breu el recorregut:

~~~text
Fonts oficials
     ↓
Connectors Python
     ↓
Normalització
     ↓
Validació
     ↓
SQLite
     ↓
GitHub
     ↓
GitHub Pages
     ↓
sql.js + Vue
~~~

#### Limitacions

Explicar explícitament que:

- les dades depenen de les fonts públiques originals;
- una absència de dades no implica necessàriament que l'activitat no existeixi;
- els formats i criteris de publicació poden canviar;
- una ingesta pot ser parcial;
- Fair Factory no modifica el significat de les dades originals, sinó que les normalitza per facilitar-ne la consulta.

### Tasques

- [ ] Crear secció "Fonts i metodologia".
- [ ] Generar la llista de fonts a partir de `summary.json`/metadades.
- [ ] Mostrar la data de l'última actualització.
- [ ] Mostrar avisos quan la ingesta sigui parcial.
- [ ] Enllaçar cada font amb el portal oficial.
- [ ] Documentar la metodologia i les limitacions.

---

## 8. Documentació per afegir un connector

Crear `docs/ingestion.md` amb una guia pràctica per desenvolupadors.

### Procés

1. Identificar una font oficial.
2. Identificar el dataset i l'administració.
3. Determinar el format de la font: API, JSON, CSV, HTML, PDF, etc.
4. Crear el connector.
5. Implementar la ingesta mitjançant la interfície comuna.
6. Normalitzar les dades.
7. Definir la clau natural `font + id_extern`.
8. Afegir URL d'origen.
9. Afegir proves.
10. Registrar el connector.
11. Afegir-lo al grup del workflow.
12. Afegir-lo a la documentació pública.

### Criteris

Un connector nou hauria de ser:

- idempotent;
- tolerant a errors de xarxa;
- limitable per a proves;
- traçable;
- executable de manera independent;
- compatible amb el model de dades existent.

---

## 9. Pipeline de GitHub Actions

Mantenir el model actual:

~~~text
             ┌─ Asturias ────────┐
             ├─ Catalunya ───────┤
Connectors ──┼─ Compartit ───────┼─→ merge
             └───────────────────┘      ↓
                                     SQLite
                                       ↓
                                      slim
                                       ↓
                                     gzip
                                       ↓
                                  GitHub Pages
~~~

### Tasques

- [ ] Mantenir les ingestes independents per grup.
- [ ] Mantenir `--keep-going` per tolerar fallades parcials.
- [ ] Fer explícit l'estat parcial de la publicació.
- [ ] Evitar duplicar configuració de fonts entre workflow i Python quan sigui possible.
- [ ] Mantenir artifacts parcials per facilitar diagnòstic.

No introduir encara un sistema de jobs més complex ni infraestructura externa.

---

## 10. Rendiment de la web

La web és estàtica i el navegador carrega el snapshot SQLite amb sql.js. Aquest model s'ha de mantenir mentre sigui suficient.

### Problema actual

El fitxer SQLite complet es descomprimeix i es manté en memòria del navegador. A mesura que creix el dataset, augmenta el consum de memòria.

### Ordre de prioritat

1. [ ] Eliminar dades redundants del snapshot.
2. [ ] Revisar índexs i mida de la base de dades.
3. [ ] Precalcular agregats que es consultin molt sovint.
4. [ ] Evitar carregar dades que la UI no necessiti.
5. [ ] Si el volum ho exigeix, estudiar dividir el snapshot en bases de dades/datasets.
6. [ ] Només si aquestes mesures no són suficients, estudiar alternatives de persistència SQLite al navegador.

No introduir un backend només per solucionar aquest problema mentre el model estàtic continuï sent viable.

---

## 11. Cobertura de dades

Un cop la infraestructura d'ingesta sigui estable, ampliar la cobertura.

### Prioritats

- [ ] Girona.
- [ ] Figueres.
- [ ] Altres municipis de Girona.
- [ ] Altres administracions del Principat.
- [ ] Nous datasets de pressupost, contractació, subvencions i indicadors socioeconòmics.

Per cada nova font, prioritzar primer la qualitat i traçabilitat abans d'afegir més volum.

---

## 12. Ordre d'implementació

### Fase 1 — Arquitectura d'ingesta

- [ ] Interfície comuna de `Connector`.
- [ ] Eliminar coneixement específic dels connectors de `cli.py`.
- [ ] Metadades comunes.
- [ ] Administració/dataset com a context.
- [ ] Millorar `ingest_run`.

### Fase 2 — Qualitat i observabilitat

- [ ] Validacions de volum i contingut.
- [ ] Estat parcial.
- [ ] `summary.json` detallat.
- [ ] Detecció d'anomalies entre ingestes.

### Fase 3 — Documentació i web

- [ ] `docs/ingestion.md`.
- [ ] Secció "Fonts i metodologia".
- [ ] Estat de les fonts a la web.
- [ ] Data d'actualització i limitacions.

### Fase 4 — Cobertura

- [ ] Completar Girona/Figueres.
- [ ] Afegir nous datasets.
- [ ] Reutilitzar connectors per fonts agregades.

### Fase 5 — Rendiment

- [ ] Mesurar mida real del snapshot i consum de memòria.
- [ ] Optimitzar SQLite.
- [ ] Reduir dades redundants.
- [ ] Dividir datasets només si és necessari.

---

## Principis del projecte

1. **Simple abans que genèric.**
2. **Fonts oficials abans que agregadors no oficials.**
3. **Traçabilitat abans que volum.**
4. **Les dades originals sempre han de poder ser identificades.**
5. **Una fallada parcial no ha de destruir tota la publicació.**
6. **La web ha d'explicar d'on provenen les dades.**
7. **No convertir Fair Factory en una plataforma amb backend si GitHub + SQLite + navegador resolen el problema.**
8. **No crear un connector nou quan només canvia l'administració o un identificador.**
9. **La normalització no ha d'alterar el significat de la font original.**
10. **Qualsevol ampliació ha de poder-se provar localment abans de publicar.**
