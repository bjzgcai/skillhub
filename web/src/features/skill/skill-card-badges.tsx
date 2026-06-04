import type { SkillBadge } from '@/api/types'
import { getSkillBadgeClassName, isFrontCardBadge } from '@/shared/lib/skill-badges'
import { cn } from '@/shared/lib/utils'

type SkillCardBadgesProps = {
  badges?: SkillBadge[]
}

export function SkillCardBadges({ badges }: SkillCardBadgesProps) {
  const cardBadges = badges?.filter(isFrontCardBadge) ?? []

  if (cardBadges.length === 0) {
    return null
  }

  return (
    <div className="flex shrink-0 flex-col items-end gap-2">
      {cardBadges.map((badge) => (
        <span
          key={badge.type}
          className={cn(
            'group/badge relative inline-flex rounded-full border px-2.5 py-1 text-xs font-medium',
            badge.description ? 'cursor-help' : '',
            getSkillBadgeClassName(badge),
          )}
        >
          {badge.displayName}
          {badge.description && (
            <span className="pointer-events-none absolute right-0 top-full z-30 mt-2 hidden w-72 rounded-xl border border-slate-200 bg-white p-3 text-left text-xs font-normal leading-relaxed text-slate-800 shadow-xl group-hover/badge:block">
              {badge.description}
            </span>
          )}
        </span>
      ))}
    </div>
  )
}
