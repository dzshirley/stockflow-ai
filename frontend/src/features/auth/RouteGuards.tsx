import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './AuthProvider'
import { FullPageLoader } from '../../components/Feedback'

export function ProtectedRoute() {
  const { user, isLoading, sessionExpired } = useAuth()
  const location = useLocation()
  if (isLoading) return <FullPageLoader label="Restoring your workspace" />
  if (!user) return <Navigate to={sessionExpired ? '/login?expired=1' : '/login'} replace state={{ from: location }} />
  return <Outlet />
}

export function PublicOnlyRoute() {
  const { user, isLoading } = useAuth()
  if (isLoading) return <FullPageLoader label="Checking your session" />
  return user ? <Navigate to="/" replace /> : <Outlet />
}
