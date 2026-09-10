package com.codelens.api.dto;

import com.codelens.domain.EdgeType;
import com.codelens.domain.EntityKind;
import com.codelens.domain.Stereotype;

import java.util.List;

// distance = hops from focus, -1 without focus
public record GraphView(String level, Long focusId, List<Node> nodes, List<Edge> edges, boolean truncated) {

    public record Node(long id, String label, String qualifiedName, EntityKind kind, Stereotype role, String module,
                       String packageName, int complexity, double riskScore, int fanIn, int fanOut, int distance,
                       boolean focus) {
    }

    public record Edge(long source, long target, EdgeType type, int weight) {
    }
}
