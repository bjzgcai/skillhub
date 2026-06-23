import type { RecommendationItem, SkillSummary } from '@/api/types'

export function getSkillKey(skill?: Pick<SkillSummary, 'namespace' | 'slug'> | null): string | null {
  if (!skill) return null
  return `${skill.namespace}/${skill.slug}`
}

export function getRecommendationSkillKey(recommendation?: Pick<RecommendationItem, 'skill'> | null): string | null {
  return getSkillKey(recommendation?.skill)
}

export function isSameRecommendationSkill(
  recommendation: Pick<RecommendationItem, 'skill'>,
  skill?: Pick<SkillSummary, 'namespace' | 'slug'> | null,
): boolean {
  const recommendationKey = getRecommendationSkillKey(recommendation)
  const skillKey = getSkillKey(skill)
  return Boolean(recommendationKey && skillKey && recommendationKey === skillKey)
}
