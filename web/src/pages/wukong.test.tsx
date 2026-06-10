import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, params?: Record<string, string>) => params?.count ? `${key}:${params.count}` : key,
    }),
  }
})

vi.mock('@/shared/hooks/use-skill-queries', () => {
  const skill = {
    id: 1,
    namespace: 'global',
    slug: 'skillhub',
    displayName: 'SkillHub',
    summary: 'Connect Wukong to SkillHub',
    downloadCount: 1200,
    starCount: 8,
    ratingCount: 0,
    updatedAt: '2026-06-10T00:00:00Z',
    canSubmitPromotion: false,
    headlineVersion: {
      id: 11,
      version: '1.1.7',
      status: 'PUBLISHED',
    },
  }

  return {
    useSearchSkills: () => ({
      data: { items: [skill], total: 1, page: 0, size: 30 },
      isLoading: false,
      isFetching: false,
      refetch: vi.fn(),
    }),
  }
})

import { WukongPage } from './wukong'

describe('WukongPage', () => {
  it('renders a Wukong embedded skill list with install actions', () => {
    const html = renderToStaticMarkup(<WukongPage />)

    expect(html).toContain('wukong.title')
    expect(html).toContain('SkillHub')
    expect(html).toContain('global/skillhub')
    expect(html).toContain('v1.1.7')
    expect(html).toContain('wukong.install')
  })
})
