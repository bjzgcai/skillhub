import { describe, expect, it } from 'vitest'
import { getGuideContent, pickPrimaryMedia } from './recommendation-detail'

describe('recommendation detail guide content', () => {
  it('parses guide content from JSON string', () => {
    const json = JSON.stringify({
      intro: 'Test intro',
      sections: [{ title: 'Section 1', body: 'Body 1' }],
      media: [],
    })
    expect(getGuideContent(json)?.intro).toBe('Test intro')
    expect(getGuideContent(json)?.sections).toHaveLength(1)
  })

  it('returns null for empty or invalid JSON', () => {
    expect(getGuideContent(undefined)).toBeNull()
    expect(getGuideContent('')).toBeNull()
    expect(getGuideContent('not json')).toBeNull()
  })

  it('selects only linked media and prefers video over ppt', () => {
    expect(
      pickPrimaryMedia([
        { type: 'ppt', title: 'PPT', description: 'No link' },
        { type: 'video', title: 'Video', description: 'No link' },
      ]),
    ).toBeNull()

    expect(
      pickPrimaryMedia([
        { type: 'ppt', title: 'PPT', description: 'Slides', href: 'https://example.com/slides' },
        { type: 'video', title: 'Video', description: 'Clip', embedUrl: 'https://example.com/video' },
      ])?.title,
    ).toBe('Video')

    expect(
      pickPrimaryMedia([
        { type: 'ppt', title: 'PPT', description: 'Slides', href: 'https://example.com/slides' },
      ])?.title,
    ).toBe('PPT')
  })
})
