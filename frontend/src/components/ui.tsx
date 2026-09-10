import { AlertTriangle, LoaderCircle } from 'lucide-react'
import type { ButtonHTMLAttributes, ReactNode } from 'react'
import { cn } from '@/lib/cn'

export function Card({ className, children }: { className?: string; children: ReactNode }) {
  return <div className={cn('rounded-xl border border-slate-200 bg-white shadow-sm', className)}>{children}</div>
}

export function CardHeader({ title, subtitle, action }: { title: ReactNode; subtitle?: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-4 border-b border-slate-100 px-5 py-3.5">
      <div className="min-w-0">
        <h3 className="text-sm font-semibold text-slate-900">{title}</h3>
        {subtitle && <p className="mt-0.5 text-xs text-slate-500">{subtitle}</p>}
      </div>
      {action}
    </div>
  )
}

export function Badge({ className, children }: { className?: string; children: ReactNode }) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 text-[11px] font-medium ring-1 ring-inset whitespace-nowrap',
        className ?? 'bg-slate-50 text-slate-600 ring-slate-200',
      )}
    >
      {children}
    </span>
  )
}

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger'

const variants: Record<Variant, string> = {
  primary: 'bg-indigo-600 text-white hover:bg-indigo-500 shadow-sm',
  secondary: 'bg-white text-slate-700 ring-1 ring-inset ring-slate-300 hover:bg-slate-50',
  ghost: 'text-slate-600 hover:bg-slate-100',
  danger: 'bg-white text-rose-600 ring-1 ring-inset ring-rose-200 hover:bg-rose-50',
}

export function Button({
  variant = 'primary',
  loading,
  className,
  children,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; loading?: boolean }) {
  return (
    <button
      {...props}
      disabled={props.disabled || loading}
      className={cn(
        'inline-flex items-center justify-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-50',
        variants[variant],
        className,
      )}
    >
      {loading && <LoaderCircle className="size-4 animate-spin" />}
      {children}
    </button>
  )
}

export function Spinner({ label }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-2 py-10 text-sm text-slate-500">
      <LoaderCircle className="size-4 animate-spin" />
      {label ?? 'Loading…'}
    </div>
  )
}

export function ErrorState({ error }: { error: unknown }) {
  const message = error instanceof Error ? error.message : String(error)
  return (
    <div className="flex items-start gap-2 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
      <AlertTriangle className="mt-0.5 size-4 shrink-0" />
      <span>{message}</span>
    </div>
  )
}

export function EmptyState({ icon, title, children }: { icon?: ReactNode; title: string; children?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 px-6 py-12 text-center">
      {icon && <div className="text-slate-300">{icon}</div>}
      <p className="text-sm font-medium text-slate-700">{title}</p>
      {children && <div className="max-w-md text-sm text-slate-500">{children}</div>}
    </div>
  )
}

export function Stat({ label, value, hint }: { label: string; value: ReactNode; hint?: ReactNode }) {
  return (
    <div className="min-w-0">
      <p className="text-xs font-medium text-slate-500">{label}</p>
      <p className="mt-1 text-2xl font-semibold tabular-nums text-slate-900">{value}</p>
      {hint && <p className="mt-0.5 text-xs text-slate-400">{hint}</p>}
    </div>
  )
}

export function Segmented<T extends string>({
  value,
  options,
  onChange,
}: {
  value: T
  options: { value: T; label: string }[]
  onChange: (v: T) => void
}) {
  return (
    <div className="inline-flex rounded-lg bg-slate-100 p-0.5">
      {options.map((o) => (
        <button
          key={o.value}
          type="button"
          onClick={() => onChange(o.value)}
          className={cn(
            'rounded-md px-2.5 py-1 text-xs font-medium transition',
            value === o.value ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:text-slate-700',
          )}
        >
          {o.label}
        </button>
      ))}
    </div>
  )
}

// headline number with an optional visual below
export function KpiTile({
  label,
  icon,
  value,
  unit,
  hint,
  children,
  className,
}: {
  label: string
  icon?: ReactNode
  value: ReactNode
  unit?: string
  hint?: ReactNode
  children?: ReactNode
  className?: string
}) {
  return (
    <Card className={cn('flex min-w-0 flex-col p-5', className)}>
      <div className="flex items-center justify-between gap-2 text-xs font-medium text-slate-500">
        <span className="truncate">{label}</span>
        {icon}
      </div>
      <div className="mt-2 flex items-baseline gap-1.5">
        <span className="text-3xl font-semibold tracking-tight text-slate-900 tabular-nums">{value}</span>
        {unit && <span className="text-sm text-slate-500">{unit}</span>}
      </div>
      {hint && <div className="mt-1 text-xs text-slate-500">{hint}</div>}
      {children && <div className="mt-auto pt-4">{children}</div>}
    </Card>
  )
}

// change vs previous run; neutral ink, growth is not good or bad by itself
export function Delta({ value, format = (v: number) => v.toLocaleString() }: { value: number | null; format?: (v: number) => string }) {
  if (value == null) return null
  if (value === 0) return <span className="text-slate-400">no change</span>
  return (
    <span className="font-medium text-slate-600 tabular-nums">
      {value > 0 ? '▲' : '▼'} {format(Math.abs(value))}
    </span>
  )
}

// marks content produced by static analysis vs Lyra
export function Provenance({ kind, model }: { kind: 'static' | 'ai'; model?: string | null }) {
  return kind === 'static' ? (
    <Badge className="bg-slate-100 text-slate-700 ring-slate-300">Static analysis</Badge>
  ) : (
    <Badge className="bg-violet-50 text-violet-700 ring-violet-200">Lyra{model ? ` · ${model}` : ''}</Badge>
  )
}
