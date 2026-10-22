import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { PageHeader } from '../components/AppShell'
import { ErrorPanel, PageSkeleton } from '../components/Feedback'
import {
  AnalyticsKpis,
  CategoryPerformancePanel,
  InventoryHealthPanel,
  RevenueTrendPanel,
  TopProductsPanel,
} from '../features/analytics/AnalyticsComponents'
import { useAuth } from '../features/auth/AuthProvider'
import {
  getAnalyticsOverview,
  getCategoryPerformance,
  getInventoryAnalyticsSummary,
  getInventoryRisk,
  getRevenueTrend,
  getTopProducts,
} from '../services/analytics'
import type { AnalyticsFilters } from '../types/api'

const filters: AnalyticsFilters = { preset: 'LAST_30_DAYS', comparePreviousPeriod: true }

export function DashboardPage() {
  const { user } = useAuth()
  const overview = useQuery({ queryKey: ['analytics', 'overview', filters], queryFn: () => getAnalyticsOverview(filters) })
  const trend = useQuery({ queryKey: ['analytics', 'trend', filters], queryFn: () => getRevenueTrend(filters) })
  const products = useQuery({ queryKey: ['analytics', 'products', filters], queryFn: () => getTopProducts(filters, 5) })
  const categories = useQuery({ queryKey: ['analytics', 'categories', filters], queryFn: () => getCategoryPerformance(filters) })
  const inventory = useQuery({ queryKey: ['analytics', 'inventory-summary', filters], queryFn: () => getInventoryAnalyticsSummary(filters) })
  const risk = useQuery({ queryKey: ['analytics', 'inventory-risk', filters], queryFn: () => getInventoryRisk(filters, 10) })
  const queries = [overview, trend, products, categories, inventory, risk]
  const loading = queries.some((query) => query.isPending)
  const error = queries.find((query) => query.error)?.error

  return <>
    <PageHeader eyebrow="Operations overview" title={`Good to see you, ${user?.displayName.split(' ')[0] ?? 'there'}.`} description="A live 30-day view of revenue, products, and inventory health." action={<div className="page-actions page-actions--group"><Link className="button button--secondary" to="/analytics">Explore analytics</Link><Link className="button button--primary" to="/orders/new">Create order</Link></div>} />
    {loading ? <PageSkeleton rows={6} /> : error ? <ErrorPanel error={error} onRetry={() => queries.forEach((query) => query.refetch())} /> : overview.data && trend.data && products.data && categories.data && inventory.data && risk.data ? <>
      <AnalyticsKpis overview={overview.data} />
      <div className="analytics-grid analytics-grid--charts">
        <RevenueTrendPanel data={trend.data} compact />
        <CategoryPerformancePanel data={categories.data} />
      </div>
      <div className="analytics-grid">
        <TopProductsPanel data={products.data} compact />
        <InventoryHealthPanel summary={inventory.data} risk={risk.data} compact />
      </div>
    </> : null}
  </>
}
