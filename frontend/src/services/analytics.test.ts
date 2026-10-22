import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getAnalyticsOverview, getRevenueTrend } from './analytics'

describe('analytics service', () => {
  beforeEach(() => vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
    ok: true,
    status: 200,
    json: async () => ({ period: {}, metrics: {}, points: [] }),
  })))

  it('serializes custom half-open date ranges and comparison options', async () => {
    await getAnalyticsOverview({
      preset: 'CUSTOM',
      startDate: '2026-09-01',
      endDateExclusive: '2026-10-01',
      comparePreviousPeriod: false,
    })

    expect(fetch).toHaveBeenCalledWith(
      '/api/analytics/overview?preset=CUSTOM&startDate=2026-09-01&endDateExclusive=2026-10-01&comparePreviousPeriod=false',
      expect.objectContaining({ credentials: 'include' }),
    )
  })

  it('adds only allow-listed trend intervals', async () => {
    await getRevenueTrend({ preset: 'LAST_90_DAYS' }, 'WEEK')

    expect(fetch).toHaveBeenCalledWith(
      '/api/analytics/revenue/trend?preset=LAST_90_DAYS&interval=WEEK',
      expect.any(Object),
    )
  })
})
