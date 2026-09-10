package com.codelens.parser.model;

import java.io.Serializable;
import java.util.List;

// pass 1 output for one file
public record ParsedFile(
        String path,
        String packageName,
        List<String> imports,
        List<String> wildcardImports,
        List<String> staticImports,
        List<TypeDecl> types,
        int loc,
        boolean test,
        String parseError
) implements Serializable {
}
