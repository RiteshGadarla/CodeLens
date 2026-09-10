package com.codelens.impact;

import com.codelens.domain.EdgeType;
import com.codelens.graph.EntityRef;
import com.codelens.graph.PathStep;
import com.codelens.metrics.RiskScorer;

import java.util.List;
import java.util.Map;

public record ImpactResult(
        EntityRef target,
        int seeds,
        int directDependents,
        int transitiveDependents,
        int maxDepth,
        List<Affected> affected,
        List<Affected> endpoints,
        Map<String, List<EntityRef>> affectedTypes,
        List<String> modules,
        List<String> packages,
        List<EntityRef> tests,
        RiskScorer.Breakdown risk,
        boolean truncated
) {

    // path runs from the changed entity to the affected one
    public record Affected(EntityRef entity, int depth, EdgeType via, List<PathStep> path) {
    }
}
