import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { PageHeader } from '../../components/AppShell'
import { EmptyState, ErrorPanel, PageSkeleton } from '../../components/Feedback'
import { StatusBadge } from '../../components/StatusBadge'
import { useToast } from '../../components/Toast'
import { InventoryAdjustmentForm } from '../../features/inventory/InventoryAdjustmentForm'
import { adjustInventory, listInventory } from '../../services/inventory'
import type { InventoryBalance } from '../../types/api'

export function InventoryPage() {
  const [params, setParams] = useSearchParams()
  const lowOnly = params.get('low') === 'true'
  const [query, setQuery] = useState('')
  const [adjusting, setAdjusting] = useState<InventoryBalance | null>(null)
  const queryClient = useQueryClient()
  const { notify } = useToast()
  const inventory = useQuery({ queryKey: ['inventory', lowOnly], queryFn: () => listInventory(lowOnly) })
  const visible = useMemo(() => (inventory.data?.items ?? []).filter((item) => `${item.productName} ${item.sku}`.toLowerCase().includes(query.toLowerCase())), [inventory.data, query])
  const adjustment = useMutation({ mutationFn: (input: { availableDelta: number; reorderPoint: number; reason: string }) => adjustInventory(adjusting!.productId, input), onSuccess: async () => { await Promise.all([queryClient.invalidateQueries({ queryKey: ['inventory'] }), queryClient.invalidateQueries({ queryKey: ['analytics'] })]); notify('Inventory adjusted and audit entry recorded.'); setAdjusting(null) } })
  return <>
    <PageHeader eyebrow="Stock control" title="Inventory" description="Monitor available and reserved stock, reorder thresholds, and auditable manual changes." />
    <div className="toolbar"><label className="search-field"><span className="sr-only">Search inventory</span><input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search product or SKU" /></label><label className="toggle"><input type="checkbox" checked={lowOnly} onChange={(e) => setParams(e.target.checked ? { low: 'true' } : {})} /><span>Low stock only</span></label></div>
    {inventory.isPending ? <PageSkeleton rows={6} /> : inventory.error ? <ErrorPanel error={inventory.error} onRetry={() => inventory.refetch()} /> : !visible.length ? <EmptyState title={lowOnly ? 'No low-stock products' : 'No inventory found'} description={query ? 'Try a different search.' : lowOnly ? 'Every active product is above its reorder point.' : 'Add a product to initialize its inventory balance.'} /> : <div className="inventory-grid">{visible.map((item) => <article className="inventory-card" key={item.productId}>
      <div className="inventory-card__top"><div><Link to={`/inventory/${item.productId}`}>{item.productName}</Link><small>{item.sku}</small></div><StatusBadge status={item.lowStock ? 'LOW_STOCK' : 'HEALTHY'} /></div>
      <div className="stock-grid stock-grid--compact"><div><strong>{item.availableQuantity}</strong><span>Available</span></div><div><strong>{item.reservedQuantity}</strong><span>Reserved</span></div><div><strong>{item.reorderPoint}</strong><span>Reorder at</span></div></div>
      <div className="inventory-card__actions"><Link className="text-link" to={`/inventory/${item.productId}`}>View history</Link><button className="button button--secondary" onClick={() => setAdjusting(item)}>Adjust</button></div>
    </article>)}</div>}
    {adjusting && <InventoryAdjustmentForm inventory={adjusting} pending={adjustment.isPending} onSubmit={async (input) => { await adjustment.mutateAsync(input) }} onClose={() => setAdjusting(null)} />}
  </>
}
