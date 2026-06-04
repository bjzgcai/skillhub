import type { LabelItem } from '@/api/types'

export function getSkillLabelClassName(label: Pick<LabelItem, 'type'>): string {
  if (label.type === 'PRIVILEGED') {
    return 'border-amber-500/40 bg-amber-100 text-amber-900'
  }
  return 'border-slate-300 bg-slate-100 text-slate-800'
}
