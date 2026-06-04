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
            'inline-flex rounded-full border px-2.5 py-1 text-xs font-medium',
            getSkillBadgeClassName(badge),
          )}
        >
          {badge.displayName}
        </span>
      ))}
    </div>
  )
}
