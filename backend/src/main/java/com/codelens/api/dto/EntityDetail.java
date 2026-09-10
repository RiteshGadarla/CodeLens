package com.codelens.api.dto;

import com.codelens.domain.EdgeType;
import com.codelens.graph.EntityRef;

import java.util.List;

public record EntityDetail(EntitySummary entity, String signature, String visibility, String modifiers,
                           String annotations, String httpMethod, String httpPath, int complexity,
                           EntityMetricDto metrics, EntitySummary parent, List<EntitySummary> members,
                           List<Relation> dependencies, List<Relation> dependents) {

    public record Relation(EntityRef entity, EdgeType type, int weight) {
    }
}
