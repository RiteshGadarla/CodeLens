import { ArrowDown, Crosshair } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useHotspots, useModules } from '@/api/queries'
import { MainSequenceChart } from '@/components/charts'
import { KindIcon, RiskBadge, RoleBadge } from '@/components/EntityBadges'
import { Card, CardHeader, EmptyState, ErrorState, Provenance, Segmented, Spinner } from '@/components/ui'
import { cn } from '@/lib/cn'
import { fmtInt, fmtRatio } from '@/lib/format'
import { useProjectId } from '@/lib/hooks'

const COLUMNS = [
  { key: 'dependents', label: 'Dependents' },
  { key: 'dependencies', label: 'Dependencies' },
  { key: 'fanIn', label: 'Fan-in' },
  { key: 'fanOut', label: 'Fan-out' },
  { key: 'depth', label: 'Depth' },
  { key: 'complexity', label: 'Complexity' },
  { key: 'riskScore', label: 'Risk' },
] as const

export default function MetricsPage() {
  const projectId = useProjectId()
  const [scope, setScope] = useState<'ALL' | 'TYPE' | 'METHOD'>('ALL')
  const [sort, setSort] = useState<string>('riskScore')
  const [level, setLevel] = useState<'PACKAGE' | 'MODULE'>('PACKAGE')
  const hotspots = useHotspots(projectId, scope, sort, 50)
  const modules = useModules(projectId, level)

  return (
    <div className="mx-auto max-w-7xl space-y-5 p-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-lg font-semibold text-slate-900">Codebase metrics</h1>
          <p className="text-sm text-slate-500">Coupling and complexity hotspots from the dependency graph</p>
        </div>
        <Provenance kind="static" />
      </div>

      <Card>
        <CardHeader
          title="Hotspots"
          subtitle="Click a column to rank by it"
          action={
            <Segmented
              value={scope}
              onChange={setScope}
              options={[
                { value: 'ALL', label: 'All' },
                { value: 'TYPE', label: 'Types' },
                { value: 'METHOD', label: 'Methods' },
              ]}
            />
          }
        />
        {hotspots.isLoading && <Spinner />}
        {hotspots.error && <div className="p-5"><ErrorState error={hotspots.error} /></div>}
        {hotspots.data && hotspots.data.items.length === 0 && <EmptyState title="No metrics yet" />}
        {hotspots.data && hotspots.data.items.length > 0 && (
          <div className={cn('overflow-x-auto transition-opacity', hotspots.isFetching && 'opacity-60')}>
            <table className="w-full text-sm">
              <thead className="border-b border-slate-100 text-xs text-slate-500">
                <tr>
                  <th className="w-10 px-5 py-2 text-right font-medium">#</th>
                  <th className="px-3 py-2 text-left font-medium">Entity</th>
                  <th className="px-3 py-2 text-left font-medium">Role</th>
                  {COLUMNS.map((c) => (
                    <th key={c.key} className="px-3 py-2 text-right font-medium">
                      <button
                        onClick={() => setSort(c.key)}
                        className={cn('inline-flex items-center gap-0.5 hover:text-slate-900', sort === c.key && 'text-slate-900')}
                      >
                        {c.label}
                        {sort === c.key && <ArrowDown className="size-3" />}
                      </button>
                    </th>
                  ))}
                  <th className="px-5 py-2" />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-50">
                {hotspots.data.items.map(({ entity, metrics }, i) => (
                  <tr key={entity.id} className="hover:bg-slate-50">
                    <td className="px-5 py-2 text-right text-slate-400 tabular-nums">{i + 1}</td>
                    <td className="max-w-md px-3 py-2">
                      <Link to={`/projects/${projectId}/entities/${entity.id}`} className="flex items-center gap-2" title={entity.qualifiedName}>
                        <KindIcon kind={entity.kind} />
                        <span className="truncate font-mono text-[13px] text-slate-800">{entity.label}</span>
                      </Link>
                    </td>
                    <td className="px-3 py-2"><RoleBadge role={entity.role} /></td>
                    <td className="px-3 py-2 text-right tabular-nums">{fmtInt(metrics.dependents)}</td>
                    <td className="px-3 py-2 text-right tabular-nums">{fmtInt(metrics.dependencies)}</td>
                    <td className="px-3 py-2 text-right tabular-nums">{metrics.fanIn}</td>
                    <td className="px-3 py-2 text-right tabular-nums">{metrics.fanOut}</td>
                    <td className="px-3 py-2 text-right tabular-nums">{metrics.depth}</td>
                    <td className="px-3 py-2 text-right tabular-nums">{metrics.complexity}</td>
                    <td className="px-3 py-2 text-right"><RiskBadge score={metrics.riskScore} level={metrics.riskLevel} /></td>
                    <td className="px-5 py-2 text-right">
                      <Link to={`/projects/${projectId}/impact/${entity.id}`} className="inline-flex text-indigo-600 hover:text-indigo-800" title="Impact analysis">
                        <Crosshair className="size-4" />
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Card>
        <CardHeader
          title="Module coupling"
          subtitle="Distance from the main sequence (D) flags modules that are rigid and concrete, or abstract and unused"
          action={
            <Segmented
              value={level}
              onChange={setLevel}
              options={[
                { value: 'PACKAGE', label: 'Packages' },
                { value: 'MODULE', label: 'Build modules' },
              ]}
            />
          }
        />
        {modules.isLoading && <Spinner />}
        {modules.error && <div className="p-5"><ErrorState error={modules.error} /></div>}
        {modules.data && (
          <div className="grid gap-4 p-5 lg:grid-cols-[480px_1fr]">
            <MainSequenceChart modules={modules.data.items} />
            <div className="max-h-[340px] overflow-auto">
              <table className="w-full text-sm">
                <thead className="sticky top-0 border-b border-slate-100 bg-white text-xs text-slate-500">
                  <tr>
                    <th className="px-3 py-2 text-left font-medium">{level === 'PACKAGE' ? 'Package' : 'Module'}</th>
                    {['Types', 'Ca', 'Ce', 'I', 'A', 'D'].map((h) => (
                      <th key={h} className="px-3 py-2 text-right font-medium">{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-50">
                  {[...modules.data.items]
                    .sort((a, b) => b.distance - a.distance)
                    .map((m) => (
                      <tr key={m.name}>
                        <td className="max-w-xs truncate px-3 py-1.5 font-mono text-[13px]" title={m.name}>{m.name}</td>
                        <td className="px-3 py-1.5 text-right tabular-nums">{m.entities}</td>
                        <td className="px-3 py-1.5 text-right tabular-nums">{m.afferent}</td>
                        <td className="px-3 py-1.5 text-right tabular-nums">{m.efferent}</td>
                        <td className="px-3 py-1.5 text-right tabular-nums">{fmtRatio(m.instability)}</td>
                        <td className="px-3 py-1.5 text-right tabular-nums">{fmtRatio(m.abstractness)}</td>
                        <td className="px-3 py-1.5 text-right font-semibold tabular-nums">{fmtRatio(m.distance)}</td>
                      </tr>
                    ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </Card>
    </div>
  )
}
