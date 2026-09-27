import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { useTranslation } from 'react-i18next'
import Markdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { useHousehold } from '../context/HouseholdContext'
import { api } from '../api/client'

type Agent = {
  id: string
  nameKey: string
  descriptionKey: string
  supportsMedia: boolean
  toolNames: string[]
  model?: string
}

type Attachment = { mimeType: string; data: string }

type RecipeSuggestion = {
  name: string
  servings: number
  notes?: string
  description?: string
  steps?: string[]
  imageMimeType?: string
  imageData?: string
  tags: string[]
  ingredients: { name: string; quantity?: number; unit?: string; category?: string }[]
}

type Message = {
  role: 'user' | 'assistant'
  text: string
  attachments?: Attachment[]
  recipe?: RecipeSuggestion
  via?: string[]
  hideText?: boolean
  dev?: DevStats
  error?: boolean
}

type DevStats = {
  agentId: string
  latencyMs: number
  conversationId: string
  delegations: { agentId: string; text: string; latencyMs?: number }[]
}

type AgentResponse = {
  agentId: string
  conversationId: string
  text: string
  toolCalls: string[]
  delegations?: { agentId: string; text: string; latencyMs?: number }[]
}

type CreatedRecipe = {
  id: string
  name: string
  servings: number
  notes?: string
  description?: string
  steps?: string[]
  imageMimeType?: string
  imageData?: string
  tags: string[]
  ingredients: { name: string; quantity?: number; unit?: string; category?: string }[]
}

const stroke = { fill: 'none', stroke: 'currentColor', strokeWidth: 1.8, strokeLinecap: 'round', strokeLinejoin: 'round' } as const

function uuid(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

function fileToBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      const result = reader.result as string
      resolve(result.split(',')[1] ?? '')
    }
    reader.onerror = reject
    reader.readAsDataURL(file)
  })
}

function normalizeForMatch(text: string): string {
  return text
    .toLowerCase()
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
}

function wantsCreate(text: string): boolean {
  const normalized = normalizeForMatch(text)
  const triggers = ['crea', 'crear', 'crei', 'creï', 'afegeix', 'guarda', 'desa', 'fes-la', 'añadir', 'agrega', 'guardar', 'salvar', 'hazla']
  return triggers.some((t) => normalized.includes(t))
}

function extractJson(text: string): string | null {
  const trimmed = text.trim()
  const codeBlock = trimmed.match(/```(?:json)?\s*([\s\S]*?)\s*```/)
  if (codeBlock) return codeBlock[1]!.trim()
  const first = trimmed.indexOf('{')
  const last = trimmed.lastIndexOf('}')
  if (first >= 0 && last > first) return trimmed.slice(first, last + 1)
  return null
}

function tryParseRecipe(text: string): RecipeSuggestion | undefined {
  console.debug('[AssistantChat] parsing response:', text)
  const json = extractJson(text)
  if (!json) {
    console.debug('[AssistantChat] no JSON found in response')
    return undefined
  }
  try {
    const parsed = JSON.parse(json) as RecipeSuggestion & { error?: boolean }
    if (parsed.error) {
      console.debug('[AssistantChat] parsed error flag')
      return undefined
    }
    if (parsed.name && Array.isArray(parsed.ingredients)) {
      console.debug('[AssistantChat] parsed recipe:', parsed)
      return parsed
    }
  } catch (err) {
    console.debug('[AssistantChat] not a JSON response', err)
  }
  return undefined
}

