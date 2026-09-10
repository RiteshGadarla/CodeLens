package com.codelens.parser.model;

import com.codelens.domain.EntityKind;
import com.codelens.domain.Stereotype;

public record EntityFact(
        String qualifiedName,
        String name,
        EntityKind kind,
        String parentQualifiedName,
        String filePath,
        String packageName,
        String signature,
        String visibility,
        String modifiers,
        String annotations,
        Stereotype stereotype,
        String httpMethod,
        String httpPath,
        int startLine,
        int endLine,
        int complexity
) {
}
