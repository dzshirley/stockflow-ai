import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { PageHeader } from '../../components/AppShell'
import { ErrorPanel, PageSkeleton } from '../../components/Feedback'
import { ConfirmDialog } from '../../components/Modal'
import { StatusBadge } from '../../components/StatusBadge'
import { useToast } from '../../components/Toast'
import { errorMessage } from '../../services/api'
import { cancelOrder, getOrder, recordPayment } from '../../services/orders'
import { formatDate, formatMoney, shortId } from '../../utils/format'

export function OrderDetailPage() {
  const { id = '' } = useParams()
  const queryClient = useQueryClient()
  const { notify } = useToast()
  const [confirmCancel, setConfirmCancel] = useState(false)
  const [actionError, setActionError] = useState('')
  const order = useQuery({ queryKey: ['order', id], queryFn: () => getOrder(id), enabled: !!id })
  const refresh = async () => { await Promise.all([queryClient.invalidateQueries({ queryKey: ['order', id] }), queryClient.invalidateQueries({ queryKey: ['orders'] }), queryClient.invalidateQueries({ queryKey: ['inventory'] }), queryClient.invalidateQueries({ queryKey: ['analytics'] })]) }
  const cancel = useMutation({ mutationFn: () => cancelOrder(id), onSuccess: async () => { await refresh(); notify('Order cancelled and reserved stock released.'); setConfirmCancel(false) }, onError: (cause) => setActionError(errorMessage(cause)) })
  const payment = useMutation({ mutationFn: (outcome: 'APPROVED' | 'DECLINED') => recordPayment(id, outcome), onSuccess: async (_, outcome) => { await refresh(); notify(outcome === 'APPROVED' ? 'Payment approved and order marked paid.' : 'Payment declined and reservation released.'); }, onError: (cause) => setActionError(errorMessage(cause)) })
  if (order.isPending) return <PageSkeleton rows={5} />
  if (order.error) return <ErrorPanel error={order.error} onRetry={() => order.refetch()} />
  const item = order.data
  const actionable = item.status === 'PENDING_PAYMENT'
  return <>
    <Link className="back-link" to="/orders">← Back to orders</Link>
    <PageHeader eyebrow={`Order #${shortId(item.id)}`} title="Order details" description={`Created ${formatDate(item.createdAt)}`} action={<StatusBadge status={item.status} />} />
    {actionError && <div className="notice notice--error" role="alert">{actionError}</div>}
    <div className="detail-grid detail-grid--order"><section className="panel"><div className="panel__header"><div><p className="eyebrow">Line items</p><h2>Products</h2></div><span>{item.items.reduce((sum, line) => sum + line.quantity, 0)} units</span></div><div className="line-item-list">{item.items.map((line) => <div className="line-item" key={line.productId}><div><strong>{line.name}</strong><small>{line.sku} · {line.quantity} × {formatMoney(line.unitPrice, item.currencyCode)}</small></div><strong>{formatMoney(line.unitPrice * line.quantity - line.discountAmount, item.currencyCode)}</strong></div>)}</div></section>
      <aside className="panel order-summary"><p className="eyebrow">Transaction</p><h2>Summary</h2><dl className="summary-list"><div><dt>Subtotal</dt><dd>{formatMoney(item.subtotal, item.currencyCode)}</dd></div><div><dt>Discount</dt><dd>−{formatMoney(item.discountTotal, item.currencyCode)}</dd></div><div className="summary-total"><dt>Total</dt><dd>{formatMoney(item.total, item.currencyCode)}</dd></div></dl><p className="muted">Reservation expires {formatDate(item.expiresAt)}</p>{actionable && <div className="action-stack"><button className="button button--primary button--wide" disabled={payment.isPending} onClick={() => payment.mutate('APPROVED')}>Approve payment</button><button className="button button--secondary button--wide" disabled={payment.isPending} onClick={() => payment.mutate('DECLINED')}>Record decline</button><button className="button-link button-link--danger" disabled={cancel.isPending} onClick={() => setConfirmCancel(true)}>Cancel order</button></div>}</aside>
    </div>
    <section className="metadata-strip"><span><strong>Order ID</strong>{item.id}</span><span><strong>Last updated</strong>{formatDate(item.updatedAt)}</span><span><strong>Status</strong>{item.status.replaceAll('_', ' ')}</span></section>
    {confirmCancel && <ConfirmDialog title="Cancel this order?" description="The order will be cancelled and its reserved inventory returned atomically. This cannot be undone." confirmLabel="Cancel order" pending={cancel.isPending} onClose={() => setConfirmCancel(false)} onConfirm={() => cancel.mutate()} />}
  </>
}
