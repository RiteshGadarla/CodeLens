import { ReactFlowProvider } from '@xyflow/react'
import { ArrowRightLeft, Crosshair, ExternalLink, Network, X } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useGraph, usePath, type GraphParams } from '@/api/queries'
import { KindIcon, RiskBadge, RoleBadge } from '@/components/EntityBadges'
import { EntityPicker } from '@/components/EntityPicker'
import { GraphCanvas } from '@/components/GraphCanvas'
import { PathChain } from '@/components/PathChain'
import { Badge, Card, EmptyState, ErrorState, Provenance, Segmented, Spinner } from '@/components/ui'
import { useProjectId } from '@/lib/hooks'
import { edgeColor, edgeLabel } from '@/lib/labels'
import type { EdgeType, EntitySummary, GraphNodeView } from '@/types/api'

const LEGEND: EdgeType[] = ['CALLS', 'IMPLEMENTS', 'EXTENDS', 'OVERRIDES', 'CREATES', 'USES_TYPE', 'IMPORTS', 'ROUTES_TO']

function PathFinder({ projectId }: { projectId: number }) {
  const [from, setFrom] = useState<EntitySummary | null>(null)
  const [to, setTo] = useState<EntitySummary | null>(null)
  const { data, isFetching, error } = usePath(projectId, from?.id, to?.id)

  const slot = (value: EntitySummary | null, set: (e: EntitySummary | null) => void, label: string) =>
    value ? (
      <div className="flex items-center gap-1.5 rounded-lg border border-slate-200 bg-slate-50 px-2 py-1.5 text-sm">
        <KindIcon kind={value.kind} />
        <span className="truncate font-mono text-xs">{value.label}</span>
        <button onClick={() => set(null)} className="ml-auto text-slate-400 hover:text-slate-600">
          <X className="size-3.5" />
        </button>
      </div>
    ) : (
      <EntityPicker projectId={projectId} placeholder={label} onSelect={set} />
    )

  return (
    <div className="space-y-2">
      <p className="flex items-center gap-1.5 text-xs font-semibold text-slate-700">
        <ArrowRightLeft className="size-3.5" /> Dependency path
      </p>
      {slot(from, setFrom, 'From…')}
      {slot(to, setTo, 'To…')}
      {isFetching && <p className="text-xs text-slate-400">Searching…</p>}
      {error && <ErrorState error={error} />}
      {data && !data.found && <p className="text-xs text-slate-500">No dependency path in either direction.</p>}
      {data?.found && (
        <div className="space-y-1.5">
          <p className="text-xs text-slate-500">
            {data.direction === 'DOWNSTREAM'
              ? `${from?.label} depends on ${to?.label}`
              : `${to?.label} depends on ${from?.label}`}
          </p>
          {data.paths.map((p, i) => (
            <PathChain
              key={i}
              steps={p}
              projectId={projectId}
              direction={data.direction === 'DOWNSTREAM' ? 'downstream' : 'upstream'}
            />
          ))}
        </div>
      )}
    </div>
  )
}

function NodePanel({ node, projectId, onFocus }: { node: GraphNodeView; projectId: number; onFocus: () => void }) {
  return (
    <div className="space-y-2">
      <div className="flex items-center gap-1.5">
        <KindIcon kind={node.kind} />
        <p className="truncate font-mono text-sm font-semibold text-slate-900">{node.label}</p>
      </div>
      <p className="font-mono text-[11px] break-all text-slate-400">{node.qualifiedName}</p>
      <div className="flex flex-wrap gap-1.5">
        <RoleBadge role={node.role} />
        <RiskBadge score={node.riskScore} />
        {node.module && <Badge>{node.module}</Badge>}
      </div>
      <p className="text-xs text-slate-500 tabular-nums">
        fan-in {node.fanIn} · fan-out {node.fanOut} · complexity {node.complexity}
        {node.distance > 0 && ` · ${node.distance} hop${node.distance > 1 ? 's' : ''} from focus`}
      </p>
      <div className="flex gap-3 pt-1 text-xs font-medium">
        <button onClick={onFocus} className="inline-flex items-center gap-1 text-indigo-600 hover:underline">
          <Crosshair className="size-3.5" /> Focus
        </button>
        <Link to={`/projects/${projectId}/entities/${node.id}`} className="inline-flex items-center gap-1 text-indigo-600 hover:underline">
          <ExternalLink className="size-3.5" /> Details
        </Link>
        <Link to={`/projects/${projectId}/impact/${node.id}`} className="inline-flex items-center gap-1 text-indigo-600 hover:underline">
          Impact
        </Link>
      </div>
    </div>
  )
}

