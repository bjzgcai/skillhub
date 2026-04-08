import { Link, useNavigate, useSearch } from '@tanstack/react-router'
import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { ApiError, getDingTalkRuntimeConfig } from '@/api/client'
import { LoginButton } from '@/features/auth/login-button'
import { useDingTalkH5Login } from '@/features/auth/use-dingtalk-h5-login'
import { useAuthMethods } from '@/features/auth/use-auth-methods'

/**
 * Authentication entry page.
 *
 * It combines password login, OAuth entry points, and optional session-bootstrap support while
 * preserving the route the user originally intended to visit.
 */
export function LoginPage() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const search = useSearch({ from: '/login' })
  const dingTalkMutation = useDingTalkH5Login()
  const dingTalkRuntimeConfig = getDingTalkRuntimeConfig()
  const dingTalkAttemptedRef = useRef(false)
  const isChinese = i18n.resolvedLanguage?.split('-')[0] === 'zh'
  const { data: authMethods } = useAuthMethods(search.returnTo)

  const returnTo = search.returnTo && search.returnTo.startsWith('/') ? search.returnTo : '/dashboard'
  const disabledMessage = search.reason === 'accountDisabled' ? t('apiError.auth.accountDisabled') : null
  const dingTalkMethod = authMethods?.find((method) =>
    method.methodType === 'OAUTH_REDIRECT' && method.provider === dingTalkRuntimeConfig.provider)
  const showDingTalkEntry = Boolean(dingTalkRuntimeConfig.provider && dingTalkMethod)

  useEffect(() => {
    if (
      !dingTalkRuntimeConfig.enabled
      || !dingTalkRuntimeConfig.auto
      || !dingTalkRuntimeConfig.corpId
      || dingTalkAttemptedRef.current
      || typeof window === 'undefined'
    ) {
      return
    }

    const dd = (window as Window & {
      dd?: {
        runtime?: {
          permission?: {
            requestAuthCode?: (options: {
              corpId: string
              onSuccess: (result: { code: string }) => void
              onFail: () => void
            }) => void
          }
        }
      }
    }).dd

    const requestAuthCode = dd?.runtime?.permission?.requestAuthCode
    if (!requestAuthCode) {
      return
    }

    dingTalkAttemptedRef.current = true
    requestAuthCode({
      corpId: dingTalkRuntimeConfig.corpId,
      onSuccess: (result) => {
        const dingTalkCode = result.code || (result as { authCode?: string }).authCode
        if (!dingTalkCode) {
          return
        }
        void dingTalkMutation.mutateAsync(dingTalkCode, {
          onSuccess: async () => {
            await navigate({ to: returnTo })
          },
          onError: () => {
            // Keep the standard login choices available.
          },
        })
      },
      onFail: () => {
        // Ignore DingTalk SDK failures so normal login remains available.
      },
    })
  }, [dingTalkMutation, dingTalkRuntimeConfig.auto, dingTalkRuntimeConfig.corpId, dingTalkRuntimeConfig.enabled, navigate, returnTo])

  const dingTalkManualError = dingTalkMutation.error instanceof ApiError
    && dingTalkMutation.error.status !== 401
    && dingTalkMutation.error.status !== 403
    ? dingTalkMutation.error.message
    : null


  return (
    <div className="flex min-h-[70vh] items-center justify-center">
      <div className="w-full max-w-md space-y-8 animate-fade-up">
        <div className="text-center space-y-3">
          <div className="inline-flex w-16 h-16 rounded-2xl bg-gradient-to-br from-primary to-primary/70 items-center justify-center shadow-glow mb-4">
            <span className="text-primary-foreground font-bold text-2xl">S</span>
          </div>
          <h1 className="text-4xl font-bold font-heading text-foreground">{t('login.title')}</h1>
          <p className="text-muted-foreground text-lg">
            {t('login.subtitle')}
          </p>
        </div>

        <div className="glass-strong p-8 rounded-2xl">
          <div className="space-y-6">
            {disabledMessage ? (
              <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                {disabledMessage}
              </div>
            ) : null}
            {dingTalkRuntimeConfig.enabled && dingTalkRuntimeConfig.auto && dingTalkMethod ? (
              <div className="rounded-2xl border border-sky-200 bg-sky-50 px-4 py-3 text-sm text-sky-700">
                {dingTalkMutation.isPending
                  ? t('login.enterpriseSsoSubmitting', { name: dingTalkMethod.displayName })
                  : t('login.enterpriseSsoAutoHint', { name: dingTalkMethod.displayName })}
              </div>
            ) : null}
            {dingTalkManualError ? (
              <p className="text-sm text-red-600">{dingTalkManualError}</p>
            ) : null}
            <div className="space-y-6">
              {showDingTalkEntry ? (
                <div className="space-y-4">
                  <p className="text-sm text-muted-foreground">
                    {t('login.dingtalkHint')}
                  </p>
                  <LoginButton returnTo={returnTo} providers={['dingtalk']} />
                </div>
              ) : (
                <div className="rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                  DingTalk SSO is required for this deployment, but the current runtime configuration is incomplete.
                </div>
              )}
            </div>
          </div>
        </div>

        <p className="text-center text-xs text-muted-foreground">
          {t('login.agreementPrefix')}
          {isChinese ? null : ' '}
          <Link to="/terms" className="text-primary hover:underline">
            {t('login.terms')}
          </Link>
          {isChinese ? null : ' '}
          {t('login.and')}
          {isChinese ? null : ' '}
          <Link to="/privacy" className="text-primary hover:underline">
            {t('login.privacy')}
          </Link>
        </p>
      </div>
    </div>
  )
}
