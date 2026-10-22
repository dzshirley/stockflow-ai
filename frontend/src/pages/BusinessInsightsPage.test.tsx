import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { BusinessReportDetail } from '../types/api'
import { BusinessInsightsPage } from './BusinessInsightsPage'

const api = vi.hoisted(() => ({
  listBusinessReports: vi.fn(),
  getBusinessReport: vi.fn(),
  createBusinessReport: vi.fn(),
}))
vi.mock('../services/businessAnalysis', () => api)
vi.mock('recharts', () => {
  const Container = ({ children }: { children?: React.ReactNode }) => <div>{children}</div>
  const Leaf = () => null
  return { ResponsiveContainer: Container, BarChart: Container, Bar: Container, Cell: Leaf, CartesianGrid: Leaf, XAxis: Leaf, YAxis: Leaf, Tooltip: Leaf }
})

const reportDetail: BusinessReportDetail = {
  metadata: {
    id: 'report-1', reportType: 'REVENUE_INVESTIGATION', title: 'Revenue investigation', status: 'COMPLETED',
    periodStart: '2026-09-01', periodEndExclusive: '2026-10-01', dataAsOf: '2026-10-22T12:00:00Z',
    createdAt: '2026-10-22T12:00:00Z', completedAt: '2026-10-22T12:00:01Z', failureCode: null,
  },
  report: {
    reportId: 'report-1', schemaVersion: 1, reportVersion: 1, reportType: 'REVENUE_INVESTIGATION',
    title: 'Revenue investigation', summary: 'Paid-order revenue decreased by 20.0000 USD.',
    storeId: 'store-1', createdByUserId: 'user-1', status: 'COMPLETED', periodStart: '2026-09-01',
    periodEndExclusive: '2026-10-01', comparisonPeriodStart: '2026-08-01', comparisonPeriodEndExclusive: '2026-09-01',
    timeZone: 'America/New_York', currency: 'USD', dataAsOf: '2026-10-22T12:00:01Z',
    metrics: [{ key: 'revenue', label: 'Revenue', value: 80, comparisonValue: 100, absoluteChange: -20, changePercent: -20, unit: 'USD', evidenceIds: ['e1', 'e2'] }],
    evidence: [
      { id: 'e1', source: 'ORDER_SNAPSHOT', toolExecutionId: null, metricKey: 'revenue.previous', value: 100, unit: 'USD', periodStart: '2026-08-01', periodEndExclusive: '2026-09-01', timeZone: 'America/New_York', filters: { orderStatus: 'PAID' }, observedAt: '2026-10-22T12:00:01Z' },
      { id: 'e2', source: 'ORDER_SNAPSHOT', toolExecutionId: null, metricKey: 'revenue.current', value: 80, unit: 'USD', periodStart: '2026-09-01', periodEndExclusive: '2026-10-01', timeZone: 'America/New_York', filters: { orderStatus: 'PAID' }, observedAt: '2026-10-22T12:00:01Z' },
    ],
    findings: [{ id: 'f1', kind: 'VERIFIED_OBSERVATION', title: 'Revenue changed', statement: 'Revenue decreased.', evidenceIds: ['e1', 'e2'], calculationDefinition: null }],
    hypotheses: [{ id: 'h1', statement: 'Inventory may be associated.', rationale: 'History is incomplete.', supportingEvidenceIds: ['e2'], counterEvidenceIds: [], status: 'UNTESTED', validationPlan: 'Capture inventory snapshots.' }],
    recommendations: [{ id: 'r1', title: 'Review drivers', description: 'Inspect declining products.', rationale: 'Contributions are verified.', findingIds: ['f1'], hypothesisIds: [], evidenceIds: ['e2'], priority: 'HIGH', expectedImpact: null, expectedImpactUnit: null, assumptions: [], risks: ['One-off orders'], nextSteps: ['Review contribution chart'], approvalRequired: true }],
    scenarios: [{ id: 's1', type: 'UNIT_SALES_CHANGE', changePercent: 10, statement: 'This is not a forecast.', baselineValue: 80, modeledValue: 88, unit: 'USD', baselineGrossProfit: 30, modeledGrossProfit: 33, grossProfitChange: 3, evidenceIds: ['e2'], assumptions: ['Mix stays constant.'], limitations: [] }],
    charts: [{ id: 'product-contributions', type: 'WATERFALL', title: 'Product contribution', categoryLabel: 'Product', valueLabel: 'Contribution', unit: 'USD', points: [{ key: 'p1', label: 'Album', value: -20 }] }],
    limitations: [{ code: 'STOCKOUT_HISTORY', description: 'Historical stockout duration is unavailable.', affectedFindingIds: ['f1'] }],
    modelMetadata: { provider: 'mock', model: 'deterministic-v1', promptVersion: 'business-analysis-v1', inputTokens: null, outputTokens: null, totalTokens: null, pricingVersion: null, estimatedCostUsd: null, latencyMs: 1 },
    createdAt: '2026-10-22T12:00:00Z', completedAt: '2026-10-22T12:00:01Z',
  },
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(<QueryClientProvider client={client}><MemoryRouter><BusinessInsightsPage /></MemoryRouter></QueryClientProvider>)
}

