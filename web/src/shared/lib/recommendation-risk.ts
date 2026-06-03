const RISK_MARKER = '风险提示：'

export interface RecommendationRiskInfo {
  riskBadge?: string
  riskNote?: string
}

export function splitRecommendationSummary(summary?: string): { summary?: string; riskNote?: string } {
  if (!summary?.includes(RISK_MARKER)) {
    return { summary }
  }
  const [main, risk] = summary.split(RISK_MARKER, 2)
  return {
    summary: main.trim() || undefined,
    riskNote: risk?.trim() ? `${RISK_MARKER}${risk.trim()}` : undefined,
  }
}

export function getRiskBadge(badge?: string): string | undefined {
  if (!badge) {
    return undefined
  }
  if (badge.includes('/')) {
    return badge.split('/').pop()?.trim() || undefined
  }
  // Existing data used badges like "#4" for ranking. Rankings are no longer shown on skill cards.
  return badge.startsWith('#') ? undefined : badge
}
