import http from 'k6/http'
import exec from 'k6/execution'
import { check, fail, sleep } from 'k6'

const duration = __ENV.DURATION || '15s'
const vus = Number(__ENV.VUS || 5)

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    api_load: {
      executor: 'constant-vus',
      exec: 'apiLoad',
      vus,
      duration,
    },
    receipt_confirmation: {
      executor: 'shared-iterations',
      exec: 'confirmReceipt',
      vus: 1,
      iterations: 1,
      startTime: '2s',
    },
  },
  noCookiesReset: true,
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
    'http_req_duration{scope:auth}': ['p(95)<750'],
    'http_req_duration{scope:catalog-read}': ['p(95)<750'],
    'http_req_duration{scope:inventory-read}': ['p(95)<750'],
    'http_req_duration{scope:analytics}': ['p(95)<750'],
    'http_req_duration{scope:commerce-write}': ['p(95)<1000'],
    'http_req_duration{scope:report-read}': ['p(95)<750'],
    'http_req_duration{scope:operations-read}': ['p(95)<750'],
    'http_req_duration{scope:receipt-confirmation}': ['p(95)<1000'],
  },
}

const baseUrl = __ENV.BASE_URL || 'http://host.docker.internal:5173'
const password = 'phase8-benchmark-password'
const datasetOrders = Number(__ENV.DATASET_ORDERS || 100)
let authenticatedEmail = null

function csrf() {
  const response = http.get(`${baseUrl}/api/auth/csrf`, { tags: { scope: 'csrf' } })
  if (!check(response, { 'csrf bootstrap succeeds': (result) => result.status === 200 })) {
    fail(`CSRF bootstrap failed with ${response.status}`)
  }
  return response.json()
}

function post(path, body, token, headers = {}, tags = {}) {
  return http.post(`${baseUrl}${path}`, JSON.stringify(body), {
    headers: { 'Content-Type': 'application/json', [token.headerName]: token.token, ...headers },
    tags,
  })
}

function setupPost(path, body, headers = {}) {
  return post(path, body, csrf(), headers, { scope: 'setup' })
}

export function setup() {
  const suffix = Date.now()
  const email = `phase8-benchmark-${suffix}@example.com`
  const registered = setupPost('/api/auth/register', {
    email,
    password,
    displayName: 'Phase 8 Benchmark',
    storeName: 'Phase 8 Benchmark Store',
    currencyCode: 'USD',
    timeZone: 'America/New_York',
  })
  if (registered.status !== 201) fail(`Tenant setup failed with ${registered.status}: ${registered.body}`)

  const product = setupPost('/api/products', {
    sku: `LOAD-${suffix}`,
    name: 'Load Test Product',
    category: 'Benchmark',
    price: 25,
    unitCost: 10,
    initialQuantity: Math.max(100000, datasetOrders * 4),
    reorderPoint: 20,
  })
  if (product.status !== 201) fail(`Load product setup failed: ${product.body}`)
  const productId = product.json('id')

  const riskProduct = setupPost('/api/products', {
    sku: `RISK-${suffix}`,
    name: 'Receipt Benchmark Product',
    category: 'Benchmark',
    price: 50,
    unitCost: 20,
    initialQuantity: 1,
    reorderPoint: 10,
  })
  if (riskProduct.status !== 201) fail(`Risk product setup failed: ${riskProduct.body}`)

  for (let index = 0; index < datasetOrders; index += 1) {
    const order = setupPost('/api/orders', { items: [{ productId, quantity: 2 }] }, {
      'Idempotency-Key': `phase8-seed-${suffix}-${index}`,
    })
    if (order.status !== 201) fail(`Seed order ${index + 1} failed: ${order.body}`)
    const payment = setupPost(`/api/orders/${order.json('id')}/payment`, { outcome: 'APPROVED' })
    if (payment.status !== 200) fail(`Seed payment ${index + 1} failed: ${payment.body}`)
  }

  const reportResponse = setupPost('/api/business-analysis/reports', {
    question: 'Which products currently need inventory attention?',
    analysisType: 'INVENTORY_RISK',
    preset: 'LAST_30_DAYS',
    scenarios: [],
  }, { 'Idempotency-Key': `phase8-report-${suffix}` })
  if (reportResponse.status !== 200 && reportResponse.status !== 201) {
    fail(`Report setup failed with ${reportResponse.status}: ${reportResponse.body}`)
  }
  const report = reportResponse.json('report')
  const recommendation = report.recommendations.find((item) =>
    item.title.toLowerCase().includes('inventory risk'))
  if (!recommendation) fail('Report did not contain an inventory-risk recommendation')

  const proposalsResponse = setupPost('/api/restock-proposals/generate', {
    question: 'Prepare a safe replenishment proposal for each verified at-risk product.',
    sourceReportId: report.reportId,
    recommendationId: recommendation.id,
  }, { 'Idempotency-Key': `phase8-proposal-${suffix}` })
  if (proposalsResponse.status !== 200) fail(`Proposal setup failed: ${proposalsResponse.body}`)
  const proposal = proposalsResponse.json('items.0')
  const approved = setupPost(`/api/restock-proposals/${proposal.id}/approve`, {
    expectedVersion: proposal.version,
  })
  if (approved.status !== 200) fail(`Proposal approval failed: ${approved.body}`)

  return {
    email,
    productId,
    reportId: report.reportId,
    proposalId: approved.json('id'),
    proposalVersion: approved.json('version'),
  }
}

