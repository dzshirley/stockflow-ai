import { FormEvent, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../../features/auth/AuthProvider'
import { errorMessage } from '../../services/api'

const initial = { displayName: '', email: '', password: '', storeName: '', currencyCode: 'USD', timeZone: 'America/New_York' }

export function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState(initial)
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const update = (field: keyof typeof form, value: string) => setForm((current) => ({ ...current, [field]: value }))

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError('')
    if (Object.values(form).some((value) => !value.trim())) return setError('Complete every field.')
    if (form.password.length < 12) return setError('Password must be at least 12 characters.')
    setSubmitting(true)
    try {
      await register({ ...form, email: form.email.trim(), currencyCode: form.currencyCode.toUpperCase() })
      navigate('/', { replace: true })
    } catch (cause) {
      setError(errorMessage(cause))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="auth-form-wrap auth-form-wrap--register">
      <p className="eyebrow">New workspace</p><h2>Create your store</h2><p className="muted">Your account becomes the store administrator.</p>
      {error && <div className="notice notice--error" role="alert">{error}</div>}
      <form onSubmit={submit} className="form-grid" noValidate>
        <label>Display name<input value={form.displayName} onChange={(e) => update('displayName', e.target.value)} /></label>
        <label>Email<input type="email" autoComplete="email" value={form.email} onChange={(e) => update('email', e.target.value)} /></label>
        <label className="field-span">Password<input aria-label="Password" type="password" autoComplete="new-password" value={form.password} onChange={(e) => update('password', e.target.value)} /><small>12–72 characters</small></label>
        <label className="field-span">Store name<input value={form.storeName} onChange={(e) => update('storeName', e.target.value)} /></label>
        <label>Currency<input maxLength={3} value={form.currencyCode} onChange={(e) => update('currencyCode', e.target.value)} /></label>
        <label>Time zone<input value={form.timeZone} onChange={(e) => update('timeZone', e.target.value)} /></label>
        <button className="button button--primary button--wide field-span" disabled={submitting}>{submitting ? 'Creating store…' : 'Create store'}</button>
      </form>
      <p className="auth-switch">Already have access? <Link to="/login">Sign in</Link></p>
    </div>
  )
}
