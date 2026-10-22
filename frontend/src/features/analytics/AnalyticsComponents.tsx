import {
  Bar,
  BarChart,
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import type {
  AnalyticsOverview,
  CategoryPerformanceResponse,
  InventoryAnalyticsSummary,
  InventoryRiskResponse,
  ProductPerformanceResponse,
  RevenueTrend,
} from '../../types/api'
import { formatMoney } from '../../utils/format'
import { EmptyState } from '../../components/Feedback'
import { StatusBadge } from '../../components/StatusBadge'
import { Link } from 'react-router-dom'

export function AnalyticsKpis({ overview }: { overview: AnalyticsOverview }) {
  const { metrics, comparison } = overview
  const growth = comparison?.revenueGrowthPercent
  const growthDetail = growth == null
    ? 'No comparable prior revenue'
    : `${growth >= 0 ? '+' : ''}${growth.toFixed(1)}% vs previous period`
  return <section className="analytics-kpis" aria-label="Sales key performance indicators">
    <Kpi label="Revenue" value={formatMoney(metrics.revenue, metrics.currency)} detail={growthDetail} tone={growth == null ? undefined : growth >= 0 ? 'success' : 'warning'} />
    <Kpi label="Paid orders" value={metrics.orders.toLocaleString()} detail="completed in period" />
    <Kpi label="Average order value" value={metrics.averageOrderValue == null ? '—' : formatMoney(metrics.averageOrderValue, metrics.currency)} detail={metrics.averageOrderValue == null ? 'No paid orders' : 'revenue per paid order'} />
    <Kpi label="Units sold" value={metrics.unitsSold.toLocaleString()} detail="across paid orders" />
    <Kpi label="Revenue change" value={comparison ? formatMoney(comparison.revenueChange, metrics.currency) : '—'} detail={comparison ? growthDetail : 'Comparison disabled'} tone={comparison?.revenueChange == null ? undefined : comparison.revenueChange >= 0 ? 'success' : 'warning'} />
  </section>
}

function Kpi({ label, value, detail, tone }: { label: string; value: string; detail: string; tone?: 'success' | 'warning' }) {
  return <article className={`metric-card analytics-kpi ${tone ? `metric-card--${tone}` : ''}`}>
    <p>{label}</p><strong>{value}</strong><small>{detail}</small>
  </article>
}

export function RevenueTrendPanel({ data, compact = false }: { data: RevenueTrend; compact?: boolean }) {
  const hasRevenue = data.points.some((point) => point.revenue !== 0)
  return <section className="panel analytics-chart-panel">
    <div className="panel__header"><div><p className="eyebrow">Revenue trajectory</p><h2>{compact ? 'Revenue trend' : `Revenue by ${data.interval.toLowerCase()}`}</h2></div><small>{data.period.timeZone}</small></div>
    {!hasRevenue ? <EmptyState title="No paid sales in this period" description="Revenue appears after an order receives an approved payment." /> : <div className={compact ? 'chart chart--compact' : 'chart'} aria-label="Revenue trend chart">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={data.points} margin={{ top: 8, right: 12, left: 4, bottom: 4 }}>
          <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e6ebe7" />
          <XAxis dataKey="bucketStart" tickFormatter={shortDate} tick={{ fontSize: 10 }} minTickGap={24} />
          <YAxis width={62} tickFormatter={(value: number) => compactMoney(value, data.currency)} tick={{ fontSize: 10 }} domain={[0, 'auto']} />
          <Tooltip labelFormatter={(label) => formatChartDate(String(label))} formatter={(value) => [formatMoney(Number(value), data.currency), 'Revenue']} />
          <Line type="monotone" dataKey="revenue" stroke="#456d4c" strokeWidth={2.5} dot={data.points.length <= 14} activeDot={{ r: 5 }} />
        </LineChart>
      </ResponsiveContainer>
    </div>}
  </section>
}

export function CategoryPerformancePanel({ data }: { data: CategoryPerformanceResponse }) {
  return <section className="panel analytics-chart-panel">
    <div className="panel__header"><div><p className="eyebrow">Sales mix</p><h2>Category performance</h2></div><small>Historical line snapshots</small></div>
    {!data.items.length ? <EmptyState title="No category sales yet" description="Paid order lines will be grouped by their saved category." /> : <div className="chart" aria-label="Category revenue chart">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data.items} layout="vertical" margin={{ top: 4, right: 18, left: 4, bottom: 4 }}>
          <CartesianGrid strokeDasharray="3 3" horizontal={false} stroke="#e6ebe7" />
          <XAxis type="number" domain={[0, 'auto']} tickFormatter={(value: number) => compactMoney(value, data.currency)} tick={{ fontSize: 10 }} />
          <YAxis type="category" dataKey="category" width={90} tick={{ fontSize: 10 }} />
          <Tooltip formatter={(value) => [formatMoney(Number(value), data.currency), 'Revenue']} />
          <Bar dataKey="revenue" fill="#789b49" radius={[0, 5, 5, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>}
  </section>
}

export function TopProductsPanel({ data, compact = false }: { data: ProductPerformanceResponse; compact?: boolean }) {
  return <section className="panel">
    <div className="panel__header"><div><p className="eyebrow">Product ranking</p><h2>Top products</h2></div><small>By revenue</small></div>
    {!data.items.length ? <EmptyState title="No product sales yet" description="Products appear here after paid orders are recorded." /> : <div className="table-wrap table-wrap--flat"><table>
      <thead><tr><th>Product</th><th>Revenue</th><th>Units</th>{!compact && <th>Orders</th>}</tr></thead>
      <tbody>{data.items.slice(0, compact ? 5 : undefined).map((item) => <tr key={item.productId}>
        <td><Link className="table-primary" to={`/products/${item.productId}`}>{item.name}<small>{item.sku}</small></Link></td>
        <td>{formatMoney(item.revenue, data.currency)}</td><td>{item.unitsSold.toLocaleString()}</td>{!compact && <td>{item.orders.toLocaleString()}</td>}
      </tr>)}</tbody>
    </table></div>}
  </section>
}

export function InventoryHealthPanel({ summary, risk, compact = false }: { summary: InventoryAnalyticsSummary; risk: InventoryRiskResponse; compact?: boolean }) {
  const alerts = risk.items.filter((item) => ['OUT_OF_STOCK', 'LOW_STOCK', 'AT_RISK'].includes(item.riskLevel))
  return <section className="panel">
    <div className="panel__header"><div><p className="eyebrow">Inventory health</p><h2>Coverage and alerts</h2></div><Link className="text-link" to="/inventory">Manage inventory</Link></div>
    <div className="inventory-analytics-summary">
      <div><strong>{summary.availableUnits.toLocaleString()}</strong><span>available</span></div>
      <div><strong>{summary.reservedUnits.toLocaleString()}</strong><span>reserved</span></div>
      <div><strong>{summary.lowStockProducts.toLocaleString()}</strong><span>low stock</span></div>
      <div><strong>{summary.averageDailyUnitsSold.toFixed(2)}</strong><span>units / day</span></div>
    </div>
    {!alerts.length ? <p className="analytics-ok">No products are currently out of stock, below reorder point, or under 14 days of coverage.</p> : <div className="list-stack">
      {alerts.slice(0, compact ? 4 : 10).map((item) => <Link className="list-row" to={`/inventory/${item.productId}`} key={item.productId}>
        <div><strong>{item.name}</strong><small>{item.sku} · {item.daysOfCover == null ? 'coverage unavailable' : `${item.daysOfCover.toFixed(1)} days of cover`}</small></div>
        <div className="list-row__end"><StatusBadge status={item.riskLevel} /><strong>{item.availableQuantity} available</strong></div>
      </Link>)}
    </div>}
  </section>
}

function shortDate(value: string) {
  return new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', timeZone: 'UTC' }).format(new Date(`${value}T00:00:00Z`))
}

function formatChartDate(value: string) {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeZone: 'UTC' }).format(new Date(`${value}T00:00:00Z`))
}

function compactMoney(value: number, currency: string) {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency, notation: 'compact', maximumFractionDigits: 1 }).format(value)
}
