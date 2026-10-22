import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { PageHeader } from '../../components/AppShell'
import { EmptyState, ErrorPanel, PageSkeleton } from '../../components/Feedback'
import { Pagination } from '../../components/Pagination'
import { StatusBadge } from '../../components/StatusBadge'
import { listOrders } from '../../services/orders'
import { formatDate, formatMoney, shortId } from '../../utils/format'

export function OrdersPage() {
  const [page, setPage] = useState(0)
  const orders = useQuery({ queryKey: ['orders', page, 20], queryFn: () => listOrders(page, 20) })
  return <>
    <PageHeader eyebrow="Fulfillment" title="Orders" description="Review transaction-safe reservations, payment state, and order history." action={<Link className="button button--primary" to="/orders/new">Create order</Link>} />
    {orders.isPending ? <PageSkeleton rows={6} /> : orders.error ? <ErrorPanel error={orders.error} onRetry={() => orders.refetch()} /> : !orders.data.items.length ? <EmptyState title="No orders yet" description="New orders reserve stock atomically and appear here." action={<Link className="button button--secondary" to="/orders/new">Create first order</Link>} /> : <>
      <div className="table-wrap"><table><thead><tr><th>Order</th><th>Created</th><th>Items</th><th>Status</th><th>Total</th><th><span className="sr-only">Open</span></th></tr></thead><tbody>{orders.data.items.map((order) => <tr key={order.id}>
        <td><Link className="table-primary" to={`/orders/${order.id}`}>#{shortId(order.id)}<small>{order.id}</small></Link></td><td>{formatDate(order.createdAt)}</td><td>{order.items.reduce((sum, line) => sum + line.quantity, 0)} unit{order.items.reduce((sum, line) => sum + line.quantity, 0) === 1 ? '' : 's'}</td><td><StatusBadge status={order.status} /></td><td><strong>{formatMoney(order.total, order.currencyCode)}</strong></td><td><Link className="text-link" to={`/orders/${order.id}`}>Details →</Link></td>
      </tr>)}</tbody></table></div><Pagination page={orders.data.page} totalPages={orders.data.totalPages} onChange={setPage} />
    </>}
  </>
}
