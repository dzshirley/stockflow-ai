import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { FormEvent, useMemo, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { PageHeader } from '../../components/AppShell'
import { ErrorPanel, PageSkeleton } from '../../components/Feedback'
import { useToast } from '../../components/Toast'
import { errorMessage } from '../../services/api'
import { listInventory } from '../../services/inventory'
import { createOrder } from '../../services/orders'
import { listProducts } from '../../services/products'
import { getStore } from '../../services/store'
import { formatMoney, newIdempotencyKey } from '../../utils/format'

interface DraftLine { productId: string; quantity: number }

export function CreateOrderPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { notify } = useToast()
  const [lines, setLines] = useState<DraftLine[]>([{ productId: '', quantity: 1 }])
  const [error, setError] = useState('')
  const idempotencyKey = useRef(newIdempotencyKey())
  const products = useQuery({ queryKey: ['products', 'order-picker'], queryFn: () => listProducts({ status: 'ACTIVE', page: 0, size: 100, sort: 'name' }) })
  const inventory = useQuery({ queryKey: ['inventory', false], queryFn: () => listInventory(false) })
  const store = useQuery({ queryKey: ['store'], queryFn: getStore })
  const productMap = useMemo(() => new Map(products.data?.items.map((item) => [item.id, item]) ?? []), [products.data])
  const stockMap = useMemo(() => new Map(inventory.data?.items.map((item) => [item.productId, item]) ?? []), [inventory.data])
  const total = lines.reduce((sum, line) => sum + (productMap.get(line.productId)?.price ?? 0) * line.quantity, 0)
  const create = useMutation({ mutationFn: () => createOrder(lines, idempotencyKey.current), onSuccess: async (order) => { await Promise.all([queryClient.invalidateQueries({ queryKey: ['orders'] }), queryClient.invalidateQueries({ queryKey: ['inventory'] }), queryClient.invalidateQueries({ queryKey: ['analytics'] })]); notify('Order created and inventory reserved.'); navigate(`/orders/${order.id}`, { replace: true }) }, onError: (cause) => setError(errorMessage(cause)) })
  const updateLine = (index: number, update: Partial<DraftLine>) => setLines((current) => current.map((line, position) => position === index ? { ...line, ...update } : line))

  function submit(event: FormEvent) {
    event.preventDefault(); setError('')
    if (create.isPending) return
    if (!lines.length || lines.some((line) => !line.productId || !Number.isInteger(line.quantity) || line.quantity < 1)) return setError('Select a product and positive whole-number quantity for every line.')
    if (new Set(lines.map((line) => line.productId)).size !== lines.length) return setError('Each product can appear only once. Update the existing line instead.')
    const unavailable = lines.find((line) => line.quantity > (stockMap.get(line.productId)?.availableQuantity ?? 0))
    if (unavailable) return setError(`${productMap.get(unavailable.productId)?.name ?? 'A product'} has insufficient available stock.`)
    create.mutate()
  }

  if (products.isPending || inventory.isPending || store.isPending) return <PageSkeleton rows={5} />
  if (products.error || inventory.error || store.error) return <ErrorPanel error={products.error ?? inventory.error ?? store.error} onRetry={() => { products.refetch(); inventory.refetch(); store.refetch() }} />
  return <>
    <Link className="back-link" to="/orders">← Back to orders</Link>
    <PageHeader eyebrow="New transaction" title="Create order" description="Stock is rechecked and reserved atomically when you submit." />
    <form className="order-builder" onSubmit={submit} noValidate>
      <section className="panel"><div className="panel__header"><div><p className="eyebrow">Order lines</p><h2>Select products</h2></div><button type="button" className="button button--secondary" onClick={() => setLines((current) => [...current, { productId: '', quantity: 1 }])}>Add line</button></div>
        {error && <div className="notice notice--error" role="alert">{error}</div>}
        {!products.data.items.length ? <div className="notice notice--warning">There are no active products. <Link to="/products">Add a product first.</Link></div> : <div className="order-lines">{lines.map((line, index) => {
          const stock = stockMap.get(line.productId)
          const product = productMap.get(line.productId)
          return <div className="order-line" key={index}><label><span>Product</span><select aria-label={lines.length === 1 ? 'Product' : `Product line ${index + 1}`} value={line.productId} onChange={(e) => updateLine(index, { productId: e.target.value })}><option value="">Choose a product</option>{products.data.items.map((item) => <option key={item.id} value={item.id}>{item.name} · {item.sku}</option>)}</select>{stock && <small>{stock.availableQuantity} available · {formatMoney(product?.price ?? 0, store.data.currencyCode)}</small>}</label><label className="quantity-field"><span>Quantity</span><input aria-label={lines.length === 1 ? 'Quantity' : `Quantity line ${index + 1}`} type="number" min="1" step="1" value={line.quantity} onChange={(e) => updateLine(index, { quantity: Number(e.target.value) })} /></label><button type="button" className="icon-button" aria-label={`Remove line ${index + 1}`} disabled={lines.length === 1} onClick={() => setLines((current) => current.filter((_, position) => position !== index))}>×</button></div>
        })}</div>}
      </section>
      <aside className="panel order-summary"><p className="eyebrow">Summary</p><h2>Order total</h2><div className="summary-lines"><span>{lines.filter((line) => line.productId).length} product lines</span><strong>{formatMoney(total, store.data.currencyCode)}</strong></div><p className="muted">Final prices and availability are authoritative on the server. Submission uses an idempotency key to prevent duplicate orders.</p><button className="button button--primary button--wide" disabled={create.isPending || !products.data.items.length}>{create.isPending ? 'Reserving stock…' : 'Create order'}</button></aside>
    </form>
  </>
}
