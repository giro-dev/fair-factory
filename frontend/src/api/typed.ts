import createClient, { type Middleware } from 'openapi-fetch'
import type { paths } from './schema'
import type { ProblemDetail } from './client'
import { getAuthHeader, setCredentials } from '../auth/authStore'

const auth: Middleware = {
  onRequest({ request }) {
    const header = getAuthHeader()
    if (header) request.headers.set('authorization', header)
    return request
  },
  onResponse({ response }) {
    if (response.status === 401) setCredentials(null)
    return response
  },
}

// Spec paths already include the /api/v1 prefix.
export const client = createClient<paths>({ baseUrl: '' })
client.use(auth)

type Result<T> = { data: T; error?: never; response: Response } | { data?: never; error: unknown; response: Response }

export async function unwrap<T>(promise: Promise<Result<T>>): Promise<T> {
  const { data, error, response } = await promise
  if (response.ok) return data as T
  const problem = (error ?? {}) as ProblemDetail
  throw new Error(problem.detail ?? problem.title ?? `Request failed (${response.status})`)
}
