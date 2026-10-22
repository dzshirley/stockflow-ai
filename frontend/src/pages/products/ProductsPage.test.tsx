import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ToastProvider } from '../../components/Toast'
import { ProductsPage } from './ProductsPage'

const productApi = vi.hoisted(() => ({
  listProducts: vi.fn(), createProduct: vi.fn(), updateProduct: vi.fn(), archiveProduct: vi.fn(),
}))
vi.mock('../../services/products', () => productApi)
vi.mock('../../services/store', () => ({ getStore: vi.fn().mockResolvedValue({ id: 's1', name: 'Store', currencyCode: 'USD', timeZone: 'UTC', role: 'ADMIN' }) }))
vi.mock('../../features/auth/AuthProvider', () => ({ useAuth: () => ({ user: { role: 'ADMIN' } }) }))

const product = { id: 'p1', sku: 'ALB-001', name: 'Limited Album', category: 'Albums', price: 24.99, unitCost: 11.5, status: 'ACTIVE' as const, createdAt: '2026-10-20T12:00:00Z', updatedAt: '2026-10-20T12:00:00Z' }

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(<QueryClientProvider client={client}><MemoryRouter><ToastProvider><ProductsPage /></ToastProvider></MemoryRouter></QueryClientProvider>)
}

describe('ProductsPage', () => {
  beforeEach(() => {
    productApi.listProducts.mockResolvedValue({ items: [product], page: 0, size: 12, totalElements: 1, totalPages: 1 })
    productApi.updateProduct.mockResolvedValue({ ...product, name: 'Updated Album' })
  })

  it('renders backend products and edits through the API', async () => {
    renderPage()
    expect(await screen.findByRole('link', { name: /Limited Album/ })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Edit' }))
    const dialog = screen.getByRole('dialog', { name: 'Edit product' })
    fireEvent.change(within(dialog).getByLabelText('Product name'), { target: { value: 'Updated Album' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }))
    await waitFor(() => expect(productApi.updateProduct).toHaveBeenCalledWith('p1', {
      sku: 'ALB-001', name: 'Updated Album', category: 'Albums', price: 24.99, unitCost: 11.5,
    }))
  })
})
