# ADR 0011 — Assistent orquestrat i configuració de models per agent

**Estat**: Acceptat · **Data**: 2026-09-23

## Context

L'[ADR 0009](0009-assistant-multiagent.md) va introduir l'assistent multiagent amb un `ModelProfile` per agent, però va deixar dues coses provisionals: la configuració dels agents només vivia al yml (punt 3) i el client escollia l'agent via `agentId` (punt 6). Ara volem que el xat tingui un sol interlocutor —un orquestrador que resol el que pot i delega la resta a subagents especialitzats— i un panell d'administració global per triar proveïdor/model/paràmetres de cada agent sense editar fitxers ni reiniciar.

## Opcions avaluades

| Opció | Pros | Contres |
|---|---|---|
| **Subagents com a tools** (`delegate_to_*` al ChatClient de l'orquestrador) | L'LLM decideix quan delegar amb el context del missatge; reutilitza `Agent.execute` i la memòria per conversa; cap component nou d'infraestructura | Una crida LLM extra per delegació (cost/latència) |
| **Router d'intenció** (classificador que tria l'agent abans de cridar el model) | Una sola crida quan encerta | Cal un classificador fiable; el xat perd la conversa directa; dues lògiques (router + agents) |
| **Un sol agent potent** sense delegació | El més senzill | Impossible separar models per funció (visió, planificació); contradueix l'esperit de l'ADR 0009 |

## Decisió

S'adopta l'opció **1**: subagents exposats com a tools de Spring AI.

1. **Orquestrador únic**: `orchestrator` és l'únic agent amb qui parla el xat (`fanel.assistant.orchestrator-id`); `AgentService.chat` l'usa per defecte quan el request no porta `agentId`. El selector d'agents desapareix de la UI. L'`agentId` explícit continua acceptat per compatibilitat (`AssistantApi.run` per `automation`, p. ex. `menu-planner`).

2. **Delegació com a tools**: `SubagentToolFactory` genera una `FunctionToolCallback` `delegate_to_<agent>` per cada subagent actiu. El context de la crida (household, member, locale, `conversationId`, adjunts) viatja pel `ToolContext` (`DelegationContext`, clau `fanel.delegation`), perquè els paràmetres de la tool només porten la `task` en llenguatge natural. Els adjunts només es passen al subagent si aquest `supportsMedia`; si l'orquestrador no és multimodal, el missatge d'usuari porta una nota textual que l'obliga a delegar.

3. **Memòria de subagent**: el subagent s'executa amb `conversationId = <conversa>:<agentId>`, així manté memòria pròpia per conversa sense barrejar-se amb l'orquestrador ni amb altres delegacions.

4. **Delegacions a la resposta**: `AgentResponse` porta `delegations: [{agentId, text}]` amb el text en brut del subagent. Això preserva el JSON estructurat (p. ex. la proposta de recepta de `recipe-from-image`) sense dependre que l'orquestrador el reprodueixi literalment; la UI el parseja des de la delegació.

5. **Catàleg + overrides**: el yml (`fanel.assistant.agents.*`) continua sent el catàleg (claus i18n, `prompt-key`, `supports-media`, `tools`, `tool-description`, defaults del model). La taula `assistant_agent_config` guarda només overrides globals (enabled, provider, model, temperature, maxTokens) editables únicament pel global admin. Config efectiva = defaults ⊕ override.

6. **Credencials només per env**: el panell no mostra ni guarda claus. `ProviderAvailability` decideix si un proveïdor és usable: OpenAI/Anthropic requereixen bean + API key no buida (Spring AI auto-configura el ChatModel encara que no hi hagi credencial), Ollama només el bean.

7. **Descobriment de models**: `ModelCatalogService` llista els models de cada proveïdor usable (Ollama `/api/tags` via `OllamaApi`, OpenAI i Anthropic `GET /v1/models`), amb cache de 5 minuts, timeout de 3 s i `error` en lloc d'excepció. Un proveïdor no usable no rep cap crida.

8. **Recàrrega en calent**: `AgentRegistry` es reconstrueix amb `reload()` (a l'arrencada via `ApplicationReadyEvent` i després de cada PUT/DELETE d'override): salta agents desactivats o sense `ChatModel` usable i reconstrueix les `delegate_to_*` de l'orquestrador amb els subagents actius.

9. **REST d'administració**: `/api/v1/admin/assistant` (només `hasGlobalAccess()`): `GET/PUT/DELETE /agents` (config efectiva + override), `GET /providers?refresh=` (descobriment) i `POST /agents/{id}/test` (ping de connectivitat del `ModelProfile` efectiu, sense tools ni memòria).

## Conseqüències

- **Substitueix els punts 3 i 6 de l'ADR 0009**: la configuració dels agents ja no és només al yml (hi ha overrides a BD), i el client ja no tria l'agent (tot passa per l'orquestrador).
- **Doble cost/latència**: cada delegació és una crida LLM addicional. Es mitiga donant `AssistantTools` a l'orquestrador perquè resolgui consultes simples directament i redactant els prompts perquè delegui només el que cal.
- **Models locals petits i tool calling**: alguns models Ollama gestionen malament moltes tools. El panell permet posar un model potent només a l'orquestrador i models barats als subagents.
- **Memòria**: continua en RAM (`MessageWindowChatMemory`); els sufixos de conversa multipliquen entrades. Persistència JDBC fora d'abast.
- **Nous fitxers**: migració `V18__assistant_agent_config.sql`, `OrchestratorAgent`, `SubagentToolFactory`, `DelegationContext`, `AgentConfigEntity/Service`, `ModelCatalogService`, `ProviderAvailability`, `AssistantAdminController`, prompts `orchestrator_{ca,es}.txt` i la pàgina `/assistent` del frontend.
