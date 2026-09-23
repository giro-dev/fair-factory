# AGENTS.md — Fanel

Guia per a agents de codi (Devin, Claude Code, Copilot, Cursor…) i persones que treballen en aquest repositori.
**Les decisions d'arquitectura d'aquest fitxer són vinculants.** Si cal canviar-ne una, escriu primer un ADR nou a `docs/adr/` que la substitueixi i actualitza aquest fitxer al mateix PR.

## Què és Fanel

Fanel (*the family organizer panel*) és una aplicació auto-allotjable per a la gestió d'una llar: menú setmanal, receptes, llista de la compra, calendari i tasques, amb un assistent d'IA i un servidor MCP perquè agents externs hi puguin operar.

Roadmap i fases: [`docs/ROADMAP.md`](docs/ROADMAP.md). Decisions: [`docs/adr/`](docs/adr/).

## Decisions d'arquitectura (resum vinculant)

| # | Decisió | ADR |
|---|---|---|
| 1 | **Monòlit modular** amb Spring Boot + Spring Modulith. Un sol procés, un sol JAR, un sol contenidor. Sense microserveis. | [0001](docs/adr/0001-monolit-modular.md) |
| 2 | **Frontend React + TypeScript (Vite, PWA)** compilat i **servit des del mateix JAR** (`static/`). Cap lògica de negoci al frontend. | [0002](docs/adr/0002-spa-react-embeguda.md) |
| 3 | **PostgreSQL i SQLite** suportats des del dia 1 via perfils `postgres` / `sqlite`. Migracions Flyway portables. | [0003](docs/adr/0003-postgres-i-sqlite.md) |
| 4 | **API first**: REST `/api/v1` documentat amb OpenAPI; és el contracte per a futurs clients (Android via Capacitor, escriptori via Tauri, nadius). Errors RFC 9457. Temps real via SSE. | [0004](docs/adr/0004-api-first-clients.md) |
| 5 | **Multi-household** al model des de l'inici: tota entitat de negoci pertany a un `Household`. | [0005](docs/adr/0005-multi-household.md) |
| 6 | **IA amb Spring AI**, proveïdor **configurable i cap actiu per defecte**. Tools `@Tool` definides una sola vegada i reutilitzades per l'assistent intern i el **servidor MCP** (`/mcp`, Streamable HTTP). Tot aïllat al mòdul `assistant`. | [0006](docs/adr/0006-spring-ai-mcp.md) |
| 7 | **i18n català + castellà** des del principi (`ca` per defecte). Cap text d'usuari hardcodejat: `react-i18next` al frontend, `MessageSource` al backend. | [0007](docs/adr/0007-i18n.md) |
| 8 | Comunicació entre mòduls per **esdeveniments de domini** (Spring Modulith, registre persistit), no per crides directes a la implementació d'un altre mòdul. | [0001](docs/adr/0001-monolit-modular.md) |
| 9 | **Assistent multiagent** amb **un model/API per funció**: cada agent té el seu `ModelProfile` (Ollama, OpenAI, Anthropic…) i les tools compartides del mòdul `assistant`. | [0009](docs/adr/0009-assistant-multiagent.md) |

## Estructura del repositori

```
pom.xml                 Maven parent (mòduls: frontend, backend)
backend/                Spring Boot; paquet base dev.agiro.fanel
  src/main/java/dev/agiro/fanel/
    household/ menu/ recipes/ shopping/ calendar/ chores/ export/
    assistant/ notifications/ automation/    ← mòduls Modulith (un paquet = un mòdul)
    shared/                                  ← mòdul OPEN: base d'entitats, errors, auditoria
  src/main/resources/db/migration/{common,postgresql,sqlite}
frontend/               Vite + React + TS (PWA); el build s'empaqueta com a recurs static/
deploy/                 Dockerfile multi-stage, docker-compose (postgres i sqlite)
docs/                   ROADMAP.md, adr/   ← fonts de veritat; NO editar docs-site/content (es regenera)
docs-site/              Lloc Hugo (Docsy) per a GitHub Pages; content/ el genera scripts/sync-docs.sh
scripts/                sync-docs.sh: sincronitza docs/ → docs-site/content/ca/docs/
.github/workflows/      CI (verify Modulith + tests + lint) i imatge a GHCR
```

