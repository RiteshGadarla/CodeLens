import { OctagonAlert, ShieldCheck, TriangleAlert } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { factorLabel, factorWeight, riskStyle } from '@/lib/risk'
import type { ModuleMetrics, RiskLevel } from '@/types/api'

// chart chrome
const INK = { primary: '#0b0b0b', secondary: '#52514e', muted: '#898781', grid: '#e1e0d9', axis: '#c3c2b7' }
const SURFACE = '#fcfcfb'
const SERIES_1 = '#2a78d6'
const SERIES_1_TRACK = '#cde2fb'

export const riskIcon: Record<RiskLevel, typeof ShieldCheck> = {
  LOW: ShieldCheck,
  MEDIUM: TriangleAlert,
  HIGH: OctagonAlert,
}

export function RiskLevelLabel({ level, className }: { level: RiskLevel; className?: string }) {
  const Icon = riskIcon[level]
  return (
    <span className={`inline-flex items-center gap-1 font-medium text-slate-700 ${className ?? ''}`}>
      <Icon className="size-4" style={{ color: riskStyle[level].hex }} />
      {level.charAt(0) + level.slice(1).toLowerCase()} risk
    </span>
  )
}

function Tooltip({ x, y, children }: { x: string; y: string; children: ReactNode }) {
  return (
    <div
      className="pointer-events-none absolute z-20 -translate-x-1/2 -translate-y-[calc(100%+10px)] rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-xs whitespace-nowrap shadow-md"
      style={{ left: x, top: y }}
    >
      {children}
    </div>
  )
}

// part-to-whole of three reserved status levels; legend carries every value
export function RiskDistributionBar({ low, medium, high }: { low: number; medium: number; high: number }) {
  const [hover, setHover] = useState<RiskLevel | null>(null)
  const segments: { level: RiskLevel; value: number }[] = [
    { level: 'HIGH', value: high },
    { level: 'MEDIUM', value: medium },
    { level: 'LOW', value: low },
  ]
  const total = low + medium + high
  if (total === 0) return <p className="text-sm text-slate-500">No scored entities yet.</p>

  let offset = 0
  return (
    <div>
      <div className="relative">
        <div className="flex h-3 w-full gap-[2px]" style={{ background: SURFACE }}>
          {segments
            .filter((s) => s.value > 0)
            .map((s, i, arr) => {
              const pct = (s.value / total) * 100
              const center = offset + pct / 2
              offset += pct
              return (
                <div
                  key={s.level}
                  onPointerEnter={() => setHover(s.level)}
                  onPointerLeave={() => setHover(null)}
                  className="relative h-full transition-opacity"
                  style={{
                    width: `${pct}%`,
                    minWidth: 3,
                    background: riskStyle[s.level].hex,
                    opacity: hover && hover !== s.level ? 0.55 : 1,
                    borderRadius: `${i === 0 ? 4 : 0}px ${i === arr.length - 1 ? 4 : 0}px ${i === arr.length - 1 ? 4 : 0}px ${i === 0 ? 4 : 0}px`,
                  }}
                  data-center={center}
                />
              )
            })}
        </div>
        {hover && (
          <Tooltip x={`${segmentCenter(segments, total, hover)}%`} y="0">
            <span className="font-semibold text-slate-900">{segments.find((s) => s.level === hover)!.value}</span>{' '}
            <span className="text-slate-500">{hover.toLowerCase()} risk entities</span>
          </Tooltip>
        )}
      </div>
      <ul className="mt-3 flex flex-wrap gap-x-5 gap-y-1.5">
        {segments.map((s) => (
          <li key={s.level} className="flex items-center gap-1.5 text-xs">
            <span className="size-2 rounded-sm" style={{ background: riskStyle[s.level].hex }} />
            <span className="text-slate-500">{s.level.charAt(0) + s.level.slice(1).toLowerCase()}</span>
            <span className="font-semibold text-slate-900 tabular-nums">{s.value}</span>
            <span className="text-slate-400 tabular-nums">{Math.round((s.value / total) * 100)}%</span>
          </li>
        ))}
      </ul>
    </div>
  )
}

function segmentCenter(segments: { level: RiskLevel; value: number }[], total: number, level: RiskLevel) {
  let offset = 0
  for (const s of segments) {
    const pct = (s.value / total) * 100
    if (s.level === level) return offset + pct / 2
    offset += pct
  }
  return 50
}

// each factor as a meter: fill = normalised value, label = points contributed
export function FactorMeters({ factors }: { factors: Record<string, number> }) {
  return (
    <ul className="space-y-2.5">
      {Object.keys(factorWeight).map((key) => {
        const value = factors[key] ?? 0
        const points = value * factorWeight[key] * 100
        return (
          <li key={key} title={`${factorLabel[key]}: normalised ${value.toFixed(2)} × weight ${factorWeight[key]}`}>
            <div className="mb-1 flex items-baseline justify-between text-xs">
              <span className="text-slate-600">{factorLabel[key]}</span>
              <span>
                <span className="font-semibold text-slate-900 tabular-nums">+{points.toFixed(1)}</span>
                <span className="text-slate-400 tabular-nums"> / {factorWeight[key] * 100}</span>
              </span>
            </div>
            <div className="h-2 w-full rounded" style={{ background: SERIES_1_TRACK }}>
              <div className="h-full rounded" style={{ width: `${value * 100}%`, background: SERIES_1 }} />
            </div>
          </li>
        )
      })}
    </ul>
  )
}

