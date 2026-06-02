import { useEffect, useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { UploadZone } from '@/features/publish/upload-zone'
import { Button } from '@/shared/ui/button'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  normalizeSelectValue,
} from '@/shared/ui/select'
import { Label } from '@/shared/ui/label'
import { Card } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Textarea } from '@/shared/ui/textarea'
import { usePublishDisplayMetadataPreview, usePublishSkill } from '@/shared/hooks/use-skill-queries'
import { useMyNamespaces } from '@/shared/hooks/use-namespace-queries'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { toast } from '@/shared/lib/toast'
import { ApiError } from '@/api/client'

/**
 * Skill publish page used inside the dashboard.
 *
 * It coordinates namespace selection, visibility selection, zip upload, and backend publish error
 * translation into user-facing toasts.
 */
function isVersionExistsMessage(message?: string): boolean {
  if (!message) {
    return false
  }

  return message.includes('error.skill.version.exists')
    || message.includes('Version already exists')
    || message.includes('版本已存在')
}

function isPrecheckFailureMessage(message?: string): boolean {
  if (!message) {
    return false
  }

  return message.includes('error.skill.publish.precheck.failed')
    || message.includes('Pre-publish validation failed')
    || message.includes('预发布校验失败')
    || message.includes('looks like a secret or token')
}

function isFrontmatterFailureMessage(message?: string): boolean {
  if (!message) {
    return false
  }

  return message.includes('Invalid SKILL.md frontmatter')
    || message.includes('技能包校验失败：Invalid SKILL.md frontmatter')
}

const EMPTY_NAMESPACE_VALUE = '__select_namespace__'

