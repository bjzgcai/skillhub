export const HOME_DISCOVERY_SOURCE = 'internal' as const

export function buildHomeSearchParams(
  sort: 'relevance' | 'downloads' | 'newest' = 'relevance',
  q = ''
) {
  return {
    q,
    source: HOME_DISCOVERY_SOURCE,
    sort,
    page: 0,
    starredOnly: false,
  }
}
