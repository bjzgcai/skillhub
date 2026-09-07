import { beforeEach, describe, expect, it, vi } from 'vitest'

type PublishParams = { namespace: string; file: File; visibility: string }

const mocks = vi.hoisted(() => ({
  mutation: vi.fn<(options: { mutationFn: (params: PublishParams) => Promise<unknown> }) => unknown>(),
  fetchJson: vi.fn(),
}))

vi.mock('@tanstack/react-query', () => ({
  useMutation: mocks.mutation,
  useQueryClient: () => ({ invalidateQueries: vi.fn() }),
}))

vi.mock('@/api/client', () => ({
  WEB_API_PREFIX: '/api/web',
  fetchJson: mocks.fetchJson,
  getCsrfHeaders: () => ({}),
}))

import { usePublishDisplayMetadataPreview, usePublishSkill } from './use-skill-queries'

describe('publish request timeout', () => {
  beforeEach(() => vi.clearAllMocks())

  for (const [name, hook, endpoint] of [
    ['preview', usePublishDisplayMetadataPreview, '/publish/preview'],
    ['publish', usePublishSkill, '/publish'],
  ] as const) {
    it(`${name} uses the five-minute upload budget`, async () => {
      mocks.fetchJson.mockResolvedValue({ status: 'ok' })
      hook()
      const options = mocks.mutation.mock.calls[0][0]
      const result = await options.mutationFn({
        namespace: '@global', file: new File(['zip'], 'skill.zip'), visibility: 'PUBLIC',
      })

      expect(result).toEqual({ status: 'ok' })
      expect(mocks.fetchJson).toHaveBeenCalledWith(`/api/web/skills/global${endpoint}`, expect.objectContaining({
        method: 'POST', timeoutMs: 300_000, body: expect.any(FormData),
      }))
    })

    it(`${name} propagates failure without retrying the upload`, async () => {
      const failure = new Error('request failed')
      mocks.fetchJson.mockRejectedValue(failure)
      hook()
      const options = mocks.mutation.mock.calls[0][0]

      await expect(options.mutationFn({
        namespace: 'global', file: new File(['zip'], 'skill.zip'), visibility: 'PUBLIC',
      })).rejects.toBe(failure)
      expect(mocks.fetchJson).toHaveBeenCalledTimes(1)
    })
  }
})
