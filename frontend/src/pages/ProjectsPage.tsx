import { Folder, FolderGit2, Plus, Trash2, Upload } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useCreateProject, useDeleteProject, useProjects, useUploadProject } from '@/api/queries'
import { Logo } from '@/components/Logo'
import { StatusBadge } from '@/components/StatusBadge'
import { Button, Card, CardHeader, EmptyState, ErrorState, Segmented, Spinner } from '@/components/ui'
import { fmtAgo, fmtDuration, fmtInt } from '@/lib/format'
import type { Project, SourceType } from '@/types/api'

const sourceIcon: Record<SourceType, typeof Folder> = { LOCAL: Folder, GIT: FolderGit2, UPLOAD: Upload }

const inputClass =
  'w-full rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100'

function CreateProject() {
  const [source, setSource] = useState<SourceType>('GIT')
  const [name, setName] = useState('')
  const [location, setLocation] = useState('')
  const [branch, setBranch] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const create = useCreateProject()
  const upload = useUploadProject()
  const navigate = useNavigate()
  const pending = create.isPending || upload.isPending

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    const project =
      source === 'UPLOAD'
        ? await upload.mutateAsync({ file: file!, name: name || undefined })
        : await create.mutateAsync({
            sourceType: source,
            name: name || undefined,
            path: source === 'LOCAL' ? location : undefined,
            url: source === 'GIT' ? location : undefined,
            branch: branch || undefined,
          })
    navigate(`/projects/${project.id}`)
  }

  return (
    <Card>
      <CardHeader title="Analyze a repository" subtitle="Java sources are parsed into a dependency graph" />
      <form onSubmit={(e) => submit(e).catch(() => undefined)} className="space-y-3 p-5">
        <Segmented
          value={source}
          onChange={setSource}
          options={[
            { value: 'GIT', label: 'Git URL' },
            { value: 'LOCAL', label: 'Local path' },
            { value: 'UPLOAD', label: 'Upload zip' },
          ]}
        />
        {source === 'UPLOAD' ? (
          <input
            type="file"
            accept=".zip"
            required
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            className="block w-full text-sm text-slate-600 file:mr-3 file:rounded-md file:border-0 file:bg-indigo-50 file:px-3 file:py-1.5 file:text-sm file:font-medium file:text-indigo-700"
          />
        ) : (
          <input
            required
            value={location}
            onChange={(e) => setLocation(e.target.value)}
            placeholder={source === 'GIT' ? 'https://github.com/spring-projects/spring-petclinic.git' : '/home/me/code/my-service'}
            className={inputClass}
          />
        )}
        <div className="flex gap-3">
          <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Name (optional)" className={inputClass} />
          {source === 'GIT' && (
            <input value={branch} onChange={(e) => setBranch(e.target.value)} placeholder="Branch (default)" className={inputClass} />
          )}
        </div>
        {(create.error || upload.error) && <ErrorState error={create.error ?? upload.error} />}
        <Button type="submit" loading={pending} className="w-full">
          <Plus className="size-4" /> Create and analyze
        </Button>
      </form>
    </Card>
  )
}

function ProjectRow({ project }: { project: Project }) {
  const remove = useDeleteProject()
  const Icon = sourceIcon[project.sourceType]
  const run = project.latestRun
  return (
    <li className="flex items-center gap-4 px-5 py-3.5 hover:bg-slate-50">
      <Icon className="size-5 shrink-0 text-slate-400" />
      <Link to={`/projects/${project.id}`} className="min-w-0 flex-1">
        <p className="truncate text-sm font-semibold text-slate-900">{project.name}</p>
        <p className="truncate font-mono text-xs text-slate-400">{project.sourceUri}</p>
      </Link>
      {run && (
        <div className="hidden text-right text-xs text-slate-500 md:block">
          <p className="tabular-nums">
            {fmtInt(run.entities)} entities · {fmtInt(run.edges)} edges
          </p>
          <p>
            {fmtDuration(run.durationMs)} · {fmtAgo(run.finishedAt ?? run.startedAt)}
          </p>
        </div>
      )}
      <StatusBadge status={project.analyzing ? 'ANALYZING' : project.status} />
      <Button
        variant="ghost"
        title="Delete project"
        loading={remove.isPending}
        disabled={project.analyzing}
        onClick={() => confirm(`Delete ${project.name} and its analysis?`) && remove.mutate(project.id)}
      >
        <Trash2 className="size-4" />
      </Button>
    </li>
  )
}

export default function ProjectsPage() {
  const { data, isLoading, error } = useProjects()
  return (
    <div className="min-h-full">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-6xl items-center gap-3 px-6 py-5">
          <Logo size={34} />
          <div>
            <h1 className="text-lg font-semibold tracking-tight text-slate-900">CodeLens</h1>
            <p className="text-sm text-slate-500">Code intelligence and change-impact analysis for Java repositories</p>
          </div>
        </div>
      </header>
      <div className="mx-auto grid max-w-6xl gap-6 px-6 py-8 lg:grid-cols-[1fr_380px]">
        <Card>
          <CardHeader title="Projects" subtitle={data ? `${data.length} repositories` : undefined} />
          {isLoading && <Spinner />}
          {error && (
            <div className="p-5">
              <ErrorState error={error} />
            </div>
          )}
          {data?.length === 0 && (
            <EmptyState icon={<FolderGit2 className="size-10" />} title="No repositories yet">
              Point CodeLens at a git URL, a local checkout or a zip to build its dependency graph.
            </EmptyState>
          )}
          <ul className="divide-y divide-slate-100">
            {data?.map((p) => <ProjectRow key={p.id} project={p} />)}
          </ul>
        </Card>
        <CreateProject />
      </div>
    </div>
  )
}
