import { describe, expect, it } from 'vitest'
import { getGuideContent, pickPrimaryMedia } from './recommendation-detail'

describe('recommendation detail guide content', () => {
  it('returns the curated guide only for skill-vetter', () => {
    expect(getGuideContent('skill-vetter')?.sections.map((section) => section.title)).toEqual([
      '安装前具体怎么用',
      'Skill Vetter 重点检查什么',
      '通过、谨慎、阻断怎么判断',
    ])
    expect(getGuideContent('demo-skill')).toBeNull()
    expect(getGuideContent(undefined)).toBeNull()
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
