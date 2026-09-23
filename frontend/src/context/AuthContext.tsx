import { createContext, useContext, useEffect, useSyncExternalStore, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { getAuthHeader, getCredentials, setCredentials, subscribe } from '../auth/authStore'

type AuthContextValue = {
  username?: string
  login: (username: string, password: string) => Promise<boolean>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const credentials = useSyncExternalStore(subscribe, getCredentials)
  const queryClient = useQueryClient()

  // OIDC logins land back on the SPA with a session cookie and no stored credentials:
  // pick the member up from /me so the session behaves like a normal login.
  useEffect(() => {
    if (getCredentials()) return
    void (async () => {
      try {
        const response = await fetch('/api/v1/me', { credentials: 'same-origin' })
        if (!response.ok) return
        const me = await response.json() as {
          kind: string
          member?: { username?: string; name?: string }
        }
        if (me.kind === 'MEMBER' && me.member) {
          setCredentials({ kind: 'session', username: me.member.username ?? me.member.name ?? '' })
          await queryClient.invalidateQueries()
        }
      } catch {
        // Not logged in via a session cookie.
      }
    })()
  }, [queryClient])

  const value: AuthContextValue = {
    username: credentials?.username,
    login: async (username, password) => {
      setCredentials({ kind: 'basic', username, password })
      try {
        const response = await fetch('/api/v1/me', { headers: { authorization: getAuthHeader()! } })
        if (!response.ok) {
          setCredentials(null)
          return false
        }
        await queryClient.invalidateQueries()
        return true
      } catch {
        setCredentials(null)
        return false
      }
    },
    logout: () => {
      if (credentials?.kind === 'session') {
        void fetch('/api/v1/auth/logout', { method: 'POST', credentials: 'same-origin' })
      }
      setCredentials(null)
      localStorage.removeItem('fanel.memberId')
      localStorage.removeItem('fanel.householdId')
    },
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used within AuthProvider')
  return context
}
