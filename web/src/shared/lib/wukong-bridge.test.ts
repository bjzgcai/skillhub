import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  WUKONG_BRIDGE_FALLBACK_DELAY_MS,
  callWukongBridge,
  getInstalledSkillKeys,
  isWukongBridgeEventMessage,
  isWukongHostMessage,
  normalizeInstalledSkills,
} from './wukong-bridge'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('normalizeInstalledSkills', () => {
  it('accepts installedSkills, skills, and items containers', () => {
    expect(normalizeInstalledSkills({ installedSkills: [{ slug: 'skillhub' }] })).toEqual([{ slug: 'skillhub' }])
    expect(normalizeInstalledSkills({ skills: [{ name: 'writer' }] })).toEqual([{ name: 'writer' }])
    expect(normalizeInstalledSkills({ items: [{ skill_id: 'demo' }] })).toEqual([{ skill_id: 'demo' }])
  })

  it('drops non-object entries', () => {
    expect(normalizeInstalledSkills([{ slug: 'skillhub' }, null, 'bad'])).toEqual([{ slug: 'skillhub' }])
  })
})

describe('getInstalledSkillKeys', () => {
  it('indexes common Wukong skill identity fields', () => {
    const keys = getInstalledSkillKeys([
      { slug: 'SkillHub', name: 'Writer', skillId: 'abc' },
      { id: 42, skill_id: 'legacy' },
    ])

    expect(keys.has('skillhub')).toBe(true)
    expect(keys.has('writer')).toBe(true)
    expect(keys.has('abc')).toBe(true)
    expect(keys.has('42')).toBe(true)
    expect(keys.has('legacy')).toBe(true)
  })
})

describe('isWukongBridgeEventMessage', () => {
  it('accepts only SkillBridge host events', () => {
    expect(isWukongBridgeEventMessage({ type: 'skill-bridge-event', event: 'skills:ready' })).toBe(true)
    expect(isWukongBridgeEventMessage({ type: 'skill-bridge-response', event: 'skills:ready' })).toBe(false)
    expect(isWukongBridgeEventMessage(null)).toBe(false)
  })
})

describe('callWukongBridge', () => {
  it('ignores bridge responses that do not come from the host window', async () => {
    const listeners = new Set<(event: MessageEvent<unknown>) => void>()
    let postedMessage: { id: string } | undefined
    const hostWindow = {
      postMessage: vi.fn((message: { id: string }) => {
        postedMessage = message
      }),
    }
    const fakeWindow = {
      parent: hostWindow,
      setTimeout,
      clearTimeout,
      addEventListener: (_type: string, listener: (event: MessageEvent<unknown>) => void) => {
        listeners.add(listener)
      },
      removeEventListener: (_type: string, listener: (event: MessageEvent<unknown>) => void) => {
        listeners.delete(listener)
      },
    }
    vi.stubGlobal('window', fakeWindow)

    const promise = callWukongBridge('skill.list', {}, { timeoutMs: 1000 })

    expect(hostWindow.postMessage).toHaveBeenCalled()
    expect(postedMessage?.id).toBeTruthy()

    listeners.forEach((listener) => listener({
      source: {},
      data: { type: 'skill-bridge-response', id: postedMessage?.id, success: true, data: ['spoofed'] },
    } as unknown as MessageEvent<unknown>))

    let resolved = false
    void promise.then(() => {
      resolved = true
    })
    await Promise.resolve()

    expect(resolved).toBe(false)

    listeners.forEach((listener) => listener({
      source: hostWindow,
      data: { type: 'skill-bridge-response', id: postedMessage?.id, success: true, data: ['trusted'] },
    } as unknown as MessageEvent<unknown>))

    await expect(promise).resolves.toEqual(['trusted'])
  })
})

describe('isWukongHostMessage', () => {
  it('accepts messages only from the Wukong host window', () => {
    const hostWindow = {}
    vi.stubGlobal('window', { parent: hostWindow })

    expect(isWukongHostMessage({ source: hostWindow } as Pick<MessageEvent<unknown>, 'source'>)).toBe(true)
    expect(isWukongHostMessage({ source: {} } as Pick<MessageEvent<unknown>, 'source'>)).toBe(false)
  })
})

describe('WUKONG_BRIDGE_FALLBACK_DELAY_MS', () => {
  it('keeps the fallback probe delay used by the Wukong client handshake', () => {
    expect(WUKONG_BRIDGE_FALLBACK_DELAY_MS).toBe(800)
  })
})
