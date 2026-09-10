package com.codelens.api.dto;

import com.codelens.graph.EntityRef;
import com.codelens.metrics.ModuleMetrics;

import java.util.List;
import java.util.Map;

public record OverviewDto(Stats stats, Map<String, Integer> kinds, Map<String, Integer> roles, RiskDistribution risk,
                          List<HotspotDto> topRisks, List<ModuleMetrics> modules, List<List<EntityRef>> cycles) {

    public record Stats(long files, long loc, long parseErrors, int types, int methods, int endpoints, int tests,
                        int edges, int modules, int packages) {
    }

    public record RiskDistribution(int low, int medium, int high) {
    }
}
