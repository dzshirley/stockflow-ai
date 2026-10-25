import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { RestockProposal } from '../types/api'
import { OperationsPage } from './OperationsPage'

const restock = vi.hoisted(() => ({
  listRestockProposals: vi.fn(), getRestockProposalHistory: vi.fn(), generateRestockProposals: vi.fn(),
  approveRestockProposal: vi.fn(), rejectRestockProposal: vi.fn(), cancelRestockProposal: vi.fn(), executeRestockProposal: vi.fn(),
}))
const reports = vi.hoisted(() => ({ listBusinessReports: vi.fn(), getBusinessReport: vi.fn() }))
vi.mock('../services/restockProposals', () => restock)
vi.mock('../services/businessAnalysis', () => reports)
vi.mock('../features/auth/AuthProvider', () => ({ useAuth: () => ({ user: { userId: 'admin-1', role: 'ADMIN' } }) }))

const proposal: RestockProposal = {
  id: 'proposal-1', productId: 'product-1', sku: 'ALBUM', productName: 'Album', status: 'APPROVED',
  proposedQuantity: 12, rationale: 'Restore coverage.', assumptions: ['Velocity is descriptive'],
  sourceReportId: 'report-1', sourceReportVersion: 1, recommendationId: 'rec-1', evidenceId: 'evidence-1',
  snapshotAvailable: 0, snapshotReserved: 0, snapshotReorderPoint: 5, snapshotInventoryVersion: 0,
  snapshotInventoryUpdatedAt: '2026-10-22T12:00:00Z', sourceDataAsOf: '2026-10-22T12:00:00Z',
  createdByUserId: 'admin-1', approvedByUserId: 'admin-1', approvedAt: '2026-10-22T12:01:00Z',
  executionCommandId: null, inventoryAdjustmentId: null, executedAt: null, failureCode: null, staleReason: null,
  modelProvider: 'mock', modelName: 'deterministic-v1', version: 1,
  createdAt: '2026-10-22T12:00:00Z', updatedAt: '2026-10-22T12:01:00Z',
}
const report = {
  metadata: { id: 'report-1', status: 'COMPLETED', title: 'Inventory risk', createdAt: '2026-10-22T12:00:00Z' },
  report: {
    recommendations: [{ id: 'rec-1', title: 'Review inventory risk', approvalRequired: true, evidenceIds: ['evidence-1'] }],
    evidence: [{ id: 'evidence-1', source: 'INVENTORY_SNAPSHOT', metricKey: 'inventory.available.product-1', value: 0,
      filters: { riskLevel: 'OUT_OF_STOCK' }, periodStart: '2026-09-22', periodEndExclusive: '2026-10-22' }],
  },
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(<QueryClientProvider client={client}><MemoryRouter><OperationsPage /></MemoryRouter></QueryClientProvider>)
}

describe('OperationsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    restock.listRestockProposals.mockResolvedValue([proposal])
    restock.getRestockProposalHistory.mockResolvedValue({ events: [], attempts: [] })
    restock.executeRestockProposal.mockResolvedValue({ ...proposal, status: 'EXECUTED', inventoryAdjustmentId: 'adjustment-1' })
    reports.listBusinessReports.mockResolvedValue([report.metadata])
    reports.getBusinessReport.mockResolvedValue(report)
  })

  it('requires explicit receipt confirmation before executing', async () => {
    renderPage()
    const execute = await screen.findByRole('button', { name: 'Record received inventory' })
    expect(execute).toBeDisabled()
    fireEvent.click(screen.getByLabelText(/I confirm 12 units were physically received/))
    expect(execute).toBeEnabled()
    fireEvent.click(execute)
    await waitFor(() => expect(restock.executeRestockProposal).toHaveBeenCalledWith('proposal-1', 1, expect.any(String)))
  })

  it('shows evidence, assumptions, and approval semantics', async () => {
    renderPage()
    expect(await screen.findByText('Receive 12 units of Album')).toBeInTheDocument()
    expect(await screen.findByText('OUT OF STOCK')).toBeInTheDocument()
    expect(screen.getByText('Velocity is descriptive')).toBeInTheDocument()
    expect(screen.getByText(/Approval is not a purchase order/)).toBeInTheDocument()
  })
})
