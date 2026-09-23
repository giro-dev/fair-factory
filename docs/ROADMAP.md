# Roadmap — Fanel

Cada fase acaba amb una versió desplegable. Les decisions d'arquitectura estan a [`adr/`](adr/) i resumides a [`AGENTS.md`](../AGENTS.md).

Origen: POC "Panel familiar" (menú setmanal 7 dies × 4 àpats, calendari, compra, tasques amb assignat, selector "qui sóc"). Pla d'arquitectura complet previ al codi: [`PLA-ORIGINAL.md`](PLA-ORIGINAL.md) (aquest `ROADMAP.md` és la font de veritat sobre l'estat real; el pla original és context).

## Estat

| Fase | Nom | Estat |
|---|---|---|
| 0 | Fonaments | ✅ fet |
| 1 | Paritat amb la POC | ✅ fet |
| 2 | Valor de domini | ✅ fet |
| 3 | IA i MCP | ⏳ |
| 4 | Clients Android i escriptori | ⏳ |
| 5 | Maduresa | ⏳ |

## Fase 0 — Fonaments

- [x] Repositori, AGENTS.md, ROADMAP, ADRs 0001–0007
- [x] Maven multi-mòdul: `frontend` (Vite build → `static/`) + `backend` (Spring Boot 4, Java 25)
- [x] Spring Modulith amb els paquets de mòdul definits i `ApplicationModules.verify()` al CI
- [x] JPA + Flyway; perfils `postgres` i `sqlite`; tests amb els dos motors
- [x] Mòdul `household` real: `Household`, `Member`, REST `/api/v1/households`
- [x] Spring Security bàsica (Basic auth des d'env), CORS configurable, SPA fallback
- [x] Frontend React + TS + PWA + i18n (ca/es) amb esquelet de 4 pestanyes i pàgina de households
- [x] Dockerfile multi-stage (una imatge, multi-arch) + docker-compose (Postgres i SQLite)
- [x] GitHub Actions: verify + lint + imatge a GHCR
- **Fet quan**: `docker compose up` → login → SPA servida des del JAR → crear una llar. ✅

## Fase 1 — Paritat amb la POC

- [x] `menu`: `MealPlan` per setmana ISO real, `MealSlot` (esmorzar/dinar/berenar/sopar) amb text lliure
- [x] `shopping`: llista per defecte, ítems amb `done`, "buida comprats"
- [x] `calendar`: esdeveniments amb data (+hora opcional), agrupats per dia, `addedBy`
- [x] `chores`: tasques amb assignat (membre) i `done`
- [x] Membres reals amb color; selector "qui sóc" i **mode tauleta** (dispositiu compartit, canvi de membre amb PIN) — `WhoAmI` i `HouseholdProvider` ja muntats a `App.tsx`
- [x] Autenticació: usuari/contrasenya per adults + token d'API per a clients — cada `Member` adult pot tenir `username`/`passwordHash` propis (`PUT /api/v1/households/{id}/members/{memberId}/credentials`); `MemberUserDetailsService` els autentica amb Basic auth juntament amb l'admin global (`AuthenticationManager` amb dos `DaoAuthenticationProvider`); el frontend té pantalla de login i pàgina "Compte" per configurar les credencials pròpies
- [x] SSE `/api/v1/events` per a actualització en temps real entre dispositius — connectat des de `App.tsx` via `useHouseholdEvents`
- [x] Export / import JSON de tota la llar — `GET /api/v1/households/{id}/export` i `POST /api/v1/households/import` (mòdul `export`)
- [x] UI de les 4 pestanyes en català i castellà — `Menu`, `Shopping`, `Calendar`, `Chores` enrutades i amb claus i18n completes
- [x] Afegir UI per al calendari, buscar i evaluar llibreries com https://www.untitledui.com/react/components/calendars i evaluarla com a adopció general — vegeu [ADR 0008](adr/0008-llibreria-ui-calendari.md): es descarta Untitled UI (de pagament, requereix Tailwind) i qualsevol llibreria de components general; s'adopta el nucli MIT de `FullCalendar` (`@fullcalendar/react` 6.1.x) només per a `Calendar.tsx`, amb vistes mes/setmana/llista i locales ca/es
- **Fet quan**: la família substitueix la POC. ✅

## Fase 2 — Valor de domini

- [x] `recipes`: receptes amb ingredients (nom, quantitat, unitat, categoria), racions, etiquetes — CRUD a `/api/v1/households/{id}/recipes`
- [x] Menú ↔ receptes; generació de la compra des del menú via esdeveniment `MealPlanned` — `MealSlot.recipeId` opcional; `menu` publica `MealPlanned` (`@ApplicationModuleListener` a `shopping.domain.MealPlanListener`) que afegeix els ingredients a la llista per defecte sense duplicar-ne el nom
- [x] Compra: categories/passadissos, múltiples llistes, ítems recurrents, quantitats — `ShoppingItem` amb `quantity`/`unit`/`category`/`recurring`; `ShoppingList` amb CRUD complet (`/api/v1/households/{id}/shopping/lists`); UI a `Shopping.tsx` amb selector de llista
- [x] `calendar`: experiència d'edició tipus Android (botó `+` flotant, detall del dia en clicar-hi, edició/eliminació d'esdeveniments des del detall) i esdeveniments recurrents (diari/setmanal/mensual/anual, interval i fi opcional) — `CalendarEvent.recurrenceFreq/-Interval/-Until`, expansió d'ocurrències a `CalendarService.list` (`GET /calendar`), `Calendar.tsx`
- [x] Recurrència (RRULE) en tasques; rotació de tasques entre membres — `Chore.dueDate`/`recurrenceFreq`/`recurrenceInterval`/`rotationMemberIds` (`PUT /chores/{id}/recurrence`); marcar una tasca recurrent com a feta avança la data de venciment i rota l'assignat en lloc de deixar-la marcada (`Chore#setDone`)
- [x] `notifications`: web push per recordatoris de tasques i esdeveniments
- [x] PWA offline real per la llista de la compra (cache + cua de sincronització)
- [x] UI de `recipes` al frontend (llistat/creació de receptes, selector de recepta al `Menu`) — pàgina `Recipes.tsx` i pestanya nova; `Menu.tsx` permet triar una recepta per àpat (a més del text lliure)

