import { beforeEach, describe, expect, it, vi } from 'vitest'
import { executeRestockProposal, generateRestockProposals } from './restockProposals'

describe('restock proposal service', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('generates through a CSRF-protected idempotent command', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ items: [] }), { status: 200 }))
    const input = { question: 'Prepare restock', sourceReportId: 'report-1', recommendationId: 'rec-1' }
    await generateRestockProposals(input, 'generation-key')
    const [, request] = fetchMock.mock.calls[1]
    const headers = new Headers(request?.headers)
    expect(request?.method).toBe('POST')
    expect(headers.get('Idempotency-Key')).toBe('generation-key')
    expect(headers.get('X-CSRF-TOKEN')).toBe('csrf')
    expect(request?.body).toBe(JSON.stringify(input))
  })

  it('sends explicit physical-receipt confirmation and a unique command ID', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'proposal-1', status: 'EXECUTED' }), { status: 200 }))
    await executeRestockProposal('proposal-1', 3, '11111111-1111-1111-1111-111111111111')
    const [, request] = fetchMock.mock.calls[1]
    const headers = new Headers(request?.headers)
    expect(headers.get('Idempotency-Key')).toBe('11111111-1111-1111-1111-111111111111')
    expect(request?.body).toBe(JSON.stringify({ expectedVersion: 3, received: true }))
  })
})
