import { FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useSetWeeklyRecommendation } from '@/features/admin/use-admin-recommendations'
import { Card } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Textarea } from '@/shared/ui/textarea'
import { Label } from '@/shared/ui/label'
import { Button } from '@/shared/ui/button'
import { toast } from '@/shared/lib/toast'
import type { RecommendationItem } from '@/api/types'

interface WeeklyRecommendationFormState {
  namespace: string
  slug: string
  title: string
  summary: string
  reason: string
  backgroundImageUrl: string
  guideContent: string
  priority: string
  startAt: string
  endAt: string
}

const INITIAL_FORM: WeeklyRecommendationFormState = {
  namespace: '',
  slug: '',
  title: '',
  summary: '',
  reason: '',
  backgroundImageUrl: '',
  guideContent: '',
  priority: '20000',
  startAt: '',
  endAt: '',
}

export function toInstant(value: string): string | undefined {
  if (!value.trim()) {
    return undefined
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString()
}

export function normalizeForm(form: WeeklyRecommendationFormState) {
  return {
    namespace: form.namespace.trim(),
    slug: form.slug.trim(),
    title: form.title.trim() || undefined,
    summary: form.summary.trim() || undefined,
    reason: form.reason.trim() || undefined,
    backgroundImageUrl: form.backgroundImageUrl.trim() || undefined,
    guideContent: form.guideContent.trim() || undefined,
    priority: form.priority.trim() ? Number(form.priority) : undefined,
    startAt: toInstant(form.startAt),
    endAt: toInstant(form.endAt),
  }
}

export function AdminWeeklyRecommendationPage() {
  const { t } = useTranslation()
  const [form, setForm] = useState<WeeklyRecommendationFormState>(INITIAL_FORM)
  const [saved, setSaved] = useState<RecommendationItem | null>(null)
  const setWeeklyMutation = useSetWeeklyRecommendation()

  const updateField = (field: keyof WeeklyRecommendationFormState, value: string) => {
    setForm((current) => ({ ...current, [field]: value }))
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const payload = normalizeForm(form)
    if (!payload.namespace || !payload.slug) {
      toast.error(t('adminWeekly.validationTitle'), t('adminWeekly.validationDescription'))
      return
    }
    if (payload.priority !== undefined && (!Number.isFinite(payload.priority) || payload.priority < 0)) {
      toast.error(t('adminWeekly.priorityValidationTitle'), t('adminWeekly.priorityValidationDescription'))
      return
    }
    if ((form.startAt.trim() && !payload.startAt) || (form.endAt.trim() && !payload.endAt)) {
      toast.error(t('adminWeekly.dateValidationTitle'), t('adminWeekly.dateValidationDescription'))
      return
    }
    if (payload.startAt && payload.endAt && new Date(payload.endAt).getTime() <= new Date(payload.startAt).getTime()) {
      toast.error(t('adminWeekly.dateWindowValidationTitle'), t('adminWeekly.dateWindowValidationDescription'))
      return
    }

    try {
      const result = await setWeeklyMutation.mutateAsync(payload)
      setSaved(result)
      toast.success(t('adminWeekly.saveSuccessTitle'), t('adminWeekly.saveSuccessDescription'))
    } catch (error) {
      toast.error(t('adminWeekly.saveErrorTitle'), error instanceof Error ? error.message : t('adminWeekly.saveErrorDescription'))
    }
  }

  return (
    <div className="container mx-auto max-w-4xl px-4 py-10">
      <div className="mb-8">
        <p className="mb-2 text-sm font-medium text-primary">{t('adminWeekly.eyebrow')}</p>
        <h1 className="text-4xl font-bold font-heading">{t('adminWeekly.title')}</h1>
        <p className="mt-3 max-w-2xl text-muted-foreground">{t('adminWeekly.subtitle')}</p>
      </div>

      <Card className="p-6">
        <form className="space-y-6" onSubmit={handleSubmit}>
          <div className="rounded-xl border border-primary/20 bg-primary/5 p-4 text-sm text-muted-foreground">
            {t('adminWeekly.guardrail')}
          </div>

          <div className="grid gap-4 md:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="weekly-namespace">{t('adminWeekly.namespace')}</Label>
              <Input
                id="weekly-namespace"
                value={form.namespace}
                onChange={(event) => updateField('namespace', event.target.value)}
                placeholder="global"
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="weekly-slug">{t('adminWeekly.slug')}</Label>
              <Input
                id="weekly-slug"
                value={form.slug}
                onChange={(event) => updateField('slug', event.target.value)}
                placeholder="skill-vetter"
                required
              />
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="weekly-title">{t('adminWeekly.formTitle')}</Label>
            <Input
              id="weekly-title"
              value={form.title}
              onChange={(event) => updateField('title', event.target.value)}
              placeholder={t('adminWeekly.titlePlaceholder')}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="weekly-summary">{t('adminWeekly.summary')}</Label>
            <Textarea
              id="weekly-summary"
              value={form.summary}
              onChange={(event) => updateField('summary', event.target.value)}
              placeholder={t('adminWeekly.summaryPlaceholder')}
              rows={4}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="weekly-background">{t('adminWeekly.backgroundImageUrl')}</Label>
            <Input
              id="weekly-background"
              value={form.backgroundImageUrl}
              onChange={(event) => updateField('backgroundImageUrl', event.target.value)}
              placeholder="https://example.com/weekly-banner.jpg"
            />
            <p className="text-xs text-muted-foreground">{t('adminWeekly.backgroundImageHint')}</p>
          </div>

          <div className="space-y-2">
            <Label htmlFor="weekly-guide-content">{t('adminWeekly.guideContent')}</Label>
            <Textarea
              id="weekly-guide-content"
              value={form.guideContent}
              onChange={(event) => updateField('guideContent', event.target.value)}
              placeholder='{"intro":"...","sections":[],"media":[]}'
              rows={6}
            />
            <p className="text-xs text-muted-foreground">{t('adminWeekly.guideContentHint')}</p>
          </div>

          <div className="grid gap-4 md:grid-cols-3">
            <div className="space-y-2">
              <Label htmlFor="weekly-reason">{t('adminWeekly.reason')}</Label>
              <Input
                id="weekly-reason"
                value={form.reason}
                onChange={(event) => updateField('reason', event.target.value)}
                placeholder={t('adminWeekly.reasonPlaceholder')}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="weekly-priority">{t('adminWeekly.priority')}</Label>
              <Input
                id="weekly-priority"
                type="number"
                min={0}
                value={form.priority}
                onChange={(event) => updateField('priority', event.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label>{t('adminWeekly.badge')}</Label>
              <div className="flex h-11 items-center rounded-lg border border-border bg-muted/50 px-4 text-sm font-semibold text-foreground">
                WEEKLY_SKILL
              </div>
            </div>
          </div>

          <div className="grid gap-4 md:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="weekly-start">{t('adminWeekly.startAt')}</Label>
              <Input
                id="weekly-start"
                type="datetime-local"
                value={form.startAt}
                onChange={(event) => updateField('startAt', event.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="weekly-end">{t('adminWeekly.endAt')}</Label>
              <Input
                id="weekly-end"
                type="datetime-local"
                value={form.endAt}
                onChange={(event) => updateField('endAt', event.target.value)}
              />
            </div>
          </div>

          <div className="flex items-center justify-between gap-3">
            <p className="text-sm text-muted-foreground">{t('adminWeekly.dateHint')}</p>
            <Button type="submit" disabled={setWeeklyMutation.isPending}>
              {setWeeklyMutation.isPending ? t('adminWeekly.saving') : t('adminWeekly.saveAction')}
            </Button>
          </div>
        </form>
      </Card>

      {saved ? (
        <Card className="mt-6 p-6">
          <h2 className="mb-3 text-lg font-semibold">{t('adminWeekly.savedTitle')}</h2>
          <dl className="grid gap-3 text-sm md:grid-cols-2">
            <div>
              <dt className="text-muted-foreground">{t('adminWeekly.savedSkill')}</dt>
              <dd className="font-medium">{saved.namespace}/{saved.slug}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">{t('adminWeekly.savedBadge')}</dt>
              <dd className="font-medium">{saved.badge}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">{t('adminWeekly.savedPriority')}</dt>
              <dd className="font-medium">{saved.priority}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">{t('adminWeekly.savedWindow')}</dt>
              <dd className="font-medium">{saved.startAt ?? '-'} → {saved.endAt ?? '-'}</dd>
            </div>
            <div className="md:col-span-2">
              <dt className="text-muted-foreground">{t('adminWeekly.savedBackground')}</dt>
              <dd className="break-all font-medium">{saved.backgroundImageUrl ?? '-'}</dd>
            </div>
          </dl>
        </Card>
      ) : null}
    </div>
  )
}
