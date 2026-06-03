import { startTransition, useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Loader2 } from 'lucide-react'
import type { SkillSummary } from '@/api/types'
import { useAuth } from '@/features/auth/use-auth'
import { SearchBar } from '@/features/search/search-bar'
import { SkillCard } from '@/features/skill/skill-card'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { EmptyState } from '@/shared/components/empty-state'
import { Pagination } from '@/shared/components/pagination'
import { useRecommendations, useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { useVisibleLabels } from '@/shared/hooks/use-label-queries'
import { useMyStars } from '@/shared/hooks/use-user-queries'
import { normalizeSearchQuery } from '@/shared/lib/search-query'
import { getRiskBadge, splitRecommendationSummary } from '@/shared/lib/recommendation-risk'
import { Button } from '@/shared/ui/button'
import { APP_SHELL_PAGE_CLASS_NAME } from '@/app/page-shell-style'

const PAGE_SIZE = 12

/**
 * Skill discovery page with synchronized URL state.
 *
 * Search text, sorting, pagination, and the starred-only filter are mirrored into router search
 * params so the page can be shared, restored, and revisited without losing state.
 */
function filterStarredSkills(skills: SkillSummary[], query: string): SkillSummary[] {
  const normalizedQuery = query.trim().toLowerCase()
  if (!normalizedQuery) {
    return skills
  }

  return skills.filter((skill) =>
    [skill.displayName, skill.summary, skill.namespace, skill.slug]
      .filter(Boolean)
      .some((value) => value!.toLowerCase().includes(normalizedQuery))
  )
}

function sortStarredSkills(skills: SkillSummary[], sort: string): SkillSummary[] {
  const sorted = [...skills]
  if (sort === 'downloads') {
    return sorted.sort((left, right) => right.downloadCount - left.downloadCount)
  }
  if (sort === 'newest' || sort === 'relevance') {
    return sorted.sort((left, right) => new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime())
  }
  return sorted
}

export function SearchPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const searchParams = useSearch({ from: '/search' })
  const { isAuthenticated } = useAuth()

  const q = normalizeSearchQuery(searchParams.q || '')
  const selectedLabel = searchParams.label || ''
  const selectedSource = searchParams.source || 'all'
  const sort = searchParams.sort || 'newest'
  const page = searchParams.page ?? 0
  const starredOnly = searchParams.starredOnly ?? false
  const [queryInput, setQueryInput] = useState(q)

  useEffect(() => {
    setQueryInput(q)
  }, [q])

  const { data, isLoading, isFetching } = useSearchSkills({
    q,
    label: selectedLabel || undefined,
    source: selectedSource,
    sort,
    page,
    size: PAGE_SIZE,
    starredOnly,
  })
  const { data: labels } = useVisibleLabels()
  const { data: recommendationData } = useRecommendations({ page: 0, size: 200 })
  const {
    data: starredSkills,
    isLoading: isLoadingStarred,
    isFetching: isFetchingStarred,
  } = useMyStars(starredOnly && isAuthenticated)

  useEffect(() => {
    // Debounce URL updates while the user is typing so query state stays shareable without
    // triggering a navigation on every keystroke.
    const normalizedQuery = normalizeSearchQuery(queryInput)
    if (normalizedQuery === q) {
      return
    }

    if (!normalizedQuery) {
      startTransition(() => {
        navigate({ to: '/search', search: { q: '', label: selectedLabel, source: selectedSource, sort, page: 0, starredOnly }, replace: page === 0 })
      })
      return
    }

    const timeoutId = window.setTimeout(() => {
      startTransition(() => {
        navigate({ to: '/search', search: { q: normalizedQuery, label: selectedLabel, source: selectedSource, sort, page: 0, starredOnly }, replace: true })
      })
    }, 250)

    return () => window.clearTimeout(timeoutId)
  }, [navigate, page, q, queryInput, selectedLabel, selectedSource, sort, starredOnly])

  const handleSearch = (query: string) => {
    const normalizedQuery = normalizeSearchQuery(query)
    setQueryInput(query)
    startTransition(() => {
      navigate({ to: '/search', search: { q: normalizedQuery, label: selectedLabel, source: selectedSource, sort, page: 0, starredOnly }, replace: true })
    })
  }

  const handleSortChange = (newSort: string) => {
    navigate({ to: '/search', search: { q, label: selectedLabel, source: selectedSource, sort: newSort, page: 0, starredOnly } })
  }

  const handlePageChange = (newPage: number) => {
    navigate({ to: '/search', search: { q, label: selectedLabel, source: selectedSource, sort, page: newPage, starredOnly } })
  }

  const handleLabelToggle = (label: string) => {
    const nextLabel = selectedLabel === label ? '' : label
    navigate({ to: '/search', search: { q, label: nextLabel, source: selectedSource, sort, page: 0, starredOnly } })
  }

  const handleLabelClear = () => {
    navigate({ to: '/search', search: { q, label: '', source: selectedSource, sort, page: 0, starredOnly } })
  }

  const handleStarredToggle = () => {
    if (!isAuthenticated) {
      navigate({
        to: '/login',
        search: {
          returnTo: `${window.location.pathname}${window.location.search}${window.location.hash}`,
        },
      })
      return
    }

    navigate({ to: '/search', search: { q, label: selectedLabel, source: selectedSource, sort, page: 0, starredOnly: !starredOnly } })
  }

  const handleSourceChange = (source: 'all' | 'internal' | 'clawhub') => {
    navigate({ to: '/search', search: { q, label: selectedLabel, source, sort, page: 0, starredOnly } })
  }

  const handleSkillClick = (namespace: string, slug: string) => {
    navigate({ to: `/space/${namespace}/${slug}` })
  }

  const filteredStarredSkills = starredOnly
    ? sortStarredSkills(filterStarredSkills(starredSkills ?? [], q), sort)
    : []
  const starredPageItems = starredOnly
    ? filteredStarredSkills.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE)
    : []
  const totalPages = starredOnly
    ? Math.ceil(filteredStarredSkills.length / PAGE_SIZE)
    : data
      ? Math.ceil(data.total / data.size)
      : 0
  const displayItems = starredOnly ? starredPageItems : (data?.items ?? [])
  const riskBySkillKey = useMemo(() => {
    const entries = new Map<string, { riskBadge?: string; riskNote?: string }>()
    for (const recommendation of recommendationData?.items ?? []) {
      const riskBadge = getRiskBadge(recommendation.badge)
      const { riskNote } = splitRecommendationSummary(recommendation.summary || recommendation.skill?.summary)
      if (!riskBadge) {
        continue
      }
      const skill = recommendation.skill
      const keys = [
        recommendation.skillId ? String(recommendation.skillId) : undefined,
        skill ? `${skill.namespace}/${skill.slug}` : undefined,
        recommendation.namespace && recommendation.slug ? `${recommendation.namespace}/${recommendation.slug}` : undefined,
      ].filter(Boolean) as string[]
      for (const key of keys) {
        entries.set(key, { riskBadge, riskNote })
      }
    }
    return entries
  }, [recommendationData?.items])
  const isPageLoading = starredOnly ? isLoadingStarred : isLoading
  const isUpdatingResults = starredOnly ? isFetchingStarred && !isLoadingStarred : isFetching && !isLoading
  const resultCount = starredOnly ? filteredStarredSkills.length : (data?.total ?? 0)

  return (
    <div className={APP_SHELL_PAGE_CLASS_NAME}>
      {/* Search Bar */}
      <div className="max-w-3xl mx-auto">
        <SearchBar
          value={queryInput}
          isSearching={isUpdatingResults}
          onChange={setQueryInput}
          onSearch={handleSearch}
        />
      </div>

      {/* Sort And Filters */}
      <div className="space-y-4">
        <div className="flex items-center justify-between flex-wrap gap-4">
          <div className="flex items-center gap-3">
            <span className="text-sm font-medium text-muted-foreground">{t('search.sort.label')}</span>
            <div className="flex gap-2">
              <Button
                variant={sort === 'relevance' ? 'default' : 'outline'}
                size="sm"
                onClick={() => handleSortChange('relevance')}
              >
                {t('search.sort.relevance')}
              </Button>
              <Button
                variant={sort === 'downloads' ? 'default' : 'outline'}
                size="sm"
                onClick={() => handleSortChange('downloads')}
              >
                {t('search.sort.downloads')}
              </Button>
              <Button
                variant={sort === 'newest' ? 'default' : 'outline'}
                size="sm"
                onClick={() => handleSortChange('newest')}
              >
                {t('search.sort.newest')}
              </Button>
            </div>
          </div>

          {resultCount > 0 && (
            <div className="text-sm text-muted-foreground">
              {t('search.results', { count: resultCount })}
            </div>
          )}
        </div>

        {isUpdatingResults ? (
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" />
            <span>{t('search.loadingMore')}</span>
          </div>
        ) : null}

        <div className="flex flex-wrap items-center gap-3">
          <span className="text-sm font-medium text-muted-foreground">{t('search.filters.label')}</span>
          <Button
            variant={starredOnly ? 'default' : 'outline'}
            size="sm"
            onClick={handleStarredToggle}
          >
            {t('search.filterStarred')}
          </Button>
          <div className="flex items-center gap-2">
            <span className="text-sm font-medium text-muted-foreground">{t('search.source.label')}</span>
            <Button
              variant={selectedSource === 'all' ? 'default' : 'outline'}
              size="sm"
              onClick={() => handleSourceChange('all')}
            >
              {t('search.source.all')}
            </Button>
            <Button
              variant={selectedSource === 'internal' ? 'default' : 'outline'}
              size="sm"
              onClick={() => handleSourceChange('internal')}
            >
              {t('search.source.internal')}
            </Button>
            <Button
              variant={selectedSource === 'clawhub' ? 'default' : 'outline'}
              size="sm"
              onClick={() => handleSourceChange('clawhub')}
            >
              {t('search.source.clawhub')}
            </Button>
          </div>
        </div>

        {!starredOnly && labels?.length ? (
          <div className="flex flex-wrap items-center gap-3">
            <span className="text-sm font-medium text-muted-foreground">{t('search.labels.label')}</span>
            <Button
              variant={!selectedLabel ? 'default' : 'outline'}
              size="sm"
              onClick={handleLabelClear}
            >
              {t('search.labels.all')}
            </Button>
            {labels.map((label) => (
              <Button
                key={label.slug}
                variant={selectedLabel === label.slug ? 'default' : 'outline'}
                size="sm"
                onClick={() => handleLabelToggle(label.slug)}
              >
                {label.displayName}
              </Button>
            ))}
          </div>
        ) : null}
      </div>

      {/* Results */}
      {isPageLoading ? (
        <SkeletonList count={PAGE_SIZE} />
      ) : displayItems.length > 0 ? (
        <>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            {displayItems.map((skill, idx) => (
              <div key={skill.id} className={`h-full animate-fade-up delay-${Math.min(idx % 6 + 1, 6)}`}>
                <SkillCard
                  skill={skill}
                  highlightStarred
                  riskInfo={riskBySkillKey.get(String(skill.id)) ?? riskBySkillKey.get(`${skill.namespace}/${skill.slug}`)}
                  onClick={() => handleSkillClick(skill.namespace, skill.slug)}
                />
              </div>
            ))}
          </div>
          {totalPages > 1 && (
            <Pagination
              page={page}
              totalPages={totalPages}
              onPageChange={handlePageChange}
            />
          )}
        </>
      ) : (
        <EmptyState
          title={starredOnly ? t('search.noStarredResults') : t('search.noResults')}
          description={
            starredOnly
              ? (q ? t('search.noStarredResultsFor', { q }) : t('search.noStarredSkills'))
              : (q ? t('search.noResultsFor', { q }) : t('search.enterKeyword'))
          }
        />
      )}
    </div>
  )
}
