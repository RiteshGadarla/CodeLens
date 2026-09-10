import { Plus, X } from 'lucide-react'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useConfig, useCreateProject, useUploadProject } from '@/api/queries'
import type { SourceType } from '@/types/api'
import { Button, ErrorState, Segmented } from './ui'

const inputClass =
  'w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm outline-none placeholder:text-slate-400 focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100'

const EXAMPLE_REPO = 'https://github.com/spring-projects/spring-petclinic.git'

export function CreateProjectForm({ onCreated }: { onCreated?: () => void }) {
  const { data: config } = useConfig()
  const [source, setSource] = useState<SourceType>('GIT')
  const [name, setName] = useState('')
  const [location, setLocation] = useState('')
  const [branch, setBranch] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const create = useCreateProject()
  const upload = useUploadProject()
  const navigate = useNavigate()
  const pending = create.isPending || upload.isPending

  const options: { value: SourceType; label: string }[] = [
    { value: 'GIT', label: 'Git URL' },
    { value: 'UPLOAD', label: 'Upload zip' },
    ...(config?.allowLocalPaths ? [{ value: 'LOCAL' as const, label: 'Local path' }] : []),
  ]

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
    onCreated?.()
    navigate(`/projects/${project.id}`)
  }

  return (
    <form onSubmit={(e) => submit(e).catch(() => undefined)} className="space-y-3">
      <Segmented value={source} onChange={setSource} options={options} />
      {source === 'UPLOAD' ? (
        <input
          type="file"
          accept=".zip"
          required
          onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          className="block w-full text-sm text-slate-600 file:mr-3 file:rounded-md file:border-0 file:bg-indigo-50 file:px-3 file:py-1.5 file:text-sm file:font-medium file:text-indigo-700"
        />
      ) : (
        <div>
          <input
            required
            value={location}
            onChange={(e) => setLocation(e.target.value)}
            placeholder={source === 'GIT' ? 'https://github.com/acme/orders-service.git' : '/home/me/code/my-service'}
            className={inputClass}
          />
          {source === 'GIT' && !location && (
            <button type="button" onClick={() => setLocation(EXAMPLE_REPO)} className="mt-1.5 text-xs font-medium text-indigo-600 hover:underline">
              Try it with spring-petclinic
            </button>
          )}
        </div>
      )}
      <div className="flex gap-3">
        <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Name (optional)" className={inputClass} />
        {source === 'GIT' && (
          <input value={branch} onChange={(e) => setBranch(e.target.value)} placeholder="Branch (default)" className={inputClass} />
        )}
      </div>
      {(create.error || upload.error) && <ErrorState error={create.error ?? upload.error} />}
      <Button type="submit" loading={pending} className="w-full py-2">
        <Plus className="size-4" /> Create and analyze
      </Button>
    </form>
  )
}

export function NewProjectDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const ref = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])

  return (
    <dialog
      ref={ref}
      onClose={onClose}
      // backdrop click
      onClick={(e) => e.target === ref.current && onClose()}
      className="m-auto w-[calc(100%-2rem)] max-w-lg rounded-2xl bg-white p-0 shadow-2xl backdrop:bg-slate-950/40 backdrop:backdrop-blur-sm"
    >
      {open && (
        <div>
          <div className="flex items-start justify-between border-b border-slate-100 px-6 py-4">
            <div>
              <h2 className="text-base font-semibold text-slate-900">Analyze a repository</h2>
              <p className="mt-0.5 text-xs text-slate-500">Java sources are parsed into a dependency graph, scored and indexed for Lyra</p>
            </div>
            <button type="button" onClick={onClose} aria-label="Close" className="rounded-md p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-600">
              <X className="size-4" />
            </button>
          </div>
          <div className="px-6 py-5">
            <CreateProjectForm onCreated={onClose} />
          </div>
        </div>
      )}
    </dialog>
  )
}