## Fase 3 — IA i MCP

- [x] `assistant`: tools `@Tool` sobre les APIs públiques dels mòduls (`AssistantTools`: compra, menú, receptes, calendari, tasques)
- [x] `ChatClient` amb memòria (de moment en memòria, no JDBC); proveïdor per configuració (Ollama / OpenAI / Anthropic), cap actiu per defecte
- [x] Xat a la UI (`AssistantChat`, selector d'agents)
- [ ] **Servidor MCP** a `/mcp` (Streamable HTTP) exposant tools, resources (menú, llista) i prompts; tokens per household amb àmbits
- [x] `automation`: regles programades setmanals per household (`automation_rule`, scheduler horari amb zona horària de la llar); tipus `MENU_PROPOSAL` (agent `menu-planner` omple els dinars/sopars buits de la setmana vinent) i `SHOPPING_REMINDER` (push amb el compte de pendents via `NotificationRequested`); CRUD a `/api/v1/households/{id}/automation/rules` i pàgina `/automatitzacions`
- [x] Importació ICS (lectura): subscripcions a URLs externes amb refresc periòdic (`calendar_subscription`, esdeveniments de només lectura `source=ICS`) i pujada puntual de fitxers `.ics` que crea esdeveniments locals editables; CalDAV no fet

## Fase 4 — Clients Android i escriptori

- [ ] Client TypeScript generat des d'OpenAPI, compartit per web / Capacitor / Tauri
- [ ] Capacitor → APK Android (push natiu, compartir text a la llista, widget bàsic)
- [ ] Tauri → escriptori Linux / Windows / macOS
- [ ] (Opcional) client Kotlin generat si es fa app nativa

## Fase 5 — Maduresa

- [ ] MCP client: Home Assistant, calendaris externs, supermercats
- [ ] RAG sobre receptes (pgvector) si aporta valor
- [ ] OIDC (Authelia/Authentik/Google), més idiomes, accessibilitat, mètriques, backups programats

## Riscos vigilats

| Risc | Mitigació |
|---|---|
| Spring AI 2.x canvia API | Tot Spring AI confinat al mòdul `assistant` |
| SQLite + Hibernate/Flyway | Tests amb els dos motors a cada PR; SQL portable a `common` |
| Sobre-enginyeria | Fase 1 estrictament paritat POC; Modulith sense DDD tàctic complet |
| Build lent (frontend dins Maven) | `-Dskip.frontend` per iterar backend; cache de node al CI |
