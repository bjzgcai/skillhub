import { useNavigate, useSearch } from '@tanstack/react-router'
import { ArrowLeft, Bell, ExternalLink, ListChecks, PlayCircle, Presentation, ShieldCheck, Terminal } from 'lucide-react'
import { useCurrentWeeklySkill, useHistoryWeeklySkills } from '@/shared/hooks/use-skill-queries'
import { Card } from '@/shared/ui/card'
import { Button } from '@/shared/ui/button'
import { WeeklySkillCard } from '@/features/recommendation/weekly-skill-card'
import { InstallCommand } from '@/features/skill/install-command'

interface GuideSection {
  title: string
  body: string
  bullets?: string[]
  example?: GuideConversationExample
}

interface GuideMedia {
  type: 'video' | 'ppt'
  title: string
  description: string
  embedUrl?: string
  href?: string
}

interface GuideConversationExample {
  user: string
  agent: string
}

interface WeeklyGuideContent {
  intro: string
  sections: GuideSection[]
  media: GuideMedia[]
}

export function getGuideContent(guideContentJson?: string): WeeklyGuideContent | null {
  if (!guideContentJson) return null
  try {
    return JSON.parse(guideContentJson)
  } catch {
    return null
  }
}

function MediaCard({ media }: { media: GuideMedia }) {
  const Icon = media.type === 'video' ? PlayCircle : Presentation

  return (
    <Card className="overflow-hidden border-dashed bg-white/80">
      {media.embedUrl ? (
        <div className="aspect-video bg-slate-950">
          {media.type === 'video' ? (
            <video className="h-full w-full" src={media.embedUrl} controls preload="metadata" />
          ) : (
            <iframe className="h-full w-full" src={media.embedUrl} title={media.title} loading="lazy" />
          )}
        </div>
      ) : (
        <div className="flex aspect-video items-center justify-center bg-[radial-gradient(circle_at_50%_40%,rgba(190,55,72,0.14),transparent_38%),linear-gradient(135deg,#fff7f2,#f8e8e4)]">
          <div className="rounded-full bg-white/80 p-5 shadow-sm ring-1 ring-rose-100">
            <Icon className="h-10 w-10 text-rose-700" />
          </div>
        </div>
      )}
      <div className="space-y-2 p-5">
        <div className="flex items-center gap-2 text-sm font-semibold text-rose-800">
          <Icon className="h-4 w-4" />
          {media.type === 'video' ? '视频' : 'PPT'}
        </div>
        <h3 className="text-lg font-semibold text-slate-950">{media.title}</h3>
        <p className="text-sm leading-6 text-muted-foreground">{media.description}</p>
        {media.href ? (
          <a
            href={media.href}
            target="_blank"
            rel="noreferrer"
            className="inline-flex h-8 items-center rounded-md border border-border px-3 text-xs font-medium transition-colors hover:bg-secondary"
          >
            打开资料
          </a>
        ) : null}
      </div>
    </Card>
  )
}

export function pickPrimaryMedia(media: GuideMedia[]): GuideMedia | null {
  const playableMedia = media.filter((item) => item.embedUrl || item.href)
  return playableMedia.find((item) => item.type === 'video') ?? playableMedia[0] ?? null
}

