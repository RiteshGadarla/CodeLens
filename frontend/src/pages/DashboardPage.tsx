import { Activity, CircleCheck, CircleX, CodeXml, Folder, FolderGit2, LoaderCircle, Plus, ShieldCheck, Trash2, Upload } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useDashboard, useDeleteProject } from '@/api/queries'
import { AppHeader } from '@/components/AppHeader'
import { ChartLegend, ColumnChart, InlineBar, MiniColumns, RiskDistributionBar } from '@/components/charts'
import { KindIcon, RiskBadge } from '@/components/EntityBadges'
import { CreateProjectForm, NewProjectDialog } from '@/components/NewProjectDialog'
import { StatusBadge } from '@/components/StatusBadge'
import { Button, Card, CardHeader, ErrorState, KpiTile, Spinner, Stat } from '@/components/ui'
import { useAuth } from '@/lib/auth'
import { fmtAgo, fmtCompact, fmtDuration, fmtInt, fmtPct } from '@/lib/format'
import { riskLevel, riskStyle } from '@/lib/risk'
import type { Dashboard, ProjectKpi, SourceType } from '@/types/api'

const sourceIcon: Record<SourceType, typeof Folder> = { LOCAL: Folder, GIT: FolderGit2, UPLOAD: Upload }

// run outcomes are states: reserved good / critical steps
const RUN_OK = riskStyle.LOW.hex
const RUN_FAILED = riskStyle.HIGH.hex

const dayLabel = (iso: string) =>
  new Date(`${iso}T00:00:00Z`).toLocaleDateString(undefined, { month: 'short', day: 'numeric', timeZone: 'UTC' })

function greeting() {
  const h = new Date().getHours()
  return h < 12 ? 'Good morning' : h < 18 ? 'Good afternoon' : 'Good evening'
}

function StatusCounts({ ready, analyzing, failed }: { ready: number; analyzing: number; failed: number }) {
  return (
    <span className="flex flex-wrap items-center gap-x-3 gap-y-1">
      <span className="flex items-center gap-1">
        <CircleCheck className="size-3.5" style={{ color: RUN_OK }} /> {ready} ready
      </span>
      {analyzing > 0 && (
        <span className="flex items-center gap-1">
          <LoaderCircle className="size-3.5 animate-spin text-indigo-500" /> {analyzing} analyzing
        </span>
      )}
      {failed > 0 && (
        <span className="flex items-center gap-1">
          <CircleX className="size-3.5" style={{ color: RUN_FAILED }} /> {failed} failed
        </span>
      )}
    </span>
  )
}

function ProjectRow({ p, maxLoc }: { p: ProjectKpi; maxLoc: number }) {
  const remove = useDeleteProject()
  const Icon = sourceIcon[p.sourceType]
  return (
    <tr className="hover:bg-slate-50">
      <td className="px-5 py-3">
        <Link to={`/projects/${p.id}`} className="flex min-w-0 items-center gap-3">
          <Icon className="size-5 shrink-0 text-slate-400" />
          <span className="min-w-0">
            <span className="block truncate font-semibold text-slate-900">{p.name}</span>
            <span className="block max-w-72 truncate font-mono text-[11px] text-slate-400">{p.sourceUri}</span>
          </span>
        </Link>
      </td>
      <td className="px-3 py-3">
        <StatusBadge status={p.analyzing ? 'ANALYZING' : p.status} />
      </td>
      <td className="px-3 py-3">
        <div className="flex items-center gap-2.5">
          <InlineBar value={p.loc} max={maxLoc} />
          <span className="text-slate-700 tabular-nums" title={fmtInt(p.loc)}>{fmtCompact(p.loc)}</span>
        </div>
      </td>
      <td className="px-3 py-3 text-right text-slate-700 tabular-nums">{fmtInt(p.types)}</td>
      <td className="px-3 py-3 text-right text-slate-700 tabular-nums">{fmtInt(p.endpoints)}</td>
      <td className="px-3 py-3">
        <div className="w-28">
          <RiskDistributionBar high={p.high} medium={p.medium} low={p.low} compact />
        </div>
      </td>
      <td className="px-3 py-3 text-right">{p.maxRisk > 0 ? <RiskBadge score={p.maxRisk} level={riskLevel(p.maxRisk)} /> : <span className="text-slate-400">-</span>}</td>
      <td className="px-3 py-3 text-xs whitespace-nowrap text-slate-500">
        {p.lastRunAt ? (
          <span className="flex items-center gap-1.5">
            {p.lastRunStatus === 'FAILED' && <CircleX className="size-3.5" style={{ color: RUN_FAILED }} aria-label="failed" />}
            {fmtAgo(p.lastRunAt)} · {fmtDuration(p.lastRunMs)}
          </span>
        ) : (
          'never'
        )}
      </td>
      <td className="px-5 py-3 text-right">
        <Button
          variant="ghost"
          title="Delete project"
          loading={remove.isPending}
          disabled={p.analyzing}
          onClick={() => confirm(`Delete ${p.name} and its analysis?`) && remove.mutate(p.id)}
        >
          <Trash2 className="size-4" />
        </Button>
      </td>
    </tr>
  )
}

