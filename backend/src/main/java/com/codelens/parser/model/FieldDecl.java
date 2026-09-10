package com.codelens.parser.model;

import java.io.Serializable;
import java.util.List;

public record FieldDecl(
        String name,
        String typeText,
        String visibility,
        List<String> modifiers,
        List<String> annotations,
        List<Creation> creations,
        int startLine,
        int endLine
) implements Serializable {
}
