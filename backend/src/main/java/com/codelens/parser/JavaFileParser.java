package com.codelens.parser;

import com.codelens.domain.EntityKind;
import com.codelens.parser.model.*;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.AccessSpecifier;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;

import java.util.*;

// pass 1: source -> declarations + raw references
public class JavaFileParser {

    private static final Set<String> ACCESS = Set.of("public", "private", "protected");
    private static final int MAX_ERROR = 500;

    // JavaParser is not thread-safe
    private static final ThreadLocal<JavaParser> PARSER = ThreadLocal.withInitial(() -> new JavaParser(
            new ParserConfiguration()
                    .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
                    .setAttributeComments(false)));

    public ParsedFile parse(String path, String source) {
        boolean test = SourcePaths.isTest(path);
        int loc = (int) source.lines().filter(l -> !l.isBlank()).count();

        ParseResult<CompilationUnit> result;
        try {
            result = PARSER.get().parse(source);
        } catch (RuntimeException e) {
            return failed(path, loc, test, e.toString());
        }
        String error = result.isSuccessful() ? null
                : result.getProblems().stream().findFirst().map(Problem::getMessage).orElse("parse error");
        if (result.getResult().isEmpty()) return failed(path, loc, test, error);

        CompilationUnit cu = result.getResult().get();
        String pkg = cu.getPackageDeclaration().map(NodeWithName::getNameAsString).orElse("");

        var imports = new ArrayList<String>();
        var wildcards = new ArrayList<String>();
        var statics = new ArrayList<String>();
        for (ImportDeclaration imp : cu.getImports()) {
            String name = imp.getNameAsString();
            if (imp.isStatic()) statics.add(imp.isAsterisk() ? name + ".*" : name);
            else if (imp.isAsterisk()) wildcards.add(name);
            else imports.add(name);
        }

        var types = new ArrayList<TypeDecl>();
        for (TypeDeclaration<?> td : cu.getTypes()) {
            collect(td, pkg, null, test, types);
        }
        return new ParsedFile(path, pkg, imports, wildcards, statics, types, loc, test, truncate(error));
    }

    public static ParsedFile failed(String path, int loc, boolean test, String error) {
        return new ParsedFile(path, "", List.of(), List.of(), List.of(), List.of(), loc, test, truncate(error));
    }

    private void collect(TypeDeclaration<?> td, String pkg, TypeDecl outer, boolean test, List<TypeDecl> out) {
        String name = td.getNameAsString();
        String qn = outer != null ? outer.qualifiedName() + "." + name : pkg.isEmpty() ? name : pkg + "." + name;
        EntityKind kind = kindOf(td);
        boolean iface = kind == EntityKind.INTERFACE || kind == EntityKind.ANNOTATION;

        var extendsTypes = new ArrayList<String>();
        var implementsTypes = new ArrayList<String>();
        var typeParams = new ArrayList<String>();
        if (td instanceof ClassOrInterfaceDeclaration c) {
            c.getExtendedTypes().forEach(t -> extendsTypes.add(t.asString()));
            c.getImplementedTypes().forEach(t -> implementsTypes.add(t.asString()));
            c.getTypeParameters().forEach(t -> typeParams.add(t.getNameAsString()));
        } else if (td instanceof EnumDeclaration e) {
            e.getImplementedTypes().forEach(t -> implementsTypes.add(t.asString()));
        } else if (td instanceof RecordDeclaration r) {
            r.getImplementedTypes().forEach(t -> implementsTypes.add(t.asString()));
            r.getTypeParameters().forEach(t -> typeParams.add(t.getNameAsString()));
        }

        String basePath = SpringAnnotations.basePath(td.getAnnotations());

        var fields = new ArrayList<FieldDecl>();
        for (FieldDeclaration fd : td.getFields()) {
            String vis = visibility(fd.getAccessSpecifier(), iface ? "public" : "package");
            var mods = modifiers(fd.getModifiers());
            var anns = annotations(fd.getAnnotations());
            for (VariableDeclarator v : fd.getVariables()) {
                var creations = v.getInitializer().map(this::creations).orElse(List.of());
                fields.add(new FieldDecl(v.getNameAsString(), v.getType().asString(), vis, mods, anns,
                        creations, begin(fd), end(fd)));
            }
        }

        var methods = new ArrayList<MethodDecl>();
        for (MethodDeclaration md : td.getMethods()) {
            methods.add(method(md, md.getNameAsString(), false, md.getType().asString(),
                    md.getBody().orElse(null), iface, basePath));
        }
        for (ConstructorDeclaration cd : td.getConstructors()) {
            methods.add(method(cd, "<init>", true, null, cd.getBody(), false, basePath));
        }
        if (td instanceof RecordDeclaration r) recordMembers(r, fields, methods);

        boolean hasTests = methods.stream()
                .flatMap(m -> m.annotations().stream())
                .map(TypeNames::simple)
                .anyMatch(SpringAnnotations.TEST_ANNOTATIONS::contains);
        var anns = annotations(td.getAnnotations());

        var decl = new TypeDecl(qn, name, kind, outer == null ? null : outer.qualifiedName(),
                visibility(td.getAccessSpecifier(), "package"), modifiers(td.getModifiers()), anns, typeParams,
                extendsTypes, implementsTypes, fields, methods,
                SpringAnnotations.stereotype(anns, extendsTypes, test || hasTests), begin(td), end(td));
        out.add(decl);

        for (BodyDeclaration<?> member : td.getMembers()) {
            if (member instanceof TypeDeclaration<?> nested) collect(nested, pkg, decl, test, out);
        }
    }

