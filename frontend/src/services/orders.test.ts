import { describe, expect, it, vi } from 'vitest'
import { createOrder } from './orders'

describe('order API', () => {
  it('sends CSRF and idempotency headers when creating an order', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-XSRF-TOKEN', token: 'csrf-token' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'order-1' }), { status: 201, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)
    await createOrder([{ productId: 'product-1', quantity: 2 }], 'web-request-123')
    const [, request] = fetchMock.mock.calls[1] as [string, RequestInit]
    const headers = new Headers(request.headers)
    expect(headers.get('X-XSRF-TOKEN')).toBe('csrf-token')
    expect(headers.get('Idempotency-Key')).toBe('web-request-123')
    expect(request.credentials).toBe('include')
  })

  it('preserves structured concurrency conflicts for the UI', async () => {
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-XSRF-TOKEN', token: 'csrf-token' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: 'INSUFFICIENT_STOCK', detail: 'Inventory changed while the order was being created.' }), { status: 409, headers: { 'Content-Type': 'application/problem+json' } })))
    await expect(createOrder([{ productId: 'product-1', quantity: 99 }], 'web-request-456')).rejects.toMatchObject({ status: 409, code: 'INSUFFICIENT_STOCK', message: 'Inventory changed while the order was being created.' })
  })
})
