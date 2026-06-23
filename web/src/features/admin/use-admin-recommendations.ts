import { useMutation, useQueryClient } from '@tanstack/react-query'
import { adminApi } from '@/api/client'
import type { RecommendationItem, RecommendationUpdateInput } from '@/api/types'

export interface SetWeeklyRecommendationInput extends RecommendationUpdateInput {
  namespace: string
  slug: string
}

export function useSetWeeklyRecommendation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ namespace, slug, ...request }: SetWeeklyRecommendationInput): Promise<RecommendationItem> =>
      adminApi.setWeeklyRecommendation(namespace, slug, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['recommendations'] })
      queryClient.invalidateQueries({ queryKey: ['recommendations', 'weekly-current'] })
    },
  })
}
