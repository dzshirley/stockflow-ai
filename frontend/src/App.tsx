import { Route, Routes } from 'react-router-dom'
import { AppShell } from './components/AppShell'
import { ProtectedRoute, PublicOnlyRoute } from './features/auth/RouteGuards'
import { AuthLayout } from './pages/auth/AuthLayout'
import { LoginPage } from './pages/auth/LoginPage'
import { RegisterPage } from './pages/auth/RegisterPage'
import { DashboardPage } from './pages/DashboardPage'
import { InventoryDetailPage } from './pages/inventory/InventoryDetailPage'
import { InventoryPage } from './pages/inventory/InventoryPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { CreateOrderPage } from './pages/orders/CreateOrderPage'
import { OrderDetailPage } from './pages/orders/OrderDetailPage'
import { OrdersPage } from './pages/orders/OrdersPage'
import { ProductDetailPage } from './pages/products/ProductDetailPage'
import { ProductsPage } from './pages/products/ProductsPage'
import { SettingsPage } from './pages/SettingsPage'
import { AnalyticsPage } from './pages/AnalyticsPage'
import { AiCopilotPage } from './pages/AiCopilotPage'
import { BusinessInsightsPage } from './pages/BusinessInsightsPage'

export function App() {
  return (
    <Routes>
      <Route element={<PublicOnlyRoute />}>
        <Route element={<AuthLayout />}>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
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
          <Route path="settings" element={<SettingsPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  )
}
