import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AiCopilotPage } from './AiCopilotPage'

const aiApi = vi.hoisted(() => ({
  listAiConversations: vi.fn(),
  getAiConversation: vi.fn(),
  queryAi: vi.fn(),
}))
vi.mock('../services/ai', () => aiApi)
vi.mock('recharts', () => {
  const Container = ({ children }: { children?: React.ReactNode }) => <div>{children}</div>
  const Leaf = () => null
  return {
    ResponsiveContainer: Container, LineChart: Container, BarChart: Container,
    CartesianGrid: Leaf, Line: Leaf, Bar: Leaf, XAxis: Leaf, YAxis: Leaf, Tooltip: Leaf, Legend: Leaf,
  }
})

const response = {
  conversationId: 'conversation-1', messageId: 'message-2', responseType: 'ANALYTICS' as const,
  summary: 'Verified revenue was 1250.00 USD across 25 completed orders.',
  metrics: [{ key: 'revenue', label: 'Revenue', value: 1250, unit: 'USD', comparisonValue: 1000, changePercent: 25, comparable: true }],
  charts: [{ id: 'trend', type: 'LINE' as const, title: 'Revenue trend', xKey: 'date', series: [{ key: 'revenue', label: 'Revenue', unit: 'USD' }], data: [{ date: '2026-10-20', revenue: 1250 }] }],
  tables: [{ id: 'products', title: 'Product performance', columns: ['product', 'revenue'], rows: [{ product: 'Top Product', revenue: 900 }] }],
  evidence: [{ toolExecutionId: 'tool-1', toolName: 'get_revenue_summary', description: 'Verified revenue overview query', period: { startDate: '2026-09-21', endDateExclusive: '2026-10-21', timeZone: 'America/New_York' } }],
  assumptions: ['Currency values use the store currency.'], limitations: ['Completed orders only.'],
  appliedFilters: [{ name: 'startDate', value: '2026-09-21' }], provider: 'mock',
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(<QueryClientProvider client={client}><MemoryRouter><AiCopilotPage /></MemoryRouter></QueryClientProvider>)
}

describe('AiCopilotPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    aiApi.listAiConversations.mockResolvedValue([])
    aiApi.queryAi.mockResolvedValue(response)
  })

  it('offers useful starter prompts', async () => {
    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: 'What are my top 5 products this month?' }))
    expect(screen.getByLabelText('Ask about your business data')).toHaveValue('What are my top 5 products this month?')
  })

  it('submits a question and renders verified metrics, charts, tables, and evidence', async () => {
    renderPage()
    const input = screen.getByLabelText('Ask about your business data')
    fireEvent.change(input, { target: { value: 'Compare revenue' } })
    fireEvent.click(screen.getByRole('button', { name: 'Analyze' }))

    expect(await screen.findByText(response.summary)).toBeInTheDocument()
    expect(aiApi.queryAi).toHaveBeenCalledWith('Compare revenue', undefined)
    expect(screen.getByText('$1,250.00')).toBeInTheDocument()
    expect(screen.getByText('Revenue trend')).toBeInTheDocument()
    expect(screen.getByText('Top Product')).toBeInTheDocument()
    expect(screen.getByText('Verified revenue overview query')).toBeInTheDocument()
  })

  it('loads a persisted conversation owned by the current user', async () => {
    aiApi.listAiConversations.mockResolvedValue([{ id: 'conversation-1', title: 'Monthly revenue', createdAt: '2026-10-20T00:00:00Z', updatedAt: '2026-10-21T00:00:00Z' }])
    aiApi.getAiConversation.mockResolvedValue({
      id: 'conversation-1', title: 'Monthly revenue', createdAt: '2026-10-20T00:00:00Z', updatedAt: '2026-10-21T00:00:00Z',
      messages: [{ id: 'message-2', role: 'ASSISTANT', content: response.summary, createdAt: '2026-10-21T00:00:00Z', response }],
    })
    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: /Monthly revenue/ }))
    expect(await screen.findByText(response.summary)).toBeInTheDocument()
    expect(aiApi.getAiConversation).toHaveBeenCalledWith('conversation-1')
  })

  it('starts a new analysis after viewing history', async () => {
    aiApi.listAiConversations.mockResolvedValue([{ id: 'conversation-1', title: 'Monthly revenue', createdAt: '2026-10-20T00:00:00Z', updatedAt: '2026-10-21T00:00:00Z' }])
    aiApi.getAiConversation.mockResolvedValue({ id: 'conversation-1', title: 'Monthly revenue', createdAt: '', updatedAt: '', messages: [] })
    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: /Monthly revenue/ }))
    await waitFor(() => expect(aiApi.getAiConversation).toHaveBeenCalledWith('conversation-1'))
    fireEvent.click(screen.getByRole('button', { name: '+ New analysis' }))
    expect(screen.getByText('What would you like to understand?')).toBeInTheDocument()
  })

  it('shows a retry action after provider failure', async () => {
    aiApi.queryAi.mockRejectedValueOnce(new Error('Provider unavailable')).mockResolvedValueOnce(response)
    renderPage()
    fireEvent.change(screen.getByLabelText('Ask about your business data'), { target: { value: 'Show revenue' } })
    fireEvent.click(screen.getByRole('button', { name: 'Analyze' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Retry' }))
    await waitFor(() => expect(aiApi.queryAi).toHaveBeenCalledTimes(2))
  })
})
