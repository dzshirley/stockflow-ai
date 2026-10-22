import { beforeEach, describe, expect, it, vi } from 'vitest'
import { listAiConversations, queryAi } from './ai'

describe('AI service', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('sends a CSRF-protected, conversation-aware query', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'token' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ conversationId: 'c1' }), { status: 200 }))

    await queryAi('Show revenue', 'c1')

    const [, request] = fetchMock.mock.calls[1]
    expect(request?.method).toBe('POST')
    expect(request?.body).toBe(JSON.stringify({ question: 'Show revenue', conversationId: 'c1' }))
    expect(new Headers(request?.headers).get('X-CSRF-TOKEN')).toBe('token')
  })

  it('unwraps the conversation list envelope', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(new Response(JSON.stringify({ items: [{ id: 'c1' }] }), { status: 200 }))
    await expect(listAiConversations()).resolves.toEqual([{ id: 'c1' }])
  })
})
