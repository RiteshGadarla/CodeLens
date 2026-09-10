package com.codelens.parser;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class TypeNames {

    private static final Pattern TOKEN = Pattern.compile("[A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*");
    private static final Pattern ANNOTATION = Pattern.compile("@[\\w.]+(\\([^)]*\\))?\\s*");
    private static final Pattern QUALIFIED = Pattern.compile("^" + TOKEN.pattern() + "$");
    private static final Set<String> SKIP = Set.of("extends", "super", "var", "final",
            "void", "boolean", "byte", "char", "short", "int", "long", "float", "double");

    private TypeNames() {
    }

    // Map<String, List<User>> -> [Map, String, List, User]
    public static List<String> tokens(String typeText) {
        if (typeText == null || typeText.isBlank()) return List.of();
        var m = TOKEN.matcher(ANNOTATION.matcher(typeText).replaceAll(""));
        var out = new LinkedHashSet<String>();
        while (m.find()) {
            if (!SKIP.contains(m.group())) out.add(m.group());
        }
        return List.copyOf(out);
    }

    public static String main(String typeText) {
        var t = tokens(typeText);
        return t.isEmpty() ? null : t.get(0);
    }

    // java.util.List<String>[] -> List[]
    public static String erasedSimple(String typeText) {
        if (typeText == null) return "";
        var sb = new StringBuilder();
        int depth = 0;
        for (char c : ANNOTATION.matcher(typeText).replaceAll("").toCharArray()) {
            if (c == '<') depth++;
            else if (c == '>') depth--;
            else if (depth == 0 && !Character.isWhitespace(c)) sb.append(c);
        }
        String erased = sb.toString().replace("...", "[]");
        int arr = erased.indexOf('[');
        String base = arr < 0 ? erased : erased.substring(0, arr);
        return simple(base) + (arr < 0 ? "" : erased.substring(arr));
    }

    public static String simple(String name) {
        return name.substring(name.lastIndexOf('.') + 1);
    }

    public static boolean isQualifiedName(String text) {
        return QUALIFIED.matcher(text).matches();
    }
}
