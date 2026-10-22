import type {
  AnalyticsFilters,
  AnalyticsOverview,
  CategoryPerformanceResponse,
  InventoryAnalyticsSummary,
  InventoryRiskResponse,
  ProductPerformanceResponse,
  RevenueTrend,
  TrendInterval,
} from '../types/api'
import { apiRequest } from './api'

function analyticsParams(filters: AnalyticsFilters) {
  const params = new URLSearchParams({ preset: filters.preset })
  if (filters.preset === 'CUSTOM') {
    if (filters.startDate) params.set('startDate', filters.startDate)
    if (filters.endDateExclusive) params.set('endDateExclusive', filters.endDateExclusive)
  }
  return params
}

function path(endpoint: string, filters: AnalyticsFilters, additions?: Record<string, string>) {
  const params = analyticsParams(filters)
  Object.entries(additions ?? {}).forEach(([key, value]) => params.set(key, value))
  return `/api/analytics/${endpoint}?${params.toString()}`
}

export function getAnalyticsOverview(filters: AnalyticsFilters) {
  return apiRequest<AnalyticsOverview>(path('overview', filters, {
    comparePreviousPeriod: String(filters.comparePreviousPeriod ?? true),
  }))
}

export function getRevenueTrend(filters: AnalyticsFilters, interval?: TrendInterval) {
  return apiRequest<RevenueTrend>(path('revenue/trend', filters, interval ? { interval } : undefined))
}

export function getTopProducts(filters: AnalyticsFilters, limit = 10) {
  return apiRequest<ProductPerformanceResponse>(path('products/top', filters, { limit: String(limit) }))
}

export function getCategoryPerformance(filters: AnalyticsFilters) {
  return apiRequest<CategoryPerformanceResponse>(path('categories/performance', filters))
}

export function getInventoryAnalyticsSummary(filters: AnalyticsFilters) {
  return apiRequest<InventoryAnalyticsSummary>(path('inventory/summary', filters))
}

export function getInventoryRisk(filters: AnalyticsFilters, limit = 20) {
  return apiRequest<InventoryRiskResponse>(path('inventory/risk', filters, { limit: String(limit) }))
}
