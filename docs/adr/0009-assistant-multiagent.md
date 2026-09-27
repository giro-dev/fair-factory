# ADR 0009 — Assistent multiagent: un model/API per funció

**Estat**: Acceptat · **Data**: 2026-09-09

> Parcialment substituït per l'[ADR 0011](0011-orquestrador-i-config-models.md) (punts 3 i 6).

## Context

L'[ADR 0006](0006-spring-ai-mcp.md) ja fixa Spring AI confinat al mòdul `assistant`, amb tools compartides i proveïdor configurable. La Fase 3 del roadmap afegeix xat, MCP i automatismes. Es vol un assistent **multiagent** on cada agent pugui tenir el model i la API més adequats a la seva funció: agents privats amb IA local (Ollama) per a consultes de la llar, i agents més potents (multimodals, cloud) per a tasques com extreure una recepta a partir d'una imatge.

## Opcions avaluades

| Opció | Pros | Contres |
|---|---|---|
| **Un sol `ChatClient` global** per a tot l'assistent | Senzill, una sola configuració | No permet triar model per tasca; impossibilita combinar Ollama privat amb GPT-4o per a visió; difícil d'escalar |
| **Un `Agent` per funció, cada un amb el seu `ChatClient` i `ModelProfile`** | Model/API ajustat a la tasca; privacitat quan es vol; permet agents de visió i futurs agents especialitzats; routing explícit o per intenció | Més classes i configuració; cal orquestrar la selecció d'agent |
| **Servidor d'agents extern (p. ex. Python/LangChain)** | Ecosistemes més madurs d'agents | Torna a la duplicació de lògica de domini i a un segon servei; contradueix l'ADR 0001/0006 |

## Decisió

S'adopta l'opció **2**: assistent multiagent dins el mòdul `assistant` de Fanel.

1. **Estructura del mòdul**: `assistant` es divideix en `api` (contracte públic), `domain` (agents, tools i orquestració), `infra` (constructors de clients, memòria, configuració) i `web` (REST). El mòdul només importa `*.api.*` dels altres mòduls i `shared`.

2. **Agent**: cada agent implementa una interfície `Agent` i exposa una `AgentDefinition` (id, claus i18n de nom/descripció, `ModelProfile`, llista de tools, suport multimodal). Els agents són beans Spring registrats a `AgentRegistry`.

3. **ModelProfile**: defineix el proveïdor (`ollama`, `openai`, `anthropic`...) i els paràmetres del model (`model`, `temperature`, `maxTokens`). La configuració es llegeix de `fanel.assistant.agents.*` i de les propietats `spring.ai.*` habituals (API keys per variable d'entorn). Cap proveïdor actiu per defecte: si no hi ha cap `ChatModel` disponible, els beans d'`assistant` no s'activen i l'aplicació funciona igualment.

4. **Tools**: es defineixen una sola vegada a `AssistantTools` (POJO amb `@Tool`) i deleguen a les `api/` dels mòduls `household`, `menu`, `recipes`, `shopping`, `calendar` i `chores`. Aquestes mateixes tools són les que usaran el xat intern i el futur servidor MCP.

5. **Agents inicials**:
   - `general`: xat amb memòria i accés a tools; pot anar a Ollama o a qualsevol model de text.
   - `recipe-from-image`: agent multimodal (sense tools o amb tools mínimes de `recipes`) pensat per a models amb visió (p. ex. GPT-4o, Claude 3.5 Sonnet); rep una imatge i extreu una proposta de recepta.
   - Futurs: `menu-planner`, `shopping-assistant`, `chores-assistant`, `automation`.

6. **Orquestració**: per ara el client escull l'agent via `agentId` al request (`AgentService` el resol des de `AgentRegistry`). En el futur es pot afegir un `RouterAgent` que triï automàticament per intenció.

7. **Memòria**: `MessageWindowChatMemory` amb `MessageChatMemoryAdvisor` per conversa (`conversationId` UUID). Inicialment memòria en memòria; la persistència JDBC es farà en una iteració posterior per no bloquejar l'inici de la fase.

8. **API REST**: `GET /api/v1/households/{householdId}/assistant/agents` (llistar agents disponibles) i `POST /api/v1/households/{householdId}/assistant/chat` (enviar missatge a un agent). Els endpoints requereixen autenticació i validen que l'usuari pertany a la llar.

9. **MCP i automatismes**: el futur servidor MCP reutilitzarà `AssistantTools` i podrà invocar agents. El mòdul `automation` podrà escoltar esdeveniments d'`assistant` o invocar `AgentService` per regles reactives.

## Conseqüències

- Noves dependències de Spring AI al `backend/pom.xml`: `spring-ai-starter-model-openai`, `spring-ai-starter-model-ollama`, `spring-ai-starter-model-anthropic` i `spring-ai-starter-mcp-server-webmvc`. La memòria persistent amb `spring-ai-starter-model-chat-memory-repository-jdbc` es deixa preparada per a una iteració posterior.
- Noves classes només dins `dev.agiro.fanel.assistant.*`; cap altre mòdul importa classes de Spring AI.
- Prompts de sistema i missatges d'agent en recursos i18n (`messages*.properties` o fitxers `assistant/prompts/`) per no hardcodejar text.
- La configuració dels agents viu a `application*.yml`; els secrets (API keys) sempre per variables d'entorn.
- El reconeixement d'imatges requereix un model multimodal; l'agent `recipe-from-image` pot rebre dades base64 i retornar una proposta de recepta estructurada.
