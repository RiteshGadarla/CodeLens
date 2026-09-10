import { ChevronRight, FlaskConical, Globe, Sparkles } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError } from '@/api/client'
import { useImpact, useImpactReport, useReports } from '@/api/queries'
import { FactorMeters, RiskLevelLabel } from '@/components/charts'
import { KindIcon, RoleBadge } from '@/components/EntityBadges'
import { Markdown } from '@/components/Markdown'
import { PathChain } from '@/components/PathChain'
import { Badge, Button, Card, CardHeader, EmptyState, ErrorState, Provenance, Spinner, Stat } from '@/components/ui'
import { fmtAgo, fmtInt } from '@/lib/format'
import { useProjectId } from '@/lib/hooks'
import { edgeLabel, kindLabel, roleStyle } from '@/lib/labels'
import type { Report, Stereotype } from '@/types/api'

function ReportCard({ projectId, entityId }: { projectId: number; entityId: number }) {
  const generate = useImpactReport(projectId)
  const { data: reports } = useReports(projectId)
  const report: Report | undefined = generate.data ?? reports?.find((r) => r.entityId === entityId)
  const error = generate.error

  return (
    <Card>
      <CardHeader
        title="Lyra impact report"
        subtitle="Narrative written by Lyra from the analysis above and retrieved source"
        action={
          <Button variant={report ? 'secondary' : 'primary'} loading={generate.isPending} onClick={() => generate.mutate(entityId)}>
            <Sparkles className="size-4" /> {report ? 'Regenerate' : 'Generate report'}
          </Button>
        }
      />
      <div className="px-5 py-4">
        {error && (
          <ErrorState
            error={error instanceof ApiError && error.status === 503 ? new Error(`Lyra is unavailable right now: ${error.message}`) : error}
          />
        )}
        {generate.isPending && (
          <p className="flex items-center gap-2 text-sm text-slate-500">
            <Sparkles className="size-4 animate-pulse text-violet-500" /> Lyra is writing the report…
          </p>
        )}
        {!report && !generate.isPending && !error && (
          <p className="text-sm text-slate-500">
            Generate a developer-facing summary: what breaks, blast radius, tests to run and a safe change plan.
          </p>
        )}
        {report && !generate.isPending && (
          <div className="space-y-3">
            <div className="flex items-center gap-2">
              <Provenance kind="ai" />
              <span className="text-xs text-slate-400">{fmtAgo(report.createdAt)}</span>
            </div>
            <Markdown text={report.summary} />
            {report.sources.length > 0 && (
              <ol className="space-y-1 border-t border-slate-100 pt-3 text-xs">
                {report.sources.map((s) => (
                  <li key={s.ref} className="flex items-center gap-2">
                    <span className="inline-flex h-4 min-w-4 items-center justify-center rounded bg-violet-100 px-1 text-[10px] font-semibold text-violet-700">{s.ref}</span>
                    {s.entityId ? (
                      <Link to={`/projects/${projectId}/entities/${s.entityId}`} className="font-mono text-indigo-600 hover:underline">{s.label}</Link>
                    ) : (
                      <span className="font-mono">{s.label}</span>
                    )}
                    <span className="truncate font-mono text-slate-400">{s.path}:{s.startLine}-{s.endLine}</span>
                  </li>
                ))}
              </ol>
            )}
          </div>
        )}
      </div>
    </Card>
  )
}

