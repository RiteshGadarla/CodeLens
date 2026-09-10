import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type {
  AskResponse, CreateProject, EntityDetail, EntityKind, EntitySummary, GraphView, Hotspot, Impact,
  ModuleMetrics, Overview, PathResult, Project, Report, Run, RunMode, Source,
} from '@/types/api'
import { api, qs } from './client'

// every project-scoped key starts with ['p', id] so one invalidation refreshes all
const pk = (projectId: number, ...rest: unknown[]) => ['p', projectId, ...rest]

export const useProjects = () =>
  useQuery({
    queryKey: ['projects'],
    queryFn: () => api<Project[]>('/projects'),
    refetchInterval: (q) => (q.state.data?.some((p) => p.analyzing) ? 2000 : false),
  })

export const useProject = (id: number) =>
  useQuery({
    queryKey: ['project', id],
    queryFn: () => api<Project>(`/projects/${id}`),
    refetchInterval: (q) => (q.state.data?.analyzing ? 1500 : false),
  })

export const useRuns = (id: number) =>
  useQuery({ queryKey: pk(id, 'runs'), queryFn: () => api<Run[]>(`/projects/${id}/runs`) })

export function useCreateProject() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateProject) => api<Project>('/projects', { method: 'POST', body: JSON.stringify(body) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['projects'] }),
  })
}

export function useUploadProject() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ file, name }: { file: File; name?: string }) => {
      const form = new FormData()
      form.set('file', file)
      if (name) form.set('name', name)
      return api<Project>('/projects/upload', { method: 'POST', body: form })
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['projects'] }),
  })
}

export function useAnalyze(id: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (mode: RunMode) => api<Run>(`/projects/${id}/analyze${qs({ mode })}`, { method: 'POST' }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['project', id] })
      qc.invalidateQueries({ queryKey: ['projects'] })
    },
  })
}

export function useDeleteProject() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => api<void>(`/projects/${id}`, { method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['projects'] }),
  })
}

export const useOverview = (id: number, enabled = true) =>
  useQuery({ queryKey: pk(id, 'overview'), queryFn: () => api<Overview>(`/projects/${id}/overview`), enabled })

export const useSearch = (id: number, q: string, kinds?: EntityKind[]) =>
  useQuery({
    queryKey: pk(id, 'search', q, kinds),
    queryFn: () => api<EntitySummary[]>(`/projects/${id}/entities/search${qs({ q, kinds: kinds?.join(','), limit: 20 })}`),
    enabled: q.trim().length > 0,
    placeholderData: keepPreviousData,
  })

export const useEntity = (id: number, entityId: number) =>
  useQuery({ queryKey: pk(id, 'entity', entityId), queryFn: () => api<EntityDetail>(`/projects/${id}/entities/${entityId}`) })

export const useSource = (id: number, entityId: number) =>
  useQuery({ queryKey: pk(id, 'source', entityId), queryFn: () => api<Source>(`/projects/${id}/entities/${entityId}/source`) })

export interface GraphParams {
  level: 'TYPE' | 'MEMBER'
  focus?: number | null
  depth: number
  direction: 'BOTH' | 'UPSTREAM' | 'DOWNSTREAM'
  limit: number
  includeTests: boolean
}

export const useGraph = (id: number, p: GraphParams) =>
  useQuery({
    queryKey: pk(id, 'graph', p),
    queryFn: () => api<GraphView>(`/projects/${id}/graph${qs({ ...p })}`),
    placeholderData: keepPreviousData,
  })

export const usePath = (id: number, from?: number, to?: number, all = false) =>
  useQuery({
    queryKey: pk(id, 'path', from, to, all),
    queryFn: () => api<PathResult>(`/projects/${id}/graph/path${qs({ from, to, all })}`),
    enabled: from != null && to != null,
  })

export const useImpact = (id: number, entityId: number, depth: number, includeTests: boolean) =>
  useQuery({
    queryKey: pk(id, 'impact', entityId, depth, includeTests),
    queryFn: () => api<Impact>(`/projects/${id}/impact/${entityId}${qs({ depth, includeTests })}`),
    placeholderData: keepPreviousData,
  })

export const useHotspots = (id: number, scope: 'ALL' | 'TYPE' | 'METHOD', sort: string, limit = 50) =>
  useQuery({
    queryKey: pk(id, 'hotspots', scope, sort, limit),
    queryFn: () => api<{ items: Hotspot[] }>(`/projects/${id}/metrics/hotspots${qs({ scope, sort, limit })}`),
    placeholderData: keepPreviousData,
  })

export const useModules = (id: number, level: 'PACKAGE' | 'MODULE') =>
  useQuery({
    queryKey: pk(id, 'modules', level),
    queryFn: () => api<{ items: ModuleMetrics[] }>(`/projects/${id}/metrics/modules${qs({ level })}`),
  })

export const useAsk = (id: number) =>
  useMutation({
    mutationFn: (question: string) =>
      api<AskResponse>(`/projects/${id}/ask`, { method: 'POST', body: JSON.stringify({ question }) }),
  })

export function useImpactReport(id: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (entityId: number) => api<Report>(`/projects/${id}/reports/impact/${entityId}`, { method: 'POST' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: pk(id, 'reports') }),
  })
}

export const useReports = (id: number) =>
  useQuery({ queryKey: pk(id, 'reports'), queryFn: () => api<Report[]>(`/projects/${id}/reports`) })

export const invalidateProject = (qc: ReturnType<typeof useQueryClient>, id: number) =>
  qc.invalidateQueries({ queryKey: ['p', id] })
