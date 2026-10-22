import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createBusinessReport, listBusinessReports } from './businessAnalysis'

describe('business analysis service', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('creates a CSRF-protected idempotent report request', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf-token' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ metadata: { id: 'r1' }, report: null }), { status: 200 }))

    await createBusinessReport({ question: 'Why did revenue decline?', preset: 'LAST_30_DAYS' }, 'report-key')

    const [, request] = fetchMock.mock.calls[1]
    const headers = new Headers(request?.headers)
    expect(request?.method).toBe('POST')
    expect(headers.get('Idempotency-Key')).toBe('report-key')
    expect(headers.get('X-CSRF-TOKEN')).toBe('csrf-token')
    expect(request?.body).toBe(JSON.stringify({ question: 'Why did revenue decline?', preset: 'LAST_30_DAYS' }))
  })

  it('serializes filters and unwraps the report list', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(new Response(JSON.stringify({
      items: [{ id: 'r1', status: 'COMPLETED' }],
    }), { status: 200 }))

    await expect(listBusinessReports({ status: 'COMPLETED', type: 'REVENUE_INVESTIGATION', limit: 10 }))
      .resolves.toEqual([{ id: 'r1', status: 'COMPLETED' }])
    expect(String(fetchMock.mock.calls[0][0])).toContain('status=COMPLETED')
    expect(String(fetchMock.mock.calls[0][0])).toContain('type=REVENUE_INVESTIGATION')
  })
})
