import { OctagonAlert, ShieldCheck, TriangleAlert } from 'lucide-react'
import { useRef, useState, type PointerEvent, type ReactNode } from 'react'
import { factorLabel, factorWeight, riskStyle } from '@/lib/risk'
import type { ModuleMetrics, RiskLevel } from '@/types/api'

// chart chrome
const INK = { primary: '#0b0b0b', secondary: '#52514e', muted: '#898781', grid: '#e1e0d9', axis: '#c3c2b7' }
const SURFACE = '#fcfcfb'
export const SERIES_1 = '#2a78d6'
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
export function RiskDistributionBar({ low, medium, high, compact }: { low: number; medium: number; high: number; compact?: boolean }) {
  const [hover, setHover] = useState<RiskLevel | null>(null)
  const segments: { level: RiskLevel; value: number }[] = [
    { level: 'HIGH', value: high },
    { level: 'MEDIUM', value: medium },
    { level: 'LOW', value: low },
  ]
  const total = low + medium + high
  if (total === 0) return compact ? <span className="text-xs text-slate-400">—</span> : <p className="text-sm text-slate-500">No scored entities yet.</p>

  let offset = 0
  return (
    <div>
      <div className="relative">
        <div className={`flex w-full gap-[2px] ${compact ? 'h-2' : 'h-3'}`} style={{ background: SURFACE }}>
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
      {!compact && (
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
      )}
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
  // label the farthest-from-sequence points, skipping any that would overlap an earlier label
  const labelled = new Set<string>()
  const placed: { x: number; y: number }[] = []
  const ranked = [...points.entries()].sort(
    (a, b) => Math.max(...b[1].map((m) => m.distance)) - Math.max(...a[1].map((m) => m.distance)),
  )
  for (const [k, group] of ranked) {
    if (labelled.size >= 4) break
    const x = sx(group[0].instability)
    const y = sy(group[0].abstractness)
    if (placed.every((p) => Math.abs(p.x - x) > 90 || Math.abs(p.y - y) > 20)) {
      labelled.add(k)
      placed.push({ x, y })
    }
  }
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
      <p className="mt-1 text-center text-[11px] text-slate-500">
        bottom-left: zone of pain (concrete, heavily used) · top-right: zone of uselessness (abstract, unused)
      </p>
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

// 0..top in about three steps of 1, 2 or 5 x 10^n
function niceTicks(max: number, integer = true) {
  if (max <= 0) return [0, 1]
  const raw = max / 3
  const pow = 10 ** Math.floor(Math.log10(raw))
  let step = [1, 2, 5, 10].map((m) => m * pow).find((v) => v >= raw) ?? raw
  if (integer) step = Math.max(1, Math.round(step))
  const ticks: number[] = []
  for (let t = 0; t < max + step; t += step) ticks.push(t)
  return ticks
}

export function ChartLegend({ items }: { items: { label: string; color: string; icon?: typeof ShieldCheck }[] }) {
  return (
    <ul className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-500">
      {items.map((i) => (
        <li key={i.label} className="flex items-center gap-1.5">
          {i.icon ? <i.icon className="size-3.5" style={{ color: i.color }} /> : <span className="size-2 rounded-sm" style={{ background: i.color }} />}
          {i.label}
        </li>
      ))}
    </ul>
  )
}

export interface ColumnDatum {
  key: string
  label: string
  // bottom to top
  segments: { name: string; value: number; color: string }[]
  detail?: ReactNode
}

// columns anchored to a zero baseline; stacked segments keep a 2px gap
export function ColumnChart({
  data,
  ariaLabel,
  height = 150,
  labelEvery = 1,
}: {
  data: ColumnDatum[]
  ariaLabel: string
  height?: number
  labelEvery?: number
}) {
  const [hover, setHover] = useState<string | null>(null)
  const totals = data.map((d) => d.segments.reduce((sum, x) => sum + x.value, 0))
  const ticks = niceTicks(Math.max(0, ...totals))
  const top = ticks[ticks.length - 1]

  return (
    <div role="img" aria-label={ariaLabel}>
      <div className="flex gap-2">
        <div className="relative w-7 shrink-0 text-right text-[11px] text-slate-400 tabular-nums" style={{ height }}>
          {ticks.map((t) => (
            <span key={t} className="absolute right-0 translate-y-1/2" style={{ bottom: `${(t / top) * 100}%` }}>
              {t.toLocaleString()}
            </span>
          ))}
        </div>
        <div className="relative flex-1" style={{ height }}>
          {ticks.map((t) => (
            <div key={t} className="absolute inset-x-0 h-px" style={{ bottom: `${(t / top) * 100}%`, background: t === 0 ? INK.axis : INK.grid }} />
          ))}
          <div className="absolute inset-0 flex items-end">
            {data.map((d, i) => {
              const pct = (totals[i] / top) * 100
              const parts = d.segments.filter((x) => x.value > 0)
              return (
                <div
                  key={d.key}
                  className="relative flex h-full flex-1 items-end justify-center px-[3px]"
                  onPointerEnter={() => setHover(d.key)}
                  onPointerLeave={() => setHover(null)}
                >
                  <div
                    className="flex w-full max-w-9 flex-col-reverse gap-[2px] transition-opacity"
                    style={{ height: `${pct}%`, opacity: hover && hover !== d.key ? 0.55 : 1 }}
                  >
                    {parts.map((x, j) => (
                      <div
                        key={x.name}
                        style={{ flex: `${x.value} 1 0`, minHeight: 2, background: x.color, borderRadius: j === parts.length - 1 ? '4px 4px 0 0' : 0 }}
                      />
                    ))}
                  </div>
                  {hover === d.key && d.detail && (
                    <Tooltip x="50%" y={`${100 - pct}%`}>
                      {d.detail}
                    </Tooltip>
                  )}
                </div>
              )
            })}
          </div>
        </div>
      </div>
      <div className="ml-9 flex">
        {data.map((d, i) => (
          <span key={d.key} className="flex-1 truncate pt-1.5 text-center text-[11px] text-slate-400">
            {i % labelEvery === 0 ? d.label : ''}
          </span>
        ))}
      </div>
    </div>
  )
}

// tiny trend for kpi tiles; last point marked, hover reads any point
export function Sparkline({
  values,
  labels,
  format = (v: number) => v.toLocaleString(),
  ariaLabel,
  height = 40,
}: {
  values: number[]
  labels?: string[]
  format?: (v: number) => string
  ariaLabel: string
  height?: number
}) {
  const [hover, setHover] = useState<number | null>(null)
  const ref = useRef<HTMLDivElement>(null)
  if (values.length < 2) return <p className="text-[11px] text-slate-400">Trend appears after two runs</p>

  const max = Math.max(...values)
  const min = Math.min(...values)
  const span = max - min || 1
  const px = (i: number) => (i / (values.length - 1)) * 100
  const py = (v: number) => (max === min ? 50 : 10 + (1 - (v - min) / span) * 80)
  const active = hover ?? values.length - 1
  const onMove = (e: PointerEvent<HTMLDivElement>) => {
    const r = ref.current!.getBoundingClientRect()
    const i = Math.round(((e.clientX - r.left) / r.width) * (values.length - 1))
    setHover(Math.min(values.length - 1, Math.max(0, i)))
  }

  return (
    <div ref={ref} role="img" aria-label={ariaLabel} className="relative" style={{ height }} onPointerMove={onMove} onPointerLeave={() => setHover(null)}>
      <svg viewBox="0 0 100 100" preserveAspectRatio="none" className="absolute inset-0 size-full overflow-visible">
        <polyline
          points={values.map((v, i) => `${px(i)},${py(v)}`).join(' ')}
          fill="none"
          stroke={SERIES_1}
          strokeWidth={2}
          strokeLinejoin="round"
          strokeLinecap="round"
          vectorEffect="non-scaling-stroke"
        />
      </svg>
      <span
        className="absolute size-2 -translate-x-1/2 -translate-y-1/2 rounded-full ring-2 ring-white"
        style={{ left: `${px(active)}%`, top: `${py(values[active])}%`, background: SERIES_1 }}
      />
      {hover != null && (
        <Tooltip x={`${px(hover)}%`} y={`${py(values[hover])}%`}>
          <span className="font-semibold text-slate-900 tabular-nums">{format(values[hover])}</span>
          {labels?.[hover] && <span className="text-slate-500"> · {labels[hover]}</span>}
        </Tooltip>
      )}
    </div>
  )
}

// small column strip for tiles, no axes
export function MiniColumns({ values, labels, format }: { values: number[]; labels: string[]; format: (v: number) => string }) {
  const [hover, setHover] = useState<number | null>(null)
  const max = Math.max(1, ...values)
  return (
    <div className="relative flex h-10 items-end gap-[2px]" role="img" aria-label={labels.map((l, i) => `${l}: ${format(values[i])}`).join(', ')}>
      {values.map((v, i) => (
        <div key={i} className="flex h-full flex-1 items-end" onPointerEnter={() => setHover(i)} onPointerLeave={() => setHover(null)}>
          <div
            className="w-full rounded-t-[3px] transition-opacity"
            style={{ height: v ? `${(v / max) * 100}%` : 2, background: v ? SERIES_1 : INK.grid, opacity: hover != null && hover !== i ? 0.55 : 1 }}
          />
        </div>
      ))}
      {hover != null && (
        <Tooltip x={`${((hover + 0.5) / values.length) * 100}%`} y="0">
          <span className="font-semibold text-slate-900">{format(values[hover])}</span>
          <span className="text-slate-500"> · {labels[hover]}</span>
        </Tooltip>
      )}
    </div>
  )
}

// ranked magnitudes, one hue
export function BarList({ items }: { items: { key: string; label: ReactNode; value: number }[] }) {
  const max = Math.max(1, ...items.map((i) => i.value))
  return (
    <ul className="space-y-2.5">
      {items.map((it) => (
        <li key={it.key}>
          <div className="mb-1 flex items-baseline justify-between gap-3 text-xs">
            <span className="truncate text-slate-600">{it.label}</span>
            <span className="font-semibold text-slate-900 tabular-nums">{it.value.toLocaleString()}</span>
          </div>
          <div className="h-2 w-full">
            <div className="h-full rounded" style={{ width: `${(it.value / max) * 100}%`, minWidth: it.value > 0 ? 3 : 0, background: SERIES_1 }} />
          </div>
        </li>
      ))}
    </ul>
  )
}

// share of the largest value, for table cells
export function InlineBar({ value, max }: { value: number; max: number }) {
  return (
    <div className="h-1.5 w-20 rounded-full" style={{ background: SERIES_1_TRACK }}>
      <div className="h-full rounded-full" style={{ width: `${max ? (value / max) * 100 : 0}%`, background: SERIES_1 }} />
    </div>
  )
}
