import type { InventoryRiskLevel, OrderStatus, ProductStatus, RestockProposalStatus } from '../types/api'

export function StatusBadge({ status }: { status: OrderStatus | ProductStatus | InventoryRiskLevel | RestockProposalStatus | 'LOW_STOCK' }) {
  return <span className={`status-badge status-badge--${status.toLowerCase()}`}>{status.replaceAll('_', ' ')}</span>
}
