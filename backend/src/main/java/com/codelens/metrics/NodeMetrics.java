package com.codelens.metrics;

public record NodeMetrics(
        long entityId,
        int fanIn,
        int fanOut,
        int dependents,
        int dependencies,
        int depth,
        int complexity,
        boolean exposed,
        double riskScore
) {
}
