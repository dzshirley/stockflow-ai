import { expect, test } from '@playwright/test'
import type { APIRequestContext } from '@playwright/test'

async function mutate(
  request: APIRequestContext,
  path: string,
  body: unknown,
  headers: Record<string, string> = {},
) {
  const csrfResponse = await request.get('/api/auth/csrf')
  expect(csrfResponse.ok()).toBeTruthy()
  const csrf = await csrfResponse.json() as { headerName: string; token: string }
  return request.post(path, {
    data: body,
    headers: { [csrf.headerName]: csrf.token, ...headers },
  })
}

test('registers a store and completes the protected commerce workflow', async ({ page }) => {
  const stamp = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  const email = `e2e-${stamp}@example.com`
  const sku = `E2E-${stamp}`.slice(0, 80)

  await page.goto('/inventory')
  await expect(page).toHaveURL(/\/login/)

  await page.getByRole('link', { name: 'Create an account' }).click()
  await page.getByLabel('Display name').fill('E2E Owner')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password').fill('e2e-secure-password')
  await page.getByLabel('Store name').fill(`E2E Store ${stamp}`)
  await page.getByRole('button', { name: 'Create store' }).click()
  await expect(page.getByRole('heading', { name: /Good to see you/ })).toBeVisible()

  await page.getByRole('link', { name: 'Products' }).click()
  await page.locator('.page-actions').getByRole('button', { name: 'Add product' }).click()
  const productDialog = page.getByRole('dialog', { name: 'Add product' })
  await productDialog.getByLabel('SKU').fill(sku)
  await productDialog.getByLabel('Category').fill('Albums')
  await productDialog.getByLabel('Product name').fill('E2E Limited Album')
  await productDialog.getByLabel('Retail price').fill('29.99')
  await productDialog.getByLabel('Unit cost').fill('12.50')
  await productDialog.getByLabel('Initial quantity').fill('5')
  await productDialog.getByLabel('Reorder point').fill('2')
  await productDialog.getByRole('button', { name: 'Add product' }).click()
  await expect(page.getByRole('link', { name: /E2E Limited Album/ })).toBeVisible()

  await page.getByRole('link', { name: 'Inventory' }).click()
  await expect(page.getByText(sku)).toBeVisible()
  await page.getByRole('button', { name: 'Adjust' }).click()
  await page.getByLabel('Available quantity change').fill('2')
  await page.getByLabel('Reason').fill('E2E shipment receipt')
  await page.getByRole('button', { name: 'Apply adjustment' }).click()
  await expect(page.locator('article').filter({ hasText: sku }).getByText('7', { exact: true })).toBeVisible()

  await page.getByRole('link', { name: 'Orders' }).click()
  await page.locator('.page-actions').getByRole('link', { name: 'Create order' }).click()
  await page.getByLabel('Product').selectOption({ label: `E2E Limited Album · ${sku.toUpperCase()}` })
  await page.getByLabel('Quantity').fill('2')
  await page.getByRole('button', { name: 'Create order' }).click()
  await expect(page.locator('.page-actions .status-badge')).toHaveText('PENDING PAYMENT')
  await page.getByRole('button', { name: 'Approve payment' }).click()
  await expect(page.locator('.page-actions .status-badge')).toHaveText('PAID')

  await page.getByRole('link', { name: 'Analytics' }).click()
  const kpis = page.getByRole('region', { name: 'Sales key performance indicators' })
  const revenueKpi = kpis.getByText('Revenue', { exact: true }).locator('..')
  await expect(revenueKpi).toContainText('$59.98')
  await expect(kpis.getByText('Paid orders', { exact: true }).locator('..')).toContainText('1')
  await expect(kpis.getByText('Units sold', { exact: true }).locator('..')).toContainText('2')
  await expect(page.getByRole('link', { name: /E2E Limited Album/ }).first()).toBeVisible()

  await page.getByRole('link', { name: 'AI assistant' }).click()
  await page.getByLabel('Ask about your business data').fill('Show revenue for the last 30 days')
  await page.getByRole('button', { name: 'Analyze' }).click()
  await expect(page.getByText(/Verified revenue was/)).toBeVisible()
  await expect(page.getByText('Revenue', { exact: true }).locator('..')).toContainText('$59.98')
  await page.getByLabel('Ask about your business data').fill('Visualize that daily')
  await page.getByRole('button', { name: 'Analyze' }).click()
  await expect(page.getByRole('heading', { name: 'Revenue trend' })).toBeVisible()

  await page.getByRole('link', { name: 'Business insights' }).click()
  await page.getByRole('button', { name: 'Why did revenue change last month?' }).click()
  await page.getByRole('button', { name: 'Generate report' }).click()
  await expect(page.getByRole('heading', { name: 'Verified findings' })).toBeVisible()
  await expect(page.getByRole('region', { name: 'Verified metrics' })).toContainText('$59.98')
  await expect(page.getByText('Revenue change decomposition')).toBeVisible()
  await expect(page.getByText(/Advisory only; no operation is executed/i)).toBeVisible()

  await page.getByLabel('Log out').click()
  await expect(page).toHaveURL(/\/login/)
})

