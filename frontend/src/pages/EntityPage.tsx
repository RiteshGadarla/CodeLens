import { Crosshair, Network } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useEntity, useSource } from '@/api/queries'
import { CodeBlock } from '@/components/CodeBlock'
import { KindIcon, RiskBadge, RoleBadge } from '@/components/EntityBadges'
import { Badge, Card, CardHeader, EmptyState, ErrorState, Provenance, Segmented, Spinner } from '@/components/ui'
import { fmtInt } from '@/lib/format'
import { useProjectId } from '@/lib/hooks'
import { edgeLabel, kindLabel } from '@/lib/labels'
import type { EdgeType, Relation } from '@/types/api'

type Tab = 'dependents' | 'dependencies' | 'members' | 'source'

function Relations({ items, projectId, empty }: { items: Relation[]; projectId: number; empty: string }) {
  if (items.length === 0) return <EmptyState title={empty} />
  const groups = new Map<EdgeType, Relation[]>()
  items.forEach((r) => groups.set(r.type, [...(groups.get(r.type) ?? []), r]))
  return (
    <div className="divide-y divide-slate-100">
      {[...groups.entries()].map(([type, rels]) => (
        <div key={type} className="px-5 py-3">
          <p className="mb-1.5 text-[11px] font-semibold tracking-wide text-slate-400 uppercase">
            {edgeLabel[type]} · {rels.length}
          </p>
          <ul className="space-y-0.5">
            {rels.map((r) => (
              <li key={`${type}-${r.entity.id}`}>
                <Link
                  to={`/projects/${projectId}/entities/${r.entity.id}`}
                  className="flex items-center gap-2 rounded-md px-2 py-1 hover:bg-slate-50"
                >
                  <KindIcon kind={r.entity.kind} />
                  <span className="truncate font-mono text-[13px] text-slate-700">{r.entity.label}</span>
                  <RoleBadge role={r.entity.role} />
                  {r.weight > 1 && <span className="ml-auto text-xs text-slate-400 tabular-nums">×{r.weight}</span>}
                </Link>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </div>
  )
}

function SourceTab({ projectId, entityId }: { projectId: number; entityId: number }) {
  const { data, isLoading, error } = useSource(projectId, entityId)
  if (isLoading) return <Spinner />
  if (error) return <div className="p-5"><ErrorState error={error} /></div>
  if (!data) return null
  return (
    <div className="p-4">
      <p className="mb-2 font-mono text-xs text-slate-500">
        {data.path}:{data.startLine}-{data.endLine}
      </p>
      <CodeBlock code={data.code} startLine={data.startLine} className="max-h-[70vh]" />
    </div>
  )
}

export default function EntityPage() {
  const projectId = useProjectId()
  const entityId = Number(useParams().entityId)
  const { data, isLoading, error } = useEntity(projectId, entityId)
  const [tab, setTab] = useState<Tab>('dependents')

  if (isLoading) return <Spinner />
  if (error) return <div className="p-6"><ErrorState error={error} /></div>
  if (!data) return null

  const e = data.entity
  const m = data.metrics
  const stats = m
    ? [
        ['Fan-in', m.fanIn],
        ['Fan-out', m.fanOut],
        ['Dependents', m.dependents],
        ['Dependencies', m.dependencies],
        ['Depth', m.depth],
        ['Complexity', m.complexity],
      ]
    : []

  return (
    <div className="mx-auto max-w-6xl space-y-5 p-6">
      <div className="flex flex-wrap items-start gap-4">
        <div className="min-w-0 flex-1">
          {data.parent && (
            <Link to={`/projects/${projectId}/entities/${data.parent.id}`} className="text-xs text-slate-400 hover:text-indigo-600">
              {data.parent.qualifiedName}
            </Link>
          )}
          <div className="mt-0.5 flex items-center gap-2">
            <KindIcon kind={e.kind} className="size-5" />
            <h1 className="truncate font-mono text-xl font-semibold text-slate-900">{e.label}</h1>
            <Badge>{kindLabel[e.kind]}</Badge>
            <RoleBadge role={e.role} />
            {m && <RiskBadge score={m.riskScore} level={m.riskLevel} />}
            {m?.exposed && <Badge className="bg-emerald-50 text-emerald-700 ring-emerald-200">reaches API</Badge>}
          </div>
          <p className="mt-1 truncate font-mono text-xs text-slate-500">{e.qualifiedName}</p>
        </div>
        <div className="flex gap-2">
          <Link
            to={`/projects/${projectId}/graph?focus=${e.id}`}
            className="inline-flex items-center gap-1.5 rounded-lg bg-white px-3 py-1.5 text-sm font-medium text-slate-700 ring-1 ring-slate-300 ring-inset hover:bg-slate-50"
          >
            <Network className="size-4" /> Graph
          </Link>
          <Link
            to={`/projects/${projectId}/impact/${e.id}`}
            className="inline-flex items-center gap-1.5 rounded-lg bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white shadow-sm hover:bg-indigo-500"
          >
            <Crosshair className="size-4" /> Impact analysis
          </Link>
        </div>
      </div>

      {(data.signature || data.httpPath || data.annotations) && (
        <Card className="space-y-2 px-5 py-4">
          {data.httpMethod && (
            <p className="font-mono text-sm">
              <span className="font-semibold text-emerald-600">{data.httpMethod}</span> {data.httpPath}
            </p>
          )}
          {data.annotations && (
            <p className="font-mono text-xs text-slate-500">
              {data.annotations.split(',').map((a) => `@${a}`).join(' ')}
            </p>
          )}
          {data.signature && <p className="font-mono text-sm text-slate-800">{data.signature}</p>}
          {e.filePath && (
            <p className="font-mono text-xs text-slate-400">
              {e.filePath}:{e.startLine}-{e.endLine} · module {e.module}
            </p>
          )}
        </Card>
      )}

      {m && (
        <Card className="px-5 py-4">
          <div className="mb-3 flex items-center justify-between">
            <h3 className="text-sm font-semibold text-slate-900">Metrics</h3>
            <Provenance kind="static" />
          </div>
          <dl className="grid grid-cols-3 gap-4 sm:grid-cols-6">
            {stats.map(([label, value]) => (
              <div key={label}>
                <dt className="text-xs text-slate-500">{label}</dt>
                <dd className="mt-0.5 text-lg font-semibold text-slate-900 tabular-nums">{fmtInt(value as number)}</dd>
              </div>
            ))}
          </dl>
        </Card>
      )}

      <Card>
        <CardHeader
          title="Relationships"
          subtitle="Direct edges from static analysis"
          action={
            <Segmented
              value={tab}
              onChange={setTab}
              options={[
                { value: 'dependents', label: `Used by (${data.dependents.length})` },
                { value: 'dependencies', label: `Uses (${data.dependencies.length})` },
                { value: 'members', label: `Members (${data.members.length})` },
                { value: 'source', label: 'Source' },
              ]}
            />
          }
        />
        {tab === 'dependents' && <Relations items={data.dependents} projectId={projectId} empty="Nothing depends on this directly" />}
        {tab === 'dependencies' && <Relations items={data.dependencies} projectId={projectId} empty="No project dependencies" />}
        {tab === 'members' &&
          (data.members.length === 0 ? (
            <EmptyState title="No members" />
          ) : (
            <ul className="divide-y divide-slate-100">
              {data.members.map((mem) => (
                <li key={mem.id}>
                  <Link
                    to={`/projects/${projectId}/entities/${mem.id}`}
                    className="flex items-center gap-2 px-5 py-2 hover:bg-slate-50"
                  >
                    <KindIcon kind={mem.kind} />
                    <span className="truncate font-mono text-[13px] text-slate-700">{mem.label}</span>
                    <span className="ml-auto text-xs text-slate-400">line {mem.startLine}</span>
                    <RiskBadge score={mem.riskScore} />
                  </Link>
                </li>
              ))}
            </ul>
          ))}
        {tab === 'source' && <SourceTab projectId={projectId} entityId={entityId} />}
      </Card>
    </div>
  )
}
