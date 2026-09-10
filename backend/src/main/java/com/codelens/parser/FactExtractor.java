package com.codelens.parser;

import com.codelens.domain.EdgeType;
import com.codelens.domain.EntityKind;
import com.codelens.parser.SymbolTable.MethodRef;
import com.codelens.parser.SymbolTable.TypeRef;
import com.codelens.parser.model.*;

import java.util.*;

// pass 2: declarations + symbol table -> entities and edges
final class FactExtractor {

    private static final int MAX_DEPTH = 8;

    private final SymbolTable table;

    FactExtractor(SymbolTable table) {
        this.table = table;
    }

    record Result(List<EntityFact> entities, List<EdgeFact> edges) {
    }

    private record Ctx(ParsedFile file, String typeQn, MethodDecl method) {
    }

    private record EdgeKey(String source, String target, EdgeType type) {
    }

    Result extract(TypeRef ref) {
        ParsedFile f = ref.file();
        TypeDecl t = ref.decl();
        String qn = t.qualifiedName();
        Set<String> typeParams = typeParamsInScope(t);
        var entities = new ArrayList<EntityFact>();
        var edges = new LinkedHashMap<EdgeKey, Integer>();

        int complexity = Math.max(1, t.methods().stream().mapToInt(MethodDecl::complexity).sum());
        entities.add(new EntityFact(qn, t.name(), t.kind(), t.outerQualifiedName(), f.path(), f.packageName(),
                Signatures.display(t), t.visibility(), csv(t.modifiers()), csv(t.annotations()), t.stereotype(),
                null, null, t.startLine(), t.endLine(), complexity));

        for (String s : t.extendsTypes()) hierarchy(edges, f, t, s, EdgeType.EXTENDS, typeParams);
        for (String s : t.implementsTypes()) hierarchy(edges, f, t, s, EdgeType.IMPLEMENTS, typeParams);
        annotations(edges, f, qn, qn, t.annotations());
        if (t.outerQualifiedName() == null) imports(edges, f, qn);

        for (FieldDecl fd : t.fields()) {
            String fq = Signatures.fieldQn(qn, fd.name());
            entities.add(new EntityFact(fq, fd.name(), EntityKind.FIELD, qn, f.path(), f.packageName(),
                    Signatures.display(fd), fd.visibility(), csv(fd.modifiers()), csv(fd.annotations()), null,
                    null, null, fd.startLine(), fd.endLine(), 1));
            typeUses(edges, f, qn, fq, fd.typeText(), typeParams);
            annotations(edges, f, qn, fq, fd.annotations());
            fd.creations().forEach(c -> creation(edges, f, qn, fq, c, typeParams));
        }

        for (MethodDecl md : t.methods()) {
            String mq = Signatures.methodQn(qn, md);
            var mtp = new HashSet<>(typeParams);
            mtp.addAll(md.typeParams());

            entities.add(new EntityFact(mq, md.constructor() ? t.name() : md.name(),
                    md.constructor() ? EntityKind.CONSTRUCTOR : EntityKind.METHOD, qn, f.path(), f.packageName(),
                    Signatures.display(md, t.name()), md.visibility(), csv(md.modifiers()), csv(md.annotations()),
                    null, md.httpMethod(), md.httpPath(), md.startLine(), md.endLine(), md.complexity()));

            md.params().forEach(p -> typeUses(edges, f, qn, mq, p.typeText(), mtp));
            typeUses(edges, f, qn, mq, md.returnType(), mtp);
            md.referencedTypes().forEach(r -> typeUses(edges, f, qn, mq, r, mtp));
            annotations(edges, f, qn, mq, md.annotations());
            md.creations().forEach(c -> creation(edges, f, qn, mq, c, mtp));

            var ctx = new Ctx(f, qn, md);
            for (CallSite cs : md.calls()) {
                for (MethodRef target : resolveCall(cs, ctx, 0)) {
                    edge(edges, mq, Signatures.methodQn(target.ownerQn(), target.method()), EdgeType.CALLS);
                }
            }

            if (!md.constructor() && !md.isStatic() && !"private".equals(md.visibility())) {
                overrides(edges, qn, mq, md);
            }

            if (md.httpMethod() != null) {
                String name = md.httpMethod() + " " + md.httpPath();
                String eq = Signatures.endpointQn(md.httpMethod(), md.httpPath(), mq);
                entities.add(new EntityFact(eq, name, EntityKind.ENDPOINT, qn, f.path(), f.packageName(), name,
                        "public", null, null, null, md.httpMethod(), md.httpPath(),
                        md.startLine(), md.startLine(), 1));
                edge(edges, eq, mq, EdgeType.ROUTES_TO);
            }
        }

        var edgeFacts = edges.entrySet().stream()
                .map(e -> new EdgeFact(e.getKey().source(), e.getKey().target(), e.getKey().type(), e.getValue(), f.path()))
                .toList();
        return new Result(entities, edgeFacts);
    }

