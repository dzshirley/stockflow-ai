import { describe, expect, it, vi } from 'vitest'
import { newIdempotencyKey } from './format'

describe('newIdempotencyKey', () => {
  it('uses a cryptographically random fallback outside secure contexts', () => {
    vi.stubGlobal('crypto', {
      getRandomValues: (bytes: Uint8Array) => {
        bytes.fill(10)
        return bytes
      },
    })
    expect(newIdempotencyKey()).toBe(`web-${'0a'.repeat(16)}`)
  })
})
