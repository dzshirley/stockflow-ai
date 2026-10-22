import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ProductForm } from './ProductForm'

describe('ProductForm', () => {
  it('blocks invalid product data', () => {
    const submit = vi.fn()
    render(<ProductForm pending={false} onSubmit={submit} onClose={vi.fn()} />)
    fireEvent.click(screen.getByRole('button', { name: 'Add product' }))
    expect(screen.getByRole('alert')).toHaveTextContent('SKU, name, and category are required')
    expect(submit).not.toHaveBeenCalled()
  })

  it('submits normalized numeric product data', async () => {
    const submit = vi.fn().mockResolvedValue(undefined)
    render(<ProductForm pending={false} onSubmit={submit} onClose={vi.fn()} />)
    fireEvent.change(screen.getByLabelText('SKU'), { target: { value: '  ALB-001 ' } })
    fireEvent.change(screen.getByLabelText('Category'), { target: { value: ' Albums ' } })
    fireEvent.change(screen.getByLabelText('Product name'), { target: { value: '  Limited EP ' } })
    fireEvent.change(screen.getByLabelText('Retail price'), { target: { value: '24.99' } })
    fireEvent.change(screen.getByLabelText('Unit cost'), { target: { value: '11.5' } })
    fireEvent.change(screen.getByLabelText('Initial quantity'), { target: { value: '8' } })
    fireEvent.change(screen.getByLabelText('Reorder point'), { target: { value: '2' } })
    fireEvent.click(screen.getByRole('button', { name: 'Add product' }))
    await waitFor(() => expect(submit).toHaveBeenCalledWith({ sku: 'ALB-001', category: 'Albums', name: 'Limited EP', price: 24.99, unitCost: 11.5, initialQuantity: 8, reorderPoint: 2 }))
  })
})