describe('BusinessInsightsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.listBusinessReports.mockResolvedValue([reportDetail.metadata])
    api.getBusinessReport.mockResolvedValue(reportDetail)
    api.createBusinessReport.mockResolvedValue(reportDetail)
  })

  it('loads a saved report and clearly separates evidence layers', async () => {
    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: /Revenue investigation/ }))

    expect((await screen.findAllByText('$80.00')).length).toBeGreaterThan(0)
    expect(screen.getByText('Verified findings')).toBeInTheDocument()
    expect(screen.getByText('Hypotheses')).toBeInTheDocument()
    expect(screen.getByText('Recommendations')).toBeInTheDocument()
    expect(screen.getByText('What-if scenarios')).toBeInTheDocument()
    expect(screen.getByText('Historical stockout duration is unavailable.')).toBeInTheDocument()
    expect(screen.getByText('Estimated financial impact: unavailable')).toBeInTheDocument()
  })

  it('creates a report from a suggested question and refreshes history', async () => {
    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: 'Why did revenue change last month?' }))
    fireEvent.click(screen.getByRole('button', { name: 'Generate report' }))

    await waitFor(() => expect(api.createBusinessReport).toHaveBeenCalledWith({
      question: 'Why did revenue change last month?', analysisType: 'AUTO', preset: 'LAST_30_DAYS',
    }, expect.any(String)))
    expect(await screen.findByText('Paid-order revenue decreased by 20.0000 USD.')).toBeInTheDocument()
  })

  it('shows a failed report as failed rather than displaying fabricated content', async () => {
    api.listBusinessReports.mockResolvedValue([{ ...reportDetail.metadata, status: 'FAILED', failureCode: 'AI_PROVIDER_UNAVAILABLE' }])
    api.getBusinessReport.mockResolvedValue({ metadata: { ...reportDetail.metadata, status: 'FAILED', failureCode: 'AI_PROVIDER_UNAVAILABLE' }, report: null })
    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: /Revenue investigation/ }))

    expect(await screen.findByRole('alert')).toHaveTextContent('No completed report was fabricated')
    expect(screen.queryByText('Verified findings')).not.toBeInTheDocument()
  })

  it('validates custom half-open periods before calling the API', async () => {
    renderPage()
    fireEvent.change(screen.getByLabelText('Business question'), { target: { value: 'Why did revenue change?' } })
    fireEvent.change(screen.getByLabelText('Reporting period'), { target: { value: 'CUSTOM' } })
    fireEvent.change(screen.getByLabelText('Start date'), { target: { value: '2026-10-01' } })
    fireEvent.change(screen.getByLabelText('End date (exclusive)'), { target: { value: '2026-09-01' } })
    fireEvent.click(screen.getByRole('button', { name: 'Generate report' }))

    expect(screen.getByRole('alert')).toHaveTextContent('valid custom start')
    expect(api.createBusinessReport).not.toHaveBeenCalled()
  })
})
