import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { client, unwrap } from '../api/typed'
import type { AgentConfig, AgentTestResult, ProviderInfo } from '../api/types'

const PROVIDER_LABELS: Record<string, string> = {
  openai: 'OpenAI',
  ollama: 'Ollama',
  anthropic: 'Anthropic',
}

const OTHER_MODEL = '__other__'

function providerLabel(provider: string): string {
  return PROVIDER_LABELS[provider] ?? provider
}

function providerState(p: ProviderInfo, t: (key: string, opts?: Record<string, unknown>) => string): string {
  if (!p.available) return t('assistantSettings.provider.notConfigured')
  if (p.error) return t('assistantSettings.provider.error', { error: p.error })
  return t('assistantSettings.provider.models', { count: p.models?.length ?? 0 })
}

function AgentCard({ agent, providers, onSaved }: {
  agent: AgentConfig
  providers: ProviderInfo[]
  onSaved: () => void
}) {
  const { t } = useTranslation()
  const [enabled, setEnabled] = useState(agent.enabled ?? true)
  const [provider, setProvider] = useState(agent.provider ?? '')
  const [model, setModel] = useState(agent.model ?? '')
  const [freeModel, setFreeModel] = useState(false)
  const [temperature, setTemperature] = useState(agent.temperature ?? 0.7)
  const [maxTokens, setMaxTokens] = useState(agent.maxTokens != null ? String(agent.maxTokens) : '')
  const [testResult, setTestResult] = useState<AgentTestResult | null>(null)

  useEffect(() => {
    setEnabled(agent.enabled ?? true)
    setProvider(agent.provider ?? '')
    setModel(agent.model ?? '')
    setTemperature(agent.temperature ?? 0.7)
    setMaxTokens(agent.maxTokens != null ? String(agent.maxTokens) : '')
    setTestResult(null)
  }, [agent])

  const availableProviders = providers.filter((p) => p.available)
  const providerOptions = availableProviders.map((p) => p.provider!)
  if (provider && !providerOptions.includes(provider)) providerOptions.unshift(provider)

  const selectedProvider = providers.find((p) => p.provider === provider)
  const modelOptions = selectedProvider?.models ?? []
  const customModel = freeModel || (model !== '' && !modelOptions.includes(model))

  const save = useMutation({
    mutationFn: () => unwrap(client.PUT('/api/v1/admin/assistant/agents/{agentId}', {
      params: { path: { agentId: agent.id } },
      body: {
        enabled,
        provider: provider || undefined,
        model: model || undefined,
        temperature,
        maxTokens: maxTokens.trim() === '' ? undefined : Number(maxTokens),
      },
    })),
    onSuccess: () => {
      setTestResult(null)
      onSaved()
    },
  })
  const reset = useMutation({
    mutationFn: () => unwrap(client.DELETE('/api/v1/admin/assistant/agents/{agentId}',
      { params: { path: { agentId: agent.id } } })),
    onSuccess: () => {
      setTestResult(null)
      onSaved()
    },
  })
  const test = useMutation({
    mutationFn: () => unwrap(client.POST('/api/v1/admin/assistant/agents/{agentId}/test',
      { params: { path: { agentId: agent.id } } })),
    onSuccess: (result) => setTestResult(result ?? null),
    onError: (err) => setTestResult({ ok: false, error: err instanceof Error ? err.message : String(err) }),
  })

  return (
    <div className={`agent-card${enabled ? '' : ' disabled'}`}>
      <div className="agent-card-head">
        <div>
          <strong>{t(agent.nameKey ?? '')}</strong>
          <p className="meta">{t(agent.descriptionKey ?? '')}</p>
        </div>
        <div className="agent-badges">
          {agent.orchestrator && <span className="agent-badge">{t('assistantSettings.badge.orchestrator')}</span>}
          {agent.supportsMedia && <span className="agent-badge">{t('assistantSettings.badge.vision')}</span>}
          {agent.overridden && <span className="agent-badge custom">{t('assistantSettings.badge.custom')}</span>}
          {agent.available === false && <span className="agent-badge warn">{t('assistantSettings.unavailable')}</span>}
        </div>
      </div>

      <div className="agent-form">
        <label className="inline">
          <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />
          {t('assistantSettings.enabled')}
        </label>
        <label>
          {t('assistantSettings.providerLabel')}
          <select value={provider} onChange={(e) => { setProvider(e.target.value); setFreeModel(false) }}>
            {provider === '' && <option value="">—</option>}
            {providerOptions.map((p) => <option key={p} value={p}>{providerLabel(p)}</option>)}
          </select>
        </label>
        <label>
          {t('assistantSettings.model')}
          <select
            value={customModel ? OTHER_MODEL : model}
            onChange={(e) => {
              if (e.target.value === OTHER_MODEL) {
                setFreeModel(true)
              } else {
                setFreeModel(false)
                setModel(e.target.value)
              }
            }}
          >
            {modelOptions.map((m) => <option key={m} value={m}>{m}</option>)}
            <option value={OTHER_MODEL}>{t('assistantSettings.otherModel')}</option>
          </select>
        </label>
        {customModel && (
          <input
            type="text"
            value={model}
            placeholder={t('assistantSettings.model')}
            onChange={(e) => { setFreeModel(true); setModel(e.target.value) }}
          />
        )}
        <label>
          {t('assistantSettings.temperature')}: {temperature.toFixed(1)}
          <input type="range" min={0} max={2} step={0.1} value={temperature}
                 onChange={(e) => setTemperature(Number(e.target.value))} />
        </label>
        <label>
          {t('assistantSettings.maxTokens')}
          <input type="number" min={1} value={maxTokens} placeholder="—"
                 onChange={(e) => setMaxTokens(e.target.value)} />
        </label>
      </div>

      <p className="meta">
        {t('assistantSettings.defaults', {
          provider: agent.defaults?.provider ?? '—',
          model: agent.defaults?.model ?? '—',
        })}
      </p>

      <div className="agent-actions">
        <button type="button" disabled={save.isPending} onClick={() => save.mutate()}>
          {t('assistantSettings.save')}
        </button>
        {agent.overridden && (
          <button type="button" className="link" disabled={reset.isPending} onClick={() => reset.mutate()}>
            {t('assistantSettings.reset')}
          </button>
        )}
        <button type="button" className="link" disabled={test.isPending} onClick={() => test.mutate()}>
          {test.isPending ? t('assistantSettings.testing') : t('assistantSettings.test')}
        </button>
      </div>
      {save.isError && <p role="alert">{t('error')}</p>}
      {testResult && (
        <p className={`agent-test-result ${testResult.ok ? 'ok' : 'error'}`}>
          {testResult.ok
            ? t('assistantSettings.testOk', { latency: testResult.latencyMs, text: testResult.text })
            : t('assistantSettings.testError', { error: testResult.error })}
        </p>
      )}
    </div>
  )
}

