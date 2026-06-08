import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { createSkillMarkdownAssetResolver } from './markdown-assets'
import { MARKDOWN_IMAGE_CLASS_NAME, MarkdownRenderer } from './markdown-renderer'

describe('MarkdownRenderer', () => {
  it('keeps markdown images at their intrinsic width while remaining responsive', () => {
    const classNames = MARKDOWN_IMAGE_CLASS_NAME.split(' ')

    expect(classNames).toContain('h-auto')
    expect(classNames).toContain('max-w-full')
    expect(classNames).not.toContain('w-full')
  })

  it('rewrites non-ascii package image references without double encoding', () => {
    const html = renderToStaticMarkup(
      <MarkdownRenderer
        content={'# 情报引擎 API Skill\n![情报引擎简介](./情报引擎简介.png)'}
        imageUrlResolver={createSkillMarkdownAssetResolver({
          namespace: 'global',
          slug: 'intelligence-engine-api',
          version: '2.0.0',
          baseFilePath: 'README.md',
        })}
      />
    )

    expect(html).toContain('path=%E6%83%85%E6%8A%A5%E5%BC%95%E6%93%8E%E7%AE%80%E4%BB%8B.png')
    expect(html).not.toContain('%25E6')
  })
})
