import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import { useAuth } from '../context/AuthContext'

/** First-run wizard: create the household and its admin member in a single step. */
export function Setup() {
  const { t, i18n } = useTranslation()
  const { login } = useAuth()
  const queryClient = useQueryClient()
  const [householdName, setHouseholdName] = useState('')
  const [memberName, setMemberName] = useState('')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState(false)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setPending(true)
    setError(false)
    try {
      await api('/setup', {
        method: 'POST',
        body: JSON.stringify({
          householdName,
          memberName,
          username,
          password,
          locale: i18n.language,
          timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
        }),
      })
      await queryClient.invalidateQueries({ queryKey: ['setup'] })
      const ok = await login(username, password)
      setError(!ok)
    } catch {
      setError(true)
    }
    setPending(false)
  }

  return (
    <div className="login-screen">
      <form className="panel login-form" onSubmit={(event) => void submit(event)}>
        <div className="brand">Fanel</div>
        <h2>{t('setup.title')}</h2>
        <p>{t('setup.subtitle')}</p>
        <label>
          {t('setup.householdName')}
          <input value={householdName} onChange={(event) => setHouseholdName(event.target.value)} required autoFocus />
        </label>
        <label>
          {t('setup.memberName')}
          <input value={memberName} onChange={(event) => setMemberName(event.target.value)} required />
        </label>
        <label>
          {t('setup.username')}
          <input value={username} onChange={(event) => setUsername(event.target.value)} required />
        </label>
        <label>
          {t('setup.password')}
          <input type="password" value={password} onChange={(event) => setPassword(event.target.value)}
                 required minLength={8} />
        </label>
        {error && <p role="alert">{t('setup.error')}</p>}
        <button type="submit" disabled={pending}>{t('setup.submit')}</button>
      </form>
    </div>
  )
}
