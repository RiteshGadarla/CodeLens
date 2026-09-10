// mirrors backend DTOs

export type EntityKind =
  | 'CLASS' | 'INTERFACE' | 'ENUM' | 'RECORD' | 'ANNOTATION'
  | 'METHOD' | 'CONSTRUCTOR' | 'FIELD' | 'ENDPOINT'
export type Stereotype = 'CONTROLLER' | 'SERVICE' | 'REPOSITORY' | 'COMPONENT' | 'CONFIGURATION' | 'ENTITY' | 'TEST'
export type EdgeType =
  | 'IMPORTS' | 'EXTENDS' | 'IMPLEMENTS' | 'CALLS' | 'CREATES'
  | 'USES_TYPE' | 'ANNOTATED_BY' | 'OVERRIDES' | 'ROUTES_TO'
export type SourceType = 'LOCAL' | 'GIT' | 'UPLOAD'
export type ProjectStatus = 'PENDING' | 'ANALYZING' | 'READY' | 'FAILED'
export type RunMode = 'FULL' | 'INCREMENTAL'
export type RunStatus = 'RUNNING' | 'SUCCESS' | 'FAILED'
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH'
export type Direction = 'UPSTREAM' | 'DOWNSTREAM'

export interface Run {
  id: number
  mode: RunMode
  status: RunStatus
  commitHash: string | null
  filesTotal: number
  filesParsed: number
  filesDeleted: number
  entities: number
  edges: number
  durationMs: number | null
  error: string | null
  startedAt: string
  finishedAt: string | null
}

export interface Project {
  id: number
  name: string
  sourceType: SourceType
  sourceUri: string | null
  branch: string | null
  lastCommit: string | null
  status: ProjectStatus
  statusMessage: string | null
  analyzing: boolean
  createdAt: string
  updatedAt: string
  latestRun: Run | null
}

export interface CreateProject {
  name?: string
  sourceType: 'LOCAL' | 'GIT'
  path?: string
  url?: string
  branch?: string
  analyze?: boolean
}

export interface EntityRef {
  id: number
  qualifiedName: string
  label: string
  kind: EntityKind
  role: Stereotype | null
  filePath: string | null
  module: string | null
}

export interface PathStep {
  id: number
  label: string
  kind: EntityKind
  edge: EdgeType | null
  dispatch: boolean
}

export interface EntitySummary {
  id: number
  name: string
  label: string
  qualifiedName: string
  kind: EntityKind
  role: Stereotype | null
  filePath: string | null
  module: string | null
  startLine: number | null
  endLine: number | null
  riskScore: number | null
}

export interface EntityMetrics {
  fanIn: number
  fanOut: number
  dependents: number
  dependencies: number
  depth: number
  complexity: number
  riskScore: number
  riskLevel: RiskLevel
  exposed: boolean
}

export interface Relation {
  entity: EntityRef
  type: EdgeType
  weight: number
}

export interface EntityDetail {
  entity: EntitySummary
  signature: string | null
  visibility: string | null
  modifiers: string | null
  annotations: string | null
  httpMethod: string | null
  httpPath: string | null
  complexity: number
  metrics: EntityMetrics | null
  parent: EntitySummary | null
  members: EntitySummary[]
  dependencies: Relation[]
  dependents: Relation[]
}

export interface Source {
  path: string
  startLine: number
  endLine: number
  code: string
  language: string
}

export interface GraphNodeView {
  id: number
  label: string
  qualifiedName: string
  kind: EntityKind
  role: Stereotype | null
  module: string | null
  packageName: string | null
  complexity: number
  riskScore: number
  fanIn: number
  fanOut: number
  distance: number
  focus: boolean
}

export interface GraphEdgeView {
  source: number
  target: number
  type: EdgeType
  weight: number
}

export interface GraphView {
  level: 'TYPE' | 'MEMBER'
  focusId: number | null
  nodes: GraphNodeView[]
  edges: GraphEdgeView[]
  truncated: boolean
}

export interface PathResult {
  found: boolean
  direction: Direction | null
  paths: PathStep[][]
}

export interface RiskBreakdown {
  score: number
  level: RiskLevel
  factors: Record<string, number>
}

export interface Affected {
  entity: EntityRef
  depth: number
  via: EdgeType
  path: PathStep[]
}

export interface Impact {
  target: EntityRef
  seeds: number
  directDependents: number
  transitiveDependents: number
  maxDepth: number
  affected: Affected[]
  endpoints: Affected[]
  affectedTypes: Record<string, EntityRef[]>
  modules: string[]
  packages: string[]
  tests: EntityRef[]
  risk: RiskBreakdown
  truncated: boolean
}

export interface ModuleMetrics {
  level: 'PACKAGE' | 'MODULE'
  name: string
  entities: number
  afferent: number
  efferent: number
  instability: number
  abstractness: number
  distance: number
}

export interface Hotspot {
  entity: EntitySummary
  metrics: EntityMetrics
}

export interface Overview {
  stats: {
    files: number
    loc: number
    parseErrors: number
    types: number
    methods: number
    endpoints: number
    tests: number
    edges: number
    modules: number
    packages: number
  }
  kinds: Record<string, number>
  roles: Record<string, number>
  risk: { low: number; medium: number; high: number }
  topRisks: Hotspot[]
  modules: ModuleMetrics[]
  cycles: EntityRef[][]
}

export interface AiSource {
  ref: number
  path: string
  label: string
  qualifiedName: string
  startLine: number
  endLine: number
  score: number
  entityId: number | null
}

export interface AskResponse {
  answer: string
  sources: AiSource[]
  facts: Record<string, unknown>
  model: string
  cached: boolean
  generatedBy: string
}

export interface Report {
  id: number
  entityId: number | null
  kind: string
  impact: Impact
  summary: string
  sources: AiSource[]
  model: string | null
  createdAt: string
}
