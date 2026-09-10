import type { EdgeType, EntityKind, Stereotype } from '@/types/api'

export const edgeLabel: Record<EdgeType, string> = {
  IMPORTS: 'imports',
  EXTENDS: 'extends',
  IMPLEMENTS: 'implements',
  CALLS: 'calls',
  CREATES: 'creates',
  USES_TYPE: 'uses type',
  ANNOTATED_BY: 'annotated by',
  OVERRIDES: 'overrides',
  ROUTES_TO: 'routes to',
}

export const edgeColor: Record<EdgeType, string> = {
  IMPORTS: '#94a3b8',
  EXTENDS: '#7c3aed',
  IMPLEMENTS: '#8b5cf6',
  CALLS: '#4f46e5',
  CREATES: '#0891b2',
  USES_TYPE: '#64748b',
  ANNOTATED_BY: '#a3a3a3',
  OVERRIDES: '#c026d3',
  ROUTES_TO: '#059669',
}

export const kindLabel: Record<EntityKind, string> = {
  CLASS: 'class',
  INTERFACE: 'interface',
  ENUM: 'enum',
  RECORD: 'record',
  ANNOTATION: 'annotation',
  METHOD: 'method',
  CONSTRUCTOR: 'constructor',
  FIELD: 'field',
  ENDPOINT: 'endpoint',
}

export const roleStyle: Record<Stereotype | 'OTHER', string> = {
  CONTROLLER: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  SERVICE: 'bg-indigo-50 text-indigo-700 ring-indigo-200',
  REPOSITORY: 'bg-amber-50 text-amber-700 ring-amber-200',
  COMPONENT: 'bg-sky-50 text-sky-700 ring-sky-200',
  CONFIGURATION: 'bg-slate-100 text-slate-700 ring-slate-200',
  ENTITY: 'bg-cyan-50 text-cyan-700 ring-cyan-200',
  TEST: 'bg-fuchsia-50 text-fuchsia-700 ring-fuchsia-200',
  OTHER: 'bg-slate-50 text-slate-600 ring-slate-200',
}

export const isType = (k: EntityKind) =>
  k === 'CLASS' || k === 'INTERFACE' || k === 'ENUM' || k === 'RECORD' || k === 'ANNOTATION'