export function RecommendationDetailPage() {
  const navigate = useNavigate()
  const { slug: querySlug } = useSearch({ from: '/recommendations/weekly' })
  const { data: recommendation, isLoading } = useCurrentWeeklySkill()
  const { data: historyData } = useHistoryWeeklySkills(0, 10)
  const historyItems = historyData?.items ?? []

  const activeItem = querySlug
    ? historyItems.find((h) => h.slug === querySlug)
    : null
  const activeSkill = activeItem?.skill ?? recommendation?.skill
  const activeRecommendation = activeItem ?? recommendation
  const skill = activeSkill
  const guide = getGuideContent(activeRecommendation?.guideContent ?? recommendation?.guideContent)
  const primaryMedia = guide ? pickPrimaryMedia(guide.media) : null

  const openSkill = () => {
    if (!skill) return
    navigate({ to: '/space/$namespace/$slug', params: { namespace: skill.namespace, slug: skill.slug } })
  }

  // P1: if user visits ?slug=xxx directly, wait for history data before rendering
  if (querySlug && !historyData) {
    return <div className="container mx-auto px-4 py-10 text-sm text-muted-foreground">Loading...</div>
  }

  if (isLoading) {
    return <div className="container mx-auto px-4 py-10 text-sm text-muted-foreground">Loading...</div>
  }

  if (!recommendation || !skill) {
    if (!activeItem && !recommendation) {
      navigate({ to: '/recommendations', search: { page: 0 }, replace: true })
      return null
    }
  }

  return (
    <div className="container mx-auto max-w-6xl px-4 py-10">
      <Button variant="ghost" onClick={() => navigate({ to: '/recommendations', search: { page: 0 } })} className="mb-5">
        <ArrowLeft className="mr-2 h-4 w-4" />
        返回推荐
      </Button>

      <WeeklySkillCard recommendation={activeRecommendation ?? recommendation!} showActions={false} />

      {primaryMedia ? (
        <div className="mt-6 max-w-3xl">
          <MediaCard media={primaryMedia} />
        </div>
      ) : null}

      <div className="mt-8 grid gap-6 lg:grid-cols-[1fr_300px]">
        <div className="space-y-6">
          {guide ? (
            <>
              <Card className="p-6 md:p-7">
                <div className="mb-4 flex items-center gap-3">
                  <div className="rounded-2xl bg-rose-50 p-3 text-rose-700">
                    <ShieldCheck className="h-6 w-6" />
                  </div>
                  <div>
                    <p className="text-sm font-medium text-rose-700">使用介绍</p>
                    <h2 className="text-2xl font-semibold">把推荐变成可执行流程</h2>
                  </div>
                </div>
                <p className="leading-7 text-muted-foreground">{guide.intro}</p>
              </Card>

              <div className="grid gap-5">
                {guide.sections.map((section, index) => (
              <Card key={section.title} className="p-6 md:p-7">
                <div className="mb-3 flex items-center gap-3">
                  <div className="flex h-9 w-9 items-center justify-center rounded-full bg-slate-950 text-sm font-semibold text-white">
                    {index + 1}
                  </div>
                  <h2 className="text-xl font-semibold">{section.title}</h2>
                </div>
                <p className="leading-7 text-muted-foreground">{section.body}</p>
                {section.bullets?.length ? (
                  <ul className="mt-4 grid gap-2 text-sm text-slate-700 sm:grid-cols-2">
                    {section.bullets.map((bullet) => (
                      <li key={bullet} className="flex items-start gap-2 rounded-xl bg-slate-50 px-3 py-2">
                        <ListChecks className="mt-0.5 h-4 w-4 shrink-0 text-rose-700" />
                        <span>{bullet}</span>
                      </li>
                    ))}
                  </ul>
                ) : null}
                {section.example ? (
                  <div className="mt-5 rounded-2xl border bg-slate-50/70 p-4">
                    <div className="mb-3 flex items-center gap-2 text-sm font-semibold text-slate-950">
                      <Terminal className="h-4 w-4" />
                      和 Agent 这样说
                    </div>
                    <p className="text-sm font-semibold text-slate-950">你：</p>
                    <p className="mt-1 text-sm leading-6 text-slate-700">{section.example.user}</p>
                    <p className="mt-3 text-sm font-semibold text-rose-800">Agent：</p>
                    <p className="mt-1 text-sm leading-6 text-muted-foreground">{section.example.agent}</p>
                  </div>
                ) : null}
                  </Card>
                ))}
              </div>
            </>
          ) : (
            <Card className="p-6 md:p-7">
              <div className="mb-4 flex items-center gap-3">
                <div className="rounded-2xl bg-rose-50 p-3 text-rose-700">
                  <ShieldCheck className="h-6 w-6" />
                </div>
                <div>
                  <p className="text-sm font-medium text-rose-700">使用介绍</p>
                  <h2 className="text-2xl font-semibold">详情内容待补充</h2>
                </div>
              </div>
              <p className="leading-7 text-muted-foreground">
                当前本周推荐暂未配置专属教程。可以先进入技能详情查看 README、版本信息和安装命令。
              </p>
            </Card>
          )}
        </div>

        {skill && (
        <aside className="space-y-5">
          <Card className="space-y-4 p-6">
            <div>
              <p className="text-sm text-muted-foreground">推荐技能</p>
              <h3 className="mt-1 text-xl font-semibold">{skill.displayName || skill.slug}</h3>
              <p className="mt-1 font-mono text-sm text-muted-foreground">{skill.namespace}/{skill.slug}</p>
            </div>
            <Button className="w-full" onClick={openSkill}>
              <ExternalLink className="mr-2 h-4 w-4" />
              进入技能详情
            </Button>
            <Button variant="outline" className="w-full" onClick={() => navigate({ to: '/settings/notifications' })}>
              <Bell className="mr-2 h-4 w-4" />
              设置每周提醒
            </Button>
          </Card>

          <Card className="space-y-3 p-6">
            <div className="flex items-center gap-2 text-sm font-semibold text-slate-950">
              <Terminal className="h-4 w-4" />
              快速开始
            </div>
            <p className="text-sm leading-6 text-muted-foreground">
              推荐做法是先完成 vetting，再安装。审查通过后，再复制安装命令或进入技能详情查看 README。
            </p>
            <InstallCommand namespace={skill.namespace} slug={skill.slug} />
          </Card>

          {historyItems.length > 0 && (
            <Card className="space-y-3 p-6">
              <p className="text-sm font-semibold text-slate-950">往期推荐</p>
              <ul className="space-y-2">
                {historyItems.map((item) => (
                  <li
                    key={`${item.namespace}-${item.slug}`}
                    className={`flex cursor-pointer items-center gap-2.5 rounded-lg px-2 py-1.5 text-sm transition-colors hover:bg-slate-50 ${querySlug === item.slug ? 'bg-slate-100 font-semibold' : ''}`}
                    onClick={() => navigate({ to: '/recommendations/weekly', search: { slug: querySlug === item.slug ? undefined : item.slug } })}
                  >
                    <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-slate-100 text-xs font-medium">
                      {item.skill?.displayName?.charAt(0) ?? item.slug.charAt(0)}
                    </span>
                    <span className="truncate font-medium">{item.skill?.displayName ?? item.slug}</span>
                    <span className="truncate text-muted-foreground">{item.slug}</span>
                  </li>
                ))}
              </ul>
            </Card>
          )}
        </aside>
        )}
      </div>
    </div>
  )
}
