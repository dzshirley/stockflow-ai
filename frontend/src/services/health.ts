export interface HealthResponse {
  status: 'UP'
  service: string
  timestamp: string
}

export async function fetchHealth(signal?: AbortSignal): Promise<HealthResponse> {
  const response = await fetch('/api/health', {
    headers: { Accept: 'application/json' },
    signal,
  })

  if (!response.ok) {
    throw new Error(`Health check failed with status ${response.status}`)
  }

  return (await response.json()) as HealthResponse
}
