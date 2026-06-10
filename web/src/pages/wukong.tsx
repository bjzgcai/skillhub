import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { AlertCircle, Check, Download, Loader2, RefreshCw, Search } from 'lucide-react'
import type { SkillSummary } from '@/api/types'
import i18n from '@/i18n/config'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { dedupeSkillsBySlug } from '@/shared/lib/skill-dedupe'
import { getSkillAvatar } from '@/shared/lib/skill-avatar'
import { getHeadlineVersion } from '@/shared/lib/skill-lifecycle'
import {
  WUKONG_BRIDGE_FALLBACK_DELAY_MS,
  callWukongBridge,
  getInstalledSkillKeys,
  isWukongBridgeEventMessage,
  isWukongEmbedded,
  isWukongHostMessage,
  normalizeInstalledSkills,
} from '@/shared/lib/wukong-bridge'
import { formatCompactCount } from '@/shared/lib/number-format'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Card } from '@/shared/ui/card'

const PAGE_SIZE = 30

type InstallState = 'idle' | 'installing' | 'installed' | 'failed'

function normalizeIdentity(value: string | number | undefined): string {
  return value === undefined ? '' : String(value).trim().toLowerCase()
}

function getSkillIdentityKeys(skill: SkillSummary): string[] {
  const keys = [
    skill.slug,
    skill.displayName,
    `${skill.namespace}/${skill.slug}`,
    skill.id,
  ].map(normalizeIdentity)

  return keys.filter(Boolean)
}

function isSkillInstalled(skill: SkillSummary, installedKeys: Set<string>): boolean {
  return getSkillIdentityKeys(skill).some((key) => installedKeys.has(key))
}

function getSkillInstallKey(skill: SkillSummary): string {
  return `${skill.namespace}/${skill.slug}`
}

function getDownloadUrl(skill: SkillSummary, version: string): string {
  const namespace = skill.namespace.startsWith('@') ? skill.namespace.slice(1) : skill.namespace
  const path = `/api/v1/skills/${encodeURIComponent(namespace)}/${encodeURIComponent(skill.slug)}/versions/${encodeURIComponent(version)}/download`

  if (typeof window === 'undefined') {
    return path
  }

  return `${window.location.origin}${path}`
}

function applyWukongTheme(theme: string | undefined) {
  if (typeof document === 'undefined') {
    return
  }

  document.documentElement.classList.toggle('dark', theme === 'dark')
}

function getInitialTheme(): string | undefined {
  if (typeof window === 'undefined') {
    return undefined
  }
  return new URLSearchParams(window.location.search).get('theme') ?? undefined
}

function applyWukongLanguage(language: string | undefined) {
  if (!language) {
    return
  }
  void i18n.changeLanguage(language)
}

function getInstallState(skill: SkillSummary, installingKey: string | null, installedKeys: Set<string>, failedKeys: Set<string>): InstallState {
  const installKey = getSkillInstallKey(skill)
  if (installingKey === installKey) {
    return 'installing'
  }
  if (isSkillInstalled(skill, installedKeys)) {
    return 'installed'
  }
  if (failedKeys.has(installKey)) {
    return 'failed'
  }
  return 'idle'
}

