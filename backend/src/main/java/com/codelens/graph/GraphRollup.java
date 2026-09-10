package com.codelens.graph;

import com.codelens.domain.EdgeType;
import com.codelens.graph.DependencyGraph.Adj;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public final class GraphRollup {

    private record Key(long source, long target, EdgeType type) {
    }

    private GraphRollup() {
    }

    // member edges lifted to their owning types
    public static DependencyGraph types(DependencyGraph g) {
        var nodes = new ArrayList<GraphNode>();
        int[] owner = new int[g.size()];
        for (int i = 0; i < g.size(); i++) {
            owner[i] = g.ownerType(i);
            if (g.node(i).kind().isType()) nodes.add(g.node(i));
        }
        var merged = new LinkedHashMap<Key, Integer>();
        for (int s = 0; s < g.size(); s++) {
            int os = owner[s];
            if (os < 0) continue;
            for (Adj a : g.out(s)) {
                int ot = owner[a.node()];
                // a type and its own nested types are one unit, not coupling
                if (ot < 0 || ot == os || nested(g, os, ot) || nested(g, ot, os)) continue;
                merged.merge(new Key(g.node(os).id(), g.node(ot).id(), a.type()), a.weight(), Integer::sum);
            }
        }
        var edges = merged.entrySet().stream()
                .map(e -> new GraphEdge(e.getKey().source(), e.getKey().target(), e.getKey().type(), e.getValue()))
                .toList();
        return DependencyGraph.of(nodes, edges);
    }

    private static boolean nested(DependencyGraph g, int inner, int outer) {
        for (int p = g.parent(inner); p >= 0; p = g.parent(p)) {
            if (p == outer) return true;
        }
        return false;
    }
}