    private MethodDecl method(CallableDeclaration<?> c, String name, boolean ctor, String returnType,
                              BlockStmt body, boolean inInterface, String basePath) {
        var params = new ArrayList<Param>();
        for (Parameter p : c.getParameters()) {
            params.add(new Param(p.getNameAsString(), p.getType().asString(), p.isVarArgs()));
        }
        var locals = new LinkedHashMap<String, Scope>();
        var referenced = new ArrayList<String>();
        var calls = new ArrayList<CallSite>();
        var creations = new ArrayList<Creation>();
        c.getThrownExceptions().forEach(t -> referenced.add(t.asString()));

        if (body != null) {
            body.walk(n -> {
                if (n instanceof MethodCallExpr mc) {
                    calls.add(new CallSite(mc.getNameAsString(), mc.getArguments().size(),
                            scopeOf(mc.getScope().orElse(null)), begin(mc)));
                } else if (n instanceof MethodReferenceExpr mr) {
                    if (mr.getIdentifier().equals("new")) {
                        creations.add(new Creation(typeText(mr.getScope()), -1, begin(mr)));
                    } else {
                        calls.add(new CallSite(mr.getIdentifier(), -1, scopeOf(mr.getScope()), begin(mr)));
                    }
                } else if (n instanceof ExplicitConstructorInvocationStmt ec) {
                    calls.add(new CallSite("<init>", ec.getArguments().size(),
                            ec.isThis() ? Scope.THIS : Scope.SUPER, begin(ec)));
                } else if (n instanceof ObjectCreationExpr oc) {
                    creations.add(new Creation(oc.getType().asString(), oc.getArguments().size(), begin(oc)));
                } else if (n instanceof VariableDeclarationExpr vd) {
                    for (var v : vd.getVariables()) {
                        if (v.getType().isVarType()) {
                            v.getInitializer().ifPresent(i -> locals.putIfAbsent(v.getNameAsString(), scopeOf(i)));
                        } else {
                            String t = v.getType().asString();
                            locals.putIfAbsent(v.getNameAsString(), new Scope.Typed(t));
                            referenced.add(t);
                        }
                    }
                } else if (n instanceof CatchClause cc) {
                    String t = cc.getParameter().getType().asString();
                    locals.putIfAbsent(cc.getParameter().getNameAsString(), new Scope.Typed(t));
                    referenced.add(t);
                } else if (n instanceof LambdaExpr le) {
                    for (var p : le.getParameters()) {
                        if (!p.getType().isUnknownType()) {
                            locals.putIfAbsent(p.getNameAsString(), new Scope.Typed(p.getType().asString()));
                        }
                    }
                } else if (n instanceof CastExpr ce) {
                    referenced.add(ce.getType().asString());
                } else if (n instanceof InstanceOfExpr io) {
                    referenced.add(io.getType().asString());
                } else if (n instanceof ClassExpr cl) {
                    referenced.add(cl.getType().asString());
                }
            });
        }

        String[] endpoint = ctor ? null : SpringAnnotations.endpoint(c.getAnnotations(), basePath);
        var typeParams = c.getTypeParameters().stream().map(NodeWithSimpleName::getNameAsString).toList();
        return new MethodDecl(name, ctor, params, returnType,
                visibility(c.getAccessSpecifier(), inInterface ? "public" : "package"),
                modifiers(c.getModifiers()), annotations(c.getAnnotations()), typeParams,
                locals, referenced, calls, creations, ComplexityCalculator.of(body),
                endpoint == null ? null : endpoint[0], endpoint == null ? null : endpoint[1],
                begin(c), end(c));
    }

