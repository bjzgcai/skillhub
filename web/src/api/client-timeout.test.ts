import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchJson } from './client'

vi.mock('@/i18n/config', () => ({ default: { resolvedLanguage: 'en', exists: () => false } }))

describe('fetchJson timeout handling', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.stubGlobal('window', { setTimeout, clearTimeout })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.useRealTimers()
  })

  it('keeps a slow upload alive past 60 seconds and reports timeout at 300 seconds', async () => {
    vi.stubGlobal('fetch', vi.fn((_input: unknown, init: RequestInit) => new Promise((_resolve, reject) => {
      init.signal?.addEventListener('abort', () => reject(init.signal?.reason), { once: true })
    })))
    const request = fetchJson('/api/web/skills/global/publish', { timeoutMs: 300_000 })
    const rejected = expect(request).rejects.toMatchObject({ status: 408, message: 'error.request.timeout' })
    await vi.advanceTimersByTimeAsync(60_001)
    expect(vi.getTimerCount()).toBe(1)
    await vi.advanceTimersByTimeAsync(239_999)
    await rejected
    expect(vi.getTimerCount()).toBe(0)
  })

  it('clears the timer after a successful response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 0, msg: 'ok', data: { id: 1 } }))))
    await expect(fetchJson('/api/test', { timeoutMs: 300_000 })).resolves.toEqual({ id: 1 })
    expect(vi.getTimerCount()).toBe(0)
  })

  it('keeps ordinary network failures distinct from timeouts', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    await expect(fetchJson('/api/test', { timeoutMs: 300_000 })).rejects.toMatchObject({ status: 0, message: 'apiError.networkError' })
    expect(vi.getTimerCount()).toBe(0)
  })
})
