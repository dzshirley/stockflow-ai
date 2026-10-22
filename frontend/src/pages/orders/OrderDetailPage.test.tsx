import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ToastProvider } from '../../components/Toast'
import { OrderDetailPage } from './OrderDetailPage'

const orderApi = vi.hoisted(() => ({ getOrder: vi.fn(), cancelOrder: vi.fn(), recordPayment: vi.fn() }))
vi.mock('../../services/orders', () => orderApi)

const order = {
  id: 'o1', status: 'PENDING_PAYMENT' as const, subtotal: 20, discountTotal: 0, total: 20, currencyCode: 'USD',
  expiresAt: '2026-10-20T12:15:00Z', createdAt: '2026-10-20T12:00:00Z', updatedAt: '2026-10-20T12:00:00Z',
  items: [{ productId: 'p1', sku: 'ALB-001', name: 'Limited Album', quantity: 2, unitPrice: 10, discountAmount: 0 }],
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(<QueryClientProvider client={client}><MemoryRouter initialEntries={['/orders/o1']}><ToastProvider><Routes><Route path="/orders/:id" element={<OrderDetailPage />} /></Routes></ToastProvider></MemoryRouter></QueryClientProvider>)
}

describe('OrderDetailPage', () => {
  beforeEach(() => {
    orderApi.getOrder.mockResolvedValue(order)
    orderApi.cancelOrder.mockResolvedValue({ ...order, status: 'CANCELLED' })
  })

  it('confirms cancellation before calling the backend', async () => {
    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: 'Cancel order' }))
    const dialog = screen.getByRole('dialog', { name: 'Cancel this order?' })
    expect(within(dialog).getByText(/reserved inventory returned atomically/)).toBeInTheDocument()
    fireEvent.click(within(dialog).getByRole('button', { name: 'Cancel order' }))
    await waitFor(() => expect(orderApi.cancelOrder).toHaveBeenCalledWith('o1'))
  })
})
