package com.codelens.parser;

import com.codelens.domain.Stereotype;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

// spring / jax-rs conventions
final class SpringAnnotations {

    private static final Map<String, String> MAPPINGS = Map.of(
            "GetMapping", "GET", "PostMapping", "POST", "PutMapping", "PUT",
            "DeleteMapping", "DELETE", "PatchMapping", "PATCH", "RequestMapping", "");
    private static final Set<String> JAXRS = Set.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS");
    private static final Set<String> REPO_BASES = Set.of("Repository", "CrudRepository", "ListCrudRepository",
            "PagingAndSortingRepository", "JpaRepository", "MongoRepository", "ReactiveCrudRepository");
    static final Set<String> TEST_ANNOTATIONS = Set.of("Test", "ParameterizedTest", "RepeatedTest", "TestFactory");

    private SpringAnnotations() {
    }

    static String basePath(NodeList<AnnotationExpr> annotations) {
        for (var a : annotations) {
            String n = simple(a);
            if (n.equals("RequestMapping") || n.equals("Path")) return path(a);
        }
        return "";
    }

    // {httpMethod, path} or null
    static String[] endpoint(NodeList<AnnotationExpr> annotations, String basePath) {
        String method = null;
        String path = "";
        for (var a : annotations) {
            String n = simple(a);
            if (MAPPINGS.containsKey(n)) {
                method = MAPPINGS.get(n).isEmpty() ? requestMethod(a) : MAPPINGS.get(n);
                path = path(a);
            } else if (JAXRS.contains(n)) {
                method = n;
            } else if (n.equals("Path")) {
                path = path(a);
            }
        }
        return method == null ? null : new String[]{method, join(basePath, path)};
    }

    static Stereotype stereotype(List<String> annotations, List<String> extendsTypes, boolean test) {
        if (test) return Stereotype.TEST;
        for (String a : annotations) {
            switch (TypeNames.simple(a)) {
                case "RestController", "Controller", "Path" -> {
                    return Stereotype.CONTROLLER;
                }
                case "Service" -> {
                    return Stereotype.SERVICE;
                }
                case "Repository" -> {
                    return Stereotype.REPOSITORY;
                }
                case "Configuration", "SpringBootApplication", "AutoConfiguration" -> {
                    return Stereotype.CONFIGURATION;
                }
                case "Entity", "Table", "Document", "Embeddable", "MappedSuperclass" -> {
                    return Stereotype.ENTITY;
                }
                case "Component" -> {
                    return Stereotype.COMPONENT;
                }
                default -> {
                }
            }
        }
        for (String e : extendsTypes) {
            String main = TypeNames.main(e);
            if (main != null && REPO_BASES.contains(TypeNames.simple(main))) return Stereotype.REPOSITORY;
        }
        return null;
    }

    static String join(String base, String path) {
        String p = ("/" + base + "/" + path).replaceAll("/+", "/");
        return p.length() > 1 && p.endsWith("/") ? p.substring(0, p.length() - 1) : p;
    }

    private static String simple(AnnotationExpr a) {
        return TypeNames.simple(a.getNameAsString());
    }

    private static String path(AnnotationExpr a) {
        if (a instanceof SingleMemberAnnotationExpr s) return literal(s.getMemberValue());
        if (a instanceof NormalAnnotationExpr n) {
            for (var p : n.getPairs()) {
                String key = p.getNameAsString();
                if (key.equals("value") || key.equals("path")) return literal(p.getValue());
            }
        }
        return "";
    }

    private static String requestMethod(AnnotationExpr a) {
        if (a instanceof NormalAnnotationExpr n) {
            for (var p : n.getPairs()) {
                if (!p.getNameAsString().equals("method")) continue;
                Expression v = p.getValue();
                if (v instanceof ArrayInitializerExpr arr && !arr.getValues().isEmpty()) v = arr.getValues().get(0);
                return TypeNames.simple(v.toString());
            }
        }
        return "ANY";
    }

    private static String literal(Expression e) {
        if (e instanceof StringLiteralExpr s) return s.asString();
        if (e instanceof ArrayInitializerExpr arr) return arr.getValues().isEmpty() ? "" : literal(arr.getValues().get(0));
        if (e instanceof BinaryExpr b) return literal(b.getLeft()) + literal(b.getRight());
        return "{" + e + "}";
    }
}
