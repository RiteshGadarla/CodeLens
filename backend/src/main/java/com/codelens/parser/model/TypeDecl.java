package com.codelens.parser.model;

import com.codelens.domain.EntityKind;
import com.codelens.domain.Stereotype;

import java.io.Serializable;
import java.util.List;

public record TypeDecl(
        String qualifiedName,
        String name,
        EntityKind kind,
        String outerQualifiedName,
        String visibility,
        List<String> modifiers,
        List<String> annotations,
        List<String> typeParams,
        List<String> extendsTypes,
        List<String> implementsTypes,
        List<FieldDecl> fields,
        List<MethodDecl> methods,
        Stereotype stereotype,
        int startLine,
        int endLine
) implements Serializable {
}
