import type { RiskLevel } from '@/types/api'

export const riskLevel = (score: number): RiskLevel => (score >= 60 ? 'HIGH' : score >= 30 ? 'MEDIUM' : 'LOW')

export const riskStyle: Record<RiskLevel, { badge: string; text: string; bar: string; hex: string }> = {
  LOW: { badge: 'bg-emerald-50 text-emerald-700 ring-emerald-200', text: 'text-emerald-600', bar: 'bg-[#0ca30c]', hex: '#0ca30c' },
  MEDIUM: { badge: 'bg-amber-50 text-amber-700 ring-amber-200', text: 'text-amber-600', bar: 'bg-[#fab219]', hex: '#fab219' },
  HIGH: { badge: 'bg-rose-50 text-rose-700 ring-rose-200', text: 'text-rose-600', bar: 'bg-[#d03b3b]', hex: '#d03b3b' },
}

export const factorLabel: Record<string, string> = {
  dependents: 'Transitive dependents',
  depth: 'Dependency depth',
  coupling: 'Coupling (fan-in + fan-out)',
  complexity: 'Cyclomatic complexity',
  exposed: 'Exposed via API',
}

// mirrors RiskScorer weights
export const factorWeight: Record<string, number> = {
  dependents: 0.3,
  depth: 0.15,
  coupling: 0.2,
  complexity: 0.2,
  exposed: 0.15,
}
