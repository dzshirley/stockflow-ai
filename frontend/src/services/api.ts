import type { ProblemDetails } from '../types/api'

const configuredBase = import.meta.env.VITE_API_BASE_URL?.trim() ?? ''
export const API_BASE_URL = configuredBase.endsWith('/') ? configuredBase.slice(0, -1) : configuredBase
export const UNAUTHORIZED_EVENT = 'stockflow:unauthorized'

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly violations: ProblemDetails['violations'] = [],
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

interface RequestOptions extends RequestInit {
  csrf?: boolean
  notifyUnauthorized?: boolean
}

async function parseProblem(response: Response): Promise<ApiError> {
  let problem: ProblemDetails = {}
  try {
    problem = (await response.json()) as ProblemDetails
  } catch {
    // Non-JSON upstream errors still become a consistent client error.
  }
  return new ApiError(
    response.status,
    problem.code ?? `HTTP_${response.status}`,
    problem.detail ?? problem.title ?? `Request failed with status ${response.status}`,
    problem.violations,
  )
}

async function csrfHeaders(): Promise<Record<string, string>> {
  const response = await fetch(`${API_BASE_URL}/api/auth/csrf`, {
    credentials: 'include',
    headers: { Accept: 'application/json' },
  })
  if (!response.ok) throw await parseProblem(response)
  const token = (await response.json()) as { headerName: string; token: string }
  return { [token.headerName]: token.token }
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { csrf = false, notifyUnauthorized = true, headers, ...requestInit } = options
  const requestHeaders = new Headers(headers)
  requestHeaders.set('Accept', 'application/json')
  if (requestInit.body && !requestHeaders.has('Content-Type')) requestHeaders.set('Content-Type', 'application/json')
  if (csrf) {
    const tokenHeaders = await csrfHeaders()
    Object.entries(tokenHeaders).forEach(([name, value]) => requestHeaders.set(name, value))
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...requestInit,
    credentials: 'include',
    headers: requestHeaders,
  })

  if (!response.ok) {
    const error = await parseProblem(response)
    if (response.status === 401 && notifyUnauthorized) window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
    throw error
  }
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.violations?.length) return error.violations.map((item) => `${item.field}: ${item.message}`).join('. ')
    return error.message
  }
  return error instanceof Error ? error.message : 'Something went wrong. Please try again.'
}
