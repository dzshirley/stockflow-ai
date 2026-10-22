import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { PageHeader } from '../components/AppShell'
import { ErrorPanel, PageSkeleton } from '../components/Feedback'
import {
  AnalyticsKpis,
  CategoryPerformancePanel,
  InventoryHealthPanel,
  RevenueTrendPanel,
  TopProductsPanel,
} from '../features/analytics/AnalyticsComponents'
import {
  getAnalyticsOverview,
  getCategoryPerformance,
  getInventoryAnalyticsSummary,
  getInventoryRisk,
  getRevenueTrend,
  getTopProducts,
} from '../services/analytics'
import type { AnalyticsFilters, AnalyticsPreset } from '../types/api'

const defaultFilters: AnalyticsFilters = { preset: 'LAST_30_DAYS', comparePreviousPeriod: true }

export function AnalyticsPage() {
  const [draft, setDraft] = useState<AnalyticsFilters>(defaultFilters)
  const [filters, setFilters] = useState<AnalyticsFilters>(defaultFilters)
  const [validationError, setValidationError] = useState('')
  const overview = useQuery({ queryKey: ['analytics', 'overview', filters], queryFn: () => getAnalyticsOverview(filters) })
  const trend = useQuery({ queryKey: ['analytics', 'trend', filters], queryFn: () => getRevenueTrend(filters) })
  const products = useQuery({ queryKey: ['analytics', 'products', filters], queryFn: () => getTopProducts(filters) })
  const categories = useQuery({ queryKey: ['analytics', 'categories', filters], queryFn: () => getCategoryPerformance(filters) })
  const inventory = useQuery({ queryKey: ['analytics', 'inventory-summary', filters], queryFn: () => getInventoryAnalyticsSummary(filters) })
  const risk = useQuery({ queryKey: ['analytics', 'inventory-risk', filters], queryFn: () => getInventoryRisk(filters) })
  const queries = [overview, trend, products, categories, inventory, risk]
  const loading = queries.some((query) => query.isPending)
  const error = queries.find((query) => query.error)?.error

  function applyFilters(event: React.FormEvent) {
    event.preventDefault()
    if (draft.preset === 'CUSTOM' && (!draft.startDate || !draft.endDateExclusive)) {
      setValidationError('Choose both a start date and an exclusive end date.')
      return
    }
    if (draft.preset === 'CUSTOM' && draft.startDate! >= draft.endDateExclusive!) {
      setValidationError('The start date must be before the exclusive end date.')
      return
    }
    setValidationError('')
    setFilters({ ...draft })
  }

  const retry = () => queries.forEach((query) => query.refetch())

  return <>
    <PageHeader eyebrow="Business intelligence" title="Analytics" description="Deterministic sales and inventory metrics calculated from paid orders and current stock." />
    <form className="analytics-toolbar" onSubmit={applyFilters}>
      <label>Reporting period
        <select value={draft.preset} onChange={(event) => setDraft((current) => ({ ...current, preset: event.target.value as AnalyticsPreset }))}>
          <option value="LAST_7_DAYS">Last 7 days</option>
          <option value="LAST_30_DAYS">Last 30 days</option>
          <option value="LAST_90_DAYS">Last 90 days</option>
          <option value="THIS_MONTH">This month</option>
          <option value="LAST_MONTH">Last month</option>
          <option value="CUSTOM">Custom range</option>
        </select>
      </label>
      {draft.preset === 'CUSTOM' && <>
        <label>Start date<input type="date" value={draft.startDate ?? ''} onChange={(event) => setDraft((current) => ({ ...current, startDate: event.target.value }))} /></label>
        <label>End date (exclusive)<input type="date" value={draft.endDateExclusive ?? ''} onChange={(event) => setDraft((current) => ({ ...current, endDateExclusive: event.target.value }))} /></label>
      </>}
      <label className="toggle analytics-compare"><input type="checkbox" checked={draft.comparePreviousPeriod ?? true} onChange={(event) => setDraft((current) => ({ ...current, comparePreviousPeriod: event.target.checked }))} /> Compare previous period</label>
      <button className="button button--primary" type="submit">Apply</button>
    </form>
    {validationError && <div className="notice notice--error" role="alert">{validationError}</div>}
    {loading ? <PageSkeleton rows={6} /> : error ? <ErrorPanel error={error} onRetry={retry} /> : overview.data && trend.data && products.data && categories.data && inventory.data && risk.data ? <>
      <div className="analytics-period-note">{overview.data.period.startDate} to {overview.data.period.endDateExclusive} (exclusive) · {overview.data.period.timeZone}</div>
      <AnalyticsKpis overview={overview.data} />
      <div className="analytics-grid analytics-grid--charts">
        <RevenueTrendPanel data={trend.data} />
        <CategoryPerformancePanel data={categories.data} />
      </div>
      <div className="analytics-grid">
        <TopProductsPanel data={products.data} />
        <InventoryHealthPanel summary={inventory.data} risk={risk.data} />
      </div>
    </> : null}
  </>
}
