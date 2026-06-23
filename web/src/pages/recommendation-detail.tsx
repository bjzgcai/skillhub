import { useNavigate } from '@tanstack/react-router'
import { ArrowLeft, Bell, ExternalLink, ListChecks, PlayCircle, Presentation, ShieldCheck, Terminal } from 'lucide-react'
import { useCurrentWeeklySkill } from '@/shared/hooks/use-skill-queries'
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

const SKILL_VETTER_GUIDE: WeeklyGuideContent = {
  intro: 'Skill Vetter 适合作为“安装技能前的检查单”。它不是让用户多看一段安全说明，而是把每一次安装、更新、推荐外部技能的动作拆成可执行的审查步骤：先确认来源和版本，再检查包内容与权限风险，最后给出能否安装的结论。',
  sections: [
    {
      title: '安装前具体怎么用',
      body: '拿到一个待安装 skill 后，不要直接 install。先把来源、版本和包内容交给 Skill Vetter 过一遍，输出结构化审查结论。',
      bullets: [
        '记录来源：SkillHub / ClawHub / GitHub / 手工上传，以及具体版本号',
        '展开包内容：优先阅读 SKILL.md、README、配置文件和脚本入口',
        '逐项检查红旗：外部网络、凭据读取、cookie、记忆文件、sudo、eval/exec',
        '形成结论：SAFE TO INSTALL / INSTALL WITH CAUTION / DO NOT INSTALL',
      ],
      example: {
        user: '我想安装 clawhub 上的 skill-vetter，先帮我做一轮安全审查，不要直接安装。',
        agent: '我会先确认来源、版本和包内容，阅读 SKILL.md / README / 配置文件，再按红旗清单检查网络、凭据、cookie、记忆文件、sudo、eval/exec 等风险，最后给出 SAFE / CAUTION / DO NOT INSTALL 的结论。',
      },
    },
    {
      title: 'Skill Vetter 重点检查什么',
      body: '它关注的是“这个 skill 会让 Agent 获得什么能力，以及这些能力是否和用途匹配”。越接近凭据、系统权限、外部传输和个人数据，越需要谨慎。',
      bullets: [
        '文件权限：是否读取 ~/.ssh、云厂商配置、浏览器数据、个人记忆文件',
        '网络行为：是否向未知域名或 IP 发送数据，是否下载二次脚本',
        '命令执行：是否包含 sudo、shell 拼接、eval、base64 解码后执行',
        '数据外流：是否把输入、文件内容、cookie、token 发送到外部服务',
        '安装行为：是否自动安装依赖、修改系统配置或写 workspace 外目录',
      ],
      example: {
        user: '这个 skill 需要访问外部 API 和写本地文件，可以装吗？',
        agent: '我会把它标为需要谨慎确认：先核对外部 API 域名是否可信、写入目录是否限制在 workspace、是否会上传用户内容；如果用途清晰且权限最小化，可以建议谨慎安装，否则转管理员确认。',
      },
    },
    {
      title: '通过、谨慎、阻断怎么判断',
      body: '审查结果要落到可执行动作上，而不是停在“有风险/无风险”的描述。建议团队统一以下口径。',
      bullets: [
        '通过：只读文档/格式化/查询类能力，权限范围和用途一致',
        '谨慎：需要网络、文件写入、浏览器或第三方 API，但用途清晰可解释',
        '阻断：读取凭据、访问 cookie、隐藏二次下载、混淆代码、请求 sudo/root',
        '人工确认：任何影响生产配置、账号权限、交易、部署或外部发送数据的 skill',
      ],
      example: {
        user: '审查报告里出现“访问 cookie / 读取凭据 / sudo”这类红旗，下一步怎么处理？',
        agent: '我不会继续安装。会把命中的红旗、相关文件路径和建议动作整理出来，请管理员或 owner 决定是否阻断、要求作者修改，或在隔离环境里继续复核。',
      },
    },
  ],
  media: [
    {
      type: 'video',
      title: '3 分钟上手视频',
      description: '演示“选择一个外部 skill → 展开包内容 → 用 Skill Vetter 输出审查报告 → 决定是否安装”的完整路径。',
    },
    {
      type: 'ppt',
      title: '团队导入流程 PPT',
      description: '团队安装门禁流程图、风险分级标准、管理员确认边界和审查记录示例。',
    },
  ],
}

export function getGuideContent(slug?: string): WeeklyGuideContent | null {
  return slug === 'skill-vetter' ? SKILL_VETTER_GUIDE : null
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
  const { data: recommendation, isLoading } = useCurrentWeeklySkill()
  const skill = recommendation?.skill
  const guide = getGuideContent(skill?.slug)
  const primaryMedia = guide ? pickPrimaryMedia(guide.media) : null

  const openSkill = () => {
    if (!skill) return
    navigate({ to: '/space/$namespace/$slug', params: { namespace: skill.namespace, slug: skill.slug } })
  }

  if (isLoading) {
    return <div className="container mx-auto px-4 py-10 text-sm text-muted-foreground">Loading...</div>
  }

  if (!recommendation || !skill) {
    navigate({ to: '/recommendations', search: { page: 0 }, replace: true })
    return null
  }

  return (
    <div className="container mx-auto max-w-6xl px-4 py-10">
      <Button variant="ghost" onClick={() => navigate({ to: '/recommendations', search: { page: 0 } })} className="mb-5">
        <ArrowLeft className="mr-2 h-4 w-4" />
        返回推荐
      </Button>

      <WeeklySkillCard recommendation={recommendation} showActions={false} />

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
        </aside>
      </div>
    </div>
  )
}
