import { useMutation } from '@tanstack/react-query'
import { authApi } from '@/api/client'

/**
 * Exchanges a DingTalk in-app auth code for a first-party SkillHub session.
 */
export function useDingTalkH5Login() {
  return useMutation({
    mutationFn: (code: string) => authApi.dingTalkH5Login(code),
  })
}
