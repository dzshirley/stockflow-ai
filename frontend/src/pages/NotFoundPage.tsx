import { Link } from 'react-router-dom'

export function NotFoundPage() {
  return <div className="not-found"><p className="eyebrow">404</p><h1>That page is off the shelf.</h1><p>Check the address or return to your dashboard.</p><Link className="button button--primary" to="/">Go to dashboard</Link></div>
}