export default function ImpactPage() {
  const projectId = useProjectId()
  const entityId = Number(useParams().entityId)
  const [depth, setDepth] = useState(10)
  const [includeTests, setIncludeTests] = useState(false)
  const [showAll, setShowAll] = useState(false)
  const [allEndpoints, setAllEndpoints] = useState(false)
  const { data, isLoading, error, isFetching } = useImpact(projectId, entityId, depth, includeTests)

  if (isLoading) return <Spinner label="Traversing dependents…" />
  if (error) return <div className="p-6"><ErrorState error={error} /></div>
  if (!data) return null

  const t = data.target
  const affected = showAll ? data.affected : data.affected.slice(0, 50)

  return (
    <div className={`mx-auto max-w-7xl space-y-5 p-6 transition-opacity ${isFetching ? 'opacity-60' : ''}`}>
      <div className="flex flex-wrap items-end gap-4">
        <div className="min-w-0 flex-1">
          <p className="text-xs font-medium text-slate-500">Change impact of</p>
          <div className="mt-0.5 flex items-center gap-2">
            <KindIcon kind={t.kind} className="size-5" />
            <Link to={`/projects/${projectId}/entities/${t.id}`} className="truncate font-mono text-xl font-semibold text-slate-900 hover:text-indigo-700">
              {t.label}
            </Link>
            <Badge>{kindLabel[t.kind]}</Badge>
            <RoleBadge role={t.role} />
          </div>
          <p className="mt-1 truncate font-mono text-xs text-slate-500">{t.qualifiedName}</p>
        </div>
        <div className="flex items-center gap-3 text-xs text-slate-600">
          <label className="flex items-center gap-1.5">
            max depth
            <select value={depth} onChange={(e) => setDepth(Number(e.target.value))} className="rounded-md border border-slate-200 px-1 py-0.5">
              {[1, 2, 3, 5, 10, 20].map((d) => <option key={d}>{d}</option>)}
            </select>
          </label>
          <label className="flex items-center gap-1.5">
            <input type="checkbox" checked={includeTests} onChange={(e) => setIncludeTests(e.target.checked)} /> count tests
          </label>
          <Provenance kind="static" />
        </div>
      </div>

      <div className="grid gap-5 lg:grid-cols-[340px_1fr]">
        <Card className="px-5 py-5">
          <p className="text-xs font-medium text-slate-500">Risk score</p>
          <div className="mt-1 flex items-baseline gap-3">
            <span className="text-5xl font-semibold text-slate-900">{data.risk.score.toFixed(0)}</span>
            <span className="text-sm text-slate-400">/ 100</span>
          </div>
          <RiskLevelLabel level={data.risk.level} className="mt-1 text-sm" />
          <div className="mt-5">
            <FactorMeters factors={data.risk.factors} />
          </div>
        </Card>

        <div className="space-y-5">
          <Card className="grid grid-cols-2 gap-6 px-6 py-5 sm:grid-cols-3 xl:grid-cols-6">
            <Stat label="Direct dependents" value={fmtInt(data.directDependents)} />
            <Stat label="Transitive dependents" value={fmtInt(data.transitiveDependents)} />
            <Stat label="Max depth" value={data.maxDepth} />
            <Stat label="APIs affected" value={data.endpoints.length} />
            <Stat label="Modules" value={data.modules.length} />
            <Stat label="Tests to run" value={data.tests.length} />
          </Card>

          <div className="grid gap-5 md:grid-cols-2">
            <Card>
              <CardHeader title="Affected components" subtitle="Owning types, by architectural role" />
              <div className="space-y-3 px-5 py-4">
                {Object.keys(data.affectedTypes).length === 0 && <p className="text-sm text-slate-500">No other components depend on this.</p>}
                {Object.entries(data.affectedTypes).map(([role, types]) => (
                  <div key={role}>
                    <Badge className={roleStyle[role as Stereotype | 'OTHER']}>{role.toLowerCase()} · {types.length}</Badge>
                    <div className="mt-1.5 flex flex-wrap gap-1">
                      {types.map((ty) => (
                        <Link key={ty.id} to={`/projects/${projectId}/entities/${ty.id}`} className="rounded bg-slate-100 px-1.5 py-0.5 font-mono text-xs text-slate-700 hover:bg-indigo-50">
                          {ty.label}
                        </Link>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            </Card>
            <Card>
              <CardHeader title="Tests to run" subtitle="Test classes that reach the change" />
              <div className="px-5 py-4">
                {data.tests.length === 0 ? (
                  <p className="flex items-center gap-2 text-sm text-amber-700">
                    <FlaskConical className="size-4" /> No tests reach this code.
                  </p>
                ) : (
                  <ul className="flex flex-wrap gap-1">
                    {data.tests.map((ty) => (
                      <li key={ty.id}>
                        <Link to={`/projects/${projectId}/entities/${ty.id}`} className="inline-flex items-center gap-1 rounded bg-fuchsia-50 px-1.5 py-0.5 font-mono text-xs text-fuchsia-700 hover:bg-fuchsia-100">
                          <FlaskConical className="size-3" /> {ty.label}
                        </Link>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </Card>
          </div>
        </div>
      </div>

      <Card>
        <CardHeader title="Exposed APIs" subtitle="Endpoints whose handlers transitively depend on the change" />
        {data.endpoints.length === 0 ? (
          <EmptyState icon={<Globe className="size-8" />} title="No API endpoints affected" />
        ) : (
          <ul className="divide-y divide-slate-100">
            {(allEndpoints ? data.endpoints : data.endpoints.slice(0, 4)).map((a) => (
              <li key={a.entity.id} className="px-5 py-3">
                <p className="mb-1.5 font-mono text-sm font-semibold text-slate-800">{a.entity.label}</p>
                <PathChain steps={a.path} projectId={projectId} />
              </li>
            ))}
            {data.endpoints.length > 4 && !allEndpoints && (
              <li className="flex flex-wrap items-center gap-1.5 px-5 py-2.5">
                {data.endpoints.slice(4).map((a) => (
                  <Badge key={a.entity.id} className="bg-slate-50 font-mono text-slate-600 ring-slate-200">{a.entity.label}</Badge>
                ))}
                <Button variant="ghost" onClick={() => setAllEndpoints(true)}>Show paths</Button>
              </li>
            )}
          </ul>
        )}
      </Card>

      <ReportCard projectId={projectId} entityId={entityId} />

      <Card>
        <CardHeader
          title={`Affected entities (${fmtInt(data.transitiveDependents)})`}
          subtitle="Breadth-first over reverse dependencies; interface calls follow dynamic dispatch"
        />
        {data.affected.length === 0 ? (
          <EmptyState title="Nothing depends on this" />
        ) : (
          <table className="w-full text-sm">
            <thead className="border-b border-slate-100 text-left text-xs text-slate-500">
              <tr>
                <th className="w-16 px-5 py-2 text-right font-medium">Depth</th>
                <th className="px-3 py-2 font-medium">Entity</th>
                <th className="px-3 py-2 font-medium">Via</th>
                <th className="px-5 py-2 font-medium">Module</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-50">
              {affected.map((a) => (
                <tr key={a.entity.id} className="align-top hover:bg-slate-50">
                  <td className="px-5 py-2 text-right text-slate-500 tabular-nums">{a.depth}</td>
                  <td className="px-3 py-2">
                    <details className="group">
                      <summary className="flex cursor-pointer list-none items-center gap-1.5">
                        <ChevronRight className="size-3.5 text-slate-400 transition group-open:rotate-90" />
                        <KindIcon kind={a.entity.kind} />
                        <span className="font-mono text-[13px] text-slate-800">{a.entity.label}</span>
                        <RoleBadge role={a.entity.role} />
                      </summary>
                      <div className="mt-2 ml-5">
                        <PathChain steps={a.path} projectId={projectId} />
                      </div>
                    </details>
                  </td>
                  <td className="px-3 py-2 text-xs text-slate-500">{edgeLabel[a.via]}</td>
                  <td className="px-5 py-2 font-mono text-xs text-slate-500">{a.entity.module}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {data.affected.length > 50 && !showAll && (
          <div className="border-t border-slate-100 px-5 py-2.5">
            <Button variant="ghost" onClick={() => setShowAll(true)}>Show all {data.affected.length}</Button>
          </div>
        )}
        {data.truncated && <p className="px-5 pb-3 text-xs text-amber-700">Result capped; increase precision by lowering depth.</p>}
      </Card>
    </div>
  )
}
