import {
  AtSign, Box, Braces, Database, Globe, Hammer, List, SquareFunction, Variable,
} from 'lucide-react'
import type { EntityKind, RiskLevel, Stereotype } from '@/types/api'
import { cn } from '@/lib/cn'
import { kindLabel, roleStyle } from '@/lib/labels'
import { riskLevel, riskStyle } from '@/lib/risk'
import { Badge } from './ui'

const icons: Record<EntityKind, typeof Box> = {
  CLASS: Box,
  INTERFACE: Braces,
  ENUM: List,
  RECORD: Database,
  ANNOTATION: AtSign,
  METHOD: SquareFunction,
  CONSTRUCTOR: Hammer,
  FIELD: Variable,
  ENDPOINT: Globe,
}

const iconColor: Record<EntityKind, string> = {
  CLASS: 'text-indigo-500',
  INTERFACE: 'text-violet-500',
  ENUM: 'text-amber-500',
  RECORD: 'text-cyan-600',
  ANNOTATION: 'text-slate-400',
  METHOD: 'text-sky-600',
  CONSTRUCTOR: 'text-sky-700',
  FIELD: 'text-slate-500',
  ENDPOINT: 'text-emerald-600',
}

export function KindIcon({ kind, className }: { kind: EntityKind; className?: string }) {
  const Icon = icons[kind]
  return (
    <span title={kindLabel[kind]}>
      <Icon className={cn('size-4 shrink-0', iconColor[kind], className)} />
    </span>
  )
}

export function RoleBadge({ role }: { role: Stereotype | null | undefined }) {
  if (!role) return null
  return <Badge className={roleStyle[role]}>{role.toLowerCase()}</Badge>
}

export function RiskBadge({ score, level }: { score: number | null | undefined; level?: RiskLevel }) {
  if (score == null) return null
  const l = level ?? riskLevel(score)
  return (
    <Badge className={riskStyle[l].badge}>
      <span className="tabular-nums">{score.toFixed(0)}</span>
      <span className="opacity-70">{l.toLowerCase()}</span>
    </Badge>
  )
}