// Martin's instability vs abstractness; distance from the diagonal is the smell
export function MainSequenceChart({ modules }: { modules: ModuleMetrics[] }) {
  const W = 460
  const H = 320
  const pad = { l: 44, r: 16, t: 14, b: 40 }
  const pw = W - pad.l - pad.r
  const ph = H - pad.t - pad.b
  const sx = (v: number) => pad.l + v * pw
  const sy = (v: number) => pad.t + (1 - v) * ph
  const [hover, setHover] = useState<string | null>(null)

  // modules sharing a coordinate become one point
  const points = new Map<string, ModuleMetrics[]>()
  modules.forEach((m) => {
    const k = `${m.instability.toFixed(2)}:${m.abstractness.toFixed(2)}`
    points.set(k, [...(points.get(k) ?? []), m])
  })
  const labelled = new Set(
    [...points.entries()]
      .sort((a, b) => Math.max(...b[1].map((m) => m.distance)) - Math.max(...a[1].map((m) => m.distance)))
      .slice(0, 3)
      .map(([k]) => k),
  )
  const ticks = [0, 0.25, 0.5, 0.75, 1]
  const hovered = hover ? points.get(hover) : undefined

  return (
    <div className="relative">
      <svg viewBox={`0 0 ${W} ${H}`} className="w-full" role="img" aria-label="Instability versus abstractness per module">
        <rect x={pad.l} y={pad.t} width={pw} height={ph} fill={SURFACE} />
        {ticks.map((t) => (
          <g key={t}>
            <line x1={sx(t)} x2={sx(t)} y1={pad.t} y2={pad.t + ph} stroke={INK.grid} strokeWidth={1} />
            <line x1={pad.l} x2={pad.l + pw} y1={sy(t)} y2={sy(t)} stroke={INK.grid} strokeWidth={1} />
            <text x={sx(t)} y={pad.t + ph + 16} textAnchor="middle" fontSize={11} fill={INK.muted} className="tabular-nums">
              {t}
            </text>
            <text x={pad.l - 8} y={sy(t) + 4} textAnchor="end" fontSize={11} fill={INK.muted} className="tabular-nums">
              {t}
            </text>
          </g>
        ))}
        <line x1={pad.l} x2={pad.l + pw} y1={pad.t + ph} y2={pad.t + ph} stroke={INK.axis} strokeWidth={1} />
        <line x1={sx(0)} y1={sy(1)} x2={sx(1)} y2={sy(0)} stroke={INK.muted} strokeWidth={1} />
        <text x={sx(0.52)} y={sy(0.52) - 6} fontSize={10} fill={INK.muted} transform={`rotate(-34 ${sx(0.52)} ${sy(0.52) - 6})`}>
          main sequence
        </text>
        <text x={sx(0.02)} y={sy(0.04)} fontSize={10} fill={INK.muted}>zone of pain</text>
        <text x={sx(0.98)} y={sy(0.95)} fontSize={10} fill={INK.muted} textAnchor="end">zone of uselessness</text>
        <text x={pad.l + pw / 2} y={H - 6} textAnchor="middle" fontSize={11} fill={INK.secondary}>
          Instability  Ce / (Ca + Ce)
        </text>
        <text x={12} y={pad.t + ph / 2} textAnchor="middle" fontSize={11} fill={INK.secondary} transform={`rotate(-90 12 ${pad.t + ph / 2})`}>
          Abstractness
        </text>

        {[...points.entries()].map(([k, group]) => {
          const m = group[0]
          const cx = sx(m.instability)
          const cy = sy(m.abstractness)
          const active = hover === k
          return (
            <g key={k} onPointerEnter={() => setHover(k)} onPointerLeave={() => setHover(null)} tabIndex={0} onFocus={() => setHover(k)} onBlur={() => setHover(null)}>
              <circle cx={cx} cy={cy} r={12} fill="transparent" />
              <circle cx={cx} cy={cy} r={active ? 6 : 4.5} fill={SERIES_1} stroke={SURFACE} strokeWidth={2} />
              {labelled.has(k) && (
                <text
                  x={cx + (m.instability > 0.7 ? -9 : 9)}
                  y={cy - 8}
                  fontSize={10.5}
                  textAnchor={m.instability > 0.7 ? 'end' : 'start'}
                  fill={INK.secondary}
                >
                  {short(m.name)}
                  {group.length > 1 ? ` +${group.length - 1}` : ''}
                </text>
              )}
            </g>
          )
        })}
      </svg>
      {hovered && (
        <Tooltip x={`${(sx(hovered[0].instability) / W) * 100}%`} y={`${(sy(hovered[0].abstractness) / H) * 100}%`}>
          {hovered.slice(0, 5).map((m) => (
            <div key={m.name} className="py-0.5">
              <span className="font-semibold text-slate-900 tabular-nums">D {m.distance.toFixed(2)}</span>{' '}
              <span className="text-slate-600">{m.name}</span>
              <div className="text-[11px] text-slate-400 tabular-nums">
                I {m.instability.toFixed(2)} · A {m.abstractness.toFixed(2)} · Ca {m.afferent} · Ce {m.efferent}
              </div>
            </div>
          ))}
          {hovered.length > 5 && <div className="text-slate-400">+{hovered.length - 5} more</div>}
        </Tooltip>
      )}
    </div>
  )
}

const short = (name: string) => name.split(/[./]/).filter(Boolean).pop() ?? name
