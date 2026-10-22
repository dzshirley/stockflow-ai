import { describe, expect, it, vi } from 'vitest'
import { fetchHealth } from './health'

describe('fetchHealth', () => {
  it('returns the typed API payload', async () => {
    const payload = { status: 'UP', service: 'stockflow-api', timestamp: '2026-10-20T12:00:00Z' }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(payload), { status: 200 })))

    await expect(fetchHealth()).resolves.toEqual(payload)
  })

  it('rejects non-successful responses', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 503 })))

    await expect(fetchHealth()).rejects.toThrow('Health check failed with status 503')
  })
})
