import { afterEach, describe, expect, it, vi } from 'vitest'
import { newIdempotencyKey, newUuid } from './format'

describe('newIdempotencyKey', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('uses a cryptographically random fallback outside secure contexts', () => {
    vi.stubGlobal('crypto', {
      getRandomValues: (bytes: Uint8Array) => {
        bytes.fill(10)
        return bytes
      },
    })
    expect(newIdempotencyKey()).toBe('web-0a0a0a0a-0a0a-4a0a-8a0a-0a0a0a0a0a0a')
    expect(newUuid()).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)
  })
})
