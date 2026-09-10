package com.codelens.graph;

import com.codelens.domain.EntityKind;
import com.codelens.parser.model.AnalysisFacts;
import com.codelens.parser.model.EntityFact;

import java.util.HashMap;
import java.util.Objects;
import java.util.function.Function;

public final class GraphFactory {

    private GraphFactory() {
    }

    // synthetic ids, for in-memory use and tests
    public static DependencyGraph fromFacts(AnalysisFacts facts, Function<String, String> moduleOf) {
        var ids = new HashMap<String, Long>();
        long next = 1;
        for (EntityFact e : facts.entities()) ids.put(e.qualifiedName(), next++);

        var nodes = facts.entities().stream()
                .map(e -> new GraphNode(ids.get(e.qualifiedName()), e.qualifiedName(), e.name(), e.kind(),
                        e.parentQualifiedName() == null ? null : ids.get(e.parentQualifiedName()), null,
                        e.filePath(), moduleOf.apply(e.filePath()), e.packageName(), e.stereotype(),
                        e.httpMethod(), e.httpPath(), e.complexity(), isAbstract(e.kind(), e.modifiers())))
                .toList();
        var edges = facts.edges().stream()
                .filter(e -> ids.containsKey(e.sourceQn()) && ids.containsKey(e.targetQn()))
                .map(e -> new GraphEdge(ids.get(e.sourceQn()), ids.get(e.targetQn()), e.type(), e.weight()))
                .toList();
        return DependencyGraph.of(nodes, edges);
    }

    public static boolean isAbstract(EntityKind kind, String modifiers) {
        return kind == EntityKind.INTERFACE || kind == EntityKind.ANNOTATION
                || Objects.requireNonNullElse(modifiers, "").contains("abstract");
    }
}
