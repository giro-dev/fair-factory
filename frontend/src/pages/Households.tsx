import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { api } from '../api/client'
import { useHousehold, type Household } from '../context/HouseholdContext'

export function Households() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const { household, households, canManageHouseholds, selectHousehold, members } = useHousehold()
  const [name, setName] = useState('')

  const create = useMutation({
    mutationFn: () => api<Household>('/households', {
      method: 'POST',
      body: JSON.stringify({ name, locale: 'ca', timezone: 'Europe/Madrid' }),
    }),
    onSuccess: async (created) => {
      setName('')
      selectHousehold(created.id)
      await queryClient.invalidateQueries({ queryKey: ['households'] })
    },
  })

  // Members are bound to a single household: show it read-only instead of a management UI.
  if (!canManageHouseholds) {
    return (
      <section className="panel">
        <h2>{t('households.mine')}</h2>
        {household && (
          <ul className="household-list">
            <li>
              <strong>{household.name}</strong>
              <span>{household.timezone}</span>
              <span>{t('households.memberCount', { count: members.length })}</span>
            </li>
          </ul>
        )}
      </section>
    )
  }

  return (
    <section className="panel">
      <h2>{t('households.title')}</h2>
      <form onSubmit={(event) => { event.preventDefault(); create.mutate() }} className="create-form">
        <label>
          {t('households.name')}
          <input value={name} onChange={(event) => setName(event.target.value)} required />
        </label>
        <button type="submit" disabled={create.isPending}>{t('households.create')}</button>
      </form>
      {create.isError && <p role="alert">{t('error')}</p>}
      {households.length === 0 && <p>{t('households.empty')}</p>}
      <ul className="household-list">
        {households.map((h) => (
          <li key={h.id}>
            <strong>{h.name}</strong>
            <span>{h.timezone}</span>
            {h.id === household?.id
              ? <span>{t('households.current')}</span>
              : <button type="button" className="link" onClick={() => selectHousehold(h.id)}>{t('households.open')}</button>}
          </li>
        ))}
      </ul>
    </section>
  )
}
