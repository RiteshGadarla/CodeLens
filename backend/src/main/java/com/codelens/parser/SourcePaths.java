package com.codelens.parser;

import java.util.regex.Pattern;

public final class SourcePaths {

    private static final Pattern TEST_NAME = Pattern.compile(".*(Test|Tests|IT|TestCase)\\.java$");

    private SourcePaths() {
    }

    public static boolean isJava(String path) {
        return path.endsWith(".java") && !path.endsWith("package-info.java") && !path.endsWith("module-info.java");
    }

    public static boolean isTest(String path) {
        String p = path.replace('\\', '/');
        return p.startsWith("src/test/") || p.contains("/src/test/")
                || p.contains("/src/integrationTest/") || TEST_NAME.matcher(p).matches();
    }
}
