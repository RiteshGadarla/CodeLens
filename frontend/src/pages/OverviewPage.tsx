import { Crosshair, FlaskConical, Gauge, GitCommitHorizontal, Globe, Network, OctagonAlert, Repeat, ShieldCheck, Timer } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useModules, useOverview, useRuns } from '@/api/queries'
import { BarList, ColumnChart, RiskDistributionBar, SERIES_1, Sparkline } from '@/components/charts'
import { KindIcon, RiskBadge, RoleBadge } from '@/components/EntityBadges'
import { useProjectContext } from '@/components/ProjectLayout'
import { StatusBadge } from '@/components/StatusBadge'
import { Card, CardHeader, Delta, EmptyState, ErrorState, KpiTile, Provenance, Spinner, Stat } from '@/components/ui'
import { fmtAgo, fmtCompact, fmtDuration, fmtInt, fmtPct, fmtRatio, shortSha } from '@/lib/format'
import { useProjectId } from '@/lib/hooks'
import { riskStyle } from '@/lib/risk'

const cap = (s: string) => s.charAt(0) + s.slice(1).toLowerCase()

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
  const k = data.kpis
  const r = data.risk
  const scored = r.low + r.medium + r.high
  const entities = Object.values(data.kinds).reduce((a, b) => a + b, 0)
  // oldest first for trends
  const ok = (runs ?? []).filter((x) => x.status === 'SUCCESS').reverse()
  const latest = ok.at(-1)
  const previous = ok.at(-2)
  const complexMethods = k.complexity.slice(3).reduce((a, b) => a + b.count, 0)
  const productionTypes = s.types - s.tests
  const cycleTypes = new Set(data.cycles.flat().map((t) => t.id)).size

  // one build module says nothing; show the packages furthest from the main sequence instead
  const byPackage = data.modules.length <= 1
  const coupling = byPackage
    ? [...(packages?.items ?? [])].sort((a, b) => b.distance - a.distance).slice(0, 8)
    : data.modules

  return (
    <div className={`mx-auto max-w-7xl space-y-5 p-6 transition-opacity ${isFetching ? 'opacity-60' : ''}`}>
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="min-w-0">
          <h1 className="text-xl font-semibold tracking-tight text-slate-900">{project.name}</h1>
          <p className="mt-0.5 flex flex-wrap items-center gap-x-2 text-sm text-slate-500">
            <span className="truncate font-mono text-xs">{project.sourceUri}</span>
            {project.branch && <span>· {project.branch}</span>}
            {latest && <span>· analyzed {fmtAgo(latest.finishedAt ?? latest.startedAt)}</span>}
          </p>
        </div>
        <Provenance kind="static" />
      </div>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <KpiTile
          label="Low-risk share"
          icon={<ShieldCheck className="size-4" style={{ color: riskStyle.LOW.hex }} />}
          value={fmtPct(r.low, scored)}
          hint={`of ${fmtInt(scored)} scored types and methods`}
        >
          <RiskDistributionBar {...r} />
        </KpiTile>
        <KpiTile
          label="High-risk components"
          icon={<OctagonAlert className="size-4" style={{ color: riskStyle.HIGH.hex }} />}
          value={fmtInt(r.high)}
          hint={`peak score ${k.maxRisk.toFixed(0)} · average ${k.avgRisk.toFixed(1)}`}
        >
          <Link to="metrics" className="text-xs font-medium text-indigo-600 hover:underline">
            Review hotspots →
          </Link>
        </KpiTile>
        <KpiTile
          label="Dependency cycles"
          icon={<Repeat className="size-4 text-slate-400" />}
          value={data.cycles.length}
          hint={data.cycles.length ? `${cycleTypes} types locked in cycles` : 'The type graph is acyclic'}
        />
        <KpiTile
          label="Dependency graph"
          icon={<Network className="size-4 text-slate-400" />}
          value={fmtCompact(entities)}
          unit="entities"
          hint={
            <span className="flex flex-wrap gap-x-2">
              <span>{fmtInt(s.edges)} dependencies</span>
              {latest && previous && <Delta value={latest.entities - previous.entities} />}
            </span>
          }
        >
          <Sparkline
            values={ok.map((x) => x.entities)}
            labels={ok.map((x) => fmtAgo(x.startedAt))}
            format={(v) => `${fmtInt(v)} entities`}
            ariaLabel="Entities per analysis run"
          />
        </KpiTile>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <KpiTile
          label="API endpoints"
          icon={<Globe className="size-4 text-slate-400" />}
          value={fmtInt(s.endpoints)}
          hint={`${fmtInt(k.exposed)} components reachable from an API`}
        />
        <KpiTile
          label="Avg method complexity"
          icon={<Gauge className="size-4 text-slate-400" />}
          value={k.avgComplexity.toFixed(1)}
          hint={`max ${k.maxComplexity} · ${fmtInt(complexMethods)} ${complexMethods === 1 ? 'method' : 'methods'} at 10 or more`}
        />
        <KpiTile
          label="Test classes"
          icon={<FlaskConical className="size-4 text-slate-400" />}
          value={fmtInt(s.tests)}
          hint={s.tests ? `alongside ${fmtInt(productionTypes)} production types` : 'No test classes detected'}
        />
        <KpiTile
          label="Last analysis"
          icon={<Timer className="size-4 text-slate-400" />}
          value={fmtDuration(latest?.durationMs)}
          hint={latest ? `${latest.mode.toLowerCase()} · parsed ${latest.filesParsed} of ${latest.filesTotal} files` : undefined}
        >
          <Sparkline
            values={ok.map((x) => x.durationMs ?? 0)}
            labels={ok.map((x) => `${x.mode.toLowerCase()} · ${fmtAgo(x.startedAt)}`)}
            format={(v) => fmtDuration(v)}
            ariaLabel="Analysis time per run"
          />
        </KpiTile>
      </div>

      <Card className="grid grid-cols-2 gap-6 px-6 py-5 sm:grid-cols-4 lg:grid-cols-8">
        <Stat label="Files" value={fmtInt(s.files)} hint={s.parseErrors ? `${s.parseErrors} parse errors` : undefined} />
        <Stat label="Lines of code" value={<span title={fmtInt(s.loc)}>{fmtCompact(s.loc)}</span>} />
        <Stat label="Types" value={fmtInt(s.types)} />
        <Stat label="Methods" value={fmtInt(s.methods)} />
        <Stat label="Dependencies" value={fmtInt(s.edges)} />
        <Stat label="Packages" value={fmtInt(s.packages)} />
        <Stat label="Modules" value={fmtInt(s.modules)} />
        <Stat label="Max depth" value={fmtInt(k.maxDepth)} hint="longest dependency chain" />
      </Card>

      <div className="grid gap-5 lg:grid-cols-3">
        <Card>
          <CardHeader title="Complexity distribution" subtitle="Methods by cyclomatic complexity" />
          <div className="px-5 py-4">
            <ColumnChart
              ariaLabel="Methods by cyclomatic complexity band"
              data={k.complexity.map((b) => ({
                key: b.label,
                label: b.label,
                segments: [{ name: 'Methods', value: b.count, color: SERIES_1 }],
                detail: (
                  <>
                    <span className="font-semibold text-slate-900 tabular-nums">{fmtInt(b.count)}</span>{' '}
                    <span className="text-slate-500">methods with complexity {b.label}</span>
                  </>
                ),
              }))}
            />
          </div>
        </Card>
        <Card>
          <CardHeader title="Architecture roles" subtitle="Types by role, from annotations and base types" />
          <div className="px-5 py-4">
            <BarList
              items={Object.entries(data.roles)
                .sort((a, b) => b[1] - a[1])
                .map(([role, count]) => ({ key: role, label: cap(role), value: count }))}
            />
          </div>
        </Card>
        <Card>
          <CardHeader title="Dependency cycles" subtitle="Strongly connected type groups" />
          <div className="px-5 py-4">
            {data.cycles.length === 0 ? (
              <p className="flex items-center gap-2 text-sm text-slate-600">
                <ShieldCheck className="size-4" style={{ color: riskStyle.LOW.hex }} /> No cycles between types.
              </p>
            ) : (
              <ul className="space-y-2">
                {data.cycles.slice(0, 6).map((cycle, i) => (
                  <li key={i} className="flex flex-wrap items-center gap-1 text-xs">
                    <Repeat className="size-3.5 text-slate-400" />
                    {cycle.map((t) => (
                      <Link key={t.id} to={`/projects/${projectId}/entities/${t.id}`} className="rounded bg-slate-100 px-1.5 py-0.5 font-mono hover:bg-indigo-50">
                        {t.label}
                      </Link>
                    ))}
                  </li>
                ))}
                {data.cycles.length > 6 && <li className="text-xs text-slate-400">+{data.cycles.length - 6} more</li>}
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
            <thead className="border-b border-slate-100 text-left text-xs whitespace-nowrap text-slate-500">
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
          <div className="overflow-x-auto">
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
          </div>
        </Card>

        <Card>
          <CardHeader title="Analysis runs" subtitle="Incremental runs re-parse only changed files" />
          <div className="overflow-x-auto">
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
                {runs?.slice(0, 8).map((run) => (
                  <tr key={run.id} title={run.error ?? undefined}>
                    <td className="px-5 py-2">
                      <span className="text-slate-700">{run.mode.toLowerCase()}</span>{' '}
                      <span className="font-mono text-xs text-slate-400">{shortSha(run.commitHash)}</span>
                    </td>
                    <td className="px-3 py-2"><StatusBadge status={run.status} /></td>
                    <td className="px-3 py-2 text-right tabular-nums">
                      {run.filesParsed}/{run.filesTotal}
                      {run.filesDeleted > 0 && <span className="text-slate-400"> −{run.filesDeleted}</span>}
                    </td>
                    <td className="px-3 py-2 text-right tabular-nums">{fmtInt(run.entities)}</td>
                    <td className="px-3 py-2 text-right tabular-nums">{fmtDuration(run.durationMs)}</td>
                    <td className="px-5 py-2 text-right text-slate-500">{fmtAgo(run.startedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      </div>
    </div>
  )
}
