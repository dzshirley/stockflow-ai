import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { InventoryAdjustmentForm } from './InventoryAdjustmentForm'

const inventory = { productId: 'p1', sku: 'LS-01', productName: 'Light Stick', availableQuantity: 10, reservedQuantity: 2, reorderPoint: 4, lowStock: false, version: 1, updatedAt: '2026-10-20T12:00:00Z' }

describe('InventoryAdjustmentForm', () => {
  it('requires a stock or reorder-point change', () => {
    const submit = vi.fn()
    render(<InventoryAdjustmentForm inventory={inventory} pending={false} onSubmit={submit} onClose={vi.fn()} />)
    fireEvent.click(screen.getByRole('button', { name: 'Apply adjustment' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Change the stock quantity or reorder point')
    expect(submit).not.toHaveBeenCalled()
  })

  it('submits a signed delta with the reorder point and reason', async () => {
    const submit = vi.fn().mockResolvedValue(undefined)
    render(<InventoryAdjustmentForm inventory={inventory} pending={false} onSubmit={submit} onClose={vi.fn()} />)
    fireEvent.change(screen.getByLabelText('Available quantity change'), { target: { value: '-2' } })
    fireEvent.change(screen.getByLabelText('Reorder point'), { target: { value: '5' } })
    fireEvent.change(screen.getByLabelText('Reason'), { target: { value: ' Cycle count correction ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Apply adjustment' }))
    await waitFor(() => expect(submit).toHaveBeenCalledWith({ availableDelta: -2, reorderPoint: 5, reason: 'Cycle count correction' }))
  })
})
