package com.codelens.api.dto;

import com.codelens.graph.GraphTraversal;
import com.codelens.graph.PathStep;

import java.util.List;

// DOWNSTREAM: from depends on to; UPSTREAM: to depends on from
public record PathResult(boolean found, GraphTraversal.Direction direction, List<List<PathStep>> paths) {
}