### Estructura d'un mòdul

```
<modul>/
  api/      interfície pública, DTOs (records) i esdeveniments  ← únic que altres mòduls poden importar
  web/      controllers REST (/api/v1/<recurs>)
  domain/   entitats JPA i serveis
  infra/    repositoris i adaptadors
  package-info.java  amb @ApplicationModule
```

Regles:
- Un mòdul **només** importa `*.api.*` d'altres mòduls i `shared`. `ApplicationModules.verify()` ho comprova als tests: si falla, arregla el disseny, no el test.
- Efectes creuats (p. ex. el menú alimenta la compra) es fan **escoltant esdeveniments** (`@ApplicationModuleListener`), no cridant serveis d'un altre mòdul.
- Tota entitat de negoci té `householdId`; tota consulta filtra per household.
- IDs UUID v7. Auditoria (`createdAt`, `createdBy`) a `shared`.
- Nova taula o columna ⇒ nova migració Flyway a `common` (SQL portable) o, si no és possible, a `postgresql/` **i** `sqlite/` alhora. No modificar migracions ja publicades.

## Convencions

- **Java 25**, Maven, Spring Boot 4.x. Records per a DTOs, `Optional` només com a retorn. Sense `Any`/reflexió innecessària. Sense Lombok.
- **TypeScript strict**, React amb components funcionals, TanStack Query per a dades del servidor, cap `any`.
- Text d'usuari: sempre via i18n (`frontend/src/i18n/{ca,es}.json`, `backend/src/main/resources/messages*.properties`). Afegir la clau a **tots** els idiomes.
- Idioma del codi i comentaris: anglès. Idioma de docs, commits i UI per defecte: català (commits en anglès també acceptats).
- Commits: Conventional Commits (`feat:`, `fix:`, `docs:`, `chore:`…). Branques `<autor>/<slug>` o `devin/<timestamp>-<slug>`.
- Configuració només per variables d'entorn / `application*.yml`. Cap secret al repo.
- Docker: la imatge final ha de continuar sent **una sola** imatge que serveix API + SPA.

## Comandes

