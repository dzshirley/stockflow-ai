import { apiRequest } from './api'
import type { Order, PageResponse } from '../types/api'

export function listOrders(page = 0, size = 20) {
  return apiRequest<PageResponse<Order>>(`/api/orders?page=${page}&size=${size}`)
}

export function getOrder(id: string) {
  return apiRequest<Order>(`/api/orders/${id}`)
}

export function createOrder(items: Array<{ productId: string; quantity: number }>, idempotencyKey: string) {
  return apiRequest<Order>('/api/orders', {
    method: 'POST',
    csrf: true,
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify({ items }),
  })
}

export function cancelOrder(id: string) {
  return apiRequest<Order>(`/api/orders/${id}/cancel`, { method: 'POST', csrf: true })
}

export function recordPayment(id: string, outcome: 'APPROVED' | 'DECLINED') {
  return apiRequest<Order>(`/api/orders/${id}/payment`, {
    method: 'POST',
    csrf: true,
    body: JSON.stringify({ outcome }),
  })
}