export function WukongPage() {
  const { t } = useTranslation()
  const [queryInput, setQueryInput] = useState('')
  const [query, setQuery] = useState('')
  const [installedKeys, setInstalledKeys] = useState<Set<string>>(new Set())
  const [installingKey, setInstallingKey] = useState<string | null>(null)
  const [failedKeys, setFailedKeys] = useState<Set<string>>(new Set())
  const [bridgeReady, setBridgeReady] = useState(false)
  const [installedLoading, setInstalledLoading] = useState(false)

  const { data, isLoading, isFetching, refetch } = useSearchSkills({
    q: query,
    source: 'internal',
    sort: query ? 'relevance' : 'downloads',
    page: 0,
    size: PAGE_SIZE,
  })

  const skills = useMemo(() => dedupeSkillsBySlug(data?.items ?? []), [data?.items])

  const refreshInstalledSkills = useCallback(async () => {
    if (!isWukongEmbedded()) {
      setBridgeReady(false)
      return
    }

    setInstalledLoading(true)
    try {
      const response = await callWukongBridge('skill.list')
      setInstalledKeys(getInstalledSkillKeys(normalizeInstalledSkills(response)))
      setBridgeReady(true)
    } catch {
      setBridgeReady(false)
    } finally {
      setInstalledLoading(false)
    }
  }, [])

  useEffect(() => {
    applyWukongTheme(getInitialTheme())

    function handleBridgeEvent(event: MessageEvent<unknown>) {
      if (!isWukongHostMessage(event) || !isWukongBridgeEventMessage(event.data)) {
        return
      }

      if (event.data.event === 'skills:ready' || event.data.event === 'skills:changed') {
        setBridgeReady(true)
        void refreshInstalledSkills()
      }

      if (event.data.event === 'page.themeChanged') {
        applyWukongTheme(event.data.data?.theme)
      }

      if (event.data.event === 'page.languageChanged') {
        applyWukongLanguage(event.data.data?.language)
      }
    }

    window.addEventListener('message', handleBridgeEvent)
    const fallbackTimer = window.setTimeout(() => {
      if (isWukongEmbedded()) {
        void refreshInstalledSkills()
      }
    }, WUKONG_BRIDGE_FALLBACK_DELAY_MS)

    return () => {
      window.clearTimeout(fallbackTimer)
      window.removeEventListener('message', handleBridgeEvent)
    }
  }, [refreshInstalledSkills])

  const handleSearchSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setQuery(queryInput.trim())
  }

  const handleInstall = async (skill: SkillSummary) => {
    const version = getHeadlineVersion(skill)?.version
    const installKey = getSkillInstallKey(skill)

    if (!version || !bridgeReady) {
      setFailedKeys((current) => new Set(current).add(installKey))
      return
    }

    setFailedKeys((current) => {
      const next = new Set(current)
      next.delete(installKey)
      return next
    })
    setInstallingKey(installKey)

    try {
      await callWukongBridge('skill.installFromUrl', {
        url: getDownloadUrl(skill, version),
        name: skill.slug,
      })
      setInstalledKeys((current) => new Set(current).add(normalizeIdentity(skill.slug)))
      await refreshInstalledSkills()
    } catch {
      setFailedKeys((current) => new Set(current).add(installKey))
    } finally {
      setInstallingKey((current) => current === installKey ? null : current)
    }
  }

  return (
    <div className="min-h-screen bg-background">
      <div className="mx-auto flex w-full max-w-6xl flex-col gap-5 px-4 py-4 sm:px-6 md:py-6">
        <header className="flex flex-col gap-4 border-b border-border pb-4 md:flex-row md:items-center md:justify-between">
          <div className="min-w-0">
            <h1 className="text-2xl font-semibold tracking-tight text-foreground">
              {t('wukong.title')}
            </h1>
            <p className="mt-1 text-sm text-muted-foreground">
              {t('wukong.subtitle')}
            </p>
          </div>

          <div className="flex shrink-0 items-center gap-2">
            <span className={bridgeReady ? 'rounded-md bg-emerald-500/10 px-2.5 py-1 text-xs font-medium text-emerald-700 dark:text-emerald-400' : 'rounded-md bg-amber-500/10 px-2.5 py-1 text-xs font-medium text-amber-700 dark:text-amber-400'}>
              {bridgeReady ? t('wukong.bridge.ready') : t('wukong.bridge.unavailable')}
            </span>
            <Button
              type="button"
              variant="outline"
              size="icon"
              aria-label={t('wukong.refresh')}
              onClick={() => {
                void refetch()
                void refreshInstalledSkills()
              }}
            >
              <RefreshCw className={isFetching || installedLoading ? 'h-4 w-4 animate-spin' : 'h-4 w-4'} />
            </Button>
          </div>
        </header>

        <form className="flex gap-2" onSubmit={handleSearchSubmit}>
          <div className="relative flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              className="pl-9"
              value={queryInput}
              placeholder={t('wukong.searchPlaceholder')}
              onChange={(event) => setQueryInput(event.target.value)}
            />
          </div>
          <Button type="submit">
            {t('wukong.search')}
          </Button>
        </form>

        {!bridgeReady && (
          <div className="flex items-start gap-2 rounded-md border border-amber-500/30 bg-amber-500/10 p-3 text-sm text-amber-800 dark:text-amber-300">
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
            <span>{t('wukong.bridge.preview')}</span>
          </div>
        )}

        {isLoading ? (
          <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
            {Array.from({ length: 6 }).map((_, index) => (
              <div key={index} className="h-36 animate-shimmer rounded-lg border border-border bg-card" />
            ))}
          </div>
        ) : skills.length ? (
          <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
            {skills.map((skill) => (
              <WukongSkillCard
                key={`${skill.namespace}/${skill.slug}`}
                skill={skill}
                installState={getInstallState(skill, installingKey, installedKeys, failedKeys)}
                bridgeReady={bridgeReady}
                onInstall={() => void handleInstall(skill)}
              />
            ))}
          </div>
        ) : (
          <div className="rounded-lg border border-dashed border-border bg-card p-8 text-center">
            <p className="text-sm font-medium text-foreground">{t('wukong.empty.title')}</p>
            <p className="mt-1 text-sm text-muted-foreground">{t('wukong.empty.description')}</p>
          </div>
        )}
      </div>
    </div>
  )
}

