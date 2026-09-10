package com.codelens.impact;

public record ImpactOptions(int maxDepth, boolean includeTests, int maxResults) {

    public static ImpactOptions defaults() {
        return new ImpactOptions(10, false, 500);
    }
}
