package com.codelens.graph;

import com.codelens.domain.EdgeType;
import com.codelens.domain.EntityKind;

import java.util.List;

// edge = relation between previous step and this one
public record PathStep(long id, String label, EntityKind kind, EdgeType edge, boolean dispatch) {

    public static List<PathStep> of(DependencyGraph g, List<GraphTraversal.Hop> hops) {
        return hops.stream().map(h -> {
            GraphNode n = g.node(h.node());
            return new PathStep(n.id(), n.label(), n.kind(), h.edge(), h.dispatch());
        }).toList();
    }
}
