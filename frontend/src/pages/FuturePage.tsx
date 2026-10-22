import { Link } from 'react-router-dom'
import { PageHeader } from '../components/AppShell'

export function FuturePage({ kind }: { kind: 'analytics' | 'ai' }) {
  const analytics = kind === 'analytics'
  return <>
    <PageHeader eyebrow="Phase 4 preview" title={analytics ? 'Analytics' : 'AI assistant'} description={analytics ? 'Decision-ready metrics will arrive once their backend aggregates and definitions are implemented.' : 'AI workflows will be built on trusted operational data, explicit permissions, and reviewable actions.'} />
    <section className="future-card"><span className="future-card__mark" aria-hidden>{analytics ? '↗' : '✦'}</span><p className="eyebrow">Intentionally not active</p><h2>{analytics ? 'No invented revenue. No placeholder charts.' : 'No simulated intelligence.'}</h2><p>{analytics ? 'Phase 3 exposes only metrics supported by current APIs: product counts, inventory levels, low-stock signals, and order state. Revenue trends, sell-through, and forecasting require dedicated backend reporting contracts.' : 'A future assistant can help investigate stock risk, explain performance, and stage operational changes. It will not be implemented until the analytics and safe-action foundations exist.'}</p><div className="future-steps"><div><strong>1</strong><span>Define authoritative metrics and reporting endpoints</span></div><div><strong>2</strong><span>Add observable, store-scoped analytics workflows</span></div><div><strong>3</strong><span>Layer reviewed AI recommendations on trusted data</span></div></div><Link className="button button--secondary" to="/">Return to dashboard</Link></section>
  </>
}
