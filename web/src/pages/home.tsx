import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { SearchBar } from '@/features/search/search-bar'
import { SkillCard } from '@/features/skill/skill-card'
import { RecommendationCard } from '@/features/recommendation/recommendation-card'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { QuickStartSection } from '@/shared/components/quick-start'
import { useRecommendations, useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { normalizeSearchQuery } from '@/shared/lib/search-query'
import { buildHomeSearchParams, HOME_DISCOVERY_SOURCE } from '@/shared/lib/home-discovery'
import { Button } from '@/shared/ui/button'

export function HomePage() {
  const { t } = useTranslation()
  const navigate = useNavigate()

  const { data: recommendations, isLoading: isLoadingRecommendations } = useRecommendations({
    size: 6,
  })

  const { data: popularSkills, isLoading: isLoadingPopular } = useSearchSkills({
    source: HOME_DISCOVERY_SOURCE,
    sort: 'downloads',
    size: 6,
  })

  const { data: latestSkills, isLoading: isLoadingLatest } = useSearchSkills({
    source: HOME_DISCOVERY_SOURCE,
    sort: 'newest',
    size: 6,
  })

  const handleSearch = (query: string) => {
    navigate({ to: '/search', search: buildHomeSearchParams('relevance', normalizeSearchQuery(query)) })
  }

  const handleSkillClick = (namespace: string, slug: string) => {
    navigate({ to: `/space/${namespace}/${slug}` })
  }

  return (
    <div className="space-y-20">
      {/* Hero Section */}
      <div className="text-center space-y-8 py-16 animate-fade-up">
        <div className="space-y-4">
          <h1 className="text-6xl md:text-7xl lg:text-8xl font-bold text-brand-gradient leading-tight">
            SkillHub
          </h1>
          <p className="text-xl md:text-2xl max-w-2xl mx-auto" style={{ color: 'hsl(var(--text-secondary))' }}>
            {t('home.subtitle')}
          </p>
          <p className="text-base max-w-xl mx-auto" style={{ color: 'hsl(var(--muted-foreground))' }}>
            {t('home.description')}
          </p>
        </div>

        <div className="max-w-2xl mx-auto animate-fade-up delay-1">
          <SearchBar onSearch={handleSearch} />
        </div>

        <div className="flex items-center justify-center gap-4 animate-fade-up delay-2">
          <button
            className="px-8 py-3.5 rounded-xl text-base font-medium text-white bg-brand-gradient shadow-sm hover:opacity-95 transition-opacity"
            onClick={() => navigate({ to: '/search', search: buildHomeSearchParams('relevance') })}
          >
            {t('home.browseSkills')}
          </button>
          <button
            className="px-8 py-3.5 rounded-xl text-base font-medium border transition-colors"
            style={{ background: 'var(--bg-secondary-btn, #F7FAFC)', borderColor: 'hsl(var(--muted-foreground))', color: 'hsl(var(--muted-foreground))' }}
            onClick={() => navigate({ to: '/dashboard/publish' })}
          >
            {t('home.publishSkill')}
          </button>
        </div>
      </div>

      {/* Global Recommendations Section */}
      <section className="space-y-6 animate-fade-up">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-3xl font-bold tracking-tight mb-2" style={{ color: 'hsl(var(--foreground))' }}>
              {t('home.recommendationsTitle')}
            </h2>
            <p style={{ color: 'hsl(var(--text-secondary))' }}>{t('home.recommendationsDescription')}</p>
          </div>
          <Button
            variant="ghost"
            onClick={() => navigate({ to: '/search', search: { q: '', source: 'all', sort: 'downloads', page: 0, starredOnly: false } })}
          >
            {t('home.viewAll')}
          </Button>
        </div>
        {isLoadingRecommendations ? (
          <SkeletonList count={6} />
        ) : recommendations?.items.length ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            {recommendations.items.map((recommendation, idx) => (
              <div key={`${recommendation.namespace}/${recommendation.slug}`} className={`animate-fade-up delay-${Math.min(idx + 1, 6)}`}>
                <RecommendationCard
                  recommendation={recommendation}
                  onClick={() => recommendation.skill && handleSkillClick(recommendation.skill.namespace, recommendation.skill.slug)}
                />
              </div>
            ))}
          </div>
        ) : null}
      </section>

      {/* Popular Downloads Section */}
      <section className="space-y-6 animate-fade-up">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-3xl font-bold tracking-tight mb-2" style={{ color: 'hsl(var(--foreground))' }}>
              {t('home.popularTitle')}
            </h2>
            <p style={{ color: 'hsl(var(--text-secondary))' }}>{t('home.popularDescription')}</p>
          </div>
          <Button
            variant="ghost"
            onClick={() => navigate({ to: '/search', search: buildHomeSearchParams('downloads') })}
          >
            {t('home.viewAll')}
          </Button>
        </div>
        {isLoadingPopular ? (
          <SkeletonList count={6} />
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            {popularSkills?.items.map((skill, idx) => (
              <div key={skill.id} className={`animate-fade-up delay-${Math.min(idx + 1, 6)}`}>
                <SkillCard
                  skill={skill}
                  onClick={() => handleSkillClick(skill.namespace, skill.slug)}
                />
              </div>
            ))}
          </div>
        )}
      </section>

      {/* Latest Releases Section */}
      <section className="space-y-6 animate-fade-up">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-3xl font-bold tracking-tight mb-2" style={{ color: 'hsl(var(--foreground))' }}>
              {t('home.latestTitle')}
            </h2>
            <p style={{ color: 'hsl(var(--text-secondary))' }}>{t('home.latestDescription')}</p>
          </div>
          <Button
            variant="ghost"
            onClick={() => navigate({ to: '/search', search: buildHomeSearchParams('newest') })}
          >
            {t('home.viewAll')}
          </Button>
        </div>
        {isLoadingLatest ? (
          <SkeletonList count={6} />
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            {latestSkills?.items.map((skill, idx) => (
              <div key={skill.id} className={`animate-fade-up delay-${Math.min(idx + 1, 6)}`}>
                <SkillCard
                  skill={skill}
                  onClick={() => handleSkillClick(skill.namespace, skill.slug)}
                />
              </div>
            ))}
          </div>
        )}
      </section>

      {/* Quick Start Section */}
      <QuickStartSection ns="home" />
    </div>
  )
}