    // implicit record fields + accessors
    private void recordMembers(RecordDeclaration r, List<FieldDecl> fields, List<MethodDecl> methods) {
        for (Parameter p : r.getParameters()) {
            String n = p.getNameAsString();
            String type = p.getType().asString();
            fields.add(new FieldDecl(n, type, "private", List.of("final"), annotations(p.getAnnotations()),
                    List.of(), begin(p), end(p)));
            boolean declared = methods.stream().anyMatch(m -> m.name().equals(n) && m.params().isEmpty());
            if (!declared) {
                methods.add(new MethodDecl(n, false, List.of(), type, "public", List.of(), List.of(), List.of(),
                        Map.of(), List.of(), List.of(), List.of(), 1, null, null, begin(p), end(p)));
            }
        }
    }

    static Scope scopeOf(Expression e) {
        if (e == null) return Scope.NONE;
        if (e.isThisExpr()) {
            return e.asThisExpr().getTypeName().<Scope>map(n -> new Scope.Typed(n.asString())).orElse(Scope.THIS);
        }
        if (e.isSuperExpr()) return Scope.SUPER;
        if (e.isNameExpr()) return new Scope.Name(e.asNameExpr().getNameAsString());
        if (e.isFieldAccessExpr()) {
            var f = e.asFieldAccessExpr();
            return new Scope.Field(scopeOf(f.getScope()), f.getNameAsString(), f.toString());
        }
        if (e.isMethodCallExpr()) {
            var m = e.asMethodCallExpr();
            return new Scope.Call(new CallSite(m.getNameAsString(), m.getArguments().size(),
                    scopeOf(m.getScope().orElse(null)), begin(m)));
        }
        if (e.isObjectCreationExpr()) return new Scope.Typed(e.asObjectCreationExpr().getType().asString());
        if (e.isCastExpr()) return new Scope.Typed(e.asCastExpr().getType().asString());
        if (e.isTypeExpr()) return new Scope.Typed(e.asTypeExpr().getType().asString());
        if (e.isEnclosedExpr()) return scopeOf(e.asEnclosedExpr().getInner());
        return Scope.UNKNOWN;
    }

    private List<Creation> creations(Expression init) {
        return init.findAll(ObjectCreationExpr.class).stream()
                .map(oc -> new Creation(oc.getType().asString(), oc.getArguments().size(), begin(oc)))
                .toList();
    }

    private static String typeText(Expression e) {
        return e.isTypeExpr() ? e.asTypeExpr().getType().asString() : e.toString();
    }

    private static EntityKind kindOf(TypeDeclaration<?> td) {
        if (td instanceof ClassOrInterfaceDeclaration c) return c.isInterface() ? EntityKind.INTERFACE : EntityKind.CLASS;
        if (td instanceof EnumDeclaration) return EntityKind.ENUM;
        if (td instanceof RecordDeclaration) return EntityKind.RECORD;
        if (td instanceof AnnotationDeclaration) return EntityKind.ANNOTATION;
        return EntityKind.CLASS;
    }

    private static String visibility(AccessSpecifier access, String fallback) {
        String s = access.asString();
        return s.isEmpty() ? fallback : s;
    }

    private static List<String> modifiers(NodeList<Modifier> modifiers) {
        return modifiers.stream().map(m -> m.getKeyword().asString()).filter(k -> !ACCESS.contains(k)).toList();
    }

    private static List<String> annotations(NodeList<AnnotationExpr> annotations) {
        return annotations.stream().map(AnnotationExpr::getNameAsString).toList();
    }

    private static int begin(Node n) {
        return n.getRange().map(r -> r.begin.line).orElse(0);
    }

    private static int end(Node n) {
        return n.getRange().map(r -> r.end.line).orElse(0);
    }

    private static String truncate(String s) {
        return s == null || s.length() <= MAX_ERROR ? s : s.substring(0, MAX_ERROR);
    }
}