test('creates, approves, and confirms receipt of an evidence-backed restock proposal', async ({ page }) => {
  const stamp = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  const sku = `RESTOCK-${stamp}`.slice(0, 80)

  await page.goto('/register')
  await page.getByLabel('Display name').fill('Operations Owner')
  await page.getByLabel('Email').fill(`operations-${stamp}@example.com`)
  await page.getByLabel('Password').fill('operations-secure-password')
  await page.getByLabel('Store name').fill(`Operations Store ${stamp}`)
  await page.getByRole('button', { name: 'Create store' }).click()

  await page.getByRole('link', { name: 'Products' }).click()
  await page.locator('.page-actions').getByRole('button', { name: 'Add product' }).click()
  const dialog = page.getByRole('dialog', { name: 'Add product' })
  await dialog.getByLabel('SKU').fill(sku)
  await dialog.getByLabel('Category').fill('Operations')
  await dialog.getByLabel('Product name').fill('At Risk Album')
  await dialog.getByLabel('Retail price').fill('24.00')
  await dialog.getByLabel('Unit cost').fill('10.00')
  await dialog.getByLabel('Initial quantity').fill('0')
  await dialog.getByLabel('Reorder point').fill('5')
  await dialog.getByRole('button', { name: 'Add product' }).click()

  await page.getByRole('link', { name: 'Business insights' }).click()
  await page.getByRole('button', { name: 'Which high-selling products are currently at risk of running out?' }).click()
  await page.getByLabel('Analysis type').selectOption('INVENTORY_RISK')
  await page.getByRole('button', { name: 'Generate report' }).click()
  await expect(page.getByRole('heading', { name: 'Recommendations' })).toBeVisible()

  await page.getByRole('link', { name: 'Restock operations' }).click()
  const reportValue = await page.getByLabel('Source report').locator('option').filter({ hasText: 'Inventory risk' }).getAttribute('value')
  await page.getByLabel('Source report').selectOption(reportValue!)
  await expect(page.getByLabel('Evidence-backed recommendation')).toHaveValue(/.+/)
  await page.getByRole('button', { name: 'Generate proposal' }).click()
  await expect(page.getByRole('heading', { name: /Receive 6 units of At Risk Album/ })).toBeVisible()

  await page.getByRole('button', { name: 'Approve proposal' }).click()
  await expect(page.getByText(/Approval is not a purchase order/)).toBeVisible()
  await page.getByLabel('I confirm 6 units were physically received.').check()
  await page.getByRole('button', { name: 'Record received inventory' }).click()
  await expect(page.locator('.proposal-hero').getByText('EXECUTED')).toBeVisible()
  await expect(page.getByText(/Receipt recorded at/)).toBeVisible()

  await page.getByRole('link', { name: 'Inventory' }).click()
  await expect(page.locator('article').filter({ hasText: sku }).getByText('6', { exact: true })).toBeVisible()
})

test('rejects duplicate, insufficient-stock, invalid-transition, and cross-store operations', async ({ page, browser }) => {
  const stamp = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  const registration = await mutate(page.request, '/api/auth/register', {
    email: `negative-${stamp}@example.com`,
    password: 'negative-secure-password',
    displayName: 'Negative Flow Owner',
    storeName: `Negative Flow ${stamp}`,
    currencyCode: 'USD',
    timeZone: 'America/New_York',
  })
  expect(registration.status()).toBe(201)

  const createdProduct = await mutate(page.request, '/api/products', {
    sku: `NEG-${stamp}`.slice(0, 80),
    name: 'Single Inventory Item',
    category: 'Reliability',
    price: 15,
    unitCost: 5,
    initialQuantity: 1,
    reorderPoint: 0,
  })
  expect(createdProduct.status()).toBe(201)
  const product = await createdProduct.json() as { id: string }
  const orderPayload = { items: [{ productId: product.id, quantity: 1 }] }
  const idempotencyKey = `negative-order-${stamp}`

  const firstOrder = await mutate(page.request, '/api/orders', orderPayload, {
    'Idempotency-Key': idempotencyKey,
  })
  expect(firstOrder.status()).toBe(201)
  const firstBody = await firstOrder.json() as { id: string }
  const replay = await mutate(page.request, '/api/orders', orderPayload, {
    'Idempotency-Key': idempotencyKey,
  })
  expect(replay.status()).toBe(201)
  expect((await replay.json() as { id: string }).id).toBe(firstBody.id)

  const changedReplay = await mutate(page.request, '/api/orders', {
    items: [{ productId: product.id, quantity: 2 }],
  }, { 'Idempotency-Key': idempotencyKey })
  expect(changedReplay.status()).toBe(409)
  expect((await changedReplay.json() as { code: string }).code).toBe('IDEMPOTENCY_KEY_REUSED')

  const insufficient = await mutate(page.request, '/api/orders', orderPayload, {
    'Idempotency-Key': `insufficient-${stamp}`,
  })
  expect(insufficient.status()).toBe(409)
  expect((await insufficient.json() as { code: string }).code).toBe('INSUFFICIENT_INVENTORY')

  const paid = await mutate(page.request, `/api/orders/${firstBody.id}/payment`, { outcome: 'APPROVED' })
  expect(paid.status()).toBe(200)
  const invalidCancellation = await mutate(page.request, `/api/orders/${firstBody.id}/cancel`, {})
  expect(invalidCancellation.status()).toBe(409)
  expect((await invalidCancellation.json() as { code: string }).code).toBe('INVALID_ORDER_TRANSITION')

  const otherContext = await browser.newContext({
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
  })
  try {
    const otherRegistration = await mutate(otherContext.request, '/api/auth/register', {
      email: `other-${stamp}@example.com`,
      password: 'other-secure-password',
      displayName: 'Other Store Owner',
      storeName: `Other Store ${stamp}`,
      currencyCode: 'USD',
      timeZone: 'America/New_York',
    })
    expect(otherRegistration.status()).toBe(201)
    expect((await otherContext.request.get(`/api/orders/${firstBody.id}`)).status()).toBe(404)
  } finally {
    await otherContext.close()
  }
})
