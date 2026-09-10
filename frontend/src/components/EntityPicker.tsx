import { LoaderCircle, Search } from 'lucide-react'
import { forwardRef, useState } from 'react'
import { useSearch } from '@/api/queries'
import { cn } from '@/lib/cn'
import { useDebounced } from '@/lib/hooks'
import type { EntityKind, EntitySummary } from '@/types/api'
import { KindIcon, RiskBadge, RoleBadge } from './EntityBadges'

interface Props {
  projectId: number
  onSelect: (entity: EntitySummary) => void
  placeholder?: string
  kinds?: EntityKind[]
  className?: string
  shortcut?: string
}

export const EntityPicker = forwardRef<HTMLInputElement, Props>(function EntityPicker(
  { projectId, onSelect, placeholder = 'Search classes, methods, endpoints…', kinds, className, shortcut },
  ref,
) {
  const [q, setQ] = useState('')
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(0)
  const query = useDebounced(q)
  const { data = [], isFetching } = useSearch(projectId, query, kinds)
  const results = q.trim() ? data : []

  const choose = (e: EntitySummary) => {
    onSelect(e)
    setQ('')
    setOpen(false)
  }

  return (
    <div className={cn('relative', className)}>
      <div className="flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 shadow-sm focus-within:border-indigo-400 focus-within:ring-2 focus-within:ring-indigo-100">
        <Search className="size-4 text-slate-400" />
        <input
          ref={ref}
          value={q}
          placeholder={placeholder}
          onChange={(e) => {
            setQ(e.target.value)
            setOpen(true)
            setActive(0)
          }}
          onFocus={() => setOpen(true)}
          onBlur={() => setTimeout(() => setOpen(false), 120)}
          onKeyDown={(e) => {
            if (e.key === 'ArrowDown') {
              e.preventDefault()
              setActive((a) => Math.min(a + 1, results.length - 1))
            } else if (e.key === 'ArrowUp') {
              e.preventDefault()
              setActive((a) => Math.max(a - 1, 0))
            } else if (e.key === 'Enter' && results[active]) {
              choose(results[active])
            } else if (e.key === 'Escape') {
              setOpen(false)
            }
          }}
          className="w-full bg-transparent text-sm outline-none placeholder:text-slate-400"
        />
        {isFetching && <LoaderCircle className="size-3.5 animate-spin text-slate-400" />}
        {shortcut && !q && (
          <kbd className="rounded border border-slate-200 px-1 font-sans text-[10px] text-slate-400">{shortcut}</kbd>
        )}
      </div>
      {open && q.trim() && (
        <div className="absolute z-50 mt-1 max-h-96 w-full min-w-80 overflow-auto rounded-lg border border-slate-200 bg-white py-1 shadow-lg">
          {results.length === 0 && !isFetching && <p className="px-3 py-2 text-sm text-slate-500">No matches</p>}
          {results.map((r, i) => (
            <button
              key={r.id}
              onMouseDown={(e) => {
                e.preventDefault()
                choose(r)
              }}
              onMouseEnter={() => setActive(i)}
              className={cn('flex w-full items-center gap-2 px-3 py-1.5 text-left', i === active && 'bg-indigo-50')}
            >
              <KindIcon kind={r.kind} />
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-medium text-slate-800">{r.label}</span>
                <span className="block truncate font-mono text-[11px] text-slate-400">{r.qualifiedName}</span>
              </span>
              <RoleBadge role={r.role} />
              <RiskBadge score={r.riskScore} />
            </button>
          ))}
        </div>
      )}
    </div>
  )
})