    // ---- references

    private void hierarchy(Map<EdgeKey, Integer> edges, ParsedFile f, TypeDecl t, String text, EdgeType type,
                           Set<String> typeParams) {
        var tokens = TypeNames.tokens(text);
        for (int i = 0; i < tokens.size(); i++) {
            if (typeParams.contains(tokens.get(i))) continue;
            EdgeType et = i == 0 ? type : EdgeType.USES_TYPE;
            table.resolveType(f, t.outerQualifiedName(), tokens.get(i))
                    .ifPresent(q -> edge(edges, t.qualifiedName(), q, et));
        }
    }

    private void imports(Map<EdgeKey, Integer> edges, ParsedFile f, String qn) {
        for (String imp : f.imports()) {
            if (table.isInternal(imp)) edge(edges, qn, imp, EdgeType.IMPORTS);
        }
        for (String s : f.staticImports()) {
            String owner = s.substring(0, s.lastIndexOf('.'));
            if (table.isInternal(owner)) edge(edges, qn, owner, EdgeType.IMPORTS);
        }
    }

    private void annotations(Map<EdgeKey, Integer> edges, ParsedFile f, String scopeQn, String source,
                             List<String> annotations) {
        for (String a : annotations) {
            table.resolveType(f, scopeQn, a)
                    .filter(q -> table.type(q).decl().kind() == EntityKind.ANNOTATION)
                    .ifPresent(q -> edge(edges, source, q, EdgeType.ANNOTATED_BY));
        }
    }

    private void typeUses(Map<EdgeKey, Integer> edges, ParsedFile f, String scopeQn, String source, String typeText,
                          Set<String> typeParams) {
        for (String tok : TypeNames.tokens(typeText)) {
            if (typeParams.contains(tok)) continue;
            table.resolveType(f, scopeQn, tok).ifPresent(q -> edge(edges, source, q, EdgeType.USES_TYPE));
        }
    }

    private void creation(Map<EdgeKey, Integer> edges, ParsedFile f, String scopeQn, String source, Creation c,
                          Set<String> typeParams) {
        var tokens = TypeNames.tokens(c.typeText());
        if (tokens.isEmpty() || typeParams.contains(tokens.get(0))) return;
        table.resolveType(f, scopeQn, tokens.get(0)).ifPresent(q -> {
            edge(edges, source, q, EdgeType.CREATES);
            for (MethodRef ctor : table.findMethods(q, "<init>", c.argCount())) {
                edge(edges, source, Signatures.methodQn(q, ctor.method()), EdgeType.CALLS);
            }
        });
        for (String arg : tokens.subList(1, tokens.size())) {
            if (!typeParams.contains(arg)) {
                table.resolveType(f, scopeQn, arg).ifPresent(q -> edge(edges, source, q, EdgeType.USES_TYPE));
            }
        }
    }

    private void overrides(Map<EdgeKey, Integer> edges, String qn, String mq, MethodDecl md) {
        for (String s : table.allSupertypes(qn)) {
            TypeDecl sd = table.type(s).decl();
            for (MethodDecl sm : sd.methods()) {
                if (overrides(md, sm, sd.typeParams())) edge(edges, mq, Signatures.methodQn(s, sm), EdgeType.OVERRIDES);
            }
        }
    }

    private static boolean overrides(MethodDecl m, MethodDecl sm, List<String> superTypeParams) {
        if (sm.constructor() || sm.isStatic() || "private".equals(sm.visibility())
                || !sm.name().equals(m.name()) || sm.params().size() != m.params().size()) {
            return false;
        }
        for (int i = 0; i < m.params().size(); i++) {
            String a = TypeNames.erasedSimple(m.params().get(i).typeText());
            String b = TypeNames.erasedSimple(sm.params().get(i).typeText());
            // generic param in the supertype matches anything
            if (!a.equals(b) && !superTypeParams.contains(b) && !sm.typeParams().contains(b)) return false;
        }
        return true;
    }

