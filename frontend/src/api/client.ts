import { getAuthHeader, setCredentials } from '../auth/authStore'

const baseUrl = '/api/v1'

export type ProblemDetail = { title?: string; detail?: string; status?: number }

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers)
  headers.set('content-type', 'application/json')
  const authHeader = getAuthHeader()
  if (authHeader) headers.set('authorization', authHeader)
  const response = await fetch(`${baseUrl}${path}`, { ...options, headers })
  if (response.status === 401) setCredentials(null)
  if (!response.ok) {
    const problem = await response.json() as ProblemDetail
    throw new Error(problem.detail ?? problem.title ?? `Request failed (${response.status})`)
  }
  const body = await response.text()
  return (body ? JSON.parse(body) : undefined) as T
}
