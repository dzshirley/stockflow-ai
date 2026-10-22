import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { FormEvent, useState } from 'react'
import { PageHeader } from '../components/AppShell'
import { EmptyState, ErrorPanel, PageSkeleton } from '../components/Feedback'
import { Modal } from '../components/Modal'
import { useToast } from '../components/Toast'
import { useAuth } from '../features/auth/AuthProvider'
import { errorMessage } from '../services/api'
import { createStaff, getStore, getStoreMembers } from '../services/store'

export function SettingsPage() {
  const { user } = useAuth()
  const [adding, setAdding] = useState(false)
  const store = useQuery({ queryKey: ['store'], queryFn: getStore })
  const members = useQuery({ queryKey: ['store-members'], queryFn: getStoreMembers, enabled: user?.role === 'ADMIN' })
  if (store.isPending || (user?.role === 'ADMIN' && members.isPending)) return <PageSkeleton rows={5} />
  if (store.error || members.error) return <ErrorPanel error={store.error ?? members.error} onRetry={() => { store.refetch(); members.refetch() }} />
  return <>
    <PageHeader eyebrow="Workspace" title="Settings" description="Store identity, localization, and role-based team access." action={user?.role === 'ADMIN' ? <button className="button button--primary" onClick={() => setAdding(true)}>Add staff member</button> : undefined} />
    <div className="detail-grid"><section className="panel"><div className="panel__header"><div><p className="eyebrow">Store profile</p><h2>{store.data.name}</h2></div></div><dl className="detail-list"><div><dt>Currency</dt><dd>{store.data.currencyCode}</dd></div><div><dt>Time zone</dt><dd>{store.data.timeZone}</dd></div><div><dt>Your access</dt><dd>{store.data.role}</dd></div><div><dt>Store ID</dt><dd className="mono">{store.data.id}</dd></div></dl></section>
      <section className="panel"><div className="panel__header"><div><p className="eyebrow">Security model</p><h2>Store-scoped access</h2></div></div><p className="body-copy">Every backend query derives the store from the authenticated session. Admins can manage staff and archive products; staff can run day-to-day catalog, inventory, and order operations.</p></section></div>
    {user?.role === 'ADMIN' ? <section className="panel section-panel"><div className="panel__header"><div><p className="eyebrow">Team</p><h2>Store members</h2></div><span>{members.data?.items.length ?? 0} members</span></div>{!members.data?.items.length ? <EmptyState title="No team members" description="Add a staff account to share operations safely." /> : <div className="member-grid">{members.data.items.map((member) => <article className="member-card" key={member.userId}><span className="avatar">{member.displayName.slice(0, 2).toUpperCase()}</span><div><strong>{member.displayName}</strong><small>{member.email}</small></div><span className="role-label">{member.role}</span></article>)}</div>}</section> : <div className="notice notice--info">Team management is available to store administrators.</div>}
    {adding && <AddStaffForm onClose={() => setAdding(false)} />}
  </>
}

function AddStaffForm({ onClose }: { onClose: () => void }) {
  const [form, setForm] = useState({ displayName: '', email: '', password: '' })
  const [error, setError] = useState('')
  const { notify } = useToast()
  const queryClient = useQueryClient()
  const create = useMutation({ mutationFn: createStaff, onSuccess: async () => { await queryClient.invalidateQueries({ queryKey: ['store-members'] }); notify('Staff account created.'); onClose() } })
  async function submit(event: FormEvent) {
    event.preventDefault(); setError('')
    if (!form.displayName.trim() || !form.email.trim()) return setError('Name and email are required.')
    if (form.password.length < 12) return setError('Password must be at least 12 characters.')
    try { await create.mutateAsync({ ...form, displayName: form.displayName.trim(), email: form.email.trim() }) } catch (cause) { setError(errorMessage(cause)) }
  }
  return <Modal title="Add staff member" onClose={onClose}><form onSubmit={submit} noValidate><div className="modal__body form-stack">{error && <div className="notice notice--error" role="alert">{error}</div>}<label>Display name<input value={form.displayName} onChange={(e) => setForm({ ...form, displayName: e.target.value })} /></label><label>Email<input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} /></label><label>Temporary password<input aria-label="Temporary password" type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} /><small>Share through a secure channel. Minimum 12 characters.</small></label></div><footer className="modal__footer"><button className="button button--secondary" type="button" onClick={onClose}>Cancel</button><button className="button button--primary" disabled={create.isPending}>{create.isPending ? 'Creating…' : 'Create staff account'}</button></footer></form></Modal>
}
