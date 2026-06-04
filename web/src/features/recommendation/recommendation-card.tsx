import type { RecommendationItem } from '@/api/types'
import { Card } from '@/shared/ui/card'
import { getHeadlineVersion } from '@/shared/lib/skill-lifecycle'
import { formatCompactCount } from '@/shared/lib/number-format'
import { splitRecommendationSummary } from '@/shared/lib/recommendation-risk'
import { getSkillAvatar } from '@/shared/lib/skill-avatar'
import { SkillCardBadges } from '@/features/skill/skill-card-badges'
import { Bookmark } from 'lucide-react'

interface RecommendationCardProps {
  recommendation: RecommendationItem
  onClick?: () => void
}

export function RecommendationCard({ recommendation, onClick }: RecommendationCardProps) {
  if (!recommendation.skill) {
    return null
  }

  const skill = {
    ...recommendation.skill,
    displayName: recommendation.title || recommendation.skill.displayName,
  }
  const headlineVersion = getHeadlineVersion(skill)
  const { summary } = splitRecommendationSummary(recommendation.summary || recommendation.skill.summary)
  const avatar = getSkillAvatar(skill.displayName, skill.slug)

  return (
    <Card
      className="h-full cursor-pointer overflow-visible border bg-white p-5 shadow-sm transition-shadow hover:shadow-md"
      style={{ borderColor: 'hsl(var(--border-card))' }}
      onClick={onClick}
    >
      <div className="flex h-full flex-col">
        <div className="mb-3 flex items-start justify-between gap-3">
          <div className="flex min-w-0 items-start gap-3">
            <div className={`flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl text-xl font-bold ring-1 ${avatar.color.bg} ${avatar.color.text} ${avatar.color.ring}`}>
              {avatar.initial}
            </div>
            <div className="min-w-0 space-y-1.5">
              <div className="flex flex-wrap items-center gap-2">
                <h3 className="font-semibold text-lg leading-tight transition-colors group-hover:text-primary" style={{ color: 'hsl(var(--foreground))' }}>
                  {skill.displayName}
                </h3>
              </div>
              <p className="font-mono text-sm text-muted-foreground">
                {skill.slug}
              </p>
            </div>
          </div>
          <SkillCardBadges badges={skill.badges} />
        </div>

        {summary && (
          <p className="mb-4 text-sm leading-relaxed text-muted-foreground line-clamp-2">
            {summary}
          </p>
        )}

        <div className="mt-auto flex items-center gap-4 text-xs text-muted-foreground">
          {headlineVersion && (
            <span className="rounded-full bg-secondary/60 px-2.5 py-1 font-mono">
              v{headlineVersion.version}
            </span>
          )}
          <span className="flex items-center gap-1">
            <svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M9 19l3 3m0 0l3-3m-3 3V10" />
            </svg>
            {formatCompactCount(skill.downloadCount)}
          </span>
          <span className="flex items-center gap-1">
            <Bookmark className="h-3.5 w-3.5" />
            {skill.starCount}
          </span>
          {skill.ratingAvg !== undefined && skill.ratingCount > 0 && (
            <span className="flex items-center gap-1">
              <svg className="h-3.5 w-3.5 text-primary" fill="currentColor" viewBox="0 0 20 20">
                <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
              </svg>
              {skill.ratingAvg.toFixed(1)}
            </span>
          )}
        </div>
      </div>
    </Card>
  )
}
