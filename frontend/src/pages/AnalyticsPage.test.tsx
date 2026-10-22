import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AnalyticsPage } from './AnalyticsPage'

const analyticsApi = vi.hoisted(() => ({
  getAnalyticsOverview: vi.fn(),
  getRevenueTrend: vi.fn(),
  getTopProducts: vi.fn(),
  getCategoryPerformance: vi.fn(),
  getInventoryAnalyticsSummary: vi.fn(),
  getInventoryRisk: vi.fn(),
}))
vi.mock('../services/analytics', () => analyticsApi)
vi.mock('recharts', () => {
  const Container = ({ children }: { children?: React.ReactNode }) => <div>{children}</div>
  const Leaf = () => null
  return {
    ResponsiveContainer: Container,
    LineChart: Container,
    BarChart: Container,
    CartesianGrid: Leaf,
    Line: Leaf,
    Bar: Leaf,
    XAxis: Leaf,
    YAxis: Leaf,
    Tooltip: Leaf,
  }
})

const period = { startDate: '2026-09-22', endDateExclusive: '2026-10-22', timeZone: 'America/New_York' }

function defaults() {
  analyticsApi.getAnalyticsOverview.mockResolvedValue({
    period,
    metrics: { revenue: 1250, currency: 'USD', orders: 25, averageOrderValue: 50, unitsSold: 40 },
    comparison: { period, revenue: 1000, revenueChange: 250, revenueGrowthPercent: 25, comparable: true },
  })
  analyticsApi.getRevenueTrend.mockResolvedValue({
    period, interval: 'DAY', currency: 'USD', points: [{ bucketStart: '2026-10-20', revenue: 1250, orders: 25, unitsSold: 40 }],
  })
  analyticsApi.getTopProducts.mockResolvedValue({
    period, currency: 'USD', items: [{ productId: 'p1', sku: 'SKU-1', name: 'Top Product', revenue: 900, unitsSold: 30, orders: 20 }],
  })
  analyticsApi.getCategoryPerformance.mockResolvedValue({
    period, currency: 'USD', items: [{ category: 'Albums', revenue: 1250, unitsSold: 40, orders: 25 }],
  })
  analyticsApi.getInventoryAnalyticsSummary.mockResolvedValue({
    activeProducts: 2, availableUnits: 20, reservedUnits: 3, lowStockProducts: 1, unitsSoldInPeriod: 40, averageDailyUnitsSold: 1.3333, salesPeriod: period,
  })
  analyticsApi.getInventoryRisk.mockResolvedValue({
    salesPeriod: period,
    items: [{ productId: 'p1', sku: 'SKU-1', name: 'Top Product', category: 'Albums', availableQuantity: 2, reservedQuantity: 0, reorderPoint: 3, unitsSold: 30, averageDailyUnitsSold: 1, daysOfCover: 2, riskLevel: 'LOW_STOCK' }],
  })
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}><MemoryRouter><AnalyticsPage /></MemoryRouter></QueryClientProvider>)
}

describe('AnalyticsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    defaults()
  })

  it('renders API-backed KPIs, comparisons, rankings, and inventory alerts', async () => {
    renderPage()

    expect(await screen.findByText('$1,250.00')).toBeInTheDocument()
    expect(screen.getAllByText('+25.0% vs previous period').length).toBeGreaterThan(0)
    expect(screen.getAllByRole('link', { name: /Top Product/ })).toHaveLength(2)
    expect(screen.getByText('low stock')).toBeInTheDocument()
  })

  it('applies a validated custom half-open date range', async () => {
    renderPage()
    await screen.findByText('$1,250.00')
    fireEvent.change(screen.getByLabelText('Reporting period'), { target: { value: 'CUSTOM' } })
    fireEvent.change(screen.getByLabelText('Start date'), { target: { value: '2026-09-01' } })
    fireEvent.change(screen.getByLabelText('End date (exclusive)'), { target: { value: '2026-10-01' } })
    fireEvent.click(screen.getByRole('button', { name: 'Apply' }))

    await waitFor(() => expect(analyticsApi.getAnalyticsOverview).toHaveBeenLastCalledWith({
      preset: 'CUSTOM', startDate: '2026-09-01', endDateExclusive: '2026-10-01', comparePreviousPeriod: true,
    }))
  })

  it('shows explicit empty and error states', async () => {
    analyticsApi.getRevenueTrend.mockResolvedValue({ period, interval: 'DAY', currency: 'USD', points: [] })
    analyticsApi.getTopProducts.mockResolvedValue({ period, currency: 'USD', items: [] })
    renderPage()
    expect(await screen.findByText('No paid sales in this period')).toBeInTheDocument()
    expect(screen.getByText('No product sales yet')).toBeInTheDocument()
  })

  it('shows API failures with a retry action', async () => {
    analyticsApi.getCategoryPerformance.mockRejectedValue(new Error('Analytics query failed'))
    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent('Analytics query failed')
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })

  it('shows a loading skeleton while analytics requests are pending', () => {
    analyticsApi.getAnalyticsOverview.mockReturnValue(new Promise(() => undefined))
    const { container } = renderPage()
    expect(container.querySelector('.skeleton-stack')).toBeInTheDocument()
  })
})
