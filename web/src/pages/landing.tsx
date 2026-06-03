import { Link, useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { normalizeSearchQuery } from '@/shared/lib/search-query'
import { Search as SearchIcon } from 'lucide-react'
import { LandingQuickStartSection } from '@/shared/components/landing-quick-start'
import { SkillCard } from '@/features/skill/skill-card'
import { RecommendationCard } from '@/features/recommendation/recommendation-card'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { useRecommendations, useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { useInView } from '@/shared/hooks/use-in-view'
import { buildHomeSearchParams, HOME_DISCOVERY_SOURCE } from '@/shared/lib/home-discovery'
import { Button } from '@/shared/ui/button'

/**
 * Marketing-style landing page for unauthenticated and first-time visitors.
 *
 * The page mixes static positioning content with live skill queries so popular and latest skills
 * stay aligned with the current registry state.
 */
export function LandingPage() {
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

  const handleSkillClick = (namespace: string, slug: string) => {
    navigate({ to: `/space/${namespace}/${slug}` })
  }

  const heroView = useInView()
  const quickStartView = useInView()
  const popularView = useInView()
  const latestView = useInView()

  const handleSearch = (query: string) => {
    const normalized = normalizeSearchQuery(query)
    navigate({
      to: '/search',
      search: buildHomeSearchParams('relevance', normalized),
    })
  }

  return (
    <>
      {/* Hero Section */}
      <main ref={heroView.ref} className={`relative z-10 flex flex-col items-center px-4 pb-8 pt-10 md:pb-10 md:pt-14 scroll-fade-up${heroView.inView ? ' in-view' : ''}`}>
        <h1
          className="text-2xl md:text-3xl font-semibold tracking-tight text-center mb-7"
          style={{ color: 'hsl(var(--foreground))' }}
        >
          {t('landing.hero.title')}
        </h1>

        {/* Search box */}
        <div className="w-full max-w-2xl mb-5">
          <div
            className="flex items-center bg-white rounded-xl border shadow-sm px-5 py-3.5"
            style={{ borderColor: 'hsl(var(--border))' }}
          >
            <SearchIcon className="w-5 h-5 flex-shrink-0 mr-3" style={{ color: 'hsl(var(--text-placeholder))' }} strokeWidth={1.5} />
            <input
              type="text"
              placeholder={t('landing.hero.searchPlaceholder')}
              className="hero-input flex-1 bg-transparent outline-none text-base"
              style={{ color: 'hsl(var(--foreground))' }}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  handleSearch((e.target as HTMLInputElement).value)
                }
              }}
            />
          </div>
        </div>

        {/* CTA buttons */}
        <div className="flex flex-wrap justify-center gap-4 mb-6">
          <Link
            to="/search"
            search={buildHomeSearchParams('relevance')}
            className="px-8 py-3.5 rounded-xl text-base font-medium text-white bg-brand-gradient shadow-sm hover:opacity-95 transition-opacity"
          >
            {t('landing.hero.exploreSkills')}
          </Link>
          <Link
            to="/dashboard/publish"
            className="px-8 py-3.5 rounded-xl text-base font-medium border transition-colors"
            style={{
              background: 'hsl(var(--secondary))',
              borderColor: 'hsl(var(--muted-foreground))',
              color: 'hsl(var(--muted-foreground))',
            }}
          >
            {t('landing.hero.publishSkill', { defaultValue: '开始构建' })}
          </Link>
        </div>

      </main>

      {/* Global Recommendations Section */}
      <section className="relative z-10 w-full px-6 py-10 md:py-12 scroll-fade-up in-view" style={{ background: 'var(--bg-page, hsl(var(--background)))' }}>
        <div className="max-w-6xl mx-auto space-y-6">
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
        </div>
      </section>

      {/* Popular Downloads Section */}
      <section ref={popularView.ref} className={`relative z-10 w-full px-6 py-10 md:py-12 scroll-fade-up${popularView.inView ? ' in-view' : ''}`} style={{ background: 'var(--bg-page, hsl(var(--background)))' }}>
        <div className="max-w-6xl mx-auto space-y-6">
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
        </div>
      </section>

      {/* Latest Releases Section */}
      <section ref={latestView.ref} className={`relative z-10 w-full px-6 py-10 md:py-12 scroll-fade-up${latestView.inView ? ' in-view' : ''}`} style={{ background: 'var(--bg-page, hsl(var(--background)))' }}>
        <div className="max-w-6xl mx-auto space-y-6">
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
        </div>
      </section>

      {/* Quick Start */}
      <div ref={quickStartView.ref} className={`scroll-fade-up${quickStartView.inView ? ' in-view' : ''}`}>
        <LandingQuickStartSection />
      </div>
    </>
  )
}
