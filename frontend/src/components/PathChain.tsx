import { Fragment } from 'react'
import { Link } from 'react-router-dom'
import { edgeLabel } from '@/lib/labels'
import type { PathStep } from '@/types/api'
import { KindIcon } from './EntityBadges'

// upstream: each step depends on the previous one; downstream: the previous depends on this one
export function PathChain({
  steps,
  projectId,
  direction = 'upstream',
}: {
  steps: PathStep[]
  projectId: number
  direction?: 'upstream' | 'downstream'
}) {
  return (
    <ol className="flex flex-wrap items-center gap-x-1 gap-y-1.5 text-xs">
      {steps.map((s, i) => (
        <Fragment key={`${s.id}-${i}`}>
          {i > 0 && (
            <li className="flex items-center gap-1 text-[11px] text-slate-400">
              <span>{direction === 'upstream' ? '←' : '→'}</span>
              <span className={s.dispatch ? 'text-fuchsia-600' : ''}>
                {s.dispatch ? 'dispatch' : s.edge ? edgeLabel[s.edge] : ''}
              </span>
              <span>{direction === 'upstream' ? '←' : '→'}</span>
            </li>
          )}
          <li>
            <Link
              to={`/projects/${projectId}/entities/${s.id}`}
              className="inline-flex items-center gap-1 rounded-md bg-slate-100 px-1.5 py-0.5 font-mono text-slate-700 hover:bg-indigo-50 hover:text-indigo-700"
            >
              <KindIcon kind={s.kind} className="size-3" />
              {s.label}
            </Link>
          </li>
        </Fragment>
      ))}
    </ol>
  )
}
