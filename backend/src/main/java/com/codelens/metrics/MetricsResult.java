package com.codelens.metrics;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public record MetricsResult(
        List<NodeMetrics> nodes,
        List<ModuleMetrics> modules,
        List<List<Long>> cycles,
        RiskScorer.Scale scale
) {

    public Map<Long, NodeMetrics> byEntityId() {
        return nodes.stream().collect(Collectors.toMap(NodeMetrics::entityId, Function.identity()));
    }
}