function Portfolio({ data }: { data: Dashboard }) {
  const t = data.totals
  const scored = t.high + t.medium + t.low
  const maxLoc = Math.max(0, ...data.projects.map((p) => p.loc))
  const peak = Math.max(0, ...data.projects.map((p) => p.maxRisk))

  return (
    <>
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <KpiTile
          label="Repositories"
          icon={<FolderGit2 className="size-4 text-slate-400" />}
          value={t.projects}
          hint={<StatusCounts ready={t.ready} analyzing={t.analyzing} failed={t.failed} />}
        />
        <KpiTile
          label="Lines of code"
          icon={<CodeXml className="size-4 text-slate-400" />}
          value={<span title={fmtInt(t.loc)}>{fmtCompact(t.loc)}</span>}
          hint={`${fmtInt(t.files)} files · ${fmtInt(t.types)} types · ${fmtInt(t.methods)} methods`}
        />
        <KpiTile
          label="Low-risk share"
          icon={<ShieldCheck className="size-4" style={{ color: riskStyle.LOW.hex }} />}
          value={fmtPct(t.low, scored)}
          hint={`${fmtInt(t.high)} high · ${fmtInt(t.medium)} medium-risk components`}
        >
          <RiskDistributionBar low={t.low} medium={t.medium} high={t.high} compact />
        </KpiTile>
        <KpiTile
          label="Analysis runs · 14 days"
          icon={<Activity className="size-4 text-slate-400" />}
          value={t.runs}
          hint={t.runs ? `${fmtPct(t.runs - t.failedRuns, t.runs)} succeeded · avg ${fmtDuration(t.avgRunMs)}` : 'No runs in the last two weeks'}
        >
          <MiniColumns
            values={data.activity.map((d) => d.success + d.failed)}
            labels={data.activity.map((d) => dayLabel(d.day))}
            format={(v) => `${v} run${v === 1 ? '' : 's'}`}
          />
        </KpiTile>
      </div>

      <Card className="grid grid-cols-2 gap-6 px-6 py-5 sm:grid-cols-3 lg:grid-cols-6">
        <Stat label="API endpoints" value={fmtInt(t.endpoints)} />
        <Stat label="Dependencies" value={<span title={fmtInt(t.edges)}>{fmtCompact(t.edges)}</span>} />
        <Stat label="Test classes" value={fmtInt(t.tests)} hint={t.types ? `${fmtPct(t.tests, t.types)} of types` : undefined} />
        <Stat label="High-risk components" value={fmtInt(t.high)} />
        <Stat label="Peak risk score" value={peak ? peak.toFixed(0) : '-'} hint={peak ? riskLevel(peak).toLowerCase() : undefined} />
        <Stat label="Failed runs · 14 days" value={fmtInt(t.failedRuns)} />
      </Card>

      <div className="grid gap-5 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader
            title="Analysis activity"
            subtitle="Runs per day over the last 14 days (UTC)"
            action={
              <ChartLegend
                items={[
                  { label: 'Succeeded', color: RUN_OK, icon: CircleCheck },
                  { label: 'Failed', color: RUN_FAILED, icon: CircleX },
                ]}
              />
            }
          />
          <div className="px-5 py-4">
            <ColumnChart
              ariaLabel="Analysis runs per day, succeeded and failed"
              height={300}
              labelEvery={2}
              data={data.activity.map((d) => ({
                key: d.day,
                label: dayLabel(d.day),
                segments: [
                  { name: 'Succeeded', value: d.success, color: RUN_OK },
                  { name: 'Failed', value: d.failed, color: RUN_FAILED },
                ],
                detail: (
                  <>
                    <p className="font-semibold text-slate-900">{dayLabel(d.day)}</p>
                    <p className="text-slate-600 tabular-nums">
                      {d.success} succeeded · {d.failed} failed
                    </p>
                    {d.avgMs != null && <p className="text-slate-400">avg {fmtDuration(d.avgMs)}</p>}
                  </>
                ),
              }))}
            />
          </div>
        </Card>

        <Card>
          <CardHeader title="Riskiest types" subtitle="Highest change-risk across all repositories" />
          {data.hotspots.length === 0 ? (
            <p className="px-5 py-4 text-sm text-slate-500">Scores appear after the first analysis finishes.</p>
          ) : (
            <ul className="divide-y divide-slate-50">
              {data.hotspots.slice(0, 7).map((h) => (
                <li key={h.entityId}>
                  <Link to={`/projects/${h.projectId}/entities/${h.entityId}`} className="flex items-center gap-3 px-5 py-2.5 hover:bg-slate-50">
                    <KindIcon kind={h.kind} />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate font-mono text-[13px] text-slate-800">{h.label}</span>
                      <span className="block truncate text-[11px] text-slate-400">
                        {h.projectName} · {fmtInt(h.dependents)} dependents
                      </span>
                    </span>
                    <RiskBadge score={h.riskScore} level={riskLevel(h.riskScore)} />
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>

      <Card>
        <CardHeader title="Repositories" subtitle="Size, API surface and change-risk per repository" />
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="border-b border-slate-100 text-left text-xs whitespace-nowrap text-slate-500">
              <tr>
                <th className="px-5 py-2 font-medium">Repository</th>
                <th className="px-3 py-2 font-medium">Status</th>
                <th className="px-3 py-2 font-medium">Lines of code</th>
                <th className="px-3 py-2 text-right font-medium">Types</th>
                <th className="px-3 py-2 text-right font-medium">Endpoints</th>
                <th className="px-3 py-2 font-medium">Risk mix</th>
                <th className="px-3 py-2 text-right font-medium">Peak risk</th>
                <th className="px-3 py-2 font-medium">Last analysis</th>
                <th className="px-5 py-2" />
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-50">
              {data.projects.map((p) => (
                <ProjectRow key={p.id} p={p} maxLoc={maxLoc} />
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </>
  )
}

function Welcome() {
  const steps = [
    ['Add a repository', 'Paste a Git URL or upload a zip of your Java sources.'],
    ['Let it analyze', 'Classes, methods and endpoints become a scored dependency graph.'],
    ['Explore and ask', 'Trace change impact, review hotspots and ask Lyra questions.'],
  ]
  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_440px]">
      <Card className="relative overflow-hidden p-8">
        <div aria-hidden className="absolute -top-24 -right-24 size-72 rounded-full bg-indigo-100 blur-3xl" />
        <div className="relative">
          <h2 className="text-xl font-semibold tracking-tight text-slate-900">Your portfolio is empty</h2>
          <p className="mt-2 max-w-lg text-sm text-slate-600">
            Add a Java repository and CodeLens will build its dependency graph, score the change risk of every class and method, and
            index it for Lyra. Small and medium repositories take seconds.
          </p>
          <ol className="mt-8 space-y-5">
            {steps.map(([title, body], i) => (
              <li key={title} className="flex gap-4">
                <span className="flex size-7 shrink-0 items-center justify-center rounded-full bg-indigo-600 text-xs font-semibold text-white">{i + 1}</span>
                <span>
                  <span className="block text-sm font-medium text-slate-900">{title}</span>
                  <span className="block text-sm text-slate-500">{body}</span>
                </span>
              </li>
            ))}
          </ol>
        </div>
      </Card>
      <Card>
        <CardHeader title="Analyze a repository" subtitle="Java sources are parsed into a dependency graph" />
        <div className="p-5">
          <CreateProjectForm />
        </div>
      </Card>
    </div>
  )
}

export default function DashboardPage() {
  const { user } = useAuth()
  const { data, isLoading, error } = useDashboard()
  const [creating, setCreating] = useState(false)
  const firstName = user?.name.split(/\s+/)[0]
  const hasProjects = (data?.projects.length ?? 0) > 0

  return (
    <div className="min-h-full">
      <AppHeader
        actions={
          hasProjects && (
            <Button onClick={() => setCreating(true)}>
              <Plus className="size-4" /> New project
            </Button>
          )
        }
      />
      <main className="mx-auto max-w-7xl space-y-6 px-6 py-8">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-slate-900">
            {greeting()}
            {firstName ? `, ${firstName}` : ''}
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            {hasProjects
              ? `Change risk and analysis health across ${data!.totals.projects} ${data!.totals.projects === 1 ? 'repository' : 'repositories'}`
              : 'Analyze a repository to see its dependency graph and change risk.'}
          </p>
        </div>
        {isLoading && <Spinner />}
        {error && <ErrorState error={error} />}
        {data && (hasProjects ? <Portfolio data={data} /> : <Welcome />)}
      </main>
      <NewProjectDialog open={creating} onClose={() => setCreating(false)} />
    </div>
  )
}
