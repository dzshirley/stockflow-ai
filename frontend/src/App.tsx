import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { AppShell } from './components/AppShell'
import { ProtectedRoute, PublicOnlyRoute } from './features/auth/RouteGuards'
import { AuthLayout } from './pages/auth/AuthLayout'
import { REGISTRATION_ENABLED } from './services/api'

const LoginPage = lazy(() => import('./pages/auth/LoginPage').then((module) => ({ default: module.LoginPage })))
const RegisterPage = lazy(() => import('./pages/auth/RegisterPage').then((module) => ({ default: module.RegisterPage })))
const DashboardPage = lazy(() => import('./pages/DashboardPage').then((module) => ({ default: module.DashboardPage })))
const InventoryDetailPage = lazy(() => import('./pages/inventory/InventoryDetailPage').then((module) => ({ default: module.InventoryDetailPage })))
const InventoryPage = lazy(() => import('./pages/inventory/InventoryPage').then((module) => ({ default: module.InventoryPage })))
const NotFoundPage = lazy(() => import('./pages/NotFoundPage').then((module) => ({ default: module.NotFoundPage })))
const CreateOrderPage = lazy(() => import('./pages/orders/CreateOrderPage').then((module) => ({ default: module.CreateOrderPage })))
const OrderDetailPage = lazy(() => import('./pages/orders/OrderDetailPage').then((module) => ({ default: module.OrderDetailPage })))
const OrdersPage = lazy(() => import('./pages/orders/OrdersPage').then((module) => ({ default: module.OrdersPage })))
const ProductDetailPage = lazy(() => import('./pages/products/ProductDetailPage').then((module) => ({ default: module.ProductDetailPage })))
const ProductsPage = lazy(() => import('./pages/products/ProductsPage').then((module) => ({ default: module.ProductsPage })))
const SettingsPage = lazy(() => import('./pages/SettingsPage').then((module) => ({ default: module.SettingsPage })))
const AnalyticsPage = lazy(() => import('./pages/AnalyticsPage').then((module) => ({ default: module.AnalyticsPage })))
const AiCopilotPage = lazy(() => import('./pages/AiCopilotPage').then((module) => ({ default: module.AiCopilotPage })))
const BusinessInsightsPage = lazy(() => import('./pages/BusinessInsightsPage').then((module) => ({ default: module.BusinessInsightsPage })))
const OperationsPage = lazy(() => import('./pages/OperationsPage').then((module) => ({ default: module.OperationsPage })))

export function App() {
  return (
    <Suspense fallback={<main className="page-loading" aria-live="polite">Loading workspace…</main>}>
      <Routes>
        <Route element={<PublicOnlyRoute />}>
          <Route element={<AuthLayout />}>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={REGISTRATION_ENABLED ? <RegisterPage /> : <Navigate to="/login" replace />} />
          </Route>
        </Route>
        <Route element={<ProtectedRoute />}>
          <Route element={<AppShell />}>
            <Route index element={<DashboardPage />} />
            <Route path="products" element={<ProductsPage />} />
            <Route path="products/:id" element={<ProductDetailPage />} />
            <Route path="inventory" element={<InventoryPage />} />
            <Route path="inventory/:productId" element={<InventoryDetailPage />} />
            <Route path="orders" element={<OrdersPage />} />
            <Route path="orders/new" element={<CreateOrderPage />} />
            <Route path="orders/:id" element={<OrderDetailPage />} />
            <Route path="analytics" element={<AnalyticsPage />} />
            <Route path="ai-assistant" element={<AiCopilotPage />} />
            <Route path="ai-copilot" element={<AiCopilotPage />} />
            <Route path="business-insights" element={<BusinessInsightsPage />} />
            <Route path="operations" element={<OperationsPage />} />
            <Route path="settings" element={<SettingsPage />} />
            <Route path="*" element={<NotFoundPage />} />
          </Route>
        </Route>
      </Routes>
    </Suspense>
  )
}
