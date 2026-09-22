import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { api } from '../api/client'
import { useHousehold } from '../context/HouseholdContext'

type RuleType = 'MENU_PROPOSAL' | 'SHOPPING_REMINDER'

type AutomationRule = {
  id: string
  householdId: string
  type: RuleType
  enabled: boolean
  dayOfWeek: number
  hour: number
  lastRunAt?: string | null
}

const RULE_TYPES: RuleType[] = ['MENU_PROPOSAL', 'SHOPPING_REMINDER']
const DAYS = [1, 2, 3, 4, 5, 6, 7]
const HOURS = Array.from({ length: 24 }, (_, i) => i)

export function Automation() {
  const { t, i18n } = useTranslation()
  const { household } = useHousehold()
  const queryClient = useQueryClient()
  const [type, setType] = useState<RuleType>('MENU_PROPOSAL')
  const [dayOfWeek, setDayOfWeek] = useState(1)
  const [hour, setHour] = useState(8)

  const dayNames = new Intl.DateTimeFormat(i18n.language, { weekday: 'long' })
  // 2024-01-01 is a Monday, so day 1..7 map to Jan 1..7.
  const dayName = (day: number) => dayNames.format(new Date(2024, 0, day))
  const formatInstant = (iso: string) =>
    new Intl.DateTimeFormat(i18n.language, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso))

  const rules = useQuery({
    queryKey: ['automation', household?.id],
    queryFn: () => api<AutomationRule[]>(`/households/${household!.id}/automation/rules`),
    enabled: !!household,
  })
  const invalidate = { onSuccess: () => queryClient.invalidateQueries({ queryKey: ['automation'] }) }

  const create = useMutation({
    mutationFn: () => api<AutomationRule>(`/households/${household!.id}/automation/rules`, {
      method: 'POST', body: JSON.stringify({ type, dayOfWeek, hour }),
    }),
    ...invalidate,
  })
  const update = useMutation({
    mutationFn: (rule: Partial<AutomationRule> & { id: string }) =>
      api<AutomationRule>(`/households/${household!.id}/automation/rules/${rule.id}`, {
        method: 'PUT', body: JSON.stringify(rule),
      }),
    ...invalidate,
  })
  const runNow = useMutation({
    mutationFn: (ruleId: string) =>
      api<AutomationRule>(`/households/${household!.id}/automation/rules/${ruleId}/run`, { method: 'POST' }),
    ...invalidate,
  })
  const remove = useMutation({
    mutationFn: (ruleId: string) =>
      api(`/households/${household!.id}/automation/rules/${ruleId}`, { method: 'DELETE' }),
    ...invalidate,
  })

  return (
    <section className="panel">
      <h2>{t('automation.title')}</h2>

      <form className="create-form" onSubmit={(event) => { event.preventDefault(); create.mutate() }}>
        <label>
          {t('automation.typeLabel')}
          <select value={type} onChange={(event) => setType(event.target.value as RuleType)}>
            {RULE_TYPES.map((rt) => <option key={rt} value={rt}>{t(`automation.type.${rt}`)}</option>)}
          </select>
        </label>
        <label>
          {t('automation.day')}
          <select value={dayOfWeek} onChange={(event) => setDayOfWeek(Number(event.target.value))}>
            {DAYS.map((d) => <option key={d} value={d}>{dayName(d)}</option>)}
          </select>
        </label>
        <label>
          {t('automation.hour')}
          <select value={hour} onChange={(event) => setHour(Number(event.target.value))}>
            {HOURS.map((h) => <option key={h} value={h}>{String(h).padStart(2, '0')}:00</option>)}
          </select>
        </label>
        <button type="submit" disabled={create.isPending}>{t('automation.add')}</button>
      </form>
      {create.isError && <p role="alert">{t('error')}</p>}

      {rules.isLoading && <p>{t('loading')}</p>}
      {rules.isError && <p role="alert">{t('error')}</p>}
      <ul className="member-list">
        {rules.data?.map((rule) => (
          <li key={rule.id}>
            <div>
              <strong>{t(`automation.type.${rule.type}`)}</strong>
              <p className="meta">{t(`automation.description.${rule.type}`)}</p>
              <p className="meta">
                {t('automation.lastRun')}: {rule.lastRunAt ? formatInstant(rule.lastRunAt) : t('automation.never')}
              </p>
            </div>
            <div className="footer-actions">
                <label className="inline">
                  <input type="checkbox" checked={rule.enabled}
                         onChange={(event) => update.mutate({ id: rule.id, enabled: event.target.checked })} />
                  {t('automation.enabled')}
                </label>
                <select value={rule.dayOfWeek}
                        onChange={(event) => update.mutate({ id: rule.id, dayOfWeek: Number(event.target.value) })}>
                  {DAYS.map((d) => <option key={d} value={d}>{dayName(d)}</option>)}
                </select>
                <select value={rule.hour}
                        onChange={(event) => update.mutate({ id: rule.id, hour: Number(event.target.value) })}>
                  {HOURS.map((h) => <option key={h} value={h}>{String(h).padStart(2, '0')}:00</option>)}
                </select>
                <button type="button" className="link" disabled={runNow.isPending}
                        onClick={() => runNow.mutate(rule.id)}>{t('automation.runNow')}</button>
                <button type="button" className="link" onClick={() => {
                  if (window.confirm(t('automation.confirmDelete'))) remove.mutate(rule.id)
                }}>{t('automation.delete')}</button>
            </div>
          </li>
        ))}
      </ul>
    </section>
  )
}
