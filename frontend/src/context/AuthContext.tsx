import { createContext, useContext, useSyncExternalStore, type ReactNode } from 'react'
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

  const value: AuthContextValue = {
    username: credentials?.username,
    login: async (username, password) => {
      setCredentials({ username, password })
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
