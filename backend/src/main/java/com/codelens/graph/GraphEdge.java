package com.codelens.graph;

import com.codelens.domain.EdgeType;

public record GraphEdge(long sourceId, long targetId, EdgeType type, int weight) {
}
