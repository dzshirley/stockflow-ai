import { apiRequest } from './api'
import type { PageResponse, Product, ProductInput, ProductStatus, ProductUpdate } from '../types/api'

export interface ProductSearch {
  q?: string
  category?: string
  status?: ProductStatus
  page?: number
  size?: number
  sort?: string
  direction?: 'ASC' | 'DESC'
}

export function listProducts(search: ProductSearch = {}) {
  const params = new URLSearchParams()
  Object.entries(search).forEach(([key, value]) => {
    if (value !== undefined && value !== '') params.set(key, String(value))
  })
  return apiRequest<PageResponse<Product>>(`/api/products?${params.toString()}`)
}

export function getProduct(id: string) {
  return apiRequest<Product>(`/api/products/${id}`)
}

export function createProduct(input: ProductInput) {
  return apiRequest<Product>('/api/products', { method: 'POST', csrf: true, body: JSON.stringify(input) })
}

export function updateProduct(id: string, input: ProductUpdate) {
  return apiRequest<Product>(`/api/products/${id}`, { method: 'PATCH', csrf: true, body: JSON.stringify(input) })
}

export function archiveProduct(id: string) {
  return apiRequest<void>(`/api/products/${id}`, { method: 'DELETE', csrf: true })
}
