import { Crosshair, GitCommitHorizontal, Repeat } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useModules, useOverview, useRuns } from '@/api/queries'
import { RiskDistributionBar } from '@/components/charts'
import { KindIcon, RiskBadge, RoleBadge } from '@/components/EntityBadges'
import { useProjectContext } from '@/components/ProjectLayout'
import { StatusBadge } from '@/components/StatusBadge'
import { Badge, Card, CardHeader, EmptyState, ErrorState, Provenance, Spinner, Stat } from '@/components/ui'
import { fmtAgo, fmtDuration, fmtInt, fmtRatio, shortSha } from '@/lib/format'
import { useProjectId } from '@/lib/hooks'
import { roleStyle } from '@/lib/labels'
import type { Stereotype } from '@/types/api'

export default function OverviewPage() {
  const projectId = useProjectId()
  const { project } = useProjectContext()
  const { data, isLoading, error, isFetching } = useOverview(projectId)
  const { data: runs } = useRuns(projectId)
  const { data: packages } = useModules(projectId, 'PACKAGE')

  if (isLoading) return <Spinner />
  if (error) return <div className="p-6"><ErrorState error={error} /></div>
  if (!data) return null

  if (data.stats.files === 0) {
    return (
      <EmptyState icon={<GitCommitHorizontal className="size-10" />} title={project.analyzing ? 'Analysis in progress' : 'Not analyzed yet'}>
        {project.analyzing
          ? 'Parsing sources and building the dependency graph. This page fills in when the run finishes.'
          : 'Use Re-analyze to parse the repository and build its dependency graph.'}
      </EmptyState>
    )
  }

  const s = data.stats
  // one build module says nothing; show the packages furthest from the main sequence instead
  const byPackage = data.modules.length <= 1
  const coupling = byPackage
    ? [...(packages?.items ?? [])].sort((a, b) => b.distance - a.distance).slice(0, 8)
    : data.modules
  return (
    <div className={`mx-auto max-w-7xl space-y-5 p-6 transition-opacity ${isFetching ? 'opacity-60' : ''}`}>
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-lg font-semibold text-slate-900">Overview</h1>
          <p className="text-sm text-slate-500">
            {project.sourceUri} {project.branch && <>· {project.branch}</>}
          </p>
        </div>
        <Provenance kind="static" />
      </div>

      <Card className="grid grid-cols-2 gap-6 px-6 py-5 sm:grid-cols-4 lg:grid-cols-8">
        <Stat label="Files" value={fmtInt(s.files)} hint={s.parseErrors ? `${s.parseErrors} parse errors` : undefined} />
        <Stat label="Lines of code" value={fmtInt(s.loc)} />
        <Stat label="Types" value={fmtInt(s.types)} hint={`${s.tests} test classes`} />
        <Stat label="Methods" value={fmtInt(s.methods)} />
        <Stat label="API endpoints" value={fmtInt(s.endpoints)} />
        <Stat label="Dependencies" value={fmtInt(s.edges)} />
        <Stat label="Packages" value={fmtInt(s.packages)} />
        <Stat label="Modules" value={fmtInt(s.modules)} />
      </Card>

      <div className="grid gap-5 lg:grid-cols-3">
        <Card>
          <CardHeader title="Risk distribution" subtitle="Types and methods by change-risk score" />
          <div className="px-5 py-4">
            <RiskDistributionBar {...data.risk} />
          </div>
        </Card>
        <Card>
          <CardHeader title="Architecture roles" subtitle="Detected from annotations and base types" />
          <ul className="flex flex-wrap gap-2 px-5 py-4">
            {Object.entries(data.roles).map(([role, count]) => (
              <li key={role}>
                <Badge className={roleStyle[role as Stereotype | 'OTHER']}>
                  {role.toLowerCase()} <span className="font-semibold tabular-nums">{count}</span>
                </Badge>
              </li>
            ))}
          </ul>
        </Card>
        <Card>
          <CardHeader title="Dependency cycles" subtitle="Strongly connected type groups" />
          <div className="px-5 py-4">
            {data.cycles.length === 0 ? (
              <p className="text-sm text-slate-500">No cycles between types.</p>
            ) : (
              <ul className="space-y-2">
                {data.cycles.slice(0, 5).map((cycle, i) => (
                  <li key={i} className="flex flex-wrap items-center gap-1 text-xs">
                    <Repeat className="size-3.5 text-slate-400" />
                    {cycle.map((t) => (
                      <Link key={t.id} to={`/projects/${projectId}/entities/${t.id}`} className="rounded bg-slate-100 px-1.5 py-0.5 font-mono hover:bg-indigo-50">
                        {t.label}
                      </Link>
                    ))}
                  </li>
                ))}
                {data.cycles.length > 5 && <li className="text-xs text-slate-400">+{data.cycles.length - 5} more</li>}
              </ul>
            )}
          </div>
        </Card>
      </div>

      <Card>
        <CardHeader
          title="Riskiest components"
          subtitle="Highest change-risk: dependents, depth, coupling, complexity and API exposure"
          action={<Link to="metrics" className="text-xs font-medium text-indigo-600 hover:underline">All hotspots</Link>}
        />
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="border-b border-slate-100 text-left text-xs text-slate-500">
              <tr>
                <th className="px-5 py-2 font-medium">Entity</th>
                <th className="px-3 py-2 font-medium">Role</th>
                <th className="px-3 py-2 text-right font-medium">Dependents</th>
                <th className="px-3 py-2 text-right font-medium">Fan-in</th>
                <th className="px-3 py-2 text-right font-medium">Complexity</th>
                <th className="px-3 py-2 font-medium">Risk</th>
                <th className="px-5 py-2" />
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-50">
              {data.topRisks.map(({ entity, metrics }) => (
                <tr key={entity.id} className="hover:bg-slate-50">
                  <td className="px-5 py-2">
                    <Link to={`/projects/${projectId}/entities/${entity.id}`} className="flex items-center gap-2">
                      <KindIcon kind={entity.kind} />
                      <span className="truncate font-mono text-[13px] text-slate-800">{entity.label}</span>
                    </Link>
                  </td>
                  <td className="px-3 py-2"><RoleBadge role={entity.role} /></td>
                  <td className="px-3 py-2 text-right tabular-nums">{fmtInt(metrics.dependents)}</td>
                  <td className="px-3 py-2 text-right tabular-nums">{fmtInt(metrics.fanIn)}</td>
                  <td className="px-3 py-2 text-right tabular-nums">{metrics.complexity}</td>
                  <td className="px-3 py-2"><RiskBadge score={metrics.riskScore} level={metrics.riskLevel} /></td>
                  <td className="px-5 py-2 text-right">
                    <Link to={`/projects/${projectId}/impact/${entity.id}`} className="inline-flex items-center gap-1 text-xs font-medium text-indigo-600 hover:underline">
                      <Crosshair className="size-3.5" /> Impact
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      <div className="grid gap-5 lg:grid-cols-2">
        <Card>
          <CardHeader
            title={byPackage ? 'Package coupling' : 'Module coupling'}
            subtitle={byPackage ? 'Packages furthest from the main sequence (D)' : 'Afferent (Ca), efferent (Ce), instability, abstractness, distance'}
            action={<Link to="metrics" className="text-xs font-medium text-indigo-600 hover:underline">Details</Link>}
          />
          <table className="w-full text-sm">
            <thead className="border-b border-slate-100 text-xs text-slate-500">
              <tr>
                <th className="px-5 py-2 text-left font-medium">{byPackage ? 'Package' : 'Module'}</th>
                {['Types', 'Ca', 'Ce', 'I', 'A', 'D'].map((h) => (
                  <th key={h} className="px-3 py-2 text-right font-medium">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-50">
              {coupling.map((m) => (
                <tr key={m.name}>
                  <td className="max-w-56 truncate px-5 py-2 font-mono text-[13px]" title={m.name}>{m.name}</td>
                  <td className="px-3 py-2 text-right tabular-nums">{m.entities}</td>
                  <td className="px-3 py-2 text-right tabular-nums">{m.afferent}</td>
                  <td className="px-3 py-2 text-right tabular-nums">{m.efferent}</td>
                  <td className="px-3 py-2 text-right tabular-nums">{fmtRatio(m.instability)}</td>
                  <td className="px-3 py-2 text-right tabular-nums">{fmtRatio(m.abstractness)}</td>
                  <td className="px-3 py-2 text-right tabular-nums">{fmtRatio(m.distance)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>

        <Card>
          <CardHeader title="Analysis runs" subtitle="Incremental runs re-parse only changed files" />
          <table className="w-full text-sm">
            <thead className="border-b border-slate-100 text-xs text-slate-500">
              <tr>
                <th className="px-5 py-2 text-left font-medium">Run</th>
                <th className="px-3 py-2 text-left font-medium">Status</th>
                <th className="px-3 py-2 text-right font-medium">Parsed</th>
                <th className="px-3 py-2 text-right font-medium">Entities</th>
                <th className="px-3 py-2 text-right font-medium">Time</th>
                <th className="px-5 py-2 text-right font-medium">When</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-50">
              {runs?.slice(0, 8).map((r) => (
                <tr key={r.id} title={r.error ?? undefined}>
                  <td className="px-5 py-2">
                    <span className="text-slate-700">{r.mode.toLowerCase()}</span>{' '}
                    <span className="font-mono text-xs text-slate-400">{shortSha(r.commitHash)}</span>
                  </td>
                  <td className="px-3 py-2"><StatusBadge status={r.status} /></td>
                  <td className="px-3 py-2 text-right tabular-nums">
                    {r.filesParsed}/{r.filesTotal}
                    {r.filesDeleted > 0 && <span className="text-slate-400"> −{r.filesDeleted}</span>}
                  </td>
                  <td className="px-3 py-2 text-right tabular-nums">{fmtInt(r.entities)}</td>
                  <td className="px-3 py-2 text-right tabular-nums">{fmtDuration(r.durationMs)}</td>
                  <td className="px-5 py-2 text-right text-slate-500">{fmtAgo(r.startedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      </div>
    </div>
  )
}
