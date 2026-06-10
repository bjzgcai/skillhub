export type WukongBridgeMethod =
  | 'skill.list'
  | 'skill.installFromUrl'
  | 'skill.removeSkillItem'
  | 'skill.enableSkillItem'
  | 'skill.disableSkillItem'
  | 'page.getTheme'
  | 'page.getLanguage'
  | 'page.openUrl'
  | 'enterprise.getTmpAuthCode'

export const WUKONG_BRIDGE_FALLBACK_DELAY_MS = 800

export interface WukongInstalledSkill {
  id?: string | number
  name?: string
  slug?: string
  skillId?: string
  skill_id?: string
  enabled?: boolean
  status?: string
}

export type WukongBridgeEventName =
  | 'skills:ready'
  | 'skills:changed'
  | 'page.themeChanged'
  | 'page.languageChanged'

export interface WukongBridgeEventMessage {
  type?: string
  event?: WukongBridgeEventName | string
  data?: {
    theme?: string
    language?: string
  }
}

interface BridgeResponseMessage<T> {
  type?: string
  id?: string
  bridgeVersion?: string
  success?: boolean
  data?: T
  error?: string
}

interface BridgeCallOptions {
  timeoutMs?: number
  targetOrigin?: string
}

export function isWukongEmbedded(): boolean {
  return typeof window !== 'undefined' && window.parent !== window
}

export function isWukongBridgeEventMessage(value: unknown): value is WukongBridgeEventMessage {
  return typeof value === 'object'
    && value !== null
    && (value as WukongBridgeEventMessage).type === 'skill-bridge-event'
}

export function isWukongHostMessage(event: Pick<MessageEvent<unknown>, 'source'>): boolean {
  return typeof window !== 'undefined' && event.source === window.parent
}

function createBridgeRequestId(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID()
  }
  return `wukong-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

export function callWukongBridge<T = unknown>(
  method: WukongBridgeMethod,
  params: Record<string, unknown> = {},
  options: BridgeCallOptions = {},
): Promise<T> {
  if (!isWukongEmbedded()) {
    return Promise.reject(new Error('Wukong bridge is unavailable'))
  }

  const id = createBridgeRequestId()
  const timeoutMs = options.timeoutMs ?? 15_000
  const targetOrigin = options.targetOrigin ?? '*'

  return new Promise((resolve, reject) => {
    const timeoutId = window.setTimeout(() => {
      window.removeEventListener('message', handleMessage)
      reject(new Error('Wukong bridge request timed out'))
    }, timeoutMs)

    function handleMessage(event: MessageEvent<BridgeResponseMessage<T>>) {
      if (!isWukongHostMessage(event) || event.data?.type !== 'skill-bridge-response' || event.data.id !== id) {
        return
      }

      window.clearTimeout(timeoutId)
      window.removeEventListener('message', handleMessage)

      if (event.data.success) {
        resolve(event.data.data as T)
        return
      }

      reject(new Error(event.data.error || 'Wukong bridge request failed'))
    }

    window.addEventListener('message', handleMessage)
    window.parent.postMessage({
      type: 'skill-bridge-request',
      id,
      method,
      params,
    }, targetOrigin)
  })
}

function normalizeInstalledSkill(value: unknown): WukongInstalledSkill | null {
  if (typeof value !== 'object' || value === null) {
    return null
  }
  return value as WukongInstalledSkill
}

export function normalizeInstalledSkills(value: unknown): WukongInstalledSkill[] {
  if (Array.isArray(value)) {
    return value.map(normalizeInstalledSkill).filter((item): item is WukongInstalledSkill => item !== null)
  }

  if (typeof value !== 'object' || value === null) {
    return []
  }

  const record = value as Record<string, unknown>
  const candidates = [record.installedSkills, record.skills, record.items]
  const list = candidates.find(Array.isArray)
  return Array.isArray(list) ? normalizeInstalledSkills(list) : []
}

function normalizeKey(value: unknown): string {
  if (typeof value !== 'string' && typeof value !== 'number') {
    return ''
  }
  return String(value).trim().toLowerCase()
}

export function getInstalledSkillKeys(skills: WukongInstalledSkill[]): Set<string> {
  const keys = new Set<string>()

  skills.forEach((skill) => {
    const slug = normalizeKey(skill.slug)
    const name = normalizeKey(skill.name)
    const skillId = normalizeKey(skill.skillId ?? skill.skill_id)
    const id = normalizeKey(skill.id)

    if (slug) keys.add(slug)
    if (name) keys.add(name)
    if (skillId) keys.add(skillId)
    if (id) keys.add(id)
  })

  return keys
}
