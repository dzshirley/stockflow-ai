import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { PageHeader } from '../../components/AppShell'
import { EmptyState, ErrorPanel, PageSkeleton } from '../../components/Feedback'
import { Pagination } from '../../components/Pagination'
import { StatusBadge } from '../../components/StatusBadge'
import { useToast } from '../../components/Toast'
import { InventoryAdjustmentForm } from '../../features/inventory/InventoryAdjustmentForm'
import { adjustInventory, getInventory, getInventoryAudit } from '../../services/inventory'
import { formatDate, shortId } from '../../utils/format'

export function InventoryDetailPage() {
  const { productId = '' } = useParams()
  const [page, setPage] = useState(0)
  const [adjusting, setAdjusting] = useState(false)
  const queryClient = useQueryClient()
  const { notify } = useToast()
  const inventory = useQuery({ queryKey: ['inventory', productId], queryFn: () => getInventory(productId), enabled: !!productId })
  const audit = useQuery({ queryKey: ['inventory-audit', productId, page], queryFn: () => getInventoryAudit(productId, page, 15), enabled: !!productId })
  const adjustment = useMutation({ mutationFn: (input: { availableDelta: number; reorderPoint: number; reason: string }) => adjustInventory(productId, input), onSuccess: async () => { await Promise.all([queryClient.invalidateQueries({ queryKey: ['inventory'] }), queryClient.invalidateQueries({ queryKey: ['inventory-audit', productId] }), queryClient.invalidateQueries({ queryKey: ['analytics'] })]); notify('Inventory adjusted and audit entry recorded.'); setAdjusting(false) } })
  if (inventory.isPending || audit.isPending) return <PageSkeleton rows={5} />
  if (inventory.error || audit.error) return <ErrorPanel error={inventory.error ?? audit.error} onRetry={() => { inventory.refetch(); audit.refetch() }} />
  const item = inventory.data
  return <>
    <Link className="back-link" to="/inventory">← Back to inventory</Link>
    <PageHeader eyebrow={item.sku} title={item.productName} description="Inventory balance and immutable adjustment history." action={<button className="button button--primary" onClick={() => setAdjusting(true)}>Adjust inventory</button>} />
    <section className="stock-summary"><div><p>Available</p><strong>{item.availableQuantity}</strong></div><div><p>Reserved</p><strong>{item.reservedQuantity}</strong></div><div><p>Reorder point</p><strong>{item.reorderPoint}</strong></div><div><p>Condition</p><StatusBadge status={item.lowStock ? 'LOW_STOCK' : 'HEALTHY'} /></div></section>
    <section className="panel"><div className="panel__header"><div><p className="eyebrow">Audit trail</p><h2>Adjustment history</h2></div><small className="muted">Version {item.version} · updated {formatDate(item.updatedAt)}</small></div>
      {!audit.data.items.length ? <EmptyState title="No adjustments yet" description="Manual stock changes will appear here. Order reservations are transactionally managed by the backend." /> : <div className="table-wrap table-wrap--flat"><table><thead><tr><th>Date</th><th>Available change</th><th>Reserved change</th><th>Reason</th><th>Actor</th></tr></thead><tbody>{audit.data.items.map((entry) => <tr key={entry.id}><td>{formatDate(entry.createdAt)}</td><td><span className={entry.availableDelta >= 0 ? 'delta-positive' : 'delta-negative'}>{entry.availableDelta > 0 ? '+' : ''}{entry.availableDelta}</span></td><td>{entry.reservedDelta > 0 ? '+' : ''}{entry.reservedDelta}</td><td>{entry.reason}</td><td title={entry.actorUserId}>{shortId(entry.actorUserId)}</td></tr>)}</tbody></table></div>}
      <Pagination page={audit.data.page} totalPages={Math.ceil(audit.data.totalElements / audit.data.size)} onChange={setPage} />
    </section>
    {adjusting && <InventoryAdjustmentForm inventory={item} pending={adjustment.isPending} onSubmit={async (input) => { await adjustment.mutateAsync(input) }} onClose={() => setAdjusting(false)} />}
  </>
}
