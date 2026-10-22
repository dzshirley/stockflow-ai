import type { InventoryRiskLevel, OrderStatus, ProductStatus } from '../types/api'

export function StatusBadge({ status }: { status: OrderStatus | ProductStatus | InventoryRiskLevel | 'LOW_STOCK' }) {
  return <span className={`status-badge status-badge--${status.toLowerCase()}`}>{status.replaceAll('_', ' ')}</span>
}