interface WukongSkillCardProps {
  skill: SkillSummary
  installState: InstallState
  bridgeReady: boolean
  onInstall: () => void
}

function WukongSkillCard({ skill, installState, bridgeReady, onInstall }: WukongSkillCardProps) {
  const { t } = useTranslation()
  const avatar = getSkillAvatar(skill.displayName, skill.slug)
  const version = getHeadlineVersion(skill)?.version
  const disabled = installState === 'installing' || installState === 'installed' || !bridgeReady || !version
  const actionLabel = installState === 'installing'
    ? t('wukong.installing')
    : installState === 'installed'
      ? t('wukong.installed')
      : installState === 'failed'
        ? t('wukong.retry')
        : t('wukong.install')

  return (
    <Card className="flex min-h-36 flex-col gap-4 rounded-lg border bg-card p-4 shadow-sm">
      <div className="flex items-start gap-3">
        <div className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-lg text-lg font-bold ring-1 ${avatar.color.bg} ${avatar.color.text} ${avatar.color.ring}`}>
          {avatar.initial}
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <h2 className="truncate text-base font-semibold text-foreground">
              {skill.displayName}
            </h2>
            {version && (
              <span className="rounded-md bg-secondary px-2 py-0.5 font-mono text-xs text-muted-foreground">
                v{version}
              </span>
            )}
          </div>
          <p className="mt-1 font-mono text-xs text-muted-foreground">
            {skill.namespace}/{skill.slug}
          </p>
        </div>
      </div>

      {skill.summary && (
        <p className="line-clamp-2 text-sm leading-6 text-muted-foreground">
          {skill.summary}
        </p>
      )}

      <div className="mt-auto flex items-center justify-between gap-3">
        <span className="text-xs text-muted-foreground">
          {t('wukong.downloads', { value: formatCompactCount(skill.downloadCount) })}
        </span>
        <Button
          type="button"
          size="sm"
          variant={installState === 'failed' ? 'outline' : 'default'}
          disabled={disabled}
          onClick={onInstall}
        >
          {installState === 'installing' ? (
            <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />
          ) : installState === 'installed' ? (
            <Check className="mr-1.5 h-3.5 w-3.5" />
          ) : (
            <Download className="mr-1.5 h-3.5 w-3.5" />
          )}
          {actionLabel}
        </Button>
      </div>
    </Card>
  )
}
