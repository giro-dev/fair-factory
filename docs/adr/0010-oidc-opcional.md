# ADR 0010 — Inici de sessió extern opcional via OIDC

**Estat**: Acceptat · **Data**: 2026-09-23

## Context

Fanel és auto-allotjable i molts desplegaments domèstics ja tenen un proveïdor d'identitat
(Authelia, Authentik) o usen un compte de Google. Avui l'accés és només Basic auth: el compte
admin global (variables d'entorn), usuaris/contrasenya per membre adult i un token Bearer per a
la API. Cal poder entrar amb SSO **sense obligar** cap instal·lació a configurar-lo.

## Opcions avaluades

| Opció | Pros | Contres |
|---|---|---|
| **OIDC opcional amb Spring Security oauth2-client** | Estàndard, funciona amb Authelia/Authentik/Google; Boot gestiona tot el flux | Requereix una sessió per al flux (cookies), mentre la resta de la API és stateless |
| **Proxy d'autenticació davant (p. ex. oauth2-proxy/Authelia) i cap canvi a Fanel** | Zero codi | Depèn d'infraestructura externa; Fanel no sabria quin membre és l'usuari (cal injectar headers de confiança i confiar el proxy) |
| **SAML/CAS** | També estàndard | Més pesat i menys habitual en self-hosting modern |

## Decisió

S'adopta l'opció **1**: login OIDC opcional dins el monòlit.

1. **Configuració** només per entorn (`fanel.oidc.*` / `FANEL_OIDC_*`): `issuer` (descobriment),
   `client-id`, `client-secret`, `name` (etiqueta del botó), `username-claim` i `scopes`. Sense
   `issuer` no existeix cap `ClientRegistrationRepository` i el comportament és idèntic a abans
   (stateless, sense `oauth2Login`).

2. **Sessió només per a OIDC**: quan hi ha registre, `SessionCreationPolicy` passa a `IF_REQUIRED`
   i es configura `oauth2Login` + `logout` a `/api/v1/auth/logout`. Els clients Basic/Bearer no
   creen sessió (Spring Security no desa el context per ells); les peticions `/api/**`
   no autenticades continuen responent **401** (entry point explícit), no un redirect a l'IdP.

3. **Correspondència amb membres**: `MemberOidcUserService` (mòdul `household`, exposat com a
   `OidcUserService` perquè `shared` no importi internals) resol l'OIDC identity a un `Member`:
   primer per `member.oidc_subject` (nova columna, índex únic), després pel claim d'usuari
   configurat (`preferred_username`, fallback `email`); el primer match per usuari **fixa** el
   subject, de manera que un canvi de nom d'usuari al proveïdor no desvincula el membre. Usuari
   desconegut ⇒ `OAuth2AuthenticationException` i redirect a `/?sso=failed`.

4. **Principal compartit**: el resultat (`MemberOidcUser`) implementa `OidcUser` **i**
   `MemberPrincipal`, així `HouseholdScopeFilter`, `CurrentAccess` i `/api/v1/me` funcionen igual
   que amb login per contrasenya.

5. **Frontend**: `authStore` admet dues credencials (`basic` i `session`); `AuthContext` prova
   `/api/v1/me` amb cookie quan no hi ha credencials desades; `Login` llista els proveïdors de
   `GET /api/v1/auth/providers` i els botons fan `window.location.assign(loginUrl)`. SSE i la
   cua de sync fan fetch same-origin (cookies automàtiques), sense cap header extra.

6. **Sense auto-aprovisionament**: el login extern només autentica membres existents; crear
   membres continua sent una acció d'admin — evita que qualsevol usuari del proveïdor entri a
   una llar sense autorització prèvia.

## Conseqüències

- Un desplegament amb `FANEL_OIDC_ISSUER` obté el flux complet; la resta no nota cap canvi (tests
  inclosos: el bean de registre no es crea, i la cadena de seguretat queda exactament com abans).
- L'endpoint `GET /api/v1/auth/providers` és públic i retorna `[]` sense OIDC.
- Si el proveïdor canvia el `sub` d'un usuari (compte migrat/reemès), el membre queda desvinculat
  fins que un admin li reassigni o l'usuari coincideixi de nou per username claim.
- Les sessions són d'un sol procés (memòria del servidor); reiniciar Fanel tanca les sessions OIDC.
