import { FormEvent, useState } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../../features/auth/AuthProvider'
import { errorMessage } from '../../services/api'

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [search] = useSearchParams()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError('')
    if (!email.trim() || !password) return setError('Enter your email and password.')
    setSubmitting(true)
    try {
      await login({ email: email.trim(), password })
      const from = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname
      navigate(from && from !== '/login' ? from : '/', { replace: true })
    } catch (cause) {
      setError(errorMessage(cause))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="auth-form-wrap">
      <p className="eyebrow">Welcome back</p><h2>Sign in to your store</h2>
      <p className="muted">Use your StockFlow team credentials.</p>
      {search.get('expired') && <div className="notice notice--warning" role="status">Your session expired. Sign in again to continue.</div>}
      {error && <div className="notice notice--error" role="alert">{error}</div>}
      <form onSubmit={submit} className="form-stack" noValidate>
        <label>Email<input type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="you@store.com" /></label>
        <label>Password<input type="password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} placeholder="Your password" /></label>
        <button className="button button--primary button--wide" disabled={submitting}>{submitting ? 'Signing in…' : 'Sign in'}</button>
      </form>
      <p className="auth-switch">Opening a new store? <Link to="/register">Create an account</Link></p>
    </div>
  )
}
