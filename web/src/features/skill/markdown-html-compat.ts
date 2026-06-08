const HTML_TABLE_PATTERN = /<table\b[\s\S]*?<\/table>/gi
const HTML_ROW_PATTERN = /<tr\b[\s\S]*?<\/tr>/gi
const HTML_CELL_PATTERN = /<t([hd])\b[^>]*>([\s\S]*?)<\/t\1>/gi
const HTML_IMG_PATTERN = /<img\b([^>]*)>/gi
const HTML_ANCHOR_PATTERN = /<a\b([^>]*)>([\s\S]*?)<\/a>/gi
const HTML_TAG_PATTERN = /<[^>]+>/g

export function normalizeMarkdownHtml(content: string): string {
  return preserveFencedCodeBlocks(content, normalizeMarkdownHtmlFragment)
}

function normalizeMarkdownHtmlFragment(content: string): string {
  return content
    .replace(HTML_TABLE_PATTERN, (table) => `\n\n${convertHtmlTableToMarkdown(table)}\n\n`)
    .replace(HTML_IMG_PATTERN, (_match, attrs: string) => convertImgAttrsToMarkdown(attrs))
}

function preserveFencedCodeBlocks(content: string, normalize: (fragment: string) => string): string {
  const fencedBlocks: string[] = []
  const placeholderPrefix = '\u0000SKILLHUB_FENCED_BLOCK_'
  const placeholderSuffix = '\u0000'

  const contentWithPlaceholders = content.replace(/(?:^|\n)(?:```|~~~)[\s\S]*?(?:\n```|\n~~~)(?=\n|$)/g, (block) => {
    const placeholder = `${placeholderPrefix}${fencedBlocks.length}${placeholderSuffix}`
    fencedBlocks.push(block)
    return placeholder
  })

  const normalized = normalize(contentWithPlaceholders)
  return fencedBlocks.reduce(
    (current, block, index) => current.replace(`${placeholderPrefix}${index}${placeholderSuffix}`, block),
    normalized
  )
}

function convertHtmlTableToMarkdown(table: string): string {
  const rows = Array.from(table.matchAll(HTML_ROW_PATTERN))
    .map((rowMatch) => parseHtmlTableRow(rowMatch[0]))
    .filter((row) => row.cells.length > 0)

  if (rows.length === 0) {
    return ''
  }

  const columnCount = Math.max(...rows.map((row) => row.cells.length))
  const header = rows[0].cells
  const bodyRows = rows.slice(1)

  return [
    formatMarkdownTableRow(padCells(header, columnCount)),
    formatMarkdownTableRow(Array.from({ length: columnCount }, () => '---')),
    ...bodyRows.map((row) => formatMarkdownTableRow(padCells(row.cells, columnCount))),
  ].join('\n')
}

function parseHtmlTableRow(row: string): { cells: string[] } {
  const cells = Array.from(row.matchAll(HTML_CELL_PATTERN)).map((cellMatch) => normalizeTableCell(cellMatch[2]))
  return { cells }
}

function normalizeTableCell(cell: string): string {
  return decodeHtmlEntities(cell)
    .replace(HTML_IMG_PATTERN, (_match, attrs: string) => convertImgAttrsToMarkdown(attrs))
    .replace(HTML_ANCHOR_PATTERN, (_match, attrs: string, text: string) => convertAnchorToMarkdown(attrs, text))
    .replace(HTML_TAG_PATTERN, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/\|/g, '\\|')
}

function convertImgAttrsToMarkdown(attrs: string): string {
  const src = getHtmlAttribute(attrs, 'src')

  if (!src) {
    return ''
  }

  const alt = getHtmlAttribute(attrs, 'alt') ?? ''
  return `![${escapeMarkdownLabel(alt)}](${src})`
}

function convertAnchorToMarkdown(attrs: string, text: string): string {
  const href = getHtmlAttribute(attrs, 'href')
  const label = decodeHtmlEntities(text).replace(HTML_TAG_PATTERN, '').replace(/\s+/g, ' ').trim()

  if (!href) {
    return label
  }

  return `[${escapeMarkdownLabel(label || href)}](${href})`
}

function getHtmlAttribute(attrs: string, name: string): string | null {
  const pattern = new RegExp(`${name}\\s*=\\s*(?:"([^"]*)"|'([^']*)'|([^\\s"'>]+))`, 'i')
  const match = attrs.match(pattern)
  return match ? decodeHtmlEntities(match[1] ?? match[2] ?? match[3] ?? '') : null
}

function padCells(cells: string[], columnCount: number): string[] {
  return [...cells, ...Array.from({ length: Math.max(columnCount - cells.length, 0) }, () => '')]
}

function formatMarkdownTableRow(cells: string[]): string {
  return `| ${cells.join(' | ')} |`
}

function escapeMarkdownLabel(value: string): string {
  return value.replace(/([\\\]\\[])/g, '\\$1')
}

function decodeHtmlEntities(value: string): string {
  return value
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
}
