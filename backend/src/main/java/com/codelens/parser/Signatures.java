package com.codelens.parser;

import com.codelens.parser.model.FieldDecl;
import com.codelens.parser.model.MethodDecl;
import com.codelens.parser.model.TypeDecl;

import java.util.stream.Collectors;

public final class Signatures {

    private Signatures() {
    }

    // save(User,int)
    public static String of(MethodDecl m) {
        return m.name() + "(" + m.params().stream()
                .map(p -> TypeNames.erasedSimple(p.typeText()) + (p.varargs() ? "[]" : ""))
                .collect(Collectors.joining(",")) + ")";
    }

    public static String methodQn(String typeQn, MethodDecl m) {
        return typeQn + "#" + of(m);
    }

    public static String fieldQn(String typeQn, String field) {
        return typeQn + "#" + field;
    }

    public static String endpointQn(String httpMethod, String path, String methodQn) {
        return "endpoint:" + httpMethod + " " + path + "@" + methodQn;
    }

    public static String display(TypeDecl t) {
        var sb = prefix(t.visibility(), t.modifiers());
        sb.append(t.kind().name().toLowerCase()).append(' ').append(t.name());
        if (!t.extendsTypes().isEmpty()) sb.append(" extends ").append(String.join(", ", t.extendsTypes()));
        if (!t.implementsTypes().isEmpty()) sb.append(" implements ").append(String.join(", ", t.implementsTypes()));
        return sb.toString();
    }

    public static String display(MethodDecl m, String typeName) {
        var sb = prefix(m.visibility(), m.modifiers());
        if (!m.constructor()) sb.append(m.returnType()).append(' ');
        sb.append(m.constructor() ? typeName : m.name()).append('(')
                .append(m.params().stream()
                        .map(p -> p.typeText() + (p.varargs() ? "..." : "") + " " + p.name())
                        .collect(Collectors.joining(", ")))
                .append(')');
        return sb.toString();
    }

    public static String display(FieldDecl f) {
        return prefix(f.visibility(), f.modifiers()).append(f.typeText()).append(' ').append(f.name()).toString();
    }

    private static StringBuilder prefix(String visibility, java.util.List<String> modifiers) {
        var sb = new StringBuilder();
        if (visibility != null && !visibility.equals("package")) sb.append(visibility).append(' ');
        modifiers.forEach(m -> sb.append(m).append(' '));
        return sb;
    }
}
