import { useQueryClient } from '@tanstack/react-query'
import { ChartColumn, ChevronLeft, LayoutDashboard, LoaderCircle, Network, RefreshCw, Sparkles } from 'lucide-react'
import { Suspense, useEffect, useRef } from 'react'
import { Link, NavLink, Outlet, useNavigate, useOutletContext } from 'react-router-dom'
import { invalidateProject, useAnalyze, useProject } from '@/api/queries'
import { cn } from '@/lib/cn'
import { fmtAgo, shortSha } from '@/lib/format'
import { useProjectId } from '@/lib/hooks'
import type { Project } from '@/types/api'
import { UserMenu } from './AppHeader'
import { EntityPicker } from './EntityPicker'
import { Logo } from './Logo'
import { StatusBadge } from './StatusBadge'
import { Button, ErrorState, Spinner } from './ui'

export const useProjectContext = () => useOutletContext<{ project: Project }>()

const nav = [
  { to: '', label: 'Overview', icon: LayoutDashboard, end: true },
  { to: 'graph', label: 'Dependency graph', icon: Network },
  { to: 'metrics', label: 'Metrics', icon: ChartColumn },
  { to: 'lyra', label: 'Lyra', icon: Sparkles },
]

export function ProjectLayout() {
  const projectId = useProjectId()
  const { data: project, error } = useProject(projectId)
  const analyze = useAnalyze(projectId)
  const qc = useQueryClient()
  const navigate = useNavigate()
  const search = useRef<HTMLInputElement>(null)
  const wasAnalyzing = useRef(false)

  // refresh project data once a run finishes
  useEffect(() => {
    if (!project) return
    if (wasAnalyzing.current && !project.analyzing) {
      invalidateProject(qc, projectId)
      qc.invalidateQueries({ queryKey: ['projects'] })
    }
    wasAnalyzing.current = project.analyzing
  }, [project, projectId, qc])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        search.current?.focus()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  return (
    <div className="flex h-full">
      <aside className="flex w-60 shrink-0 flex-col border-r border-slate-200 bg-white">
        <Link to="/dashboard" className="flex items-center gap-2 px-4 py-4">
          <Logo size={26} />
          <span className="text-[15px] font-semibold tracking-tight text-slate-900">CodeLens</span>
        </Link>
        <div className="mx-3 mb-3 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2.5">
          <Link to="/dashboard" className="mb-1 flex items-center gap-0.5 text-[11px] text-slate-400 hover:text-slate-600">
            <ChevronLeft className="size-3" /> Dashboard
          </Link>
          <p className="truncate text-sm font-semibold text-slate-900" title={project?.name}>
            {project?.name ?? '…'}
          </p>
          {project && (
            <div className="mt-1.5 flex items-center gap-2 text-[11px] text-slate-500">
              <StatusBadge status={project.analyzing ? 'ANALYZING' : project.status} />
              <span className="font-mono">{shortSha(project.lastCommit)}</span>
            </div>
          )}
        </div>
        <nav className="flex flex-col gap-0.5 px-2">
          {nav.map((item) => (
            <NavLink
              key={item.label}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm font-medium transition',
                  isActive ? 'bg-indigo-50 text-indigo-700' : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900',
                )
              }
            >
              <item.icon className="size-4" />
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="mt-auto border-t border-slate-100">
          {project?.latestRun && (
            <p className="px-4 pt-3 text-[11px] text-slate-500">
              Last run {fmtAgo(project.latestRun.finishedAt ?? project.latestRun.startedAt)} ·{' '}
              {project.latestRun.mode.toLowerCase()}
            </p>
          )}
          <div className="p-2">
            <UserMenu placement="above" showName />
          </div>
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center gap-3 border-b border-slate-200 bg-white px-6 py-2.5">
          <EntityPicker
            ref={search}
            projectId={projectId}
            className="w-full max-w-xl"
            shortcut="Ctrl K"
            onSelect={(e) => navigate(`/projects/${projectId}/entities/${e.id}`)}
          />
          <div className="ml-auto flex items-center gap-2">
            <Button
              variant="secondary"
              loading={analyze.isPending}
              disabled={project?.analyzing}
              onClick={() => analyze.mutate('INCREMENTAL')}
              title="Re-analyze changed files only"
            >
              <RefreshCw className="size-4" /> Re-analyze
            </Button>
            <Button
              variant="ghost"
              disabled={project?.analyzing || analyze.isPending}
              onClick={() => analyze.mutate('FULL')}
              title="Rebuild the whole graph"
            >
              Full
            </Button>
          </div>
        </header>

        {project?.analyzing && (
          <div className="flex items-center gap-2 border-b border-indigo-100 bg-indigo-50 px-6 py-2 text-sm text-indigo-700">
            <LoaderCircle className="size-4 animate-spin" />
            Analyzing {project.latestRun?.mode === 'INCREMENTAL' ? 'changed files' : 'repository'}… results refresh
            automatically.
          </div>
        )}
        {project?.status === 'FAILED' && !project.analyzing && (
          <div className="border-b border-rose-100 bg-rose-50 px-6 py-2 text-sm text-rose-700">
            Last analysis failed: {project.statusMessage}
          </div>
        )}
        {analyze.error && (
          <div className="px-6 pt-4">
            <ErrorState error={analyze.error} />
          </div>
        )}

        <main className="min-h-0 flex-1 overflow-auto">
          {error ? (
            <div className="p-6">
              <ErrorState error={error} />
            </div>
          ) : project ? (
            <Suspense fallback={<Spinner />}>
              <Outlet context={{ project }} />
            </Suspense>
          ) : (
            <Spinner />
          )}
        </main>
      </div>
    </div>
  )
}
