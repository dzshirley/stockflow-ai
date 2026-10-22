export function formatMoney(value: number, currency = 'USD') {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency, minimumFractionDigits: 2 }).format(value)
}

export function formatDate(value: string) {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

export function shortId(value: string) { return value.slice(0, 8).toUpperCase() }

export function newIdempotencyKey() {
  if (typeof crypto.randomUUID === 'function') return `web-${crypto.randomUUID()}`
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  const randomHex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')
  return `web-${randomHex}`
}
