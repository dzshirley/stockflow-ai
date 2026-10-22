import { FormEvent, useState } from 'react'
import { Modal } from '../../components/Modal'
import { errorMessage } from '../../services/api'
import type { Product, ProductInput, ProductUpdate } from '../../types/api'

type FormState = Record<'sku' | 'name' | 'category' | 'price' | 'unitCost' | 'initialQuantity' | 'reorderPoint', string>

export function ProductForm({ product, pending, onSubmit, onClose }: {
  product?: Product
  pending: boolean
  onSubmit: (input: ProductInput | ProductUpdate) => Promise<void>
  onClose: () => void
}) {
  const [form, setForm] = useState<FormState>({
    sku: product?.sku ?? '', name: product?.name ?? '', category: product?.category ?? '',
    price: product ? String(product.price) : '', unitCost: product ? String(product.unitCost) : '',
    initialQuantity: '0', reorderPoint: '0',
  })
  const [error, setError] = useState('')
  const update = (field: keyof FormState, value: string) => setForm((current) => ({ ...current, [field]: value }))

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError('')
    if (!form.sku.trim() || !form.name.trim() || !form.category.trim()) return setError('SKU, name, and category are required.')
    if ([form.price, form.unitCost].some((value) => value === '' || Number(value) < 0)) return setError('Price and unit cost must be zero or greater.')
    if (!product && [form.initialQuantity, form.reorderPoint].some((value) => !Number.isInteger(Number(value)) || Number(value) < 0)) return setError('Stock quantities must be whole numbers of zero or greater.')
    const core = { sku: form.sku.trim(), name: form.name.trim(), category: form.category.trim(), price: Number(form.price), unitCost: Number(form.unitCost) }
    try {
      await onSubmit(product ? core : { ...core, initialQuantity: Number(form.initialQuantity), reorderPoint: Number(form.reorderPoint) })
    } catch (cause) {
      setError(errorMessage(cause))
    }
  }

  return <Modal title={product ? 'Edit product' : 'Add product'} onClose={onClose} wide>
    <form onSubmit={submit} noValidate>
      <div className="modal__body form-grid">
        {error && <div className="notice notice--error field-span" role="alert">{error}</div>}
        <label>SKU<input value={form.sku} maxLength={80} onChange={(e) => update('sku', e.target.value)} placeholder="ALB-NJ-GETUP" /></label>
        <label>Category<input value={form.category} maxLength={120} onChange={(e) => update('category', e.target.value)} placeholder="Albums" /></label>
        <label className="field-span">Product name<input value={form.name} maxLength={200} onChange={(e) => update('name', e.target.value)} placeholder="NewJeans 2nd EP 'Get Up'" /></label>
        <label>Retail price<input type="number" min="0" step="0.01" value={form.price} onChange={(e) => update('price', e.target.value)} /></label>
        <label>Unit cost<input type="number" min="0" step="0.01" value={form.unitCost} onChange={(e) => update('unitCost', e.target.value)} /></label>
        {!product && <><label>Initial quantity<input type="number" min="0" step="1" value={form.initialQuantity} onChange={(e) => update('initialQuantity', e.target.value)} /></label><label>Reorder point<input type="number" min="0" step="1" value={form.reorderPoint} onChange={(e) => update('reorderPoint', e.target.value)} /></label></>}
      </div>
      <footer className="modal__footer"><button type="button" className="button button--secondary" onClick={onClose}>Cancel</button><button className="button button--primary" disabled={pending}>{pending ? 'Saving…' : product ? 'Save changes' : 'Add product'}</button></footer>
    </form>
  </Modal>
}
