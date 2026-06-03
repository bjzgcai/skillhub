import { useNavigate, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { RecommendationCard } from '@/features/recommendation/recommendation-card'
import { EmptyState } from '@/shared/components/empty-state'
import { Pagination } from '@/shared/components/pagination'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { useVisibleLabels } from '@/shared/hooks/use-label-queries'
import { useRecommendations } from '@/shared/hooks/use-skill-queries'
import { Button } from '@/shared/ui/button'
import { APP_SHELL_PAGE_CLASS_NAME } from '@/app/page-shell-style'

const PAGE_SIZE = 12
const FETCH_SIZE = 100

export function RecommendationsPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const search = useSearch({ from: '/recommendations' })
  const page = search.page ?? 0
  const selectedLabel = search.label
  const { data, isLoading } = useRecommendations({ page: 0, size: FETCH_SIZE })
  const { data: labels } = useVisibleLabels()
  const allRecommendations = data?.items.filter((item) => item.skill) ?? []
  const selectedLabelItem = labels?.find((label) => label.slug === selectedLabel)
  const filteredRecommendations = selectedLabelItem
    ? allRecommendations.filter((item) => item.reason?.trim() === selectedLabelItem.displayName)
    : allRecommendations
  const totalPages = Math.max(1, Math.ceil(filteredRecommendations.length / PAGE_SIZE))
  const currentPage = Math.min(page, totalPages - 1)
  const recommendations = filteredRecommendations.slice(currentPage * PAGE_SIZE, (currentPage + 1) * PAGE_SIZE)

  const handlePageChange = (nextPage: number) => {
    navigate({ to: '/recommendations', search: { page: nextPage, label: selectedLabel } })
  }

  const handleLabelChange = (label?: string) => {
    navigate({ to: '/recommendations', search: { page: 0, label } })
  }

  const handleSkillClick = (namespace: string, slug: string) => {
    navigate({ to: `/space/${namespace}/${slug}` })
  }

  return (
    <div className={APP_SHELL_PAGE_CLASS_NAME}>
      <div className="space-y-3 text-center">
        <p className="text-sm font-medium text-primary">{t('recommendations.eyebrow')}</p>
        <h1 className="text-4xl font-bold tracking-tight text-brand-gradient">{t('recommendations.title')}</h1>
        <p className="mx-auto max-w-2xl text-muted-foreground">{t('recommendations.description')}</p>
      </div>

      {isLoading ? (
        <SkeletonList count={PAGE_SIZE} />
      ) : allRecommendations.length > 0 ? (
        <>
          <div className="flex flex-wrap gap-3">
            <Button
              variant={selectedLabel ? 'outline' : 'default'}
              className={!selectedLabel ? 'bg-black text-white hover:bg-black/90' : 'bg-white'}
              onClick={() => handleLabelChange(undefined)}
            >
              {t('recommendations.allDomains')}
            </Button>
            {labels?.map((label) => (
              <Button
                key={label.slug}
                variant={selectedLabel === label.slug ? 'default' : 'outline'}
                className={selectedLabel === label.slug ? 'bg-black text-white hover:bg-black/90' : 'bg-white'}
                onClick={() => handleLabelChange(label.slug)}
              >
                {label.displayName}
              </Button>
            ))}
          </div>

          {recommendations.length > 0 ? (
            <>
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
                {recommendations.map((recommendation, idx) => (
                  <div key={`${recommendation.namespace}/${recommendation.slug}`} className={`animate-fade-up delay-${Math.min(idx + 1, 6)}`}>
                    <RecommendationCard
                      recommendation={recommendation}
                      onClick={() => recommendation.skill && handleSkillClick(recommendation.skill.namespace, recommendation.skill.slug)}
                    />
                  </div>
                ))}
              </div>
              <Pagination page={currentPage} totalPages={totalPages} onPageChange={handlePageChange} />
            </>
          ) : (
            <EmptyState title={t('recommendations.emptyTitle')} description={t('recommendations.emptyDescription')} />
          )}
        </>
      ) : (
        <EmptyState title={t('recommendations.emptyTitle')} description={t('recommendations.emptyDescription')} />
      )}
    </div>
  )
}
