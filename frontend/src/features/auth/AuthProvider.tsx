import { useQuery, useQueryClient } from '@tanstack/react-query'
import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import type { AuthUser, LoginInput, RegisterInput } from '../../types/api'
import { getCurrentUser, login as loginRequest, logout as logoutRequest, register as registerRequest } from '../../services/auth'
import { UNAUTHORIZED_EVENT } from '../../services/api'

interface AuthContextValue {
  user: AuthUser | null
  isLoading: boolean
  sessionExpired: boolean
  login: (input: LoginInput) => Promise<AuthUser>
  register: (input: RegisterInput) => Promise<AuthUser>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [sessionExpired, setSessionExpired] = useState(false)
  const auth = useQuery({ queryKey: ['auth', 'me'], queryFn: getCurrentUser, retry: false })

  useEffect(() => {
    const expire = () => {
      setSessionExpired(true)
      queryClient.setQueryData(['auth', 'me'], null)
      queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== 'auth' })
    }
    window.addEventListener(UNAUTHORIZED_EVENT, expire)
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, expire)
  }, [queryClient])

  const value = useMemo<AuthContextValue>(() => ({
    user: auth.data ?? null,
    isLoading: auth.isPending,
    sessionExpired,
    login: async (input) => {
      const user = await loginRequest(input)
      setSessionExpired(false)
      queryClient.setQueryData(['auth', 'me'], user)
      return user
    },
    register: async (input) => {
      const user = await registerRequest(input)
      setSessionExpired(false)
      queryClient.setQueryData(['auth', 'me'], user)
      return user
    },
    logout: async () => {
      await logoutRequest()
      setSessionExpired(false)
      queryClient.clear()
      queryClient.setQueryData(['auth', 'me'], null)
    },
  }), [auth.data, auth.isPending, queryClient, sessionExpired])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// Context hooks intentionally share the component module with their provider.
// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth must be used inside AuthProvider')
  return value
}
