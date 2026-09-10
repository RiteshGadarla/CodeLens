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

    public String label() {
        return labelOf(qualifiedName, kind, name);
    }

    // UserService, Scope.None, UserService#find(Long), UserService(Repo), GET /users
    public static String labelOf(String qualifiedName, EntityKind kind, String name) {
        if (kind == EntityKind.ENDPOINT) return name;
        int hash = qualifiedName.indexOf('#');
        String type = typeLabel(hash < 0 ? qualifiedName : qualifiedName.substring(0, hash));
        if (hash < 0) return type;
        String member = qualifiedName.substring(hash + 1);
        return member.startsWith("<init>") ? type + member.substring("<init>".length()) : type + "#" + member;
    }

    // keeps outer types: com.acme.Scope.None -> Scope.None
    private static String typeLabel(String type) {
        String[] parts = type.split("\\.");
        int first = parts.length - 1;
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].isEmpty() && Character.isUpperCase(parts[i].charAt(0))) {
                first = i;
                break;
            }
        }
        return String.join(".", java.util.Arrays.copyOfRange(parts, first, parts.length));
    }
}
