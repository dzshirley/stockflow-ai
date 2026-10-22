import type { ReactNode } from 'react'
import { errorMessage } from '../services/api'

export function FullPageLoader({ label }: { label: string }) {
  return <main className="centered-page"><div className="loader-ring" /><p>{label}…</p></main>
}

export function PageSkeleton({ rows = 4 }: { rows?: number }) {
  return <div className="skeleton-stack" role="status" aria-label="Loading content">
    {Array.from({ length: rows }, (_, index) => <div className="skeleton-row" key={index} />)}
  </div>
}

export function ErrorPanel({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  return <div className="feedback-panel feedback-panel--error" role="alert">
    <div><strong>We couldn’t complete that request.</strong><p>{errorMessage(error)}</p></div>
    {onRetry && <button className="button button--secondary" onClick={onRetry} type="button">Try again</button>}
  </div>
}

export function EmptyState({ title, description, action }: { title: string; description: string; action?: ReactNode }) {
  return <div className="empty-state"><span className="empty-state__mark">SF</span><h3>{title}</h3><p>{description}</p>{action}</div>
}
