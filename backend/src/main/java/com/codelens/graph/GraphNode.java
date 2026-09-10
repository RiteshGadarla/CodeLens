package com.codelens.graph;

import com.codelens.domain.EntityKind;
import com.codelens.domain.Stereotype;

public record GraphNode(
        long id,
        String qualifiedName,
        String name,
        EntityKind kind,
        Long parentId,
        Long fileId,
        String filePath,
        String module,
        String packageName,
        Stereotype stereotype,
        String httpMethod,
        String httpPath,
        int complexity,
        boolean abstractType
) {

    // UserService, UserService#find(Long), GET /users
    public String label() {
        if (kind == EntityKind.ENDPOINT) return name;
        int hash = qualifiedName.indexOf('#');
        String type = hash < 0 ? qualifiedName : qualifiedName.substring(0, hash);
        String simple = type.substring(type.lastIndexOf('.') + 1);
        return hash < 0 ? simple : simple + qualifiedName.substring(hash);
    }
}
