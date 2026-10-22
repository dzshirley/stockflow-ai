import { apiRequest } from './api'
import type { InventoryAdjustment, InventoryBalance } from '../types/api'

export function listInventory(lowStockOnly = false) {
  return apiRequest<{ items: InventoryBalance[] }>(`/api/inventory?lowStockOnly=${lowStockOnly}`)
}

export function getInventory(productId: string) {
  return apiRequest<InventoryBalance>(`/api/inventory/${productId}`)
}

export function adjustInventory(
  productId: string,
  input: { availableDelta: number; reorderPoint?: number; reason?: string },
) {
  return apiRequest<InventoryBalance>(`/api/inventory/${productId}/adjustments`, {
    method: 'POST',
    csrf: true,
    body: JSON.stringify(input),
  })
}

export function getInventoryAudit(productId: string, page = 0, size = 20) {
  return apiRequest<{ items: InventoryAdjustment[]; page: number; size: number; totalElements: number }>(
    `/api/inventory/${productId}/adjustments?page=${page}&size=${size}`,
  )
}
