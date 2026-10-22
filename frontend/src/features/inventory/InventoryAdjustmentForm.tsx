import { FormEvent, useState } from 'react'
import { Modal } from '../../components/Modal'
import { errorMessage } from '../../services/api'
import type { InventoryBalance } from '../../types/api'

export function InventoryAdjustmentForm({ inventory, pending, onSubmit, onClose }: {
  inventory: InventoryBalance
  pending: boolean
  onSubmit: (input: { availableDelta: number; reorderPoint: number; reason: string }) => Promise<void>
  onClose: () => void
}) {
  const [delta, setDelta] = useState('')
  const [reorderPoint, setReorderPoint] = useState(String(inventory.reorderPoint))
  const [reason, setReason] = useState('')
  const [error, setError] = useState('')
  async function submit(event: FormEvent) {
    event.preventDefault(); setError('')
    const parsedDelta = delta === '' ? 0 : Number(delta)
    if (!Number.isInteger(parsedDelta)) return setError('Adjustment must be a whole number.')
    if (!Number.isInteger(Number(reorderPoint)) || Number(reorderPoint) < 0) return setError('Reorder point must be a whole number of zero or greater.')
    if (parsedDelta === 0 && Number(reorderPoint) === inventory.reorderPoint) return setError('Change the stock quantity or reorder point.')
    if (parsedDelta !== 0 && !reason.trim()) return setError('Add a reason so the stock change is auditable.')
    try { await onSubmit({ availableDelta: parsedDelta, reorderPoint: Number(reorderPoint), reason: reason.trim() }) }
    catch (cause) { setError(errorMessage(cause)) }
  }
  const projected = inventory.availableQuantity + (Number(delta) || 0)
  return <Modal title="Adjust inventory" onClose={onClose}>
    <form onSubmit={submit} noValidate><div className="modal__body form-stack">
      <div className="form-context"><strong>{inventory.productName}</strong><span>{inventory.sku} · {inventory.availableQuantity} currently available</span></div>
      {error && <div className="notice notice--error" role="alert">{error}</div>}
      <label>Available quantity change<input aria-label="Available quantity change" type="number" step="1" value={delta} onChange={(e) => setDelta(e.target.value)} placeholder="e.g. 12 or -3" /><small>Projected balance: {projected}</small></label>
      <label>Reorder point<input type="number" min="0" step="1" value={reorderPoint} onChange={(e) => setReorderPoint(e.target.value)} /></label>
      <label>Reason<textarea maxLength={300} rows={3} value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Shipment received, cycle count correction…" /></label>
    </div><footer className="modal__footer"><button type="button" className="button button--secondary" onClick={onClose}>Cancel</button><button className="button button--primary" disabled={pending}>{pending ? 'Applying…' : 'Apply adjustment'}</button></footer></form>
  </Modal>
}