function authenticate(email) {
  if (authenticatedEmail === email) return
  const response = post('/api/auth/login', { email, password }, csrf(), {}, { scope: 'auth' })
  if (!check(response, { 'benchmark login succeeds': (result) => result.status === 200 })) {
    fail(`Authentication failed with ${response.status}: ${response.body}`)
  }
  authenticatedEmail = email
}

function expectStatus(response, status, label) {
  check(response, { [label]: (result) => result.status === status })
  if (response.status !== status) fail(`${label}: ${response.status} ${response.body}`)
}

export function apiLoad(data) {
  authenticate(data.email)
  const sequence = `${__VU}-${exec.scenario.iterationInTest}-${Date.now()}`

  expectStatus(http.get(`${baseUrl}/api/products?page=0&size=20`, { tags: { scope: 'catalog-read' } }), 200,
    'product list succeeds')
  expectStatus(http.get(`${baseUrl}/api/inventory/${data.productId}`, { tags: { scope: 'inventory-read' } }), 200,
    'inventory read succeeds')
  expectStatus(http.get(`${baseUrl}/api/analytics/overview?preset=LAST_30_DAYS`, { tags: { scope: 'analytics' } }), 200,
    'analytics overview succeeds')
  expectStatus(http.get(`${baseUrl}/api/analytics/revenue/trend?preset=LAST_30_DAYS`, { tags: { scope: 'analytics' } }), 200,
    'analytics trend succeeds')
  expectStatus(http.get(`${baseUrl}/api/business-analysis/reports/${data.reportId}`, { tags: { scope: 'report-read' } }), 200,
    'report retrieval succeeds')
  expectStatus(http.get(`${baseUrl}/api/restock-proposals?limit=20`, { tags: { scope: 'operations-read' } }), 200,
    'proposal list succeeds')

  const order = post('/api/orders', { items: [{ productId: data.productId, quantity: 1 }] }, csrf(), {
    'Idempotency-Key': `phase8-load-${sequence}`,
  }, { scope: 'commerce-write' })
  expectStatus(order, 201, 'order creation succeeds')
  const cancellation = post(`/api/orders/${order.json('id')}/cancel`, {}, csrf(), {}, {
    scope: 'commerce-write',
  })
  expectStatus(cancellation, 200, 'order cancellation succeeds')
  sleep(0.1)
}

export function confirmReceipt(data) {
  authenticate(data.email)
  const response = post(`/api/restock-proposals/${data.proposalId}/execute`, {
    received: true,
    expectedVersion: data.proposalVersion,
  }, csrf(), { 'Idempotency-Key': data.proposalId }, {
    scope: 'receipt-confirmation',
  })
  expectStatus(response, 200, 'receipt confirmation succeeds')
}
