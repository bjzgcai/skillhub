import { useTranslation } from 'react-i18next'
import type { NotificationPreferenceItem } from '@/api/types'
import { useNotificationPreferences, useUpdateNotificationPreferences } from './use-notification-preferences'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card'

const CATEGORIES = ['PUBLISH', 'REVIEW', 'PROMOTION', 'REPORT'] as const
const WEEKLY_SKILL_CHANNELS = ['FEISHU', 'DINGTALK'] as const
type Category = (typeof CATEGORIES)[number]

const CATEGORY_KEYS: Record<Category, { label: string; desc: string }> = {
  PUBLISH: { label: 'notification.preferences.publish', desc: 'notification.preferences.publishDesc' },
  REVIEW: { label: 'notification.preferences.review', desc: 'notification.preferences.reviewDesc' },
  PROMOTION: { label: 'notification.preferences.promotion', desc: 'notification.preferences.promotionDesc' },
  REPORT: { label: 'notification.preferences.report', desc: 'notification.preferences.reportDesc' },
}

function getEnabled(preferences: NotificationPreferenceItem[], category: string, channel = 'IN_APP'): boolean {
  const item = preferences.find((p) => p.category === category && p.channel === channel)
  // External weekly skill pushes are opt-in; existing in-app operational notifications stay opt-out.
  return item?.enabled ?? !(category === 'WEEKLY_SKILL' && channel !== 'IN_APP')
}

function buildUpdatedPreferences(
  current: NotificationPreferenceItem[],
  category: string,
  enabled: boolean,
  channel = 'IN_APP',
): NotificationPreferenceItem[] {
  const existing = current.find((p) => p.category === category && p.channel === channel)
  if (existing) {
    return current.map((p) =>
      p.category === category && p.channel === channel ? { ...p, enabled } : p,
    )
  }
  return [...current, { category, channel, enabled }]
}

/**
 * Renders the notification preference toggles for all supported categories.
 */
export function NotificationPreferenceForm() {
  const { t } = useTranslation()
  const { data: preferences = [], isLoading } = useNotificationPreferences()
  const { mutate: updatePreferences, isPending } = useUpdateNotificationPreferences()

  function handleToggle(category: string, channel = 'IN_APP') {
    const current = getEnabled(preferences, category, channel)
    const updated = buildUpdatedPreferences(preferences, category, !current, channel)
    updatePreferences(updated)
  }

  return (
    <Card className="glass-strong">
      <CardHeader>
        <CardTitle>{t('notification.preferences.title')}</CardTitle>
        <CardDescription>{t('notification.preferences.description')}</CardDescription>
      </CardHeader>
      <CardContent>
        <div className="divide-y divide-border">
          <div className="pb-4">
            <div className="mb-3 space-y-1">
              <p className="text-sm font-medium">{t('notification.preferences.weeklySkill')}</p>
              <p className="text-xs text-muted-foreground">{t('notification.preferences.weeklySkillDesc')}</p>
            </div>
            <div className="space-y-3 rounded-xl border border-border bg-secondary/20 p-4">
              {WEEKLY_SKILL_CHANNELS.map((channel) => {
                const enabled = getEnabled(preferences, 'WEEKLY_SKILL', channel)
                const toggleId = `pref-toggle-WEEKLY_SKILL-${channel}`
                return (
                  <div key={channel} className="flex items-center justify-between gap-4">
                    <div>
                      <label htmlFor={toggleId} className="text-sm font-medium cursor-pointer">
                        {channel === 'FEISHU' ? t('notification.preferences.feishu') : t('notification.preferences.dingtalk')}
                      </label>
                      <p className="text-xs text-muted-foreground">{t('notification.preferences.weeklySkillOptIn')}</p>
                    </div>
                    <button
                      id={toggleId}
                      role="switch"
                      aria-checked={enabled}
                      disabled={isLoading || isPending}
                      onClick={() => handleToggle('WEEKLY_SKILL', channel)}
                      className={[
                        'relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent',
                        'transition-colors duration-200 ease-in-out focus-visible:outline-none focus-visible:ring-2',
                        'focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50',
                        enabled ? 'bg-primary' : 'bg-input',
                      ].join(' ')}
                    >
                      <span
                        aria-hidden="true"
                        className={[
                          'pointer-events-none inline-block h-5 w-5 rounded-full bg-background shadow-lg',
                          'ring-0 transition duration-200 ease-in-out',
                          enabled ? 'translate-x-5' : 'translate-x-0',
                        ].join(' ')}
                      />
                    </button>
                  </div>
                )
              })}
            </div>
          </div>

          {CATEGORIES.map((category) => {
            const enabled = getEnabled(preferences, category)
            const keys = CATEGORY_KEYS[category]
            const toggleId = `pref-toggle-${category}`

            return (
              <div key={category} className="flex items-center justify-between py-4 first:pt-0 last:pb-0">
                <div className="space-y-0.5">
                  <label htmlFor={toggleId} className="text-sm font-medium cursor-pointer">
                    {t(keys.label)}
                  </label>
                  <p className="text-xs text-muted-foreground">{t(keys.desc)}</p>
                </div>

                {/* Accessible toggle switch */}
                <button
                  id={toggleId}
                  role="switch"
                  aria-checked={enabled}
                  disabled={isLoading || isPending}
                  onClick={() => handleToggle(category)}
                  className={[
                    'relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent',
                    'transition-colors duration-200 ease-in-out focus-visible:outline-none focus-visible:ring-2',
                    'focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50',
                    enabled ? 'bg-primary' : 'bg-input',
                  ].join(' ')}
                >
                  <span
                    aria-hidden="true"
                    className={[
                      'pointer-events-none inline-block h-5 w-5 rounded-full bg-background shadow-lg',
                      'ring-0 transition duration-200 ease-in-out',
                      enabled ? 'translate-x-5' : 'translate-x-0',
                    ].join(' ')}
                  />
                </button>
              </div>
            )
          })}
        </div>
      </CardContent>
    </Card>
  )
}
