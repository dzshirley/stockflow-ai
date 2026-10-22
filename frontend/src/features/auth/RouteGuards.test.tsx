import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ProtectedRoute, PublicOnlyRoute } from './RouteGuards'

const authState = vi.hoisted(() => ({ user: null as null | { userId: string; storeId: string; email: string; displayName: string; role: 'ADMIN' }, isLoading: false, sessionExpired: false }))
vi.mock('./AuthProvider', () => ({ useAuth: () => authState }))

describe('route guards', () => {
  beforeEach(() => { authState.user = null; authState.isLoading = false; authState.sessionExpired = false })

  it('redirects anonymous users away from protected pages', () => {
    render(<MemoryRouter initialEntries={['/inventory']}><Routes><Route element={<ProtectedRoute />}><Route path="/inventory" element={<div>Private inventory</div>} /></Route><Route path="/login" element={<div>Login screen</div>} /></Routes></MemoryRouter>)
    expect(screen.getByText('Login screen')).toBeInTheDocument()
    expect(screen.queryByText('Private inventory')).not.toBeInTheDocument()
  })

  it('allows an authenticated user into protected pages', () => {
    authState.user = { userId: 'u1', storeId: 's1', email: 'admin@example.com', displayName: 'Admin', role: 'ADMIN' }
    render(<MemoryRouter initialEntries={['/inventory']}><Routes><Route element={<ProtectedRoute />}><Route path="/inventory" element={<div>Private inventory</div>} /></Route></Routes></MemoryRouter>)
    expect(screen.getByText('Private inventory')).toBeInTheDocument()
  })

  it('keeps signed-in users out of public auth pages', () => {
    authState.user = { userId: 'u1', storeId: 's1', email: 'admin@example.com', displayName: 'Admin', role: 'ADMIN' }
    render(<MemoryRouter initialEntries={['/login']}><Routes><Route element={<PublicOnlyRoute />}><Route path="/login" element={<div>Login screen</div>} /></Route><Route path="/" element={<div>Dashboard</div>} /></Routes></MemoryRouter>)
    expect(screen.getByText('Dashboard')).toBeInTheDocument()
  })
})
