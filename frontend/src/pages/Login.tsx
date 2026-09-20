import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../context/AuthContext'

export function Login() {
  const { t } = useTranslation()
  const { login } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState(false)

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
        <button type="submit" disabled={pending}>{t('login.submit')}</button>
      </form>
    </div>
  )
}
