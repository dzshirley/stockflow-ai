import { apiRequest } from './api'
import type { AuthUser, Store, StoreMember } from '../types/api'

export function getStore() {
  return apiRequest<Store>('/api/store')
}

export function getStoreMembers() {
  return apiRequest<{ items: StoreMember[] }>('/api/store/members')
}

export function createStaff(input: { email: string; password: string; displayName: string }) {
  return apiRequest<AuthUser>('/api/store/members', {
    method: 'POST',
    csrf: true,
    body: JSON.stringify(input),
  })
}
