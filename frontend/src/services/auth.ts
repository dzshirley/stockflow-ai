import type { AuthUser, LoginInput, RegisterInput } from '../types/api'
import { ApiError, apiRequest } from './api'

export async function getCurrentUser(): Promise<AuthUser | null> {
  try {
    return await apiRequest<AuthUser>('/api/auth/me', { notifyUnauthorized: false })
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return null
    throw error
  }
}

export const login = (input: LoginInput) => apiRequest<AuthUser>('/api/auth/login', {
  method: 'POST', csrf: true, body: JSON.stringify(input), notifyUnauthorized: false,
})

export const register = (input: RegisterInput) => apiRequest<AuthUser>('/api/auth/register', {
  method: 'POST', csrf: true, body: JSON.stringify(input), notifyUnauthorized: false,
})

export const logout = () => apiRequest<void>('/api/auth/logout', { method: 'POST', csrf: true })
