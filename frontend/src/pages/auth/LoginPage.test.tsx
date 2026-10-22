import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { LoginPage } from './LoginPage'

const login = vi.hoisted(() => vi.fn())
vi.mock('../../features/auth/AuthProvider', () => ({ useAuth: () => ({ login }) }))

function renderLogin(entry = '/login') {
  return render(<MemoryRouter initialEntries={[entry]}><Routes><Route path="/login" element={<LoginPage />} /><Route path="/" element={<div>Dashboard screen</div>} /></Routes></MemoryRouter>)
}

describe('LoginPage', () => {
  it('validates required credentials without calling the backend', () => {
    renderLogin()
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Enter your email and password')
    expect(login).not.toHaveBeenCalled()
  })

  it('signs in and navigates to the dashboard', async () => {
    login.mockResolvedValueOnce({ userId: 'u1' })
    renderLogin()
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'owner@example.com' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'very-secure-password' } })
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    await waitFor(() => expect(login).toHaveBeenCalledWith({ email: 'owner@example.com', password: 'very-secure-password' }))
    expect(await screen.findByText('Dashboard screen')).toBeInTheDocument()
  })

  it('shows session expiry context', () => {
    renderLogin('/login?expired=1')
    expect(screen.getByText(/session expired/i)).toBeInTheDocument()
  })
})