export default function GraphPage() {
  const projectId = useProjectId()
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const [selected, setSelected] = useState<GraphNodeView | null>(null)

  const query: GraphParams = {
    level: (params.get('level') as GraphParams['level']) ?? 'TYPE',
    focus: params.get('focus') ? Number(params.get('focus')) : null,
    depth: Number(params.get('depth') ?? 2),
    direction: (params.get('direction') as GraphParams['direction']) ?? 'BOTH',
    limit: Number(params.get('limit') ?? 80),
    includeTests: params.get('tests') === 'true',
  }
  const set = (patch: Record<string, string | null>) => {
    const next = new URLSearchParams(params)
    Object.entries(patch).forEach(([k, v]) => (v == null ? next.delete(k) : next.set(k, v)))
    setParams(next, { replace: true })
  }
  const { data, isFetching, error } = useGraph(projectId, query)
  const focusNode = data?.nodes.find((n) => n.focus)

  return (
    <div className="flex h-full">
      <div className="relative min-w-0 flex-1">
        <div className="absolute top-3 left-3 z-10 flex flex-wrap items-center gap-2 rounded-xl border border-slate-200 bg-white/95 p-2 shadow-sm backdrop-blur">
          <Segmented
            value={query.level}
            onChange={(v) => set({ level: v })}
            options={[
              { value: 'TYPE', label: 'Types' },
              { value: 'MEMBER', label: 'Members' },
            ]}
          />
          <Segmented
            value={query.direction}
            onChange={(v) => set({ direction: v })}
            options={[
              { value: 'BOTH', label: 'Both' },
              { value: 'UPSTREAM', label: 'Dependents' },
              { value: 'DOWNSTREAM', label: 'Dependencies' },
            ]}
          />
          <label className="flex items-center gap-1.5 text-xs text-slate-600">
            depth
            <select
              value={query.depth}
              onChange={(e) => set({ depth: e.target.value })}
              className="rounded-md border border-slate-200 px-1 py-0.5 text-xs"
            >
              {[1, 2, 3, 4].map((d) => (
                <option key={d}>{d}</option>
              ))}
            </select>
          </label>
          <label className="flex items-center gap-1.5 text-xs text-slate-600">
            <input type="checkbox" checked={query.includeTests} onChange={(e) => set({ tests: e.target.checked ? 'true' : null })} />
            tests
          </label>
          {focusNode ? (
            <span className="flex items-center gap-1 rounded-md bg-indigo-50 px-2 py-0.5 text-xs text-indigo-700">
              <Crosshair className="size-3" /> {focusNode.label}
              <button onClick={() => set({ focus: null })} className="ml-0.5 hover:text-indigo-900">
                <X className="size-3" />
              </button>
            </span>
          ) : (
            <span className="text-xs text-slate-500">showing highest-risk {query.level === 'TYPE' ? 'types' : 'members'}</span>
          )}
          {isFetching && <span className="text-xs text-slate-400">loading…</span>}
        </div>

        {error && (
          <div className="p-6 pt-20">
            <ErrorState error={error} />
          </div>
        )}
        {!data && !error && <Spinner />}
        {data && data.nodes.length === 0 && (
          <EmptyState icon={<Network className="size-10" />} title="No graph yet">
            Run an analysis to build the dependency graph.
          </EmptyState>
        )}
        {data && data.nodes.length > 0 && (
          <ReactFlowProvider>
            <GraphCanvas
              key={JSON.stringify(query) + data.nodes.length}
              view={data}
              onSelect={setSelected}
              onOpen={(n) => navigate(`/projects/${projectId}/entities/${n.id}`)}
            />
          </ReactFlowProvider>
        )}
        {data?.truncated && (
          <div className="absolute bottom-3 left-1/2 z-10 -translate-x-1/2 rounded-md bg-amber-50 px-2 py-1 text-xs text-amber-700 ring-1 ring-amber-200">
            Showing {data.nodes.length} nodes; focus an entity to explore further
          </div>
        )}
      </div>

      <aside className="w-80 shrink-0 space-y-5 overflow-auto border-l border-slate-200 bg-white p-4">
        <div className="space-y-2">
          <p className="text-xs font-semibold text-slate-700">Focus</p>
          <EntityPicker projectId={projectId} placeholder="Center graph on…" onSelect={(e) => set({ focus: String(e.id) })} />
        </div>
        <Card className="p-3">
          {selected ? (
            <NodePanel node={selected} projectId={projectId} onFocus={() => set({ focus: String(selected.id) })} />
          ) : (
            <p className="text-xs text-slate-500">Click a node to inspect it, double-click to open.</p>
          )}
        </Card>
        <PathFinder projectId={projectId} />
        <div className="space-y-1.5">
          <div className="flex items-center justify-between">
            <p className="text-xs font-semibold text-slate-700">Edges</p>
            <Provenance kind="static" />
          </div>
          {LEGEND.map((t) => (
            <div key={t} className="flex items-center gap-2 text-xs text-slate-600">
              <span className="h-0.5 w-5 rounded" style={{ background: edgeColor[t] }} />
              {edgeLabel[t]}
            </div>
          ))}
          <p className="pt-1 text-[11px] text-slate-400">Arrows point from dependent to dependency. Left border = risk.</p>
        </div>
      </aside>
    </div>
  )
}
