package com.codelens.graph;

import com.codelens.domain.EntityKind;
import com.codelens.domain.Stereotype;

public record EntityRef(long id, String qualifiedName, String label, EntityKind kind, Stereotype role,
                        String filePath, String module) {

    public static EntityRef of(DependencyGraph g, int i) {
        GraphNode n = g.node(i);
        int owner = g.ownerType(i);
        Stereotype role = owner >= 0 ? g.node(owner).stereotype() : n.stereotype();
        return new EntityRef(n.id(), n.qualifiedName(), n.label(), n.kind(), role, n.filePath(), n.module());
    }
}
