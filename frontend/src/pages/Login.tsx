import { useEffect, useState, type FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { api } from '../api/client'
import { useAuth } from '../context/AuthContext'

type AuthProvider = { id: string; name: string; loginUrl: string }

export function Login() {
  const { t } = useTranslation()
  const { login } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState(false)
  const [ssoError, setSsoError] = useState(false)

  const providers = useQuery({
    queryKey: ['auth-providers'],
    queryFn: () => api<AuthProvider[]>('/auth/providers'),
    staleTime: Infinity,
  })

  // The backend redirects back to /?sso=failed when the OIDC flow cannot resolve a member.
  useEffect(() => {
    if (new URLSearchParams(window.location.search).has('sso')) {
      setSsoError(true)
      window.history.replaceState(null, '', window.location.pathname)
    }
  }, [])

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setPending(true)
    const ok = await login(username, password)
    setPending(false)
    setError(!ok)
  }

  return (
    <div className="login-screen">
      <form className="panel login-form" onSubmit={(event) => void submit(event)}>
        <div className="brand">Fanel</div>
        <h2>{t('login.title')}</h2>
        <label>
          {t('login.username')}
          <input value={username} onChange={(event) => setUsername(event.target.value)} required autoFocus />
        </label>
        <label>
          {t('login.password')}
          <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} required />
        </label>
        {error && <p role="alert">{t('login.error')}</p>}
        {ssoError && <p role="alert">{t('login.ssoError')}</p>}
        <button type="submit" disabled={pending}>{t('login.submit')}</button>
        {(providers.data?.length ?? 0) > 0 && (
          <>
            <p className="meta login-divider">{t('login.or')}</p>
            {providers.data!.map((provider) => (
              <button key={provider.id} type="button" className="sso-button"
                      onClick={() => window.location.assign(provider.loginUrl)}>
                {t('login.ssoWith', { name: provider.name })}
              </button>
            ))}
          </>
        )}
      </form>
    </div>
  )
}