    // ---- call resolution

    private List<MethodRef> resolveCall(CallSite cs, Ctx ctx, int depth) {
        if (depth > MAX_DEPTH) return List.of();
        if (cs.scope() instanceof Scope.None) {
            for (String t = ctx.typeQn(); t != null; t = table.outerOf(t)) {
                var found = table.findMethods(t, cs.name(), cs.argCount());
                if (!found.isEmpty()) return found;
            }
            for (String si : ctx.file().staticImports()) {
                String member = TypeNames.simple(si);
                if (!member.equals(cs.name()) && !member.equals("*")) continue;
                String owner = si.substring(0, si.lastIndexOf('.'));
                if (table.isInternal(owner)) {
                    var found = table.findMethods(owner, cs.name(), cs.argCount());
                    if (!found.isEmpty()) return found;
                }
            }
            return List.of();
        }
        return scopeType(cs.scope(), ctx, depth + 1)
                .map(t -> table.findMethods(t, cs.name(), cs.argCount()))
                .orElse(List.of());
    }

    private Optional<String> scopeType(Scope scope, Ctx ctx, int depth) {
        if (depth > MAX_DEPTH) return Optional.empty();
        return switch (scope) {
            case Scope.None n -> Optional.of(ctx.typeQn());
            case Scope.This t -> Optional.of(ctx.typeQn());
            case Scope.Super s -> table.superclass(ctx.typeQn());
            case Scope.Unknown u -> Optional.empty();
            case Scope.Typed t -> typeOf(ctx, t.typeText());
            case Scope.Name n -> nameType(n.name(), ctx, depth);
            case Scope.Field fa -> fieldAccessType(fa, ctx, depth);
            case Scope.Call c -> resolveCall(c.call(), ctx, depth).stream().findFirst()
                    .flatMap(r -> table.resolveIn(r.ownerQn(), r.method().returnType()));
        };
    }

    // param -> local -> field (own/outer) -> static type reference
    private Optional<String> nameType(String name, Ctx ctx, int depth) {
        for (Param p : ctx.method().params()) {
            if (p.name().equals(name)) return typeOf(ctx, p.typeText());
        }
        Scope local = ctx.method().locals().get(name);
        if (local != null) return scopeType(local, ctx, depth + 1);
        for (String t = ctx.typeQn(); t != null; t = table.outerOf(t)) {
            var field = table.findField(t, name);
            if (field.isPresent()) return table.resolveIn(field.get().ownerQn(), field.get().field().typeText());
        }
        return table.resolveType(ctx.file(), ctx.typeQn(), name);
    }

    private Optional<String> fieldAccessType(Scope.Field fa, Ctx ctx, int depth) {
        if (!fa.text().startsWith("this.") && TypeNames.isQualifiedName(fa.text())) {
            var asType = table.resolveType(ctx.file(), ctx.typeQn(), fa.text());
            if (asType.isPresent()) return asType;
        }
        return scopeType(fa.target(), ctx, depth + 1).flatMap(owner -> table.findField(owner, fa.name())
                .flatMap(fr -> table.resolveIn(fr.ownerQn(), fr.field().typeText()))
                .or(() -> Optional.of(owner + "." + fa.name()).filter(table::isInternal)));
    }

    private Optional<String> typeOf(Ctx ctx, String typeText) {
        String main = TypeNames.main(typeText);
        if (main == null || ctx.method().typeParams().contains(main)) return Optional.empty();
        return table.resolveType(ctx.file(), ctx.typeQn(), main);
    }

    // ---- helpers

    private Set<String> typeParamsInScope(TypeDecl t) {
        var out = new HashSet<>(t.typeParams());
        for (String o = t.outerQualifiedName(); o != null; o = table.outerOf(o)) {
            out.addAll(table.type(o).decl().typeParams());
        }
        return out;
    }

    private static void edge(Map<EdgeKey, Integer> edges, String source, String target, EdgeType type) {
        if (!source.equals(target)) edges.merge(new EdgeKey(source, target, type), 1, Integer::sum);
    }

    private static String csv(List<String> values) {
        return values == null || values.isEmpty() ? null : String.join(",", values);
    }
}
