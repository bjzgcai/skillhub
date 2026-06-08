import { describe, expect, it } from 'vitest'
import { normalizeMarkdownHtml } from './markdown-html-compat'

describe('normalizeMarkdownHtml', () => {
  it('converts simple html image tags to markdown images', () => {
    expect(normalizeMarkdownHtml('<img src="cover.png" alt="封面" width="100%">')).toBe('![封面](cover.png)')
  })

  it('converts html tables with images and links to markdown tables', () => {
    const html = `
<table>
  <tr><th width="50%">模板</th><th>链接</th></tr>
  <tr>
    <td><img src="current-adaptive-swiss-grid.png" alt="自适应瑞士网格" width="100%"></td>
    <td><a href="http://example.com/report">打开报告</a></td>
  </tr>
</table>`

    expect(normalizeMarkdownHtml(html)).toContain('| 模板 | 链接 |')
    expect(normalizeMarkdownHtml(html)).toContain('| ![自适应瑞士网格](current-adaptive-swiss-grid.png) | [打开报告](http://example.com/report) |')
  })

  it('does not rewrite html inside fenced code blocks', () => {
    const markdown = [
      '```html',
      '<img src="cover.png" alt="封面">',
      '```',
      '<img src="real.png" alt="真实图片">',
    ].join('\n')

    const normalized = normalizeMarkdownHtml(markdown)

    expect(normalized).toContain('<img src="cover.png" alt="封面">')
    expect(normalized).toContain('![真实图片](real.png)')
  })
})
