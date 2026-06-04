import type { SkillBadge } from '@/api/types'

export const SCANNED_SAFE_BADGE = 'SCANNED_SAFE'
export const REQUIRES_API_KEY_BADGE = 'REQUIRES_API_KEY'
export const REQUIRES_OAUTH_BADGE = 'REQUIRES_OAUTH'

const FRONT_CARD_BADGES = new Set([
  SCANNED_SAFE_BADGE,
  REQUIRES_API_KEY_BADGE,
  REQUIRES_OAUTH_BADGE,
])

export function isFrontCardBadge(badge: Pick<SkillBadge, 'type'>): boolean {
  return FRONT_CARD_BADGES.has(badge.type)
}

export function getSkillBadgeClassName(badge: Pick<SkillBadge, 'type'>): string {
  if (badge.type === SCANNED_SAFE_BADGE) {
    return 'border-emerald-500/40 bg-emerald-50 text-emerald-800 ring-1 ring-emerald-200'
  }
  if (badge.type === REQUIRES_API_KEY_BADGE || badge.type === REQUIRES_OAUTH_BADGE) {
    return 'border-sky-500/40 bg-sky-50 text-sky-800 ring-1 ring-sky-200'
  }
  return 'border-slate-300 bg-slate-100 text-slate-800'
}
