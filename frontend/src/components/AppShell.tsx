import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useState } from 'react'
import { useAuth } from '../features/auth/AuthProvider'

const navigation = [
  { to: '/', label: 'Dashboard', icon: '⌂', end: true },
  { to: '/products', label: 'Products', icon: '◇' },
  { to: '/inventory', label: 'Inventory', icon: '▦' },
  { to: '/orders', label: 'Orders', icon: '≡' },
  { to: '/analytics', label: 'Analytics', icon: '↗' },
  { to: '/ai-assistant', label: 'AI assistant', icon: '✦' },
  { to: '/business-insights', label: 'Business insights', icon: '◈' },
  { to: '/settings', label: 'Settings', icon: '⚙' },
]

export function AppShell() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [mobileOpen, setMobileOpen] = useState(false)
  const [loggingOut, setLoggingOut] = useState(false)

  async function handleLogout() {
    setLoggingOut(true)
    try {
      await logout()
      navigate('/login', { replace: true })
    } finally {
      setLoggingOut(false)
    }
  }

  return (
    <div className="app-shell">
      <button className="mobile-menu" type="button" onClick={() => setMobileOpen((open) => !open)} aria-expanded={mobileOpen} aria-controls="primary-navigation">
        <span aria-hidden>☰</span> Menu
      </button>
      <aside className={`sidebar ${mobileOpen ? 'sidebar--open' : ''}`}>
        <NavLink to="/" className="brand" onClick={() => setMobileOpen(false)}>
          <span className="brand-mark">SF</span>
          <span><strong>StockFlow AI</strong><small>Operations console</small></span>
        </NavLink>
        <nav id="primary-navigation" className="nav-list" aria-label="Primary navigation">
          {navigation.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) => `nav-item ${isActive ? 'nav-item--active' : ''}`}
              onClick={() => setMobileOpen(false)}
            >
              <span className="nav-icon" aria-hidden>{item.icon}</span>
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-user">
          <span className="avatar" aria-hidden>{user?.displayName.slice(0, 2).toUpperCase()}</span>
          <span className="sidebar-user__identity"><strong>{user?.displayName}</strong><small>{user?.role.toLowerCase()}</small></span>
          <button className="icon-button icon-button--dark" onClick={handleLogout} disabled={loggingOut} aria-label="Log out" title="Log out">↪</button>
        </div>
      </aside>
      {mobileOpen && <button className="sidebar-scrim" aria-label="Close menu" onClick={() => setMobileOpen(false)} />}
      <main className="workspace"><Outlet /></main>
    </div>
  )
}

export function PageHeader({ eyebrow, title, description, action }: { eyebrow: string; title: string; description?: string; action?: React.ReactNode }) {
  return (
    <header className="page-header">
      <div><p className="eyebrow">{eyebrow}</p><h1>{title}</h1>{description && <p className="page-description">{description}</p>}</div>
      {action && <div className="page-actions">{action}</div>}
    </header>
  )
}