export function AssistantSettings() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [refreshTick, setRefreshTick] = useState(0)

  const agents = useQuery({
    queryKey: ['assistant-admin', 'agents'],
    queryFn: () => unwrap(client.GET('/api/v1/admin/assistant/agents')),
  })
  const providers = useQuery({
    queryKey: ['assistant-admin', 'providers', refreshTick],
    queryFn: () => unwrap(client.GET('/api/v1/admin/assistant/providers', {
      params: { query: { refresh: refreshTick > 0 } },
    })),
  })

  const invalidateAgents = () => queryClient.invalidateQueries({ queryKey: ['assistant-admin', 'agents'] })

  const list = (agents.data ?? []) as AgentConfig[]
  const orchestrator = list.find((a) => a.orchestrator)
  const subagents = list.filter((a) => !a.orchestrator)

  return (
    <section className="panel assistant-settings">
      <h2>{t('assistantSettings.title')}</h2>
      <p className="meta">{t('assistantSettings.intro')}</p>

      <div className="provider-chips">
        {providers.data?.map((p) => (
          <span key={p.provider} className={`provider-chip${p.available && !p.error ? ' ok' : p.error ? ' error' : ''}`}
                title={p.error ?? undefined}>
            <strong>{providerLabel(p.provider ?? '')}</strong>
            <span>{providerState(p, t)}</span>
          </span>
        ))}
        <button type="button" className="link" disabled={providers.isFetching}
                onClick={() => setRefreshTick((n) => n + 1)}>
          {t('assistantSettings.refreshModels')}
        </button>
      </div>
      {providers.isError && <p role="alert">{t('error')}</p>}

      {agents.isLoading && <p>{t('loading')}</p>}
      {agents.isError && <p role="alert">{t('error')}</p>}
      {agents.data && (
        <div className="agent-graph">
          {orchestrator && (
            <AgentCard agent={orchestrator} providers={providers.data ?? []} onSaved={invalidateAgents} />
          )}
          {subagents.length > 0 && (
            <>
              <div className="agent-connector" aria-hidden="true" />
              <div className="subagent-row">
                {subagents.map((a) => (
                  <AgentCard key={a.id} agent={a} providers={providers.data ?? []} onSaved={invalidateAgents} />
                ))}
              </div>
            </>
          )}
        </div>
      )}
    </section>
  )
}
