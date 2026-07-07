import { describe, expect, it } from 'vitest'
import { normalizeForm, toInstant } from './weekly-recommendation'

describe('AdminWeeklyRecommendationPage helpers', () => {
  it('converts datetime-local values to ISO instants', () => {
    expect(toInstant('2026-06-22T10:30')).toBe('2026-06-22T10:30:00.000Z')
    expect(toInstant('')).toBeUndefined()
    expect(toInstant('not-a-date')).toBeUndefined()
  })

  it('normalizes optional fields without manufacturing invalid values', () => {
    expect(
      normalizeForm({
        namespace: ' global ',
        slug: ' skill-vetter ',
        title: ' ',
        summary: ' Summary ',
        reason: ' ',
        backgroundImageUrl: ' /recommendation-banners/weekly/banner.jpg ',
        guideContent: '',
        priority: '',
        startAt: 'bad-date',
        endAt: '',
      }),
    ).toEqual({
      namespace: 'global',
      slug: 'skill-vetter',
      title: undefined,
      summary: 'Summary',
      reason: undefined,
      backgroundImageUrl: '/recommendation-banners/weekly/banner.jpg',
      guideContent: undefined,
      priority: undefined,
      startAt: undefined,
      endAt: undefined,
    })
  })
})
