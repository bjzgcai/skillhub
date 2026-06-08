import { buildApiUrl, WEB_API_PREFIX } from '@/api/client'

interface SkillMarkdownAssetResolverOptions {
  namespace: string
  slug: string
  version: string
  baseFilePath?: string | null
}

const EXTERNAL_URL_PATTERN = /^(?:https?:|data:|blob:|mailto:|#)/i

export function createSkillMarkdownAssetResolver({
  namespace,
  slug,
  version,
  baseFilePath,
}: SkillMarkdownAssetResolverOptions): (src: string) => string {
  const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace

  return (src: string) => {
    if (!src || EXTERNAL_URL_PATTERN.test(src) || src.startsWith('//')) {
      return src
    }

    const filePath = resolvePackageRelativePath(src, baseFilePath)
    const url = `${WEB_API_PREFIX}/skills/${encodeURIComponent(cleanNamespace)}/${encodeURIComponent(slug)}`
      + `/versions/${encodeURIComponent(version)}/file?path=${encodeURIComponent(filePath)}`

    return buildApiUrl(url)
  }
}

export function resolvePackageRelativePath(src: string, baseFilePath?: string | null): string {
  const decodedSrc = safeDecodeUri(src)
  const packageRootPath = decodedSrc.startsWith('/')
    ? decodedSrc.replace(/^\/+/, '')
    : joinPackagePath(getBaseDirectory(baseFilePath), decodedSrc)
  return normalizePackagePath(packageRootPath)
}

function safeDecodeUri(value: string): string {
  try {
    return decodeURI(value)
  } catch {
    return value
  }
}

function getBaseDirectory(baseFilePath?: string | null): string {
  if (!baseFilePath?.includes('/')) {
    return ''
  }

  return baseFilePath.slice(0, baseFilePath.lastIndexOf('/'))
}

function joinPackagePath(baseDirectory: string, relativePath: string): string {
  if (!baseDirectory) {
    return relativePath
  }

  return `${baseDirectory}/${relativePath}`
}

function normalizePackagePath(path: string): string {
  const parts: string[] = []

  for (const part of path.split('/')) {
    if (!part || part === '.') {
      continue
    }

    if (part === '..') {
      parts.pop()
      continue
    }

    parts.push(part)
  }

  return parts.join('/')
}