export function PublishPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [namespaceSlug, setNamespaceSlug] = useState<string>('')
  const [visibility, setVisibility] = useState<string>('PUBLIC')
  const [displayName, setDisplayName] = useState<string>('')
  const [summary, setSummary] = useState<string>('')
  const [previewSlug, setPreviewSlug] = useState<string>('')
  const [isExistingSkill, setIsExistingSkill] = useState<boolean | null>(null)
  const [isPreviewingDisplayMetadata, setIsPreviewingDisplayMetadata] = useState(false)

  const { data: namespaces, isLoading: isLoadingNamespaces } = useMyNamespaces()
  const previewMutation = usePublishDisplayMetadataPreview()
  const publishMutation = usePublishSkill()
  const selectedNamespace = namespaces?.find((ns) => ns.slug === namespaceSlug)
  const namespaceOnlyLabel = selectedNamespace?.type === 'GLOBAL'
    ? t('publish.visibilityOptions.loggedInUsersOnly')
    : t('publish.visibilityOptions.namespaceOnly')
  const publishDisabledReason = selectedFile && !namespaceSlug
    ? t('publish.displayMetadataPreview.selectNamespaceFirst')
    : null

  useEffect(() => {
    if (namespaceSlug || !namespaces?.length) {
      return
    }
    const defaultNamespace = namespaces.find((ns) => ns.type === 'GLOBAL') ?? namespaces[0]
    setNamespaceSlug(defaultNamespace.slug)
  }, [namespaceSlug, namespaces])

  useEffect(() => {
    if (!selectedFile || !namespaceSlug) {
      setPreviewSlug('')
      setIsExistingSkill(null)
      setIsPreviewingDisplayMetadata(false)
      return
    }

    let cancelled = false
    setIsPreviewingDisplayMetadata(true)
    previewMutation.mutateAsync({ namespace: namespaceSlug, file: selectedFile })
      .then((preview) => {
        if (cancelled) {
          return
        }
        setPreviewSlug(preview.slug)
        setIsExistingSkill(preview.existingSkill)
        setDisplayName(preview.existingSkill ? preview.displayName : '')
        setSummary(preview.existingSkill ? preview.summary : '')
        setIsPreviewingDisplayMetadata(false)
      })
      .catch(() => {
        if (cancelled) {
          return
        }
        setPreviewSlug('')
        setIsExistingSkill(null)
        setIsPreviewingDisplayMetadata(false)
      })

    return () => {
      cancelled = true
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedFile, namespaceSlug])

  const resetDisplayMetadataPreview = () => {
    setDisplayName('')
    setSummary('')
    setPreviewSlug('')
    setIsExistingSkill(null)
  }

  const handleNamespaceChange = (value: string) => {
    const nextNamespace = value === EMPTY_NAMESPACE_VALUE ? '' : value
    setNamespaceSlug(nextNamespace)
    resetDisplayMetadataPreview()
    setIsPreviewingDisplayMetadata(Boolean(selectedFile && nextNamespace))
  }

  const handleFileSelect = (file: File) => {
    setSelectedFile(file)
    resetDisplayMetadataPreview()
    setIsPreviewingDisplayMetadata(Boolean(namespaceSlug))
  }

  const handleRemoveSelectedFile = () => {
    setSelectedFile(null)
    resetDisplayMetadataPreview()
    setIsPreviewingDisplayMetadata(false)
  }

  const handlePublish = async () => {
    if (!selectedFile || !namespaceSlug) {
      toast.error(t('publish.selectRequired'))
      return
    }

    try {
      const result = await publishMutation.mutateAsync({
        namespace: namespaceSlug,
        file: selectedFile,
        visibility,
        displayName,
        summary,
      })
      const skillLabel = `${result.namespace}/${result.slug}@${result.version}`
      if (result.status === 'PUBLISHED') {
        toast.success(
          t('publish.publishedTitle'),
          t('publish.publishedDescription', { skill: skillLabel })
        )
      } else {
        toast.success(
          t('publish.pendingReviewTitle'),
          t('publish.pendingReviewDescription', { skill: skillLabel })
        )
      }
      navigate({ to: '/dashboard/skills' })
    } catch (error) {
      if (error instanceof ApiError && error.status === 408) {
        toast.error(t('publish.timeoutTitle'), t('publish.timeoutDescription'))
        return
      }

      if (error instanceof ApiError && isVersionExistsMessage(error.serverMessage || error.message)) {
        toast.error(
          t('publish.versionExistsTitle'),
          t('publish.versionExistsDescription'),
        )
        return
      }

      if (error instanceof ApiError && isPrecheckFailureMessage(error.serverMessage || error.message)) {
        toast.error(
          t('publish.precheckFailedTitle'),
          error.serverMessage || t('publish.precheckFailedDescription'),
        )
        return
      }

      if (error instanceof ApiError && isFrontmatterFailureMessage(error.serverMessage || error.message)) {
        toast.error(
          t('publish.frontmatterFailedTitle'),
          error.serverMessage || t('publish.frontmatterFailedDescription'),
        )
        return
      }

      toast.error(t('publish.error'), error instanceof Error ? error.message : '')
    }
  }

  return (
    <div className="max-w-2xl mx-auto space-y-8 animate-fade-up">
      <DashboardPageHeader title={t('publish.title')} subtitle={t('publish.subtitle')} />

      <Card className="p-4 bg-blue-500/5 border-blue-500/20">
        <div className="flex items-start gap-3">
          <svg className="w-5 h-5 text-blue-500 mt-0.5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <div className="flex-1">
            <h3 className="text-sm font-semibold text-foreground mb-1">{t('publish.reviewNotice.title')}</h3>
            <p className="text-sm text-muted-foreground">{t('publish.reviewNotice.description')}</p>
          </div>
        </div>
      </Card>

      <Card className="p-4 bg-amber-500/5 border-amber-500/20">
        <div className="flex items-start gap-3">
          <svg className="w-5 h-5 text-amber-500 mt-0.5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v4m0 4h.01M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" />
          </svg>
          <div className="flex-1 space-y-3">
            <div>
              <h3 className="text-sm font-semibold text-foreground mb-1">{t('publish.displayGuidance.title')}</h3>
              <p className="text-sm text-muted-foreground">{t('publish.displayGuidance.description')}</p>
            </div>
            <ul className="space-y-1 text-sm text-muted-foreground list-disc pl-5">
              <li>{t('publish.displayGuidance.nameRule')}</li>
              <li>{t('publish.displayGuidance.descriptionRule')}</li>
              <li>{t('publish.displayGuidance.bodyRule')}</li>
            </ul>
            <pre className="overflow-x-auto rounded-lg bg-background/70 border border-border/60 p-3 text-xs text-foreground">
              <code>{t('publish.displayGuidance.example')}</code>
            </pre>
          </div>
        </div>
      </Card>

      <Card className="p-8 space-y-8">
        <div className="space-y-3">
          <Label htmlFor="namespace" className="text-sm font-semibold font-heading">{t('publish.namespace')}</Label>
          {isLoadingNamespaces ? (
            <div className="h-11 animate-shimmer rounded-lg" />
          ) : (
            <Select
              value={normalizeSelectValue(namespaceSlug) ?? EMPTY_NAMESPACE_VALUE}
              onValueChange={handleNamespaceChange}
            >
              <SelectTrigger id="namespace">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={EMPTY_NAMESPACE_VALUE}>{t('publish.selectNamespace')}</SelectItem>
                {namespaces?.map((ns) => (
                  <SelectItem key={ns.id} value={ns.slug}>
                    {ns.displayName} (@{ns.slug})
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
        </div>

        <div className="space-y-3">
          <Label htmlFor="visibility" className="text-sm font-semibold font-heading">{t('publish.visibility')}</Label>
          <Select value={visibility} onValueChange={setVisibility}>
            <SelectTrigger id="visibility">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="PUBLIC">{t('publish.visibilityOptions.public')}</SelectItem>
              <SelectItem value="NAMESPACE_ONLY">{namespaceOnlyLabel}</SelectItem>
              <SelectItem value="PRIVATE">{t('publish.visibilityOptions.private')}</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-3">
          <Label className="text-sm font-semibold font-heading">{t('publish.file')}</Label>
          <UploadZone
            key={selectedFile ? `${selectedFile.name}-${selectedFile.lastModified}` : 'empty'}
            onFileSelect={handleFileSelect}
            disabled={publishMutation.isPending}
          />
          {selectedFile && (
            <div className="flex items-center justify-between gap-3 rounded-lg border border-border/60 bg-secondary/30 px-4 py-3">
              <div className="min-w-0 text-sm text-muted-foreground flex items-center gap-2">
                <svg className="w-4 h-4 text-emerald-500 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                </svg>
                <span className="truncate">
                  {selectedFile.name} ({(selectedFile.size / 1024).toFixed(1)} KB)
                </span>
              </div>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={handleRemoveSelectedFile}
                disabled={publishMutation.isPending}
              >
                {t('publish.removeSelectedFile')}
              </Button>
            </div>
          )}
        </div>

        <div className="space-y-3">
          <div className="space-y-2">
            <Label htmlFor="displayName" className="text-sm font-semibold font-heading">{t('publish.displayName')}</Label>
            <Input
              id="displayName"
              value={displayName}
              onChange={(event) => setDisplayName(event.target.value)}
              placeholder={t('publish.displayNamePlaceholder')}
              maxLength={100}
              disabled={publishMutation.isPending || isPreviewingDisplayMetadata}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="summary" className="text-sm font-semibold font-heading">{t('publish.summary')}</Label>
            <Textarea
              id="summary"
              value={summary}
              onChange={(event) => setSummary(event.target.value)}
              placeholder={t('publish.summaryPlaceholder')}
              rows={3}
              maxLength={300}
              disabled={publishMutation.isPending || isPreviewingDisplayMetadata}
            />
          </div>
          {selectedFile && (
            <p className="text-xs text-muted-foreground">
              {publishDisabledReason
                ?? (isPreviewingDisplayMetadata
                  ? t('publish.displayMetadataPreview.loading')
                  : isExistingSkill
                    ? t('publish.displayMetadataPreview.existing', { slug: previewSlug })
                    : t('publish.displayMetadataPreview.new'))}
            </p>
          )}
        </div>

        <Button
          className="w-full text-primary-foreground disabled:text-primary-foreground"
          size="lg"
          onClick={handlePublish}
          disabled={!selectedFile || !namespaceSlug || publishMutation.isPending || isPreviewingDisplayMetadata}
        >
          {publishMutation.isPending ? t('publish.publishing') : t('publish.confirm')}
        </Button>
      </Card>
    </div>
  )
}
