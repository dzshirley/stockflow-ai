import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { useState } from 'react'
import { PageHeader } from '../../components/AppShell'
import { ErrorPanel, PageSkeleton } from '../../components/Feedback'
import { StatusBadge } from '../../components/StatusBadge'
import { useToast } from '../../components/Toast'
import { ProductForm } from '../../features/products/ProductForm'
import { getInventory } from '../../services/inventory'
import { getProduct, updateProduct } from '../../services/products'
import { getStore } from '../../services/store'
import type { ProductInput, ProductUpdate } from '../../types/api'
import { formatDate, formatMoney } from '../../utils/format'

export function ProductDetailPage() {
  const { id = '' } = useParams()
  const queryClient = useQueryClient()
  const { notify } = useToast()
  const [editing, setEditing] = useState(false)
  const product = useQuery({ queryKey: ['product', id], queryFn: () => getProduct(id), enabled: !!id })
  const inventory = useQuery({ queryKey: ['inventory', id], queryFn: () => getInventory(id), enabled: !!id })
  const store = useQuery({ queryKey: ['store'], queryFn: getStore })
  const save = useMutation({ mutationFn: (input: ProductInput | ProductUpdate) => updateProduct(id, input), onSuccess: async () => { await Promise.all([queryClient.invalidateQueries({ queryKey: ['product', id] }), queryClient.invalidateQueries({ queryKey: ['products'] }), queryClient.invalidateQueries({ queryKey: ['inventory'] }), queryClient.invalidateQueries({ queryKey: ['analytics'] })]); notify('Product updated.'); setEditing(false) } })
  if (product.isPending || inventory.isPending || store.isPending) return <PageSkeleton rows={5} />
  if (product.error || inventory.error || store.error) return <ErrorPanel error={product.error ?? inventory.error ?? store.error} onRetry={() => { product.refetch(); inventory.refetch(); store.refetch() }} />
  const item = product.data
  const stock = inventory.data
  return <>
    <Link className="back-link" to="/products">← Back to products</Link>
    <PageHeader eyebrow={item.sku} title={item.name} description={item.category} action={<button className="button button--primary" onClick={() => setEditing(true)}>Edit product</button>} />
    <div className="detail-grid">
      <section className="panel"><div className="panel__header"><div><p className="eyebrow">Catalog record</p><h2>Product details</h2></div><StatusBadge status={item.status} /></div><dl className="detail-list"><div><dt>Retail price</dt><dd>{formatMoney(item.price, store.data.currencyCode)}</dd></div><div><dt>Unit cost</dt><dd>{formatMoney(item.unitCost, store.data.currencyCode)}</dd></div><div><dt>Category</dt><dd>{item.category}</dd></div><div><dt>Last updated</dt><dd>{formatDate(item.updatedAt)}</dd></div></dl></section>
      <section className="panel"><div className="panel__header"><div><p className="eyebrow">Inventory</p><h2>Stock position</h2></div><StatusBadge status={stock.lowStock ? 'LOW_STOCK' : 'HEALTHY'} /></div><div className="stock-grid"><div><strong>{stock.availableQuantity}</strong><span>Available</span></div><div><strong>{stock.reservedQuantity}</strong><span>Reserved</span></div><div><strong>{stock.reorderPoint}</strong><span>Reorder point</span></div></div><Link className="button button--secondary" to={`/inventory/${id}`}>Manage inventory</Link></section>
    </div>
    {editing && <ProductForm product={item} pending={save.isPending} onSubmit={async (input) => { await save.mutateAsync(input) }} onClose={() => setEditing(false)} />}
  </>
}
