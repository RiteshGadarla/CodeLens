import { LoaderCircle } from 'lucide-react'
import type { ProjectStatus, RunStatus } from '@/types/api'
import { Badge } from './ui'

const styles: Record<ProjectStatus | RunStatus, string> = {
  READY: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  SUCCESS: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  ANALYZING: 'bg-indigo-50 text-indigo-700 ring-indigo-200',
  RUNNING: 'bg-indigo-50 text-indigo-700 ring-indigo-200',
  PENDING: 'bg-slate-50 text-slate-600 ring-slate-200',
  FAILED: 'bg-rose-50 text-rose-700 ring-rose-200',
}

export function StatusBadge({ status }: { status: ProjectStatus | RunStatus }) {
  const busy = status === 'ANALYZING' || status === 'RUNNING'
  return (
    <Badge className={styles[status]}>
      {busy && <LoaderCircle className="size-3 animate-spin" />}
      {status.toLowerCase()}
    </Badge>
  )
}
