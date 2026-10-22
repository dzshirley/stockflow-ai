import http from 'k6/http'
import { check, group } from 'k6'

export const options = {
  vus: Number(__ENV.VUS || 5),
  duration: __ENV.DURATION || '10s',
  // The SPA uses a durable HTTP session; preserve each VU's cookie jar between iterations.
  noCookiesReset: true,
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
    'http_req_duration{scope:analytics}': ['p(95)<750'],
  },
}

const baseUrl = __ENV.BASE_URL || 'http://host.docker.internal:5173'
const password = 'analytics-demo-password'
const datasetOrders = Number(__ENV.DATASET_ORDERS || 100)
let authenticated = false
let loggedFailure = false

function csrf() {
  const response = http.get(`${baseUrl}/api/auth/csrf`)
  check(response, { 'csrf bootstrap succeeds': (result) => result.status === 200 })
  return response.json()
}

function mutation(path, body, token, extraHeaders = {}) {
  return http.post(`${baseUrl}${path}`, JSON.stringify(body), {
    headers: { 'Content-Type': 'application/json', [token.headerName]: token.token, ...extraHeaders },
  })
}

export function setup() {
  const email = `analytics-benchmark-${Date.now()}@example.com`
  let token = csrf()
  const registered = mutation('/api/auth/register', {
    email,
    password,
    displayName: 'Analytics Benchmark',
    storeName: 'Analytics Benchmark Store',
    currencyCode: 'USD',
    timeZone: 'America/New_York',
  }, token)
  check(registered, { 'benchmark tenant created': (result) => result.status === 201 })

  token = csrf()
  const product = mutation('/api/products', {
    sku: 'BENCH-001', name: 'Benchmark Product', category: 'Benchmark',
    price: 25, unitCost: 10, initialQuantity: Math.max(1000, datasetOrders * 2), reorderPoint: 20,
  }, token)
  check(product, { 'benchmark product created': (result) => result.status === 201 })
  const productId = product.json('id')

  token = csrf()
  for (let index = 0; index < datasetOrders; index += 1) {
    const order = mutation('/api/orders', { items: [{ productId, quantity: 2 }] }, token, {
      'Idempotency-Key': `benchmark-${Date.now()}-${index}`,
    })
    if (!check(order, { 'benchmark order created': (result) => result.status === 201 })) {
      throw new Error(`Unable to create benchmark order ${index + 1}`)
    }
    const payment = mutation(`/api/orders/${order.json('id')}/payment`, { outcome: 'APPROVED' }, token)
    if (!check(payment, { 'benchmark order paid': (result) => result.status === 200 })) {
      throw new Error(`Unable to pay benchmark order ${index + 1}`)
    }
  }
  return { email }
}

function authenticate(email) {
  const token = csrf()
  const response = mutation('/api/auth/login', { email, password }, token)
  check(response, { 'benchmark login succeeds': (result) => result.status === 200 })
  authenticated = response.status === 200
}

export default function (data) {
  if (!authenticated) authenticate(data.email)
  group('analytics reads', () => {
    const endpoints = [
      '/api/analytics/overview?preset=LAST_30_DAYS',
      '/api/analytics/revenue/trend?preset=LAST_30_DAYS',
      '/api/analytics/products/top?preset=LAST_30_DAYS&limit=10',
      '/api/analytics/categories/performance?preset=LAST_30_DAYS',
      '/api/analytics/inventory/risk?preset=LAST_30_DAYS&limit=20',
    ]
    endpoints.forEach((endpoint) => {
      const response = http.get(`${baseUrl}${endpoint}`, { tags: { scope: 'analytics' } })
      if (response.status !== 200 && !loggedFailure) {
        console.warn(`analytics request failed status=${response.status} endpoint=${endpoint} body=${response.body}`)
        loggedFailure = true
      }
      check(response, { [`${endpoint} returns 200`]: (result) => result.status === 200 })
    })
  })
}
