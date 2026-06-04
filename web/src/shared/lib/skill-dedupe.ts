import type { RecommendationItem, SkillSummary } from '@/api/types'

type SkillIdentity = Pick<SkillSummary, 'id' | 'namespace' | 'slug'>

function getSkillKey(skill: SkillIdentity): string {
  return skill.namespace && skill.slug ? `${skill.namespace}/${skill.slug}` : String(skill.id)
}

export function dedupeSkillsBySlug<T extends SkillIdentity>(skills: T[]): T[] {
  const seen = new Set<string>()
  return skills.filter((skill) => {
    const key = getSkillKey(skill)
    if (seen.has(key)) {
      return false
    }
    seen.add(key)
    return true
  })
}

export function dedupeRecommendationsBySkillSlug(recommendations: RecommendationItem[]): RecommendationItem[] {
  const seen = new Set<string>()
  return recommendations.filter((recommendation) => {
    const skill = recommendation.skill
    const namespace = skill?.namespace ?? recommendation.namespace
    const slug = skill?.slug ?? recommendation.slug
    const key = namespace && slug ? `${namespace}/${slug}` : `${recommendation.sourceType}:${recommendation.namespace ?? ''}/${recommendation.slug ?? ''}:${recommendation.title ?? ''}`
    if (seen.has(key)) {
      return false
    }
    seen.add(key)
    return true
  })
}