export function AssistantChat() {
  const { t } = useTranslation()
  const { household, member } = useHousehold()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [agents, setAgents] = useState<Agent[]>([])
  const [loadError, setLoadError] = useState<string | null>(null)
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [pending, setPending] = useState(false)
  const [file, setFile] = useState<File | null>(null)
  const [pendingImage, setPendingImage] = useState<Attachment | null>(null)
  const [conversationId, setConversationId] = useState<string>(() => uuid())
  const [devMode, setDevMode] = useState(() => localStorage.getItem('fanel.assistant.dev') === '1')
  const endRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!household || !open) return
    setLoadError(null)
    api<Agent[]>(`/households/${household.id}/assistant/agents`)
      .then(setAgents)
      .catch((err) => {
        setAgents([])
        setLoadError(err instanceof Error ? err.message : t('error'))
      })
  }, [household, open, t])

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, open])

  if (!household) return null

  const canAttach = agents.some((a) => a.supportsMedia)
  const noAgents = !loadError && agents.length === 0

  const toggleDevMode = () =>
    setDevMode((v) => {
      localStorage.setItem('fanel.assistant.dev', v ? '0' : '1')
      return !v
    })

  const agentLabel = (id: string) => {
    const agent = agents.find((a) => a.id === id)
    return agent ? t(agent.nameKey) : id
  }
  const agentModel = (id: string) => agents.find((a) => a.id === id)?.model

  const send = async () => {
    if (noAgents || (!input.trim() && !file)) return
    setPending(true)

    const userMessage = input.trim()
    const shouldCreate = wantsCreate(userMessage)

    const attachments: Attachment[] = []
    if (file) {
      const data = await fileToBase64(file)
      console.debug('[AssistantChat] attached image:', file.type, data.length)
      attachments.push({ mimeType: file.type, data })
      setPendingImage({ mimeType: file.type, data })
    }

    console.debug('[AssistantChat] sending message:', { message: input, attachments: attachments.length, shouldCreate })

    const userText = userMessage || (file ? t('assistant.imageAttached') : '')
    setMessages((prev) => [...prev, { role: 'user', text: userText, attachments }])
    setInput('')
    setFile(null)

    const startedAt = performance.now()
    try {
      const response = await api<AgentResponse>(`/households/${household.id}/assistant/chat`, {
        method: 'POST',
        body: JSON.stringify({ conversationId, message: input, attachments, memberId: member?.id ?? null }),
      })
      const latencyMs = Math.round(performance.now() - startedAt)
      console.debug('[AssistantChat] agent response:', response)
      setConversationId(response.conversationId)
      const delegationText = response.delegations?.find((d) => d.agentId === 'recipe-from-image')?.text
      let recipe = delegationText ? tryParseRecipe(delegationText) : undefined
      let hideText = false
      if (!recipe) {
        // A recipe proposal parsed straight from the reply text replaces it: the card renders it.
        recipe = tryParseRecipe(response.text)
        hideText = recipe != null
      }
      if (recipe && pendingImage) {
        recipe.imageMimeType = pendingImage.mimeType
        recipe.imageData = pendingImage.data
        console.debug('[AssistantChat] attached image to recipe:', pendingImage.mimeType, pendingImage.data.length)
      }
      const via = response.delegations?.map((d) => agentLabel(d.agentId))
      const dev: DevStats = {
        agentId: response.agentId,
        latencyMs,
        conversationId: response.conversationId,
        delegations: response.delegations ?? [],
      }
      setMessages((prev) => [...prev, { role: 'assistant', text: response.text, recipe, via, hideText, dev }])
      if (recipe && shouldCreate) {
        console.debug('[AssistantChat] auto-creating recipe because user asked')
        void createRecipe(recipe, true)
      }
    } catch (err) {
      setMessages((prev) => [
        ...prev,
        { role: 'assistant', text: err instanceof Error ? err.message : t('error'), error: true },
      ])
    } finally {
      setPending(false)
    }
  }

  const createRecipe = async (recipe: RecipeSuggestion, redirect = false) => {
    console.debug('[AssistantChat] creating recipe:', recipe)
    try {
      await api<CreatedRecipe>(`/households/${household.id}/recipes`, {
        method: 'POST',
        body: JSON.stringify({
          name: recipe.name,
          servings: recipe.servings,
          notes: recipe.notes,
          description: recipe.description,
          steps: recipe.steps,
          tags: recipe.tags,
          ingredients: recipe.ingredients.map((i) => ({
            name: i.name,
            quantity: i.quantity,
            unit: i.unit,
            category: i.category,
          })),
          imageMimeType: recipe.imageMimeType,
          imageData: recipe.imageData,
        }),
      })
      setPendingImage(null)
      setMessages((prev) => [...prev, { role: 'assistant', text: t('assistant.recipeCreated') }])
      if (redirect) {
        setOpen(false)
        navigate('/receptes')
      }
    } catch (err) {
      setMessages((prev) => [
        ...prev,
        { role: 'assistant', text: err instanceof Error ? err.message : t('error'), error: true },
      ])
    }
  }

  const handleKey = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      void send()
    }
  }

  return (
    <>
      <button
        type="button"
        className="assistant-fab"
        aria-label={t('assistant.open')}
        onClick={() => setOpen(true)}
      >
        <svg viewBox="0 0 24 24" width="1.5rem" height="1.5rem" {...stroke}>
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
        </svg>
      </button>

      {open && (
        <>
          <div className="assistant-backdrop" onClick={() => setOpen(false)} />
          <div className="assistant-panel" role="dialog" aria-label={t('assistant.title')}>
            <div className="assistant-header">
              <h3>{t('assistant.title')}</h3>
              <button
                type="button"
                className={`assistant-dev-toggle${devMode ? ' on' : ''}`}
                onClick={toggleDevMode}
                aria-label={t('assistant.dev.toggle')}
                aria-pressed={devMode}
              >
                DEV
              </button>
              <button type="button" className="icon-btn" onClick={() => setOpen(false)} aria-label={t('assistant.close')}>
                ✕
              </button>
            </div>

            {loadError && <p className="assistant-error">{loadError}</p>}

            <div className="assistant-messages">
              {messages.length === 0 && !loadError && (
                <p className="assistant-empty">{noAgents ? t('assistant.noAgents') : t('assistant.intro')}</p>
              )}
              {messages.map((m, i) => (
                <div
                  key={i}
                  className={`assistant-message ${m.role}${m.error ? ' error' : ''}`}
                >
                  {m.attachments?.map((a, j) => (
                    a.mimeType.startsWith('image/') ? (
                      <img
                        key={j}
                        src={`data:${a.mimeType};base64,${a.data}`}
                        alt={t('assistant.imageAttached')}
                      />
                    ) : (
                      <span key={j}>{t('assistant.imageAttached')}</span>
                    )
                  ))}
                  {m.text && !m.hideText && (
                    m.role === 'assistant' && !m.error ? (
                      <div className="assistant-md">
                        <Markdown remarkPlugins={[remarkGfm]}>{m.text}</Markdown>
                      </div>
                    ) : (
                      <p>{m.text}</p>
                    )
                  )}
                  {m.recipe && (
                    <div className="assistant-recipe">
                      <h4>{m.recipe.name}</h4>
                      {m.recipe.imageMimeType && m.recipe.imageData && (
                        <img
                          src={`data:${m.recipe.imageMimeType};base64,${m.recipe.imageData}`}
                          alt={m.recipe.name}
                          className="recipe-thumb"
                        />
                      )}
                      <p>{t('recipes.servings')}: {m.recipe.servings}</p>
                      {m.recipe.description && (
                        <p className="recipe-description">{m.recipe.description}</p>
                      )}
                      {m.recipe.steps && m.recipe.steps.length > 0 && (
                        <ol className="steps-list">
                          {m.recipe.steps.map((s, k) => <li key={k}>{s}</li>)}
                        </ol>
                      )}
                      {m.recipe.ingredients.length > 0 && (
                        <ul>
                          {m.recipe.ingredients.map((ing, k) => (
                            <li key={k}>
                              {ing.name}
                              {ing.quantity !== undefined && ` — ${ing.quantity}`}
                              {ing.unit && ` ${ing.unit}`}
                              {ing.category && ` (${ing.category})`}
                            </li>
                          ))}
                        </ul>
                      )}
                      <button type="button" onClick={() => void createRecipe(m.recipe!)}>
                        {t('assistant.createRecipe')}
                      </button>
                    </div>
                  )}
                  {m.via && m.via.length > 0 && (
                    <p className="assistant-via">{t('assistant.via', { agents: m.via.join(', ') })}</p>
                  )}
                  {devMode && m.dev && (
                    <div className="assistant-dev">
                      <div className="assistant-dev-line">
                        {agentLabel(m.dev.agentId)}
                        {agentModel(m.dev.agentId) && <> · {agentModel(m.dev.agentId)}</>}
                        {' · '}{m.dev.latencyMs} ms
                      </div>
                      <div className="assistant-dev-line dim">
                        {t('assistant.dev.conversation')}: {m.dev.conversationId.slice(0, 8)}…
                      </div>
                      {m.dev.delegations.map((d, k) => (
                        <details key={k} className="assistant-dev-delegation">
                          <summary>
                            ↳ {agentLabel(d.agentId)}
                            {agentModel(d.agentId) && <> · {agentModel(d.agentId)}</>}
                            {d.latencyMs != null && <> · {d.latencyMs} ms</>}
                          </summary>
                          <pre>{d.text}</pre>
                        </details>
                      ))}
                    </div>
                  )}
                </div>
              ))}
              <div ref={endRef} />
            </div>

            <div className="assistant-input">
              {!member && <p className="assistant-hint">{t('assistant.pickMember')}</p>}
              {canAttach && (
                <label className="assistant-file">
                  <input
                    type="file"
                    accept="image/*"
                    onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                  />
                  {file ? file.name : t('assistant.attachImage')}
                </label>
              )}
              <div className="assistant-input-row">
                <textarea
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  onKeyDown={handleKey}
                  placeholder={t('assistant.placeholder')}
                  rows={2}
                  disabled={pending}
                />
                <button type="button" onClick={() => void send()}
                        disabled={pending || noAgents || (!input.trim() && !file)}>
                  {t('assistant.send')}
                </button>
              </div>
            </div>
          </div>
        </>
      )}
    </>
  )
}
