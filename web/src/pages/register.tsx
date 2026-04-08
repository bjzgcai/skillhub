import { Link, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card'

/**
 * Registration is disabled in the DingTalk-only deployment mode.
 */
export function RegisterPage() {
  const { t } = useTranslation()
  const search = useSearch({ from: '/register' })
  const returnTo = search.returnTo && search.returnTo.startsWith('/') ? search.returnTo : '/dashboard'

  return (
    <div className="mx-auto flex min-h-[70vh] max-w-2xl items-center justify-center">
      <Card className="w-full border-slate-200 bg-white/95 shadow-xl">
        <CardHeader className="space-y-3 text-center">
          <CardTitle>{t('register.title')}</CardTitle>
          <CardDescription>DingTalk SSO is the only supported sign-in method for this deployment.</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="space-y-4 text-center">
            <p className="text-sm text-muted-foreground">
              Local account registration has been disabled. Please return to the login page and continue with DingTalk SSO.
            </p>
            <Link
              to="/login"
              search={{ returnTo }}
              className="font-medium text-primary hover:underline"
            >
              {t('register.login')}
            </Link>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
