package com.codelens.parser;

import com.codelens.parser.model.FieldDecl;
import com.codelens.parser.model.MethodDecl;
import com.codelens.parser.model.ParsedFile;
import com.codelens.parser.model.TypeDecl;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

// global index of project types; resolves names to internal qualified names
public class SymbolTable {

    public record TypeRef(ParsedFile file, TypeDecl decl) {
    }

    public record MethodRef(String ownerQn, MethodDecl method) {
    }

    public record FieldRef(String ownerQn, FieldDecl field) {
    }

    private final Map<String, TypeRef> types = new LinkedHashMap<>();
    private final List<String> duplicates = new ArrayList<>();
    private final Map<String, Optional<String>> typeCache = new ConcurrentHashMap<>();
    private final Map<String, List<String>> superCache = new ConcurrentHashMap<>();

    public SymbolTable(Collection<ParsedFile> files) {
        for (ParsedFile f : files) {
            for (TypeDecl t : f.types()) {
                if (types.putIfAbsent(t.qualifiedName(), new TypeRef(f, t)) != null) {
                    duplicates.add(t.qualifiedName() + " in " + f.path());
                }
            }
        }
    }

    public Collection<TypeRef> types() {
        return types.values();
    }

    public TypeRef type(String qn) {
        return types.get(qn);
    }

    public boolean isInternal(String qn) {
        return qn != null && types.containsKey(qn);
    }

    public List<String> duplicates() {
        return duplicates;
    }

    public String outerOf(String qn) {
        TypeRef r = types.get(qn);
        return r == null ? null : r.decl().outerQualifiedName();
    }

    // name as written in `file`, seen from inside type `fromQn` (nullable)
    public Optional<String> resolveType(ParsedFile file, String fromQn, String name) {
        if (name == null || name.isEmpty()) return Optional.empty();
        return typeCache.computeIfAbsent(file.path() + '|' + fromQn + '|' + name,
                k -> Optional.ofNullable(resolveUncached(file, fromQn, name)));
    }

    // type text declared inside ownerQn
    public Optional<String> resolveIn(String ownerQn, String typeText) {
        TypeRef r = types.get(ownerQn);
        return r == null ? Optional.empty() : resolveType(r.file(), ownerQn, TypeNames.main(typeText));
    }

    public List<String> directSupertypes(String qn) {
        return superCache.computeIfAbsent(qn, k -> {
            TypeRef r = types.get(k);
            if (r == null) return List.of();
            var out = new ArrayList<String>();
            Stream.concat(r.decl().extendsTypes().stream(), r.decl().implementsTypes().stream())
                    .forEach(t -> resolveType(r.file(), outerOf(k), TypeNames.main(t))
                            .filter(s -> !s.equals(k))
                            .ifPresent(out::add));
            return out;
        });
    }

    public Optional<String> superclass(String qn) {
        TypeRef r = types.get(qn);
        if (r == null || r.decl().extendsTypes().isEmpty()) return Optional.empty();
        return resolveType(r.file(), outerOf(qn), TypeNames.main(r.decl().extendsTypes().get(0)));
    }

    // BFS, nearest first
    public List<String> allSupertypes(String qn) {
        var seen = new LinkedHashSet<String>();
        var queue = new ArrayDeque<>(directSupertypes(qn));
        while (!queue.isEmpty()) {
            String s = queue.poll();
            if (!s.equals(qn) && seen.add(s)) queue.addAll(directSupertypes(s));
        }
        return List.copyOf(seen);
    }

    public List<MethodRef> findMethods(String typeQn, String name, int argCount) {
        var own = declared(typeQn, name, argCount);
        if (!own.isEmpty() || name.equals("<init>")) return own;
        for (String s : allSupertypes(typeQn)) {
            var found = declared(s, name, argCount);
            if (!found.isEmpty()) return found;
        }
        return List.of();
    }

    public Optional<FieldRef> findField(String typeQn, String name) {
        for (String t : Stream.concat(Stream.of(typeQn), allSupertypes(typeQn).stream()).toList()) {
            TypeRef r = types.get(t);
            if (r == null) continue;
            for (FieldDecl f : r.decl().fields()) {
                if (f.name().equals(name)) return Optional.of(new FieldRef(t, f));
            }
        }
        return Optional.empty();
    }

    private List<MethodRef> declared(String typeQn, String name, int argCount) {
        TypeRef r = types.get(typeQn);
        if (r == null) return List.of();
        var out = new ArrayList<MethodRef>();
        for (MethodDecl m : r.decl().methods()) {
            if (m.name().equals(name) && arityMatches(m, argCount)) out.add(new MethodRef(typeQn, m));
        }
        return out;
    }

    private static boolean arityMatches(MethodDecl m, int argCount) {
        int n = m.params().size();
        if (argCount < 0 || argCount == n) return true;
        return n > 0 && m.params().get(n - 1).varargs() && argCount >= n - 1;
    }

    private String resolveUncached(ParsedFile file, String fromQn, String name) {
        if (name.indexOf('.') > 0 && types.containsKey(name)) return name;
        int dot = name.indexOf('.');
        String head = dot < 0 ? name : name.substring(0, dot);
        String base = resolveSimple(file, fromQn, head);
        if (base == null) return null;
        String qn = dot < 0 ? base : base + name.substring(dot);
        return types.containsKey(qn) ? qn : null;
    }

    // enclosing/nested -> explicit import -> same package -> wildcard import
    private String resolveSimple(ParsedFile file, String fromQn, String simple) {
        for (String t = fromQn; t != null; t = outerOf(t)) {
            if (TypeNames.simple(t).equals(simple)) return t;
            String nested = t + "." + simple;
            if (types.containsKey(nested)) return nested;
        }
        for (String imp : file.imports()) {
            if (TypeNames.simple(imp).equals(simple)) return types.containsKey(imp) ? imp : null;
        }
        String samePkg = file.packageName().isEmpty() ? simple : file.packageName() + "." + simple;
        if (types.containsKey(samePkg)) return samePkg;
        for (String w : file.wildcardImports()) {
            String qn = w + "." + simple;
            if (types.containsKey(qn)) return qn;
        }
        return null;
    }
}
