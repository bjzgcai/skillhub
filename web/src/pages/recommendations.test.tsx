import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
  useSearch: () => ({ page: 0 }),
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
    }),
  }
})

vi.mock('@/features/recommendation/recommendation-card', () => ({
  RecommendationCard: () => <article>recommendation-card</article>,
}))

vi.mock('@/shared/components/empty-state', () => ({
  EmptyState: ({ title }: { title: string }) => <section>{title}</section>,
}))

vi.mock('@/shared/components/pagination', () => ({
  Pagination: () => <nav>pagination</nav>,
}))

vi.mock('@/shared/components/skeleton-loader', () => ({
  SkeletonList: () => <div>skeleton-list</div>,
}))

const useRecommendations = vi.fn()
const useCurrentWeeklySkill = vi.fn()

vi.mock('@/shared/hooks/use-label-queries', () => ({
  useVisibleLabels: () => ({
    data: [{ slug: 'domain-ai-intelligence', displayName: 'AI 智能' }],
  }),
}))

vi.mock('@/shared/hooks/use-skill-queries', () => ({
  useCurrentWeeklySkill: (...args: unknown[]) => useCurrentWeeklySkill(...args),
  useRecommendations: (...args: unknown[]) => useRecommendations(...args),
}))

import { RecommendationsPage } from './recommendations'

describe('RecommendationsPage', () => {
  it('renders empty state when no recommendations exist', () => {
    useRecommendations.mockReturnValue({ data: { items: [], total: 0, page: 0, size: 12 }, isLoading: false })
    useCurrentWeeklySkill.mockReturnValue({ data: null, isLoading: false })

    const html = renderToStaticMarkup(<RecommendationsPage />)

    expect(html).toContain('recommendations.title')
    expect(html).toContain('recommendations.emptyTitle')
  })

  it('renders recommendation cards for items with skills', () => {
    useRecommendations.mockReturnValue({
      data: {
        items: [
          {
            id: 1,
            title: 'Demo',
            skill: { namespace: 'global', slug: 'demo-skill' },
          },
        ],
        total: 1,
        page: 0,
        size: 12,
      },
      isLoading: false,
    })
    useCurrentWeeklySkill.mockReturnValue({ data: null, isLoading: false })

    const html = renderToStaticMarkup(<RecommendationsPage />)

    expect(html).toContain('recommendation-card')
    expect(html).toContain('pagination')
  })

  it('hides the weekly skill from regular recommendation cards', () => {
    useRecommendations.mockReturnValue({
      data: {
        items: [
          {
            id: 1,
            title: 'Weekly duplicate',
            skill: { namespace: 'global', slug: 'skill-vetter' },
          },
        ],
        total: 1,
        page: 0,
        size: 12,
      },
      isLoading: false,
    })
    useCurrentWeeklySkill.mockReturnValue({
      data: { title: 'Weekly', skill: { namespace: 'global', slug: 'skill-vetter' } },
      isLoading: false,
    })

    const html = renderToStaticMarkup(<RecommendationsPage />)

    expect(html).toContain('每周一技')
    expect(html).not.toContain('recommendation-card')
  })
})
