package com.codelens.parser.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public record MethodDecl(
        String name,
        boolean constructor,
        List<Param> params,
        String returnType,
        String visibility,
        List<String> modifiers,
        List<String> annotations,
        List<String> typeParams,
        Map<String, Scope> locals,
        List<String> referencedTypes,
        List<CallSite> calls,
        List<Creation> creations,
        int complexity,
        String httpMethod,
        String httpPath,
        int startLine,
        int endLine
) implements Serializable {

    public boolean isStatic() {
        return modifiers.contains("static");
    }
}
