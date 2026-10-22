import { Link, Outlet } from 'react-router-dom'

export function AuthLayout() {
  return (
    <main className="auth-shell">
      <section className="auth-story" aria-label="StockFlow introduction">
        <Link to="/" className="brand brand--auth"><span className="brand-mark">SF</span><span><strong>StockFlow AI</strong><small>Operations console</small></span></Link>
        <div><p className="eyebrow">Built for modern K-pop retail</p><h1>Keep every album, light stick, and order in rhythm.</h1><p>One calm workspace for catalog operations, inventory accuracy, and reliable fulfillment.</p></div>
        <p className="auth-story__note">Secure store-scoped access · Transaction-safe ordering</p>
      </section>
      <section className="auth-panel"><Outlet /></section>
    </main>
  )
}
