import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { getAuthHeader } from '../auth/authStore'

const TOPIC_KEYS: Record<string, string[]> = {
  menu: ['menu'],
  shopping: ['shopping'],
  calendar: ['calendar'],
  chores: ['chores'],
}

/** Subscribes to the SSE stream and invalidates the affected queries per topic. */
export function useHouseholdEvents(householdId?: string) {
  const queryClient = useQueryClient()
  useEffect(() => {
    if (!householdId) return
    // EventSource cannot send headers; use fetch-based streaming. Session-cookie logins
    // (OIDC) have no auth header and rely on the same-origin cookie instead.
    const authHeader = getAuthHeader()
    const controller = new AbortController()
    void (async () => {
      try {
        const headers: Record<string, string> = { accept: 'text/event-stream' }
        if (authHeader) headers.authorization = authHeader
        const response = await fetch(`/api/v1/events?household=${householdId}`, {
          headers,
          credentials: 'same-origin',
          signal: controller.signal,
        })
        if (!response.ok || !response.body) return
        const reader = response.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''
        for (;;) {
          const { done, value } = await reader.read()
          if (done) break
          buffer += decoder.decode(value, { stream: true })
          const events = buffer.split('\n\n')
          buffer = events.pop() ?? ''
          for (const raw of events) {
            const name = raw.split('\n').find((l) => l.startsWith('event:'))?.slice(6).trim()
            const keys = name ? TOPIC_KEYS[name] : undefined
            if (keys) for (const key of keys) void queryClient.invalidateQueries({ queryKey: [key] })
          }
        }
      } catch {
        // aborted or network error; ignore
      }
    })()
    return () => controller.abort()
  }, [householdId, queryClient])
}
