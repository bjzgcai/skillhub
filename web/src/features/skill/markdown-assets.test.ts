import { describe, expect, it } from 'vitest'
import { createSkillMarkdownAssetResolver, resolvePackageRelativePath } from './markdown-assets'

describe('resolvePackageRelativePath', () => {
  it('resolves images relative to the markdown file directory', () => {
    expect(resolvePackageRelativePath('../assets/cover.png', 'docs/readme/README.md')).toBe('docs/assets/cover.png')
  })

  it('treats leading slash paths as package-root relative', () => {
    expect(resolvePackageRelativePath('/images/banner.png', 'docs/README.md')).toBe('images/banner.png')
  })

  it('normalizes dot segments without escaping the package root', () => {
    expect(resolvePackageRelativePath('../../logo.png', 'README.md')).toBe('logo.png')
  })

  it('decodes markdown-normalized non-ascii paths before final API encoding', () => {
    expect(resolvePackageRelativePath('./%E6%83%85%E6%8A%A5%E5%BC%95%E6%93%8E%E7%AE%80%E4%BB%8B.png', 'README.md'))
      .toBe('情报引擎简介.png')
  })
})

describe('createSkillMarkdownAssetResolver', () => {
  it('rewrites package-relative image URLs to the SkillHub file API', () => {
    const resolve = createSkillMarkdownAssetResolver({
      namespace: 'global',
      slug: 'intel-engine',
      version: '1.0.0',
      baseFilePath: 'docs/README.md',
    })

    expect(resolve('images/hero.png')).toBe(
      '/api/web/skills/global/intel-engine/versions/1.0.0/file?path=docs%2Fimages%2Fhero.png'
    )
  })

  it('does not double-encode image paths already encoded by markdown parsing', () => {
    const resolve = createSkillMarkdownAssetResolver({
      namespace: 'global',
      slug: 'intelligence-engine-api',
      version: '2.0.0',
      baseFilePath: 'README.md',
    })

    expect(resolve('./%E6%83%85%E6%8A%A5%E5%BC%95%E6%93%8E%E7%AE%80%E4%BB%8B.png')).toBe(
      '/api/web/skills/global/intelligence-engine-api/versions/2.0.0/file?path=%E6%83%85%E6%8A%A5%E5%BC%95%E6%93%8E%E7%AE%80%E4%BB%8B.png'
    )
  })

  it('leaves remote and data image URLs untouched', () => {
    const resolve = createSkillMarkdownAssetResolver({
      namespace: 'global',
      slug: 'intel-engine',
      version: '1.0.0',
      baseFilePath: 'README.md',
    })

    expect(resolve('https://example.com/a.png')).toBe('https://example.com/a.png')
    expect(resolve('data:image/png;base64,abc')).toBe('data:image/png;base64,abc')
  })
})