| Acció | Comanda |
|---|---|
| Build complet (frontend + backend, un JAR) | `mvn -B verify` |
| Backend sol (salta build del frontend) | `mvn -pl backend verify -Dskip.frontend` |
| Backend en dev (SQLite, usuari `admin/admin`) | `make dev-backend` |
| Frontend en dev (proxy a :8080) | `make dev-frontend` |
| Lint/typecheck frontend | `cd frontend && npm run lint && npm run typecheck` |
| Preview del lloc de docs | `make dev-docs` (Hugo server a :1313) |
| Imatge Docker | `docker build -f deploy/Dockerfile .` |
| Execució auto-allotjada | `cd deploy && docker compose up -d` (Postgres) o `docker compose -f docker-compose.sqlite.yml up -d` |
| Regenerar especificació OpenAPI (`docs/openapi.json`) | `mvn -pl backend verify` (l'escriu `OpenApiSpecSqliteIT`) |
| Regenerar tipus TS (`frontend/src/api/schema.d.ts`) | `cd frontend && npm run gen:api` |

`docs/openapi.json` i `frontend/src/api/schema.d.ts` són fitxers generats que es commitejen; la CI falla si queden desfasats (`git diff` després de regenerar).

Abans d'obrir un PR: `mvn -B verify` verd (inclou `ApplicationModules.verify()` i tests amb Postgres i SQLite) i lint/typecheck del frontend verds.

## Releases

La release es fa amb la pipeline **Actions → Release → Run workflow**, indicant la versió semver `X.Y.Z` (s'executa des de la branca triada, normalment `main`):

1. `prepare`: posa la versió als poms Maven (`versions:set`) i a `android/app/build.gradle.kts` (`versionName` + `versionCode = major·10000 + minor·100 + patch`), fa commit `chore: release vX.Y.Z` i crea el tag `vX.Y.Z`.
2. En paral·lel des del tag: **jar** (`mvn package -DskipTests`), **apk** (`./gradlew assembleRelease`, signat), **aab** (`./gradlew bundleRelease`, signat) i **docker** (build multi-arch `linux/amd64,linux/arm64` i push a Docker Hub amb tags `X.Y.Z` i `latest`).
3. `publish`: crea la GitHub Release `vX.Y.Z` amb `fanel-X.Y.Z.jar`, `fanel-X.Y.Z.apk` i `fanel-X.Y.Z.aab` adjunts.
4. (Opcional) `playstore`: si a l'input `play_track` s'indica un track (`internal`, `alpha`, `beta` o `production`; per defecte `none`), puja el `.aab` de la GitHub Release al Play Console amb l'estat `play_status` (`draft` per defecte). Mentre l'app encara sigui un *draft* al Play Console només s'accepta `draft` (si no: *"Only releases with status draft may be created on draft app"*); un cop publicada l'app, fer servir `completed`. La mateixa pujada es pot fer a mà més tard amb **Actions → Publish to Play Store → Run workflow** (workflow reutilitzable `playstore-publish.yml`).
5. `bump`: torna a la branca i deixa tot a `X.Y.(Z+1)-SNAPSHOT` (`chore: prepare next development iteration`), llest per a la següent release.

Secrets/variables necessaris a GitHub:
- `DOCKERHUB_USERNAME` + `DOCKERHUB_TOKEN` (secrets, obligatoris). Imatge per defecte: `<usuari>/fanel`; es pot sobreescriure amb la variable `DOCKERHUB_IMAGE`.
- `FANEL_KEYSTORE_BASE64`, `FANEL_KEYSTORE_PASSWORD`, `FANEL_KEY_ALIAS`, `FANEL_KEY_PASSWORD` (secrets, opcionals): clau real de signatura de l'APK/AAB. Si no hi són, es fa servir la clau self-signed de dev pujada al repo (`android/app/selfsigned.jks`). **Atenció**: la clau del repo és pública — qualsevol pot signar "actualitzacions" de l'app; per a distribució real cal generar una clau privada i posar-la als secrets abans de la primera release pública.
- `PLAY_SERVICE_ACCOUNT_JSON` (secret, opcional): JSON del compte de servei de Google Cloud amb permís a l'API Google Play Android Developer. Necessari per a la pipeline `Publish to Play Store`.
- El push dels commits de versió el fa `github-actions[bot]`; si la branca té protecció que ho impedeix, cal exemptar-lo o crear la release des d'una branca sense protecció.

`docker.yml` (GHCR, tag `latest`) queda només per a pushes a `main`; les versions etiquetades les publica `release.yml` a Docker Hub.

## Què NO fer

- No afegir un segon servei/contenidor obligatori (excepte la BD Postgres opcional).
- No introduir Spring AI fora del mòdul `assistant`.
- No afegir lògica de negoci al frontend ni duplicar-la per a clients futurs.
- No acoblar mòduls per `domain`/`infra` d'un altre mòdul.
- No canviar una decisió de la taula de dalt sense ADR.
- No modificar tests per fer-los passar; arregla el codi.

## Per a agents amb MCP

A partir de la Fase 3 Fanel exposa un servidor MCP a `/mcp` (Streamable HTTP, autenticació per token de household). Les eines disponibles es deriven de les `@Tool` del mòdul `assistant`; consulta `docs/ROADMAP.md` per l'estat.
