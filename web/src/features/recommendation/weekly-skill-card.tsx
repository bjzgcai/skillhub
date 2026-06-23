import type { KeyboardEvent, MouseEvent } from 'react'
import type { RecommendationItem } from '@/api/types'
import { Card } from '@/shared/ui/card'
import { Button } from '@/shared/ui/button'
import { Bell, Eye } from 'lucide-react'

interface WeeklySkillCardProps {
  recommendation: RecommendationItem
  onOpenRecommendation?: () => void
  onStartLearning?: () => void
  onViewSkill?: () => void
  onConfigureReminder?: () => void
  compact?: boolean
  showActions?: boolean
}

function stopCardClick(event: MouseEvent<HTMLButtonElement>) {
  event.stopPropagation()
}

export function normalizeBackgroundImageUrl(value?: string, currentOrigin?: string): string | undefined {
  const url = value?.trim()
  if (!url) return undefined
  if (url.startsWith('/') && !url.startsWith('//')) return url
  try {
    const parsed = new URL(url)
    const origin = currentOrigin ?? (typeof window === 'undefined' ? undefined : window.location.origin)
    if (origin && parsed.origin === origin) return url
    return parsed.protocol === 'https:' ? url : undefined
  } catch {
    return undefined
  }
}

export function WeeklySkillCard({
  recommendation,
  onOpenRecommendation,
  onStartLearning,
  onViewSkill,
  onConfigureReminder,
  compact = false,
  showActions = true,
}: WeeklySkillCardProps) {
  const backgroundImageUrl = normalizeBackgroundImageUrl(recommendation.backgroundImageUrl)
  const title = recommendation.title || recommendation.skill?.displayName || recommendation.slug
  const summary = recommendation.summary?.trim() || recommendation.skill?.summary?.trim()
  const isClickable = Boolean(onOpenRecommendation)

  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (!onOpenRecommendation || event.target !== event.currentTarget) {
      return
    }
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      onOpenRecommendation()
    }
  }

  return (
    <Card
      role={isClickable ? 'button' : undefined}
      tabIndex={isClickable ? 0 : undefined}
      onClick={onOpenRecommendation}
      onKeyDown={handleKeyDown}
      className={[
        'group relative isolate overflow-hidden border-0 text-left shadow-2xl shadow-rose-950/20',
        'transition duration-300 hover:-translate-y-0.5 hover:shadow-rose-950/30 focus-visible:outline-none',
        'focus-visible:ring-2 focus-visible:ring-primary/60 focus-visible:ring-offset-2',
        compact ? 'min-h-[210px]' : 'min-h-[252px]',
        isClickable ? 'cursor-pointer' : '',
      ].join(' ')}
      style={
        backgroundImageUrl
          ? { backgroundImage: `url(${backgroundImageUrl})`, backgroundSize: 'cover', backgroundPosition: 'center' }
          : undefined
      }
    >
      <div className="absolute inset-0 -z-10 bg-[linear-gradient(110deg,#84172e_0%,#a41935_42%,#671729_100%)]" />
      <div className="absolute inset-0 -z-10 bg-[radial-gradient(circle_at_78%_28%,rgba(255,205,154,0.28),transparent_19%),linear-gradient(90deg,rgba(50,0,13,0.72),rgba(97,9,30,0.42)_52%,rgba(45,0,12,0.22))]" />
      <div className="absolute inset-y-0 right-0 -z-10 w-1/2 bg-[linear-gradient(135deg,rgba(255,255,255,0.08)_25%,transparent_25%,transparent_50%,rgba(255,255,255,0.08)_50%,rgba(255,255,255,0.08)_75%,transparent_75%)] bg-[length:48px_48px] opacity-35" />
      <div className="absolute -right-10 top-8 -z-10 h-40 w-40 rounded-full border border-amber-200/20 opacity-70 blur-[1px]" />
      <div className="absolute inset-x-5 bottom-0 h-px bg-gradient-to-r from-transparent via-white/35 to-transparent" />

      <div className="flex min-h-[inherit] items-center justify-between gap-6 p-7 md:p-10">
        <div className="max-w-3xl space-y-5 text-white">
          <div className="inline-flex w-fit items-center rounded-full bg-white/14 px-4 py-1.5 text-base font-bold tracking-wide text-rose-50 ring-1 ring-white/18 backdrop-blur md:text-lg">
            每周一技
          </div>
          <div className="space-y-3">
            <h2 className={compact ? 'text-3xl font-bold tracking-tight md:text-4xl' : 'text-4xl font-bold tracking-tight md:text-5xl'}>
              {title}
            </h2>
            {summary ? (
              <p className="max-w-2xl text-base leading-7 text-rose-50/84 line-clamp-2 md:text-lg">
                {summary}
              </p>
            ) : null}
          </div>
        </div>

        {showActions ? (
          <div className="hidden shrink-0 flex-col gap-2 md:flex" onClick={(event) => event.stopPropagation()}>
            <Button
              variant="secondary"
              onClick={(event) => {
                stopCardClick(event)
                onStartLearning?.()
              }}
              disabled={!onStartLearning}
            >
              开始学习
            </Button>
            <Button
              variant="outline"
              onClick={(event) => {
                stopCardClick(event)
                onViewSkill?.()
              }}
              disabled={!onViewSkill}
              className="bg-white/10 text-white hover:bg-white/20"
            >
              <Eye className="mr-2 h-4 w-4" />
              查看技能
            </Button>
            <Button
              variant="ghost"
              onClick={(event) => {
                stopCardClick(event)
                onConfigureReminder?.()
              }}
              disabled={!onConfigureReminder}
              className="text-white/90 hover:bg-white/15 hover:text-white"
            >
              <Bell className="mr-2 h-4 w-4" />
              每周提醒
            </Button>
          </div>
        ) : null}
      </div>
    </Card>
  )
}
