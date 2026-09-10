export const fmtInt = (n: number | null | undefined) => (n ?? 0).toLocaleString()

const compact = new Intl.NumberFormat('en', { notation: 'compact', maximumFractionDigits: 1 })

// 12400 -> 12.4K
export const fmtCompact = (n: number | null | undefined) => compact.format(n ?? 0)

export const fmtPct = (part: number, total: number) => (total === 0 ? '—' : `${Math.round((part / total) * 100)}%`)

export const fmtScore = (n: number | null | undefined) => (n == null ? '—' : n.toFixed(1))

export const fmtRatio = (n: number) => n.toFixed(2)

export function fmtDuration(ms: number | null | undefined) {
  if (ms == null) return '—'
  if (ms < 1000) return `${ms} ms`
  return ms < 60_000 ? `${(ms / 1000).toFixed(1)} s` : `${Math.round(ms / 60_000)} min`
}

export function fmtAgo(iso: string | null | undefined) {
  if (!iso) return '—'
  const s = Math.round((Date.now() - new Date(iso).getTime()) / 1000)
  if (s < 60) return 'just now'
  if (s < 3600) return `${Math.floor(s / 60)}m ago`
  if (s < 86_400) return `${Math.floor(s / 3600)}h ago`
  return `${Math.floor(s / 86_400)}d ago`
}

export const shortSha = (sha: string | null | undefined) => (sha ? sha.slice(0, 7) : '—')

// com.acme.service.UserService#find(Long) -> com.acme.service
export function packageOf(qualifiedName: string) {
  const type = qualifiedName.split('#')[0]
  const i = type.lastIndexOf('.')
  return i < 0 ? '' : type.slice(0, i)
}
