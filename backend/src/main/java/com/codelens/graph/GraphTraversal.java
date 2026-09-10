package com.codelens.graph;

import com.codelens.domain.EdgeType;
import com.codelens.graph.DependencyGraph.Adj;

import java.util.*;

public final class GraphTraversal {

    public enum Direction { UPSTREAM, DOWNSTREAM }

    // via = -1 for seeds; dispatch = reached through an overridden declaration
    public record Hop(int node, int depth, int via, EdgeType edge, boolean dispatch) {
    }

    private GraphTraversal() {
    }

    // upstream = dependents, downstream = dependencies
    public static Map<Integer, Hop> bfs(DependencyGraph g, Collection<Integer> seeds, Direction dir,
                                        int maxDepth, boolean dispatch) {
        return bfs(g, seeds, dir, maxDepth, dispatch, -1);
    }

    public static List<Hop> pathTo(Map<Integer, Hop> hops, int target) {
        var path = new ArrayList<Hop>();
        for (Hop h = hops.get(target); h != null; h = h.via() < 0 ? null : hops.get(h.via())) {
            path.add(h);
        }
        Collections.reverse(path);
        return path;
    }

    public static Optional<List<Hop>> shortestPath(DependencyGraph g, int from, int to, Direction dir, int maxDepth) {
        var hops = bfs(g, List.of(from), dir, maxDepth, false, to);
        return hops.containsKey(to) ? Optional.of(pathTo(hops, to)) : Optional.empty();
    }

    // simple paths, bounded DFS
    public static List<List<Integer>> allPaths(DependencyGraph g, int from, int to, Direction dir,
                                               int maxDepth, int maxPaths) {
        var result = new LinkedHashSet<List<Integer>>();
        int[] stack = new int[maxDepth + 1];
        int[] cursor = new int[maxDepth + 1];
        boolean[] onPath = new boolean[g.size()];
        int sp = 0;
        stack[0] = from;
        onPath[from] = true;

        while (sp >= 0 && result.size() < maxPaths) {
            int v = stack[sp];
            if (v == to && sp > 0) {
                result.add(Arrays.stream(stack, 0, sp + 1).boxed().toList());
                onPath[v] = false;
                sp--;
                continue;
            }
            Adj[] adj = dir == Direction.UPSTREAM ? g.in(v) : g.out(v);
            if (sp == maxDepth || cursor[sp] >= adj.length) {
                onPath[v] = false;
                cursor[sp] = 0;
                sp--;
                continue;
            }
            int w = adj[cursor[sp]++].node();
            if (!onPath[w]) {
                stack[++sp] = w;
                cursor[sp] = 0;
                onPath[w] = true;
            }
        }
        return List.copyOf(result);
    }

    private static Map<Integer, Hop> bfs(DependencyGraph g, Collection<Integer> seeds, Direction dir,
                                         int maxDepth, boolean dispatch, int stopAt) {
        var hops = new LinkedHashMap<Integer, Hop>();
        var queue = new ArrayDeque<Integer>();
        for (int s : seeds) {
            if (hops.putIfAbsent(s, new Hop(s, 0, -1, null, false)) == null) queue.add(s);
        }
        boolean upstream = dir == Direction.UPSTREAM;

        while (!queue.isEmpty()) {
            int cur = queue.poll();
            if (cur == stopAt) break;
            Hop hop = hops.get(cur);

            // callers of an overridden declaration may dispatch here; same depth
            if (dispatch && upstream) {
                for (Adj a : g.out(cur)) {
                    if (a.type() == EdgeType.OVERRIDES && !hops.containsKey(a.node())) {
                        hops.put(a.node(), new Hop(a.node(), hop.depth(), cur, EdgeType.OVERRIDES, true));
                        queue.addFirst(a.node());
                    }
                }
            }
            if (hop.depth() >= maxDepth) continue;

            for (Adj a : upstream ? g.in(cur) : g.out(cur)) {
                // sibling implementations are not affected
                if (hop.dispatch() && a.type() == EdgeType.OVERRIDES) continue;
                if (!hops.containsKey(a.node())) {
                    hops.put(a.node(), new Hop(a.node(), hop.depth() + 1, cur, a.type(), false));
                    queue.addLast(a.node());
                }
            }
        }
        return hops;
    }
}
