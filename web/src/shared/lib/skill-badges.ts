import type { SkillBadge } from '@/api/types'

export const SCANNED_SAFE_BADGE = 'SCANNED_SAFE'
export const REQUIRES_API_KEY_BADGE = 'REQUIRES_API_KEY'
export const REQUIRES_OAUTH_BADGE = 'REQUIRES_OAUTH'
export const FALSE_POSITIVE_ALLOWED_BADGE = 'FALSE_POSITIVE_ALLOWED'
export const MEMORY_WRITE_BADGE = 'MEMORY_WRITE'
export const CREDENTIAL_RISK_BADGE = 'CREDENTIAL_RISK'
export const LOCAL_FILE_SYNC_BADGE = 'LOCAL_FILE_SYNC'
export const PENDING_REVIEW_BADGE = 'PENDING_REVIEW'

const FRONT_CARD_BADGES = new Set([
  SCANNED_SAFE_BADGE,
  FALSE_POSITIVE_ALLOWED_BADGE,
  MEMORY_WRITE_BADGE,
  CREDENTIAL_RISK_BADGE,
  LOCAL_FILE_SYNC_BADGE,
  PENDING_REVIEW_BADGE,
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
  if (badge.type === FALSE_POSITIVE_ALLOWED_BADGE) {
    return 'border-amber-500/40 bg-amber-50 text-amber-800 ring-1 ring-amber-200'
  }
  if (badge.type === CREDENTIAL_RISK_BADGE || badge.type === PENDING_REVIEW_BADGE) {
    return 'border-rose-500/40 bg-rose-50 text-rose-800 ring-1 ring-rose-200'
  }
  if (badge.type === MEMORY_WRITE_BADGE || badge.type === LOCAL_FILE_SYNC_BADGE) {
    return 'border-orange-500/40 bg-orange-50 text-orange-800 ring-1 ring-orange-200'
  }
  if (badge.type === REQUIRES_API_KEY_BADGE || badge.type === REQUIRES_OAUTH_BADGE) {
    return 'border-sky-500/40 bg-sky-50 text-sky-800 ring-1 ring-sky-200'
  }
  return 'border-slate-300 bg-slate-100 text-slate-800'
}
