import { Link, useNavigate, useSearch } from '@tanstack/react-router'
import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ChevronDown, Eye, EyeOff } from 'lucide-react'
import { ApiError, getDingTalkRuntimeConfig, getDirectAuthRuntimeConfig } from '@/api/client'
import { LoginButton } from '@/features/auth/login-button'
import { useDingTalkH5Login } from '@/features/auth/use-dingtalk-h5-login'
import { SessionBootstrapEntry } from '@/features/auth/session-bootstrap-entry'
import { useAuthMethods } from '@/features/auth/use-auth-methods'
import { usePasswordLogin } from '@/features/auth/use-password-login'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'

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
  const loginMutation = usePasswordLogin()
  const dingTalkMutation = useDingTalkH5Login()
  const dingTalkRuntimeConfig = getDingTalkRuntimeConfig()
  const directAuthConfig = getDirectAuthRuntimeConfig()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [showPasswordLogin, setShowPasswordLogin] = useState<boolean | null>(null)
  const [fieldErrors, setFieldErrors] = useState<{ username?: string, password?: string }>({})
  const dingTalkAttemptedRef = useRef(false)
  const isChinese = i18n.resolvedLanguage?.split('-')[0] === 'zh'
  const { data: authMethods } = useAuthMethods(search.returnTo)

  const returnTo = search.returnTo && search.returnTo.startsWith('/') ? search.returnTo : '/dashboard'
  const disabledMessage = search.reason === 'accountDisabled' ? t('apiError.auth.accountDisabled') : null
  const directMethod = directAuthConfig.provider
    ? authMethods?.find((method) =>
      method.methodType === 'DIRECT_PASSWORD' && method.provider === directAuthConfig.provider)
    : undefined
  const bootstrapMethod = authMethods?.find((method) => method.methodType === 'SESSION_BOOTSTRAP')
  const dingTalkMethod = authMethods?.find((method) =>
    method.methodType === 'OAUTH_REDIRECT' && method.provider === dingTalkRuntimeConfig.provider)
  const showDingTalkEntry = Boolean(dingTalkRuntimeConfig.provider && dingTalkMethod)
  const shouldExpandPasswordLogin = !showDingTalkEntry && showPasswordLogin !== false

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

  useEffect(() => {
    setShowPasswordLogin(!showDingTalkEntry)
  }, [showDingTalkEntry])

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const trimmedUsername = username.trim()
    const nextFieldErrors: { username?: string, password?: string } = {}

    if (!trimmedUsername) {
      nextFieldErrors.username = t('login.usernameRequired')
    }
    if (!password) {
      nextFieldErrors.password = t('login.passwordRequired')
    }
    if (nextFieldErrors.username || nextFieldErrors.password) {
      setFieldErrors(nextFieldErrors)
      return
    }

    setFieldErrors({})
    try {
      await loginMutation.mutateAsync({ username: trimmedUsername, password })
      await navigate({ to: returnTo })
    } catch {
      // mutation state drives the error UI
    }
  }

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
            <SessionBootstrapEntry
              methodDisplayName={bootstrapMethod?.displayName}
              onAuthenticated={() => navigate({ to: returnTo })}
            />
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
              ) : null}

              <div className="rounded-2xl border border-border/60 bg-background/40">
                <button
                  type="button"
                  className="flex w-full items-center justify-between px-4 py-3 text-left text-sm font-medium text-foreground"
                  onClick={() => setShowPasswordLogin((current) => !current)}
                  aria-expanded={Boolean(showPasswordLogin)}
                >
                  <span>{showDingTalkEntry ? t('login.otherLoginMethods') : t('login.tabPassword')}</span>
                  <ChevronDown className={`h-4 w-4 transition-transform ${showPasswordLogin ? 'rotate-180' : ''}`} />
                </button>

                {shouldExpandPasswordLogin ? (
                  <div className="border-t border-border/60 px-4 py-4">
                    <form className="space-y-4" onSubmit={handleSubmit}>
                      {directAuthConfig.enabled ? (
                        <p className="text-sm text-muted-foreground">
                          {t('login.passwordCompatHint', {
                            name: directMethod?.displayName ?? directAuthConfig.provider,
                          })}
                        </p>
                      ) : null}
                      <div className="space-y-2">
                        <label className="text-sm font-medium" htmlFor="username">{t('login.username')}</label>
                        <Input
                          id="username"
                          autoComplete="username"
                          value={username}
                          onChange={(event) => {
                            setUsername(event.target.value)
                            if (fieldErrors.username) {
                              setFieldErrors((current) => ({ ...current, username: undefined }))
                            }
                          }}
                          placeholder={t('login.usernamePlaceholder')}
                          aria-invalid={fieldErrors.username ? 'true' : 'false'}
                        />
                        {fieldErrors.username ? (
                          <p className="text-sm text-red-600">{fieldErrors.username}</p>
                        ) : null}
                      </div>
                      <div className="space-y-2">
                        <label className="text-sm font-medium" htmlFor="password">{t('login.password')}</label>
                        <div className="relative">
                          <Input
                            id="password"
                            type={showPassword ? 'text' : 'password'}
                            autoComplete="current-password"
                            value={password}
                            onChange={(event) => {
                              setPassword(event.target.value)
                              if (fieldErrors.password) {
                                setFieldErrors((current) => ({ ...current, password: undefined }))
                              }
                            }}
                            placeholder={t('login.passwordPlaceholder')}
                            className="pr-12"
                            aria-invalid={fieldErrors.password ? 'true' : 'false'}
                          />
                          <button
                            type="button"
                            aria-label={showPassword ? t('login.hidePassword') : t('login.showPassword')}
                            aria-pressed={showPassword}
                            onClick={() => setShowPassword((current) => !current)}
                            className="absolute inset-y-0 right-0 flex w-12 items-center justify-center text-muted-foreground transition-colors hover:text-foreground"
                          >
                            {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                          </button>
                        </div>
                        {fieldErrors.password ? (
                          <p className="text-sm text-red-600">{fieldErrors.password}</p>
                        ) : null}
                      </div>
                      {loginMutation.error ? (
                        <p className="text-sm text-red-600">{loginMutation.error.message}</p>
                      ) : null}
                      <Button className="w-full" disabled={loginMutation.isPending} type="submit">
                        {loginMutation.isPending ? t('login.submitting') : t('login.submit')}
                      </Button>
                      <p className="text-center text-sm text-muted-foreground">
                        {t('login.noAccount')}
                        {' '}
                        <Link
                          to="/register"
                          search={{ returnTo }}
                          className="font-medium text-primary hover:underline"
                        >
                          {t('login.register')}
                        </Link>
                      </p>
                    </form>
                  </div>
                ) : null}
              </div>
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
