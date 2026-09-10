import {
  Background, Controls, Handle, MarkerType, MiniMap, Position, ReactFlow,
  type Edge, type Node, type NodeProps,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import { useMemo } from 'react'
import { cn } from '@/lib/cn'
import { edgeColor, edgeLabel } from '@/lib/labels'
import { layoutGraph, NODE_WIDTH } from '@/lib/layout'
import { riskLevel, riskStyle } from '@/lib/risk'
import type { EdgeType, GraphNodeView, GraphView } from '@/types/api'
import { KindIcon } from './EntityBadges'

type EntityNodeData = { node: GraphNodeView }
type EntityFlowNode = Node<EntityNodeData, 'entity'>

// most meaningful relation wins the edge colour
const PRIORITY: EdgeType[] = [
  'CALLS', 'OVERRIDES', 'EXTENDS', 'IMPLEMENTS', 'CREATES', 'ROUTES_TO', 'USES_TYPE', 'IMPORTS', 'ANNOTATED_BY',
]

const handleStyle = { width: 6, height: 6, background: '#cbd5e1', border: 0 }

function EntityNode({ data, selected }: NodeProps<EntityFlowNode>) {
  const n = data.node
  const hex = riskStyle[riskLevel(n.riskScore)].hex
  return (
    <div
      className={cn(
        'rounded-lg border bg-white px-3 py-2 shadow-sm transition',
        selected ? 'border-indigo-400 shadow-md' : 'border-slate-200',
        n.focus && 'ring-2 ring-indigo-500 ring-offset-1',
      )}
      style={{ width: NODE_WIDTH, borderLeft: `4px solid ${hex}` }}
    >
      <Handle type="target" position={Position.Left} style={handleStyle} />
      <div className="flex items-center gap-1.5">
        <KindIcon kind={n.kind} className="size-3.5" />
        <span className="truncate text-xs font-semibold text-slate-800" title={n.qualifiedName}>
          {n.label}
        </span>
      </div>
      <div className="mt-1 flex items-center gap-2 text-[10px] text-slate-500">
        <span className="truncate">{n.role?.toLowerCase() ?? n.packageName ?? n.kind.toLowerCase()}</span>
        <span className="ml-auto shrink-0 tabular-nums">
          in {n.fanIn} · out {n.fanOut} · <span style={{ color: hex }}>{n.riskScore.toFixed(0)}</span>
        </span>
      </div>
      <Handle type="source" position={Position.Right} style={handleStyle} />
    </div>
  )
}

const nodeTypes = { entity: EntityNode }

export function GraphCanvas({
  view,
  onSelect,
  onOpen,
}: {
  view: GraphView
  onSelect: (node: GraphNodeView | null) => void
  onOpen: (node: GraphNodeView) => void
}) {
  const { nodes, edges } = useMemo(() => {
    const merged = new Map<string, { source: string; target: string; types: EdgeType[]; weight: number }>()
    for (const e of view.edges) {
      const key = `${e.source}-${e.target}`
      const m = merged.get(key) ?? { source: String(e.source), target: String(e.target), types: [], weight: 0 }
      m.types.push(e.type)
      m.weight += e.weight
      merged.set(key, m)
    }
    const flowEdges: Edge[] = [...merged.entries()].map(([id, m]) => {
      const main = PRIORITY.find((t) => m.types.includes(t)) ?? m.types[0]
      const color = edgeColor[main]
      return {
        id,
        source: m.source,
        target: m.target,
        ariaLabel: m.types.map((t) => edgeLabel[t]).join(', '),
        style: { stroke: color, strokeWidth: 1 + Math.min(2.5, Math.log2(m.weight + 1) / 2) },
        markerEnd: { type: MarkerType.ArrowClosed, color, width: 14, height: 14 },
      }
    })
    const flowNodes: EntityFlowNode[] = view.nodes.map((n) => ({
      id: String(n.id),
      type: 'entity',
      position: { x: 0, y: 0 },
      data: { node: n },
    }))
    return { nodes: layoutGraph(flowNodes, flowEdges), edges: flowEdges }
  }, [view])

  return (
    <ReactFlow
      defaultNodes={nodes}
      defaultEdges={edges}
      nodeTypes={nodeTypes}
      fitView
      fitViewOptions={{ padding: 0.15 }}
      minZoom={0.1}
      maxZoom={2}
      onNodeClick={(_, n) => onSelect((n as EntityFlowNode).data.node)}
      onNodeDoubleClick={(_, n) => onOpen((n as EntityFlowNode).data.node)}
      onPaneClick={() => onSelect(null)}
    >
      <Background gap={20} color="#e2e8f0" />
      <Controls showInteractive={false} />
      <MiniMap pannable zoomable nodeColor={(n) => riskStyle[riskLevel((n.data as EntityNodeData).node.riskScore)].hex} />
    </ReactFlow>
  )
}
