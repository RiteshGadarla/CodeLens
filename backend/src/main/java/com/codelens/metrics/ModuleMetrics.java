package com.codelens.metrics;

import com.codelens.domain.MetricLevel;

// Martin's package metrics: Ca, Ce, I, A, D
public record ModuleMetrics(
        MetricLevel level,
        String name,
        int entities,
        int afferent,
        int efferent,
        double instability,
        double abstractness,
        double distance
) {
}
