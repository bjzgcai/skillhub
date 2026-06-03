const AVATAR_COLORS = [
  { bg: 'bg-rose-50', text: 'text-rose-700', ring: 'ring-rose-100' },
  { bg: 'bg-orange-50', text: 'text-orange-700', ring: 'ring-orange-100' },
  { bg: 'bg-amber-50', text: 'text-amber-700', ring: 'ring-amber-100' },
  { bg: 'bg-emerald-50', text: 'text-emerald-700', ring: 'ring-emerald-100' },
  { bg: 'bg-cyan-50', text: 'text-cyan-700', ring: 'ring-cyan-100' },
  { bg: 'bg-sky-50', text: 'text-sky-700', ring: 'ring-sky-100' },
  { bg: 'bg-indigo-50', text: 'text-indigo-700', ring: 'ring-indigo-100' },
  { bg: 'bg-fuchsia-50', text: 'text-fuchsia-700', ring: 'ring-fuchsia-100' },
]

function hashText(value: string): number {
  let hash = 0
  for (const char of value) {
    hash = (hash * 31 + char.codePointAt(0)!) >>> 0
  }
  return hash
}

export function getSkillAvatar(displayName: string, slug: string) {
  const source = displayName.trim() || slug.trim() || '?'
  const initial = Array.from(source)[0]?.toUpperCase() || '?'
  const color = AVATAR_COLORS[hashText(slug || source) % AVATAR_COLORS.length]
  return { initial, color }
}
