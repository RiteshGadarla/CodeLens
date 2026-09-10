import dagre from '@dagrejs/dagre'
import type { Edge, Node } from '@xyflow/react'

export const NODE_WIDTH = 220
export const NODE_HEIGHT = 58

// layered layout; dependencies flow left to right
export function layoutGraph<N extends Node>(nodes: N[], edges: Edge[], direction: 'LR' | 'TB' = 'LR'): N[] {
  const g = new dagre.graphlib.Graph()
  g.setGraph({ rankdir: direction, nodesep: 28, ranksep: 90, marginx: 20, marginy: 20 })
  g.setDefaultEdgeLabel(() => ({}))
  nodes.forEach((n) => g.setNode(n.id, { width: NODE_WIDTH, height: NODE_HEIGHT }))
  edges.forEach((e) => g.setEdge(e.source, e.target))
  dagre.layout(g)
  return nodes.map((n) => {
    const p = g.node(n.id)
    return { ...n, position: { x: p.x - NODE_WIDTH / 2, y: p.y - NODE_HEIGHT / 2 } }
  })
}
