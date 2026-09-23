const STORAGE_KEY = 'fanel.auth'

export type Credentials =
  | { kind: 'basic'; username: string; password: string }
  | { kind: 'session'; username: string }

function readStored(): Credentials | null {
  const raw = sessionStorage.getItem(STORAGE_KEY)
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw) as Partial<Credentials> & { password?: string }
    // Legacy entries have no kind: they are username/password credentials.
    if (parsed && parsed.username && !('kind' in parsed)) {
      return { kind: 'basic', username: parsed.username, password: parsed.password ?? '' }
    }
    return parsed as Credentials
  } catch {
    return null
  }
}

let credentials: Credentials | null = readStored()
const listeners = new Set<() => void>()

export function getCredentials(): Credentials | null {
  return credentials
}

export function getAuthHeader(): string | undefined {
  return credentials?.kind === 'basic'
    ? `Basic ${btoa(`${credentials.username}:${credentials.password}`)}`
    : undefined
}

export function setCredentials(next: Credentials | null) {
  credentials = next
  if (next) sessionStorage.setItem(STORAGE_KEY, JSON.stringify(next))
  else sessionStorage.removeItem(STORAGE_KEY)
  listeners.forEach((listener) => listener())
}

export function subscribe(listener: () => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}
